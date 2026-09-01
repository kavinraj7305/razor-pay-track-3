# Reason → action (what Beekeeper should show)

`recovery_case.reason` is the diagnosis. `recovery_action.action_type` is the first move. The baseline planner reads **reason** (and `source`) and writes one row in `recovery_action`.

```
API (simulate or webhook)
    → ingest writes recovery_case.reason
    → BaselineActionPlanner.decide()  (Java if/else — not a DB lookup table)
    → writes recovery_action.action_type + status
    → updates recovery_case.status
    → writes audit_event
```

There is **no mapping table** in Postgres. The picker is code: `BaselineActionPlanner.decide()`.

---

## Flow: which API, which tables, what gets stored

### APIs

| API | Who calls it | What it does |
|---|---|---|
| `GET/POST /api/webhooks/simulate/{slug}` | you (local demo) | Builds a fake Razorpay JSON for that slug, then calls ingest **in the same request** |
| `GET/POST /api/webhooks/simulate/all` | you | Runs all 8 slugs |
| `POST /webhooks/razorpay` | Razorpay (or tunnel) | HMAC check → Redis SETNX → Kafka topic → consumer → **same ingest** |

Simulate skips Kafka on purpose so you can see the case + action immediately. Real webhooks go Kafka first; the consumer still calls `RecoveryCaseIngestService.consume()`.

### It does not pick a row from a lookup table

1. Parse the JSON (`event` + `payload`).
2. **Write** a new `recovery_case` (reason copied from `error_reason` or from the event name).
3. **Read that same case in memory** (`reason` + `source`).
4. Run if/else in Java → choose `action_type`.
5. **Write** `recovery_action` + `audit_event`, then **update** `recovery_case.status`.

Duplicate same `source` + `source_id` → skip. No second case, no second action.

### Tables written (in order)

| Step | Table | What is stored | Example |
|---|---|---|---|
| 1 | `webhook_event` | raw event inbox | `event_type=payment.failed`, full JSON |
| 2 | `merchant` | upsert by `account_id` | `acc_test_recovery` |
| 3 | `customer` | upsert by email/contact | `funds.user@example.com` |
| 4a | `payment` | only for `payment.failed` | `pay_fnd_…`, status FAILED |
| 4b | `checkout_session` | only for checkout abandoned | `chk_abd_…`, status ABANDONED |
| 5 | `recovery_case` | the problem | `reason=insufficient_funds`, status starts `OPEN` |
| 6 | `recovery_action` | the first move | `action_type=RETRY_PAYMENT`, status `PLANNED` (or `CANCELLED` if stop) |
| 7 | `audit_event` | why we chose it | `BASELINE_ACTION_PLANNED` or `BASELINE_ACTION_BLOCKED` |
| 8 | `recovery_case` again | status after plan | usually `ACTION_PLANNED`; risk stay `OPEN` |

`payment.captured` skips 4–6 for a new case. It **finds** the existing case by payment id and sets status `RECOVERED`, then writes `audit_event` `RECOVERY_CASE_RECOVERED`.

### Worked example: `GET /api/webhooks/simulate/insufficient-funds`

```
1. API builds JSON: event=payment.failed, error_reason=insufficient_funds, amount=49900 paise
2. webhook_event     ← evt_…
3. merchant/customer ← upsert
4. payment           ← pay_fnd_… FAILED ₹499.00
5. recovery_case     ← reason=insufficient_funds, source=PAYMENT, amount_at_risk=499.00, status=OPEN
6. planner sees reason contains "insufficient_funds"
7. recovery_action   ← RETRY_PAYMENT / PLANNED  ("Transient fail: delayed retry")
8. recovery_case     ← status=ACTION_PLANNED
9. audit_event       ← BASELINE_ACTION_PLANNED
```

Same API, `risk-failed`:

