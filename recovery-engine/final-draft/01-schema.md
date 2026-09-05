# 1. Schema

The product starts as a ledger. A failed payment is not a chat. It is a row with a reason, rupees at risk, a planned action, and an audit trail.

Flyway applies four migrations on first boot. The 13 Phase 1 tables do not change after that. Inbox, seats, and signed intake are additive.

## What the book holds

**Who owns the money**

| Table | Why it exists |
|---|---|
| `merchant` | The account the payment belongs to |
| `customer` | Who we may chase |
| `desk_user` | Two seats: CEO and human in the loop |

**What failed**

| Table | Why it exists |
|---|---|
| `payment` | A charge that failed, authorised, or came back |
| `payment_attempt` | Each try and its failure code |
| `subscription` | Mandate / recurring debit |
| `invoice` | B2B receivable |
| `checkout_session` | Abandoned checkout |
| `webhook_event` | Raw event inbox. How it arrived (desk or HMAC-signed) |

**What we do about it**

| Table | Why it exists |
|---|---|
| `recovery_case` | The problem: reason, amount at risk, status |
| `recovery_action` | The move: retry, pay link, promise-to-pay, email, SMS |
| `recovery_outcome` | Did money come back |
| `recovery_policy` | Max retries, human-approval threshold |
| `promise_to_pay` | Schema ready for a dated promise |
| `audit_event` | Every detect, score, propose, block, execute, close |

## Case and action states

`recovery_case.status`

`OPEN` → `ACTION_PLANNED` → `RECOVERING` → `RECOVERED`  
or `FAILED` / `EXPIRED` / `PROMISE_TO_PAY`

`recovery_action.status`

`PLANNED` → `APPROVED` → `EXECUTING` → `EXECUTED`  
or `FAILED` / `CANCELLED`

A case is unique on `source` + `source_id`. The same payment does not open twice. `payment.captured` does not open a new case. It finds the matching open case and marks it recovered.

`webhook_event.intake` is `DESK_SIMULATE` or `HMAC_SIGNED`. HMAC is stamped on the inbox row before Kafka, so a signed receipt exists even if ingest is still catching up.

## Ingest write order

1. `webhook_event`
2. `merchant` upsert
3. `customer` upsert
4. `payment` / `checkout_session` / `subscription` / `invoice`
5. `recovery_case`
6. `recovery_action` (first move from the reason planner)
7. `audit_event`
8. case status, usually `ACTION_PLANNED`

## SQL — V1 Phase 1 (13 tables)

Source: `backend/revenue-recovery/src/main/resources/db/migration/V1__create_phase1_schema.sql`

