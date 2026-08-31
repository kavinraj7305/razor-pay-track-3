-- Phase 1 locked schema. Do not change these 13 tables without an explicit new migration.

CREATE TABLE merchant (
    id              BIGSERIAL PRIMARY KEY,
    merchant_id     VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    default_currency CHAR(3) NOT NULL DEFAULT 'INR',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_merchant_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
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

    CONSTRAINT fk_customer_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchant(merchant_id),

    CONSTRAINT chk_customer_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_customer_merchant
    ON customer(merchant_id);

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

    CONSTRAINT fk_payment_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchant(merchant_id),

    CONSTRAINT fk_payment_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id),

    CONSTRAINT chk_payment_amount
        CHECK (amount > 0),

    CONSTRAINT chk_payment_status
        CHECK (
            status IN (
                'CREATED',
                'PENDING',
                'AUTHORIZED',
                'SUCCESS',
                'FAILED',
                'CANCELLED',
                'REFUNDED'
            )
        )
);

CREATE INDEX idx_payment_merchant
    ON payment(merchant_id);

CREATE INDEX idx_payment_customer
    ON payment(customer_id);

CREATE INDEX idx_payment_status
    ON payment(status);

CREATE TABLE payment_attempt (
    id              BIGSERIAL PRIMARY KEY,
    attempt_id      VARCHAR(50) NOT NULL UNIQUE,

    payment_id      VARCHAR(50) NOT NULL,

    attempt_number  INT NOT NULL,

    status          VARCHAR(30) NOT NULL,

    failure_code    VARCHAR(100),
    failure_message VARCHAR(500),

    attempted_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_attempt_payment
        FOREIGN KEY (payment_id)
        REFERENCES payment(payment_id),

    CONSTRAINT chk_attempt_number
        CHECK (attempt_number > 0),

    CONSTRAINT chk_attempt_status
        CHECK (
            status IN (
                'INITIATED',
                'PENDING',
                'SUCCESS',
                'FAILED'
            )
        ),

    CONSTRAINT uq_payment_attempt_number
        UNIQUE (payment_id, attempt_number)
);

CREATE INDEX idx_attempt_payment
    ON payment_attempt(payment_id);

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

    CONSTRAINT fk_subscription_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchant(merchant_id),

    CONSTRAINT fk_subscription_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id),

    CONSTRAINT chk_subscription_amount
        CHECK (amount > 0),

    CONSTRAINT chk_subscription_interval
        CHECK (
            billing_interval IN (
                'DAILY',
                'WEEKLY',
                'MONTHLY',
                'QUARTERLY',
                'YEARLY'
            )
        ),

    CONSTRAINT chk_subscription_status
        CHECK (
            status IN (
                'ACTIVE',
                'PAUSED',
                'PAST_DUE',
                'CANCELLED',
                'EXPIRED'
            )
        )
);

CREATE INDEX idx_subscription_customer
    ON subscription(customer_id);

CREATE INDEX idx_subscription_status
    ON subscription(status);

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

    CONSTRAINT fk_invoice_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchant(merchant_id),

    CONSTRAINT fk_invoice_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id),

    CONSTRAINT fk_invoice_subscription
        FOREIGN KEY (subscription_id)
        REFERENCES subscription(subscription_id),

    CONSTRAINT chk_invoice_amount
        CHECK (amount > 0),

    CONSTRAINT chk_invoice_amount_paid
        CHECK (amount_paid >= 0 AND amount_paid <= amount),

    CONSTRAINT chk_invoice_status
        CHECK (
            status IN (
                'DRAFT',
                'OPEN',
                'PAID',
                'PARTIALLY_PAID',
                'OVERDUE',
                'CANCELLED'
            )
        )
);

CREATE INDEX idx_invoice_customer
    ON invoice(customer_id);

CREATE INDEX idx_invoice_status
    ON invoice(status);

CREATE INDEX idx_invoice_due_date
    ON invoice(due_date);

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

    CONSTRAINT fk_checkout_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchant(merchant_id),

    CONSTRAINT fk_checkout_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id),

    CONSTRAINT chk_checkout_amount
        CHECK (amount > 0),

    CONSTRAINT chk_checkout_status
        CHECK (
            status IN (
                'CREATED',
                'IN_PROGRESS',
                'COMPLETED',
                'ABANDONED',
                'EXPIRED'
            )
        )
);

CREATE INDEX idx_checkout_merchant
    ON checkout_session(merchant_id);

