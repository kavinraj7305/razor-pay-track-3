# Razorpay webhook — what we get and how it is used

This project starts recovery from **real-time failed payments**. Razorpay POSTs an event to our backend the moment a charge fails, a subscription stalls, or money later succeeds. We do not poll their API.

Endpoint: `POST /webhooks/razorpay`  
Code: `recovery-engine/backend/revenue-recovery/.../webhook/`

---

## What we get (envelope)

Every webhook is one JSON event. These top-level fields are always present:

| Field | What it is | How we use it |
|---|---|---|
| `id` | Razorpay event id | Dedup key (`webhook_event.event_id`). Same event twice → ignore. |
| `event` | Event name, e.g. `payment.failed` | Stored as `event_type`. Decides start vs stop recovery. |
| `account_id` | Merchant Razorpay account | Identifies which merchant the event belongs to. |
| `contains` | Which objects are in `payload` | Tells us if payment / order / subscription / invoice is included. |
| `payload` | Nested objects (payment, order, …) | Stored whole as JSONB. Recovery will read this later. |
| `created_at` | Unix timestamp from Razorpay | When Razorpay created the event. |

We also get the HTTP header `X-Razorpay-Signature` (HMAC-SHA256 of the **raw** body). That is not business data — it proves the POST is from Razorpay.

---

## Events we listen to

| Event | What it means | Recovery action (intended) |
|---|---|---|
| `payment.failed` | Card/UPI/netbanking charge failed | **Open / update a recovery case** |
| `subscription.pending` | Recurring charge did not go through | Start recovery on the subscription |
| `subscription.halted` | Razorpay stopped retrying the sub | Escalate — subscription is dead until the customer acts |
| `invoice.expired` | Invoice was never paid | Start recovery on the invoice |
| `payment.captured` | Payment succeeded | Close / mark recovered |
| `order.paid` | Order fully paid | Close / mark recovered |
| `subscription.charged` | Subscription charge succeeded | Close / mark recovered |
| `invoice.paid` | Invoice paid | Close / mark recovered |

Sample payloads live in `recovery-engine/scripts/razorpay/payloads/`.

---

## Fields inside `payload` (the useful recovery data)

We store the **full JSON**. Downstream recovery should read these nested fields.

### Payment (`payload.payment.entity`) — main trigger

| Field | What it is |
|---|---|
| `id` | Payment id (`pay_…`) — source id for a recovery case |
| `amount` | Amount in **paise** (49900 = ₹499.00) |
| `currency` | e.g. `INR` |
| `status` | `failed` / `captured` / … |
| `order_id` | Linked order (`order_…`) |
| `invoice_id` | Linked invoice if any |
| `method` | `card`, `upi`, `netbanking`, … |
| `email` | Customer email |
| `contact` | Customer phone |
| `error_code` | Razorpay error code (`BAD_REQUEST_ERROR`, `GATEWAY_ERROR`, …) |
| `error_description` | Human-readable failure |
| `error_reason` | Why it failed (`payment_failed`, `card_declined`, …) |
| `error_source` | Who caused it (`customer`, `bank`, `gateway`) |
| `error_step` | Where it failed (`payment_authentication`, …) |
| `notes` | Merchant notes we attached when creating the order |

These map later to `recovery_case` (`source`, `source_id`, `amount_at_risk`, `currency`, `reason`) and `payment_attempt` (`failure_code`, `failure_message`).

### Order (`payload.order.entity`) — on `order.paid`

| Field | What it is |
|---|---|
| `id` | Order id |
| `amount` / `amount_paid` | Expected vs paid (paise) |
| `status` | `paid` when settled |

### Subscription (`payload.subscription.entity`)

| Field | What it is |
|---|---|
| `id` | Subscription id (`sub_…`) |
| `plan_id` | Plan that is billing |
| `customer_id` | Razorpay customer (`cust_…`) |
| `status` | `pending` / `halted` / … |
| `quantity` | Seat / quantity |
| `charge_at` | Next charge time (unix) |

### Invoice (`payload.invoice.entity`)

| Field | What it is |
|---|---|
| `id` | Invoice id (`inv_…`) |
| `customer_id` | Who owes |
| `amount` / `amount_paid` | Due vs paid (paise) |
| `status` | `expired` / `paid` / … |
| `receipt` / `invoice_number` | Merchant-facing ids |
| `expired_at` | When it expired |

---

## How this is done here (Step 1.4)

```
Razorpay (or simulate-webhooks.ps1)
        │  POST JSON + X-Razorpay-Signature
        ▼
RazorpayWebhookController   /webhooks/razorpay
        │  1. Signature — HMAC-SHA256(raw body, RAZORPAY_WEBHOOK_SECRET)
        │  2. Schema — malformed envelope/payload → 400
        │  3. Redis SETNX idempotency:webhook:{eventId} TTL 24h
        │     already seen → 200 duplicate=true (drop)
        │  4–5. Publish raw JSON to Kafka, then 200
        │     payment.* / subscription.* → payment.events
        │     invoice.*                 → invoice.events
        │     order.* / checkout        → checkout.events
        ▼
RecoveryEventConsumer (async)
        │  persist webhook_event
        │  payment.failed / subscription.pending|halted / invoice.expired
        │     → INSERT recovery_case (amount_at_risk = paise / 100)
        │  payment.captured / order.paid / invoice.paid / subscription.charged
        │     → close matching OPEN case as RECOVERED
        ▼
Postgres recovery_case   (Day 1 exit: row with correct amountAtRisk)
```

HTTP does **not** create the recovery case. That happens in the Kafka consumer so Razorpay gets a fast 200.

**Idempotency:** Redis SETNX stops double-publish. Consumer skips if a case already exists for the same `source` + `source_id`.

**Amount:** Razorpay `amount` is paise. `amount_at_risk` is rupees (`49900` → `499.00`).

**Local test (no live Razorpay needed):**

```powershell
cd recovery-engine/scripts/razorpay
.\simulate-webhooks.ps1 -Event payment.failed
```

That signs a sample payload with `RAZORPAY_WEBHOOK_SECRET` and POSTs it the same way Razorpay would.

---

## REST API vs webhook (do not mix them)

| | REST (`api.razorpay.com/v1`) | Webhook (`POST /webhooks/razorpay`) |
|---|---|---|
| Direction | We call Razorpay | Razorpay calls us |
| Keys | `RAZORPAY_KEY_ID` + `RAZORPAY_KEY_SECRET` | `RAZORPAY_WEBHOOK_SECRET` |
| Used for | Create customer, order, plan, subscription, payment link (`create-fixtures.ps1`) | Real-time failed / paid events |
| Cannot do | Create a card charge from curl (PCI) | Create orders |

Webhook is the recovery trigger. REST is only for test fixtures and later retries.