```sql
CREATE TABLE merchant (
    id              BIGSERIAL PRIMARY KEY,
    merchant_id     VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    default_currency CHAR(3) NOT NULL DEFAULT 'INR',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_merchant_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE customer (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     VARCHAR(50) NOT NULL UNIQUE,
    merchant_id     VARCHAR(50) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(30),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id),
    CONSTRAINT chk_customer_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE payment (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      VARCHAR(50) NOT NULL UNIQUE,
    merchant_id     VARCHAR(50) NOT NULL,
    customer_id     VARCHAR(50),
    amount          NUMERIC(19,2) NOT NULL,
    currency        CHAR(3) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    payment_type    VARCHAR(30),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id),
    CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT chk_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_status CHECK (status IN (
        'CREATED', 'PENDING', 'AUTHORIZED', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED'
    ))
);

CREATE TABLE payment_attempt (
    id              BIGSERIAL PRIMARY KEY,
    attempt_id      VARCHAR(50) NOT NULL UNIQUE,
    payment_id      VARCHAR(50) NOT NULL,
    attempt_number  INT NOT NULL,
    status          VARCHAR(30) NOT NULL,
    failure_code    VARCHAR(100),
    failure_message VARCHAR(500),
    attempted_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attempt_payment FOREIGN KEY (payment_id) REFERENCES payment(payment_id),
    CONSTRAINT chk_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT chk_attempt_status CHECK (status IN ('INITIATED', 'PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT uq_payment_attempt_number UNIQUE (payment_id, attempt_number)
);

CREATE TABLE subscription (
    id                  BIGSERIAL PRIMARY KEY,
    subscription_id     VARCHAR(50) NOT NULL UNIQUE,
    merchant_id         VARCHAR(50) NOT NULL,
    customer_id         VARCHAR(50) NOT NULL,
    amount              NUMERIC(19,2) NOT NULL,
    currency            CHAR(3) NOT NULL,
    billing_interval    VARCHAR(30) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    next_billing_at     TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_subscription_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id),
    CONSTRAINT fk_subscription_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT chk_subscription_amount CHECK (amount > 0),
    CONSTRAINT chk_subscription_interval CHECK (billing_interval IN (
        'DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY'
    )),
    CONSTRAINT chk_subscription_status CHECK (status IN (
        'ACTIVE', 'PAUSED', 'PAST_DUE', 'CANCELLED', 'EXPIRED'
    ))
);

CREATE TABLE invoice (
    id              BIGSERIAL PRIMARY KEY,
    invoice_id      VARCHAR(50) NOT NULL UNIQUE,
    merchant_id     VARCHAR(50) NOT NULL,
    customer_id     VARCHAR(50) NOT NULL,
    subscription_id VARCHAR(50),
    amount          NUMERIC(19,2) NOT NULL,
    amount_paid     NUMERIC(19,2) NOT NULL DEFAULT 0,
    currency        CHAR(3) NOT NULL,
    issued_at       TIMESTAMP NOT NULL,
    due_date        TIMESTAMP NOT NULL,
    status          VARCHAR(30) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoice_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id),
    CONSTRAINT fk_invoice_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT fk_invoice_subscription FOREIGN KEY (subscription_id) REFERENCES subscription(subscription_id),
    CONSTRAINT chk_invoice_amount CHECK (amount > 0),
    CONSTRAINT chk_invoice_amount_paid CHECK (amount_paid >= 0 AND amount_paid <= amount),
    CONSTRAINT chk_invoice_status CHECK (status IN (
        'DRAFT', 'OPEN', 'PAID', 'PARTIALLY_PAID', 'OVERDUE', 'CANCELLED'
    ))
);

CREATE TABLE checkout_session (
    id                  BIGSERIAL PRIMARY KEY,
    checkout_session_id VARCHAR(50) NOT NULL UNIQUE,
    merchant_id         VARCHAR(50) NOT NULL,
    customer_id         VARCHAR(50),
    amount              NUMERIC(19,2) NOT NULL,
    currency            CHAR(3) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    started_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP,
    abandoned_at        TIMESTAMP,
    CONSTRAINT fk_checkout_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id),
    CONSTRAINT fk_checkout_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT chk_checkout_amount CHECK (amount > 0),
    CONSTRAINT chk_checkout_status CHECK (status IN (
        'CREATED', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED', 'EXPIRED'
    ))
);

CREATE TABLE recovery_case (
    id              BIGSERIAL PRIMARY KEY,
    case_id         VARCHAR(50) NOT NULL UNIQUE,
    merchant_id     VARCHAR(50) NOT NULL,
    customer_id     VARCHAR(50),
    source          VARCHAR(50) NOT NULL,
    source_id       VARCHAR(50) NOT NULL,
    amount_at_risk  NUMERIC(19,2) NOT NULL,
    currency        CHAR(3) NOT NULL,
    reason          VARCHAR(100) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at       TIMESTAMP,
    CONSTRAINT fk_recovery_case_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id),
    CONSTRAINT fk_recovery_case_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT chk_recovery_amount CHECK (amount_at_risk > 0),
    CONSTRAINT chk_recovery_status CHECK (status IN (
        'OPEN', 'ANALYZING', 'ACTION_PLANNED', 'RECOVERING',
        'RECOVERED', 'PROMISE_TO_PAY', 'FAILED', 'EXPIRED'
    )),
    CONSTRAINT chk_recovery_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE TABLE recovery_action (
    id              BIGSERIAL PRIMARY KEY,
    action_id       VARCHAR(50) NOT NULL UNIQUE,
    case_id         VARCHAR(50) NOT NULL,
    action_type     VARCHAR(50) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    attempt_number  INT,
    reason          VARCHAR(500),
    executed_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_action_case FOREIGN KEY (case_id) REFERENCES recovery_case(case_id),
    CONSTRAINT chk_action_status CHECK (status IN (
        'PLANNED', 'APPROVED', 'EXECUTING', 'EXECUTED', 'FAILED', 'CANCELLED'
    ))
);

CREATE TABLE recovery_outcome (
    id                  BIGSERIAL PRIMARY KEY,
    outcome_id          VARCHAR(50) NOT NULL UNIQUE,
    case_id             VARCHAR(50) NOT NULL,
    result              VARCHAR(50) NOT NULL,
    amount_recovered    NUMERIC(19,2) NOT NULL DEFAULT 0,
    currency            CHAR(3) NOT NULL,
    resolution_reason   VARCHAR(500),
    resolved_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_outcome_case FOREIGN KEY (case_id) REFERENCES recovery_case(case_id),
    CONSTRAINT chk_recovered_amount CHECK (amount_recovered >= 0),
    CONSTRAINT chk_outcome_result CHECK (result IN (
        'PAYMENT_RECOVERED', 'PARTIALLY_RECOVERED', 'PROMISE_TO_PAY',
        'CUSTOMER_DECLINED', 'RECOVERY_FAILED', 'EXPIRED'
    ))
);

CREATE TABLE recovery_policy (
    id                          BIGSERIAL PRIMARY KEY,
    policy_id                   VARCHAR(50) NOT NULL UNIQUE,
    merchant_id                 VARCHAR(50) NOT NULL,
    max_payment_retries         INT NOT NULL DEFAULT 3,
    max_discount_percentage     NUMERIC(5,2) NOT NULL DEFAULT 0,
    human_approval_threshold    NUMERIC(19,2),
    allowed_actions             JSONB NOT NULL,
    policy_version              INT NOT NULL DEFAULT 1,
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_policy_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id),
    CONSTRAINT chk_max_retries CHECK (max_payment_retries >= 0),
    CONSTRAINT chk_discount CHECK (max_discount_percentage >= 0 AND max_discount_percentage <= 100),
    CONSTRAINT uq_active_policy UNIQUE (merchant_id, policy_version)
);

CREATE TABLE promise_to_pay (
    id                  BIGSERIAL PRIMARY KEY,
    promise_id          VARCHAR(50) NOT NULL UNIQUE,
    case_id             VARCHAR(50) NOT NULL,
    customer_id         VARCHAR(50) NOT NULL,
    promised_amount     NUMERIC(19,2) NOT NULL,
    currency            CHAR(3) NOT NULL,
    promised_date       TIMESTAMP NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fulfilled_at        TIMESTAMP,
    CONSTRAINT fk_ptp_case FOREIGN KEY (case_id) REFERENCES recovery_case(case_id),
    CONSTRAINT fk_ptp_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT chk_ptp_amount CHECK (promised_amount > 0),
    CONSTRAINT chk_ptp_status CHECK (status IN (
        'PENDING', 'FULFILLED', 'PARTIALLY_FULFILLED', 'BROKEN', 'CANCELLED'
    ))
);

CREATE TABLE audit_event (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(50) NOT NULL UNIQUE,
    case_id         VARCHAR(50),
    event_type      VARCHAR(100) NOT NULL,
    actor_type      VARCHAR(50) NOT NULL,
    actor_id        VARCHAR(100),
    action          VARCHAR(100),
    details         JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_case FOREIGN KEY (case_id) REFERENCES recovery_case(case_id)
);
```