```
5. recovery_case     ← reason=payment_risk_check_failed, amount_at_risk=80000.00, status=OPEN
6. planner sees "payment_risk_check_failed" → STOP
7. recovery_action   ← SEND_EMAIL / CANCELLED
8. recovery_case     ← stays OPEN  (do not retry)
9. audit_event       ← BASELINE_ACTION_BLOCKED
```

### How it “solves” (the if/else)

Reads `recovery_case.reason` (lowercased) and `recovery_case.source`. First match wins:

| If | Then store on `recovery_action` |
|---|---|
| reason has `payment_risk_check_failed` or `payment_cancelled` | `SEND_EMAIL` CANCELLED — stop |
| source is INVOICE or reason has `invoice.expired` | `REQUEST_PROMISE_TO_PAY` PLANNED |
| source is CHECKOUT_SESSION or reason has `checkout.abandoned` | `SEND_PAYMENT_LINK` PLANNED |
| reason has `subscription.halted` | `SEND_PAYMENT_LINK` PLANNED |
| reason has `card_expired` or `invalid_vpa` | `SEND_PAYMENT_LINK` PLANNED |
| reason has `insufficient_funds` / gateway / bank / `subscription.pending` | `RETRY_PAYMENT` PLANNED |
| anything else (`card_declined`, unknown) | `RETRY_PAYMENT` PLANNED (default) |

Nothing is executed yet (no real retry, no real pay-link). Rows are **planned**. Kafka batch + Redis cooldown come next.

### What you should see in Beekeeper

Join case → action. One new case should have one action.

```sql
SELECT c.reason, c.status AS case_status, a.action_type, a.status AS action_status
FROM recovery_case c
LEFT JOIN recovery_action a ON a.case_id = c.case_id
ORDER BY c.opened_at DESC
LIMIT 20;
```

---

## Coverage tracker (done vs not done)

Use this to see what is live. **Done** = simulate slug + case reason stored + planner writes a distinct action. **Partial** = planner branch exists, no simulate. **Not done** = named in the track brief, not wired.

### Payment `error_reason` values

| Reason | Simulate | Planner branch | Distinct action | Status |
|---|---|---|---|---|
| `insufficient_funds` | `/simulate/insufficient-funds` | yes → retry | `RETRY_PAYMENT` | **Done** |
| `card_expired` | `/simulate/card-expired` | yes → pay-link | `SEND_PAYMENT_LINK` | **Done** |
| `payment_risk_check_failed` | `/simulate/risk-failed` | yes → stop | `SEND_EMAIL` CANCELLED | **Done** |
| `payment_cancelled` | no | yes → stop | `SEND_EMAIL` CANCELLED | Partial — no simulate |
| `gateway_technical` / `gateway_technical_error` | no | yes → retry | `RETRY_PAYMENT` | Partial — no simulate |
| `bank_technical` | no | yes → retry | `RETRY_PAYMENT` | Partial — no simulate |
| `invalid_vpa` | no | yes → pay-link | `SEND_PAYMENT_LINK` | Partial — no simulate |
| `incorrect_otp` | no | no (falls to default retry) | default `RETRY_PAYMENT` | **Not done** |
| `otp_attempts_exceeded` | no | no | — | **Not done** |
| `card_declined` | no (old leftover rows only) | no (default retry) | default `RETRY_PAYMENT` | Leftover — not a demo case |

### Non-payment reasons (event type stored as `reason`)

| Reason | Simulate | Planner branch | Distinct action | Status |
|---|---|---|---|---|
| `subscription.pending` | `/simulate/subscription-pending` | yes → retry | `RETRY_PAYMENT` | **Done** |
| `subscription.halted` | `/simulate/subscription-halted` | yes → pay-link | `SEND_PAYMENT_LINK` | **Done** |
| `invoice.expired` | `/simulate/invoice-expired` | yes → PTP | `REQUEST_PROMISE_TO_PAY` | **Done** (action row only; no `promise_to_pay` table write) |
| `checkout.abandoned` | `/simulate/checkout-abandoned` | yes → pay-link | `SEND_PAYMENT_LINK` | **Done** |

