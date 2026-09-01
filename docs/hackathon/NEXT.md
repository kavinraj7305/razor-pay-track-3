# NEXT — do this now

Last updated: **1 Sep 2026**. Deadline: **before 5 Sep**.

---

## Done

- Day 1 webhook → `recovery_case`
- 8 simulate scenarios
- **Baseline actions** (no Kafka / Redis yet): each new case gets a `recovery_action` + `audit_event`

Restart `bootRun`, then [http://localhost:8080/api/webhooks/simulate/all](http://localhost:8080/api/webhooks/simulate/all)

Beekeeper: `recovery_case` (reason + status) and **`recovery_action`** (`action_type`).

Full flow (API → which tables → how it picks → what is stored): **[reason-to-action.md](./reason-to-action.md)** (flow section at the top, then done/not-done tracker)

| Scenario | Action |
|---|---|
| insufficient_funds | `RETRY_PAYMENT` |
| card_expired | `SEND_PAYMENT_LINK` |
| risk-failed | `SEND_EMAIL` **CANCELLED** (stop) |
| subscription.pending | `RETRY_PAYMENT` |
| subscription.halted | `SEND_PAYMENT_LINK` |
| invoice.expired | `REQUEST_PROMISE_TO_PAY` |
| checkout.abandoned | `SEND_PAYMENT_LINK` |
| payment.captured | audit `RECOVERY_CASE_RECOVERED` |

---

## Next after you confirm actions in Beekeeper

1. **Kafka batch** — move this planner off the HTTP thread onto `recovery.events` / `action.events`
2. **Redis cooldown** — `recovery:cooldown:{customerId}`, retry counter, case lock

Do not start ML or frontend until those two are in.

---

## Not next

Voice, Vault, RLS, 80 error codes.