CREATE INDEX idx_checkout_status
    ON checkout_session(status);

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

    CONSTRAINT fk_recovery_case_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchant(merchant_id),

    CONSTRAINT fk_recovery_case_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id),

    CONSTRAINT chk_recovery_amount
        CHECK (amount_at_risk > 0),

    CONSTRAINT chk_recovery_status
        CHECK (
            status IN (
                'OPEN',
                'ANALYZING',
                'ACTION_PLANNED',
                'RECOVERING',
                'RECOVERED',
                'PROMISE_TO_PAY',
                'FAILED',
                'EXPIRED'
            )
        ),

    CONSTRAINT chk_recovery_priority
        CHECK (
            priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        )
);

CREATE INDEX idx_recovery_merchant
    ON recovery_case(merchant_id);

CREATE INDEX idx_recovery_customer
    ON recovery_case(customer_id);

CREATE INDEX idx_recovery_status
    ON recovery_case(status);

CREATE INDEX idx_recovery_source
    ON recovery_case(source, source_id);

CREATE INDEX idx_recovery_priority
    ON recovery_case(priority);

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

    CONSTRAINT fk_action_case
        FOREIGN KEY (case_id)
        REFERENCES recovery_case(case_id),

    CONSTRAINT chk_action_status
        CHECK (
            status IN (
                'PLANNED',
                'APPROVED',
                'EXECUTING',
                'EXECUTED',
                'FAILED',
                'CANCELLED'
            )
        )
);

CREATE INDEX idx_action_case
    ON recovery_action(case_id);

CREATE INDEX idx_action_status
    ON recovery_action(status);

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

    CONSTRAINT fk_outcome_case
        FOREIGN KEY (case_id)
        REFERENCES recovery_case(case_id),

    CONSTRAINT chk_recovered_amount
        CHECK (amount_recovered >= 0),

    CONSTRAINT chk_outcome_result
        CHECK (
            result IN (
                'PAYMENT_RECOVERED',
                'PARTIALLY_RECOVERED',
                'PROMISE_TO_PAY',
                'CUSTOMER_DECLINED',
                'RECOVERY_FAILED',
                'EXPIRED'
            )
        )
);

CREATE INDEX idx_outcome_case
    ON recovery_outcome(case_id);

CREATE TABLE recovery_policy (
    id                          BIGSERIAL PRIMARY KEY,

    policy_id                   VARCHAR(50) NOT NULL UNIQUE,

    merchant_id                 VARCHAR(50) NOT NULL,

    max_payment_retries        INT NOT NULL DEFAULT 3,

    max_discount_percentage    NUMERIC(5,2) NOT NULL DEFAULT 0,

    human_approval_threshold   NUMERIC(19,2),

    allowed_actions             JSONB NOT NULL,

    policy_version              INT NOT NULL DEFAULT 1,

    active                      BOOLEAN NOT NULL DEFAULT TRUE,

    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_policy_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchant(merchant_id),

    CONSTRAINT chk_max_retries
        CHECK (max_payment_retries >= 0),

    CONSTRAINT chk_discount
        CHECK (
            max_discount_percentage >= 0
            AND max_discount_percentage <= 100
        ),

    CONSTRAINT uq_active_policy
        UNIQUE (merchant_id, policy_version)
);

CREATE INDEX idx_policy_merchant
    ON recovery_policy(merchant_id);

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

    CONSTRAINT fk_ptp_case
        FOREIGN KEY (case_id)
        REFERENCES recovery_case(case_id),

    CONSTRAINT fk_ptp_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id),

    CONSTRAINT chk_ptp_amount
        CHECK (promised_amount > 0),

    CONSTRAINT chk_ptp_status
        CHECK (
            status IN (
                'PENDING',
                'FULFILLED',
                'PARTIALLY_FULFILLED',
                'BROKEN',
                'CANCELLED'
            )
        )
);

CREATE INDEX idx_ptp_case
    ON promise_to_pay(case_id);

CREATE INDEX idx_ptp_customer
    ON promise_to_pay(customer_id);

CREATE INDEX idx_ptp_status
    ON promise_to_pay(status);

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

    CONSTRAINT fk_audit_case
        FOREIGN KEY (case_id)
        REFERENCES recovery_case(case_id)
);

CREATE INDEX idx_audit_case
    ON audit_event(case_id);

CREATE INDEX idx_audit_event_type
    ON audit_event(event_type);

CREATE INDEX idx_audit_created_at
    ON audit_event(created_at);