### Close-the-loop (not a `reason`, still part of the 8)

| Event | Simulate | What it does | Status |
|---|---|---|---|
| `payment.captured` | `/simulate/payment-captured` | closes matching OPEN/ACTION_PLANNED case → `RECOVERED` | **Done** |
| `order.paid` | no | should close case | **Not done** |
| `subscription.charged` | no | should close case | **Not done** |
| `invoice.paid` | no | should close case | **Not done** |
| Promise kept / broken | no | `promise_to_pay` row + escalate if missed | **Not done** |

### Counts

| Bucket | Count | What it is |
|---|---|---|
| **Done** | 8 | The demo pack judges should see |
| **Partial** | 4 | Planner knows them; add a simulate slug later if time |
| **Not done** | 6 | OTP, extra close events, real PTP tracker |
| Skip on purpose | 80+ Razorpay codes | Do not enumerate |

**Next reasons to add only if there is spare time:** `payment_cancelled` and `gateway_technical_error` (they already have planner branches — simulate is the missing piece). Do **not** add `incorrect_otp` until those two exist.

---

Look at **two tables**, not one:


| Table             | Column        | Meaning                                                      |
| ----------------- | ------------- | ------------------------------------------------------------ |
| `recovery_case`   | `reason`      | Why money is at risk                                         |
| `recovery_case`   | `source`      | PAYMENT / SUBSCRIPTION / INVOICE / CHECKOUT_SESSION          |
| `recovery_case`   | `status`      | OPEN → ACTION_PLANNED → RECOVERED (or stays OPEN if blocked) |
| `recovery_action` | `action_type` | What we will try                                             |
| `recovery_action` | `status`      | PLANNED (do it) or CANCELLED (stop)                          |


If you only look at `recovery_case.reason` you will see `insufficient_funds` / `card_declined` / etc. The action lives on `recovery_action`.

---



## 1. Action types we have in code


| `action_type`            | When we use it                                                          |
| ------------------------ | ----------------------------------------------------------------------- |
| `RETRY_PAYMENT`          | Transient fail — try the same instrument later                          |
| `SEND_PAYMENT_LINK`      | Instrument is dead, checkout dropped, or subscription retries exhausted |
| `REQUEST_PROMISE_TO_PAY` | B2B invoice expired — chase, get a date                                 |
| `SEND_EMAIL`             | Risk / cancel — **do not retry**; escalate only                         |
| `SEND_SMS`               | In the enum, **not used** by baseline yet                               |


---



## 2. The 8 demo cases (this is the product)

These are the scenarios we simulate. Hit `/api/webhooks/simulate/all` or `/{slug}`.


| #   | Slug                   | Webhook event          | `recovery_case.reason`      | `source`         | `action_type`            | Action status | Case status after plan        |
| --- | ---------------------- | ---------------------- | --------------------------- | ---------------- | ------------------------ | ------------- | ----------------------------- |
| 1   | `insufficient-funds`   | `payment.failed`       | `insufficient_funds`        | PAYMENT          | `RETRY_PAYMENT`          | PLANNED       | ACTION_PLANNED                |
| 2   | `card-expired`         | `payment.failed`       | `card_expired`              | PAYMENT          | `SEND_PAYMENT_LINK`      | PLANNED       | ACTION_PLANNED                |
| 3   | `risk-failed`          | `payment.failed`       | `payment_risk_check_failed` | PAYMENT          | `SEND_EMAIL`             | **CANCELLED** | stays **OPEN**                |
| 4   | `subscription-pending` | `subscription.pending` | `subscription.pending`      | SUBSCRIPTION     | `RETRY_PAYMENT`          | PLANNED       | ACTION_PLANNED                |
| 5   | `subscription-halted`  | `subscription.halted`  | `subscription.halted`       | SUBSCRIPTION     | `SEND_PAYMENT_LINK`      | PLANNED       | ACTION_PLANNED                |
| 6   | `invoice-expired`      | `invoice.expired`      | `invoice.expired`           | INVOICE          | `REQUEST_PROMISE_TO_PAY` | PLANNED       | ACTION_PLANNED                |
| 7   | `checkout-abandoned`   | `checkout.abandoned`   | `checkout.abandoned`        | CHECKOUT_SESSION | `SEND_PAYMENT_LINK`      | PLANNED       | ACTION_PLANNED                |
| 8   | `payment-captured`     | `payment.captured`     | *(no new case)*             | PAYMENT          | **none**                 | —             | matching case → **RECOVERED** |


