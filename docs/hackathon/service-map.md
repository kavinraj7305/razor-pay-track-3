# Service folder map

Path: `recovery-engine/backend/revenue-recovery/src/main/java/com/razorpayhackthon/revenue_recovery/service/`

Controllers only call these. Each service file stays under **200 lines**.

Not in `service/`: `webhook/` (Razorpay parse/HMAC/payloads), `controller/` (HTTP only), `exception/` (HTTP status mapping).

```
service/
├── webhook/     receive + simulate Razorpay events
├── ingest/      turn an event into a recovery_case
└── plan/        pick a reason handler and write/read the action
    └── handler/ one 4-step folder per reason + DefaultReasonHandler for unknown
```

---

## `service/webhook` — get the event in

Needed so HTTP/Kafka do not write cases themselves. This folder **acks** and **feeds ingest**.

| File | Needed for |
|---|---|
| `RazorpayWebhookService.java` | Real `POST /webhooks/razorpay`: check secret + signature, Redis SETNX (no duplicate event), publish raw JSON to Kafka, return ack |
| `WebhookSimulateService.java` | Local demo `GET /api/webhooks/simulate/...`: build the 8 fake events, call ingest, return caseId + actionType |
| `RecoveryEventConsumer.java` | Kafka listener on `payment.events` / `invoice.events` / `checkout.events` → `RecoveryCaseIngestService.consume()` |

Without this folder: no signed webhook ack, no simulate pack, no consumer.

---

## `service/ingest` — open / close the case

Needed so a webhook JSON becomes rows in Postgres (`webhook_event`, `merchant`, `customer`, `payment` / `checkout_session`, `recovery_case`).

| File | Needed for |
|---|---|
| `RecoveryCaseIngestService.java` | Orchestrator: save inbox, **open** case on fail/pending/halted/expired/abandoned, **close** on captured/paid/charged, then call the planner |
| `RecoveryCaseDraftFactory.java` | Pick `source`, `sourceId`, `reason`, `amountAtRisk`, `priority` from the payload |
| `RecoveryCaseDraft.java` | The draft record (not a Spring bean) |
| `MerchantCustomerService.java` | Upsert `merchant` + `customer` so the case has owners |
| `RecoverySourceWriter.java` | Write `payment` (failed) or `checkout_session` (abandoned) |
| `WebhookPayloadSupport.java` | Shared JSON helpers (id, amount paise→₹, error_reason). Not a bean |

Without this folder: events ack but no `recovery_case`.

---

## `service/plan` — what to do, and show it

Needed so a case is not detection-only. Writes `recovery_action` + `audit_event`, and serves the action-plan API.

| File | Needed for |
|---|---|
| `BaselineActionPlanner.java` | **Picks** the first matching reason handler and **writes** `recovery_action` + `audit_event` |
| `PlannedDecision.java` | The action the handler chose (type, status, note) |
| `handler/BaselineReasonHandler.java` | Interface: `supports(case)` + `decide(case)` + `executeNext(case)` |
| `handler/PlaybookRunner.java` | Shared 4-step execute (reuse planned row, bump attempt) |
| `handler/DevPlaybookOps.java` | DEV retry / SMS / pay-link / block helpers |
| `handler/insufficientfunds/` | `insufficient_funds` — 3 retries then pay-link |
| `handler/riskfailed/` | `payment_risk_check_failed` — block, ops SMS, wait, stop |
| `handler/paymentcancelled/` | `payment_cancelled` — block, low-pri SMS, wait, stop |
| `handler/cardexpired/` | `card_expired` — pay-link, SMS, nudge, stop (no retry) |
| `handler/invalidvpa/` | `invalid_vpa` — same as expired card |
| `handler/gatewaytechnical/` | `gateway_technical` / `bank_technical` — 3 retries then pay-link |
| `handler/subscriptionpending/` | `subscription.pending` — 3 retries then warning SMS |
| `handler/subscriptionhalted/` | `subscription.halted` — pay-link, SMS, nudge, stop |
| `handler/invoiceexpired/` | `invoice.expired` — PTP chase, follow-up, escalate, stop |
| `handler/checkoutabandoned/` | `checkout.abandoned` — pay-link, SMS, nudge, stop |
| `handler/DefaultReasonHandler.java` | Unknown reasons (`card_declined`, …) — plan retry, no `/execute` playbook |
| `RecoveryActionPlanService.java` | Read plan; `/{caseId}/plan` and `/{caseId}/execute` |
| `notify/DevSmsService.java` | Free DEV SMS (logs only, no paid gateway) |
| `retry/DevPaymentRetryService.java` | Free DEV retry (no Razorpay charge; always fails so you can walk all 4 steps) |

Without this folder: cases exist with no `action_type`, and the plan API has nothing to return.

---

## Call order (who needs whom)

```
controller
  → service.webhook.RazorpayWebhookService     (or WebhookSimulateService)
      → Kafka → RecoveryEventConsumer          (simulate skips Kafka, calls ingest directly)
          → service.ingest.RecoveryCaseIngestService
              → draft + merchant/customer + payment/checkout
              → service.plan.BaselineActionPlanner   (writes recovery_action)
  → service.plan.RecoveryActionPlanService     (GET the plan later)
```

| API | Service folder it uses |
|---|---|
| `POST /webhooks/razorpay` | `webhook` then (async) `ingest` then `plan` |
| `GET /api/webhooks/simulate/{slug}` | `webhook` → `ingest` → `plan` (same request) |
| `GET /api/recovery-cases` | `plan` only (read) |
| `GET /api/recovery-cases/{caseId}` | `plan` only (read) |
