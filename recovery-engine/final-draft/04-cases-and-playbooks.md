# 4. Recovery cases and playbooks

The planner is code: `BaselineActionPlanner.decide()`. First match wins. There is no mapping table in Postgres.

Each reason then has a four-step folder under `service/plan/handler/`. `/execute` runs **one** step. DEV retries are built to fail locally so the four steps can be walked without charging a live card.

## First action (planner)

| If this is true | First action | After plan |
|---|---|---|
| Risk check failed, or payment cancelled | `SEND_EMAIL` | Action cancelled. Case stays open. No auto-retry |
| Invoice expired | `REQUEST_PROMISE_TO_PAY` | Planned |
| Checkout abandoned | `SEND_PAYMENT_LINK` | Planned |
| Subscription halted | `SEND_PAYMENT_LINK` | Planned |
| Card expired, or invalid VPA | `SEND_PAYMENT_LINK` | Planned |
| Insufficient funds, gateway/bank technical, subscription pending | `RETRY_PAYMENT` | Planned |
| Anything else | `RETRY_PAYMENT` | Planned |

`payment.captured` is not a playbook. It closes the matching open case.

## The eight desk cases

| Slug | Event | Reason | First move |
|---|---|---|---|
| insufficient-funds | `payment.failed` | `insufficient_funds` | Retry, planned |
| card-expired | `payment.failed` | `card_expired` | Payment link, planned |
| risk-failed | `payment.failed` | `payment_risk_check_failed` | Email, cancelled — no auto-charge |
| subscription-pending | `subscription.pending` | `subscription.pending` | Retry, planned |
| subscription-halted | `subscription.halted` | `subscription.halted` | Payment link, planned |
| invoice-expired | `invoice.expired` | `invoice.expired` | Promise-to-pay chase, planned |
| checkout-abandoned | `checkout.abandoned` | `checkout.abandoned` | Payment link, planned |
| payment-captured | `payment.captured` | — | Matching case → recovered |

Also understood by the planner, without a desk button: `payment_cancelled`, `gateway_technical`, `bank_technical`, `invalid_vpa`.

---

## Insufficient funds

The bank said there was not enough money. Wait for payday. Try the same instrument a few times. Then change channel.

| Step | Action | What happens |
|---|---|---|
| 1 | Silent delayed retry | Same instrument, once, after a wait |
| 2 | Second silent retry | Longer wait, one more try |
| 3 | Last auto-retry + SMS | Final debit attempt, then tell the customer |
| 4 | Payment link | Stop retrying this card. Send one link for another method |

## Card expired

The saved card is dead. Another debit on it will fail.

| Step | Action | What happens |
|---|---|---|
| 1 | Payment link | Ask for a new card |
| 2 | SMS nudge | Remind once to update the card |
| 3 | Second SMS | One more nudge |
| 4 | Stop | No more nudges |

## Risk check failed

Fraud or risk blocked the charge. Money stays put until a person signs off.

| Step | Action | What happens |
|---|---|---|
| 1 | Block retry | Do not auto-charge |
| 2 | Notify ops | Record that someone must look |
| 3 | Wait for human | Case sits in the policy queue |
| 4 | Stop | Still blocked unless a person lets it through |

## Payment cancelled

The customer or merchant cancelled. We do not retry a cancelled charge.

| Step | Action | What happens |
|---|---|---|
| 1 | Block retry | Cancelled stays cancelled |
| 2 | Low-priority SMS | Only if they still want to pay |
| 3 | Wait | Do not chase hard |
| 4 | Stop | End |

## Invalid VPA

The UPI address is wrong. Retrying the same VPA will fail.

| Step | Action | What happens |
|---|---|---|
| 1 | Payment link | Ask for a new UPI or method |
| 2 | SMS nudge | Ask for a valid VPA |
| 3 | Second SMS | One more nudge |
| 4 | Stop | No more VPA nudges |

## Gateway / bank technical

A short technical miss. Wait, try the same instrument, then change channel.

| Step | Action | What happens |
|---|---|---|
| 1 | Silent delayed retry | Same instrument, once |
| 2 | Second silent retry | Longer wait, one more try |
| 3 | Last auto-retry | Final debit attempt |
| 4 | Payment link | Stop retrying. One link |

## Subscription pending

The mandate did not go through. Space out a few mandate retries, then warn.

| Step | Action | What happens |
|---|---|---|
| 1 | Silent delayed retry | Same mandate, once |
| 2 | Second silent retry | Next window, once |
| 3 | Last auto-retry | Final debit before warning |
| 4 | Warning SMS | Subscription may halt next |

## Subscription halted

Retries already ran out. Someone has to pick a new mandate.

| Step | Action | What happens |
|---|---|---|
| 1 | Payment link | Update the mandate |
| 2 | SMS nudge | Ask to restart |
| 3 | Second SMS | One more nudge |
| 4 | Stop | No more halt nudges |

## Invoice expired

A B2B invoice timed out. Chase a promise, not a silent card retry.

| Step | Action | What happens |
|---|---|---|
| 1 | Promise-to-pay chase | Ask for a date |
| 2 | Follow-up | If no promise yet |
| 3 | Escalate | Hand toward collections |
| 4 | Stop | Chase ends |

## Checkout abandoned

They left checkout. Send a pay link once. Do not keep charging.

| Step | Action | What happens |
|---|---|---|
| 1 | Payment link | Complete the checkout |
| 2 | SMS nudge | Remind once |
| 3 | Second SMS | One more nudge |
| 4 | Stop | No more checkout nudges |

---

## Why the playbooks differ

Same rupees at risk is not the same problem.

- Temporary shortfall → wait, then retry the same instrument.
- Dead instrument or wrong VPA → new method, never silent-retry the dead one.
- Risk or cancel → no auto-charge.
- Abandoned checkout or halted mandate → one link, then stop.
- Expired invoice → receivables chase, not card retries.

That is the product before scoring and before the agent.