Case 8 does **not** create a new `recovery_action`. It closes the open `insufficient_funds` case from the same `/all` run and writes `audit_event` `RECOVERY_CASE_RECOVERED`.

---



## 3. Reasons that land on `recovery_case`



### Demo reasons (what new rows should have)


| `reason` stored             | Comes from                            | Demo slug              |
| --------------------------- | ------------------------------------- | ---------------------- |
| `insufficient_funds`        | `payload.payment.error_reason`        | `insufficient-funds`   |
| `card_expired`              | `payload.payment.error_reason`        | `card-expired`         |
| `payment_risk_check_failed` | `payload.payment.error_reason`        | `risk-failed`          |
| `subscription.pending`      | event type (not nested payment error) | `subscription-pending` |
| `subscription.halted`       | event type (not nested payment error) | `subscription-halted`  |
| `invoice.expired`           | event type                            | `invoice-expired`      |
| `checkout.abandoned`        | event type                            | `checkout-abandoned`   |




### Extra reasons the planner already understands (not in the 8-pack yet)

Same `payment.failed` webhook, different `error_reason`. If a real webhook (or a later simulate) writes these, baseline already branches:


| `reason`                                        | `action_type`       | Action status | Why                                    |
| ----------------------------------------------- | ------------------- | ------------- | -------------------------------------- |
| `payment_cancelled`                             | `SEND_EMAIL`        | CANCELLED     | Customer cancelled — no retry          |
| `gateway_technical` / `gateway_technical_error` | `RETRY_PAYMENT`     | PLANNED       | Transient — retry later                |
| `bank_technical`                                | `RETRY_PAYMENT`     | PLANNED       | Transient — retry later                |
| `invalid_vpa`                                   | `SEND_PAYMENT_LINK` | PLANNED       | Instrument dead — same as expired card |




### Leftover / default


