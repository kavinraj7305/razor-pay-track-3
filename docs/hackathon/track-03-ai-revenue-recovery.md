# Track 03 — AI Revenue Recovery

Official Razorpay hackathon track. Stored here so the demo stays aligned with how this track is judged.

## Official brief

**Find revenue that’s slipping away and win it back**

Build an agent that detects revenue at risk, determines the right intervention, and executes a bounded recovery workflow: from payment failures and checkout abandonment to overdue receivables.

**Why now.** Revenue loss rarely happens in one clean step. A payment degrades, a checkout gets abandoned, a subscription fails, or an invoice goes overdue. AI can now close the loop from detecting the problem to diagnosing it, choosing the right intervention, and recovering the money.

**Example directions**

- Payment degradation → root cause → recovery action
- Checkout drop-off recovery
- Failed-subscription recovery
- B2B receivables chaser
- Mandate retry sequencer
- Hinglish voice recovery
- Promise-to-pay tracker

**The bar.** Don’t just identify the problem. Show **measured money recovered across a batch**, with **compliant escalation**, **stopping rules**, and an **audit trail**.

This is a hard track because detection alone loses. Judges want: detect → diagnose → act → stop → prove ₹ recovered.

---

## What “winning” looks like

One batch demo, not a single `card_declined` row.

| Judge asks | You must show |
|---|---|
| What slipped away? | Batch of `recovery_case` with `amount_at_risk` |
| Why? | Razorpay `error_reason` / source (payment, subscription, invoice, checkout) |
| What did the agent do? | Different `recovery_action` per reason (retry vs pay-link vs PTP vs escalate) |
| Did it stop? | Max retries, no retry on risk/cancel, human approval above threshold |
| Did money come back? | `recovery_outcome.amount_recovered` vs `amount_at_risk` (recovery rate %) |
| Can we trust it? | `audit_event` for every detect / action / close |

Our schema already has this: `recovery_case`, `recovery_action`, `recovery_outcome`, `recovery_policy`, `promise_to_pay`, `audit_event`, `checkout_session`.

---

## Cases to cover (map track → Razorpay → our tables)

Do **8 live demo cases**. That covers every example direction except Hinglish voice (optional extra).

### A. Payment degradation → root cause → action (Track core)

Same webhook `payment.failed`, **different reason → different action**. This is the “don’t treat all fails as card declined” story.

| # | Scenario | Razorpay | Agent does | Stop rule |
|---|---|---|---|---|
| 1 | Insufficient funds | `error_reason=insufficient_funds` | `RETRY_PAYMENT` later (payday) | Max 3 retries |
| 2 | Expired card | `card_expired` | `SEND_PAYMENT_LINK` (update card) | Stop after link + 1 nudge |
| 3 | Wrong OTP / 3DS | `incorrect_otp` | Soft retry checkout now | Stop if `otp_attempts_exceeded` |
| 4 | Gateway / bank down | `gateway_technical_error` | Retry later (transient) | Stop after policy retries |
| 5 | Risk / fraud | `payment_risk_check_failed` | **Do not auto-retry** — escalate | Immediate stop + audit |
| 6 | Customer cancelled | `payment_cancelled` | Low-priority email only | No retry |

Webhook: `POST /webhooks/razorpay` event `payment.failed`.

### B. Failed-subscription recovery

| # | Scenario | Razorpay | Agent does |
|---|---|---|---|
| 7 | Recurring fail, still retrying | `subscription.pending` | Sequence retries (mandate retry sequencer) |
| 8 | Retries exhausted | `subscription.halted` | Escalate: pay-link + HIGH/CRITICAL case |

### C. B2B receivables / overdue invoice

| # | Scenario | Razorpay | Agent does |
|---|---|---|---|
| 9 | Invoice never paid | `invoice.expired` | Receivables chaser: email/SMS → PTP |

### D. Checkout drop-off

| # | Scenario | How we get it | Agent does |
|---|---|---|---|
| 10 | Checkout abandoned | `checkout_session` status `ABANDONED` (Razorpay has no perfect webhook; simulate or payment-link expired) | `SEND_PAYMENT_LINK` / cart recovery |

### E. Promise-to-pay tracker (closes the loop)

| # | Scenario | Agent does | Outcome |
|---|---|---|---|
| 11 | Customer promises Friday | `REQUEST_PROMISE_TO_PAY` → `promise_to_pay` | Status `PROMISE_TO_PAY` |
| 12 | Promise kept | `payment.captured` / `invoice.paid` | `PAYMENT_RECOVERED`, case `RECOVERED` |
| 13 | Promise broken | Due date passed, no pay | Escalate or `RECOVERY_FAILED` |

### F. The bar (must show on the same batch)

| # | What | Where |
|---|---|---|
| 14 | Money recovered across batch | Sum `amount_recovered` / sum `amount_at_risk` |
| 15 | Compliant escalation | High amount → `human_approval_threshold` on `recovery_policy` |
| 16 | Stopping rules | `max_payment_retries`; never retry risk/cancelled |
| 17 | Audit trail | `audit_event` rows per case |

Success webhooks that **prove recovery**: `payment.captured`, `order.paid`, `subscription.charged`, `invoice.paid`.

---

## Demo batch (what to click in 5 minutes)

Run these simulates so judges see a mixed book, not one reason:

1. `insufficient_funds` → retry scheduled  
2. `card_expired` → payment link  
3. `payment_risk_check_failed` → **no retry** (stopping rule)  
4. `subscription.pending` → retry sequencer  
5. `subscription.halted` → escalate  
6. `invoice.expired` → receivables chase  
7. checkout abandoned → pay-link  
8. `payment.captured` on case 1 → recovered ₹  

Dashboard: **₹ at risk / ₹ recovered / recovery % / cases stopped / audit lines**.

Hinglish voice is optional flavour (call script / TTS). Do not depend on it to win; the bar is measured recovery + stops + audit.

---

## What we already have vs still need

| Piece | Status |
|---|---|
| Detect `payment.failed` webhook → `recovery_case` | Built |
| Vary `error_reason` on simulate | Not yet (always `card_declined`) |
| `subscription.pending` / `halted` / `invoice.expired` simulate | Ingest supports it; simulate API does not yet |
| Checkout abandonment ingest | Schema only |
| Agent chooses action from reason | Not built |
| Policy stop rules + human threshold | Schema only (`recovery_policy`) |
| Promise-to-pay | Schema only |
| Outcome ₹ recovered | Schema only |
| Audit trail writes | Schema only |
| Batch recovery dashboard | Not built |

---

## Do not waste time on

- All 80+ Razorpay `error_reason` values  
- Live-mode keys  
- Polling Razorpay REST as the main path (webhook is the detect loop)

Cover **reason groups** + **subscription** + **invoice** + **checkout** + **PTP** + **close with captured** + **batch metrics**.