## SQL — V2 webhook inbox

Source: `V2__create_webhook_event.sql`

```sql
CREATE TABLE webhook_event (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(50) NOT NULL UNIQUE,
    provider        VARCHAR(30) NOT NULL DEFAULT 'RAZORPAY',
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    received_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed       BOOLEAN NOT NULL DEFAULT FALSE
);
```

## SQL — V3 desk seats

Source: `V3__desk_user_roles.sql`

```sql
CREATE TABLE desk_user (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(50) NOT NULL UNIQUE,
    email           VARCHAR(120) NOT NULL UNIQUE,
    display_name    VARCHAR(120) NOT NULL,
    password_hash   VARCHAR(200) NOT NULL,
    role            VARCHAR(20) NOT NULL,
    session_token   VARCHAR(80),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_desk_user_role CHECK (role IN ('ADMIN', 'APPROVER', 'OPERATOR'))
);
```

`OPERATOR` remains in the check constraint so old rows do not break. The product uses two seats: `ADMIN` (CEO) and `APPROVER` (human in the loop). Leftover operator rows are marked inactive on boot.

## SQL — V4 signed intake

Source: `V4__webhook_intake_hmac.sql`

```sql
ALTER TABLE webhook_event
    ALTER COLUMN event_id TYPE VARCHAR(80);

ALTER TABLE webhook_event
    ADD COLUMN intake VARCHAR(30) NOT NULL DEFAULT 'DESK_SIMULATE';

ALTER TABLE webhook_event
    ADD COLUMN signature_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE webhook_event
    ADD CONSTRAINT chk_webhook_intake
        CHECK (intake IN ('DESK_SIMULATE', 'HMAC_SIGNED'));
```