| `reason`        | What happens                          | Why you see it in Beekeeper                                                                                                                                                                                        |
| --------------- | ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `card_declined` | **default** → `RETRY_PAYMENT` PLANNED | Old simulate always used this. Also nested on subscription payloads, but **subscription cases store** `subscription.pending` **/** `subscription.halted`, not `card_declined`. Old **payment** rows still have it. |
| anything else   | default → `RETRY_PAYMENT` PLANNED     | Unknown reason = retry then link later                                                                                                                                                                             |


`card_declined` is **not** one of the 8 demo case reasons anymore. Those rows are leftover from before the reason-aware simulate. They still get an action: default retry.

---



## 4. Planner rules (order matters)

Code: `BaselineActionPlanner.decide()`. First match wins.


| Priority | If this is true                                                                                                       | Then `action_type`       | Action status | Case status    |
| -------- | --------------------------------------------------------------------------------------------------------------------- | ------------------------ | ------------- | -------------- |
| 1        | reason contains `payment_risk_check_failed` **or** `payment_cancelled`                                                | `SEND_EMAIL`             | CANCELLED     | OPEN (blocked) |
| 2        | `source = INVOICE` **or** reason contains `invoice.expired`                                                           | `REQUEST_PROMISE_TO_PAY` | PLANNED       | ACTION_PLANNED |
| 3        | `source = CHECKOUT_SESSION` **or** reason contains `checkout.abandoned`                                               | `SEND_PAYMENT_LINK`      | PLANNED       | ACTION_PLANNED |
| 4        | reason contains `subscription.halted`                                                                                 | `SEND_PAYMENT_LINK`      | PLANNED       | ACTION_PLANNED |
| 5        | reason contains `card_expired` **or** `invalid_vpa`                                                                   | `SEND_PAYMENT_LINK`      | PLANNED       | ACTION_PLANNED |
| 6        | reason contains `insufficient_funds` **or** `gateway_technical` **or** `bank_technical` **or** `subscription.pending` | `RETRY_PAYMENT`          | PLANNED       | ACTION_PLANNED |
| 7        | else (`card_declined`, unknown, …)                                                                                    | `RETRY_PAYMENT`          | PLANNED       | ACTION_PLANNED |


---



## 5. Join in Beekeeper

```sql
SELECT
  c.case_id,
  c.source,
  c.reason,
  c.status          AS case_status,
  c.amount_at_risk,
  a.action_type,
  a.status          AS action_status,
  a.reason          AS action_note
FROM recovery_case c
LEFT JOIN recovery_action a ON a.case_id = c.case_id
ORDER BY c.opened_at DESC;
```

Expected for a fresh `/simulate/all`:


| `reason`                    | `action_type`             | `action_status`  |
| --------------------------- | ------------------------- | ---------------- |
| `insufficient_funds`        | `RETRY_PAYMENT`           | PLANNED          |
| `card_expired`              | `SEND_PAYMENT_LINK`       | PLANNED          |
| `payment_risk_check_failed` | `SEND_EMAIL`              | CANCELLED        |
| `subscription.pending`      | `RETRY_PAYMENT`           | PLANNED          |
| `subscription.halted`       | `SEND_PAYMENT_LINK`       | PLANNED          |
| `invoice.expired`           | `REQUEST_PROMISE_TO_PAY`  | PLANNED          |
| `checkout.abandoned`        | `SEND_PAYMENT_LINK`       | PLANNED          |
| (captured case, was funds)  | *(existing retry action)* | case `RECOVERED` |


If the `reason` column is almost only `card_declined` + `insufficient_funds`, those are **old payment.failed rows**. New runs should add `card_expired`, `payment_risk_check_failed`, `subscription.pending`, `subscription.halted`, `invoice.expired`, `checkout.abandoned`.

---



## 6. Case status vs action status (do not mix)


| Enum                     | Values we actually write today                                     |
| ------------------------ | ------------------------------------------------------------------ |
| `recovery_case.status`   | `OPEN` (risk stop), `ACTION_PLANNED` (act), `RECOVERED` (captured) |
| `recovery_action.status` | `PLANNED` (will act) or `CANCELLED` (must not act)                 |


`SEND_EMAIL` + `CANCELLED` on the risk case is the **stop rule**. The email type is a placeholder for “escalate to human”; we are not sending mail yet.

---



## 7. What we are not covering (on purpose)

Not 80 Razorpay error codes. Groups only:


| Group                    | Example reasons                                  | Action family   |
| ------------------------ | ------------------------------------------------ | --------------- |
| Transient                | `insufficient_funds`, gateway/bank technical     | retry           |
| Dead instrument          | `card_expired`, `invalid_vpa`                    | payment link    |
| Stop                     | `payment_risk_check_failed`, `payment_cancelled` | no retry        |
| Subscription still alive | `subscription.pending`                           | retry sequencer |
| Subscription dead        | `subscription.halted`                            | payment link    |
| B2B                      | `invoice.expired`                                | promise-to-pay  |
| Checkout                 | `checkout.abandoned`                             | payment link    |
| Unknown / old            | `card_declined`                                  | default retry   |


