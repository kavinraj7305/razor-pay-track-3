# Winning overlay (on top of the Day 1–6 build guide)

**What to do this minute → [intelligence-layer-plan.md](./intelligence-layer-plan.md)** (Sep 1–5 timeline). Local URLs → [NEXT.md](./NEXT.md).

Created: **1 Sep 2026** (overlay vs Day 1–6 guide)
Last updated: **1 Sep 2026, 19:40 IST**
Why updated: pitch order is playbook first, then ML for customer-level retry.

Use the original Day 1–6 guide as the build order. This file is **only what to add, change, or skip** so Track 03 can actually select you.

**Deadline:** before Sep 5, 2026. **Today:** Sep 1. That is **4 days**, not 6. Do not restart Day 1.

**Track bar (non-negotiable):** detect → diagnose → act → **stop** → **measured ₹ recovered on a batch** → **audit/decision trace**. Identification-only loses.

**What we built, in order (say this):** we first shipped **reason playbooks** (issue → reason → fixed retries / pay-link / stop). That is not enough: the same reason still retries every customer the same way. We then added **ML on existing Postgres tables** so `P(recovery)` is about **this customer** (history, LTV, amount) — retry or skip — while Java still executes. Detail: [intelligence-layer-plan.md](./intelligence-layer-plan.md#why-ml-after-the-playbook-say-this-in-the-video). Hurdles + fixes to say out loud: [hurdles-and-solutions.md](./hurdles-and-solutions.md).

---

## Already done (do not rebuild)

Day 1 skeleton, JPA/Flyway domain, docker Postgres+Redis+Kafka, Razorpay test keys, webhook HMAC + schema 400 + Redis SETNX + Kafka `payment.events` / `invoice.events` / `checkout.events` + async `RecoveryCase` with `amountAtRisk`.

**Day 1 remaining exit:** prove one failure → webhook → Kafka → `recovery_case` row. Use `/api/webhooks/simulate/payment-failed` plus Beekeeper. Then **leave Day 1**.

Do **not** rename tables to match the guide (`PAYMENT_FAILED` vs our `PAYMENT`, `IN_PROGRESS` vs `RECOVERING`). Map in code. A mid-hackathon schema rewrite wastes a day.

---



## Add these (not explicit enough in the original plan)



### 1. Eight demo scenarios (not eight error codes)

Original plan is payment-fail heavy. Track 03 names checkout, subscription, invoice, PTP. Add this pack and simulate each:


| #   | Trigger                                        | Agent/baseline action  | Why it is on the track   |
| --- | ---------------------------------------------- | ---------------------- | ------------------------ |
| 1   | `payment.failed` + `insufficient_funds`        | Delayed retry          | Root cause → action      |
| 2   | `payment.failed` + `card_expired`              | Payment link           | Different action than #1 |
| 3   | `payment.failed` + `payment_risk_check_failed` | **No retry**, escalate | Stopping rule            |
| 4   | `subscription.pending`                         | Retry sequencer        | Failed subscription      |
| 5   | `subscription.halted`                          | Escalate               | Retries exhausted        |
| 6   | `invoice.expired`                              | Chase / promise-to-pay | B2B receivables          |
| 7   | Checkout abandoned                             | Payment link           | Checkout drop-off        |
| 8   | `payment.captured` on case 1                   | Close + ₹ recovered    | Measured money back      |


Keep `card_declined` only as extra. Voice/Hinglish is **not** in this 8.

### 2. Baseline must be reason-aware (small add to Step 2.3)

Guide says: wait 24h → retry → link → stop at 3. **Add branches** or the AI story is weak:

- `insufficient_funds` / gateway error → delayed retry  
- `card_expired` / invalid VPA → payment link (do not retry same instrument)  
- `payment_risk_check_failed` / `payment_cancelled` → **stop immediately**  
- else → original 3-attempt sequence

Without this, AI vs baseline is “both spam retry” and judges will not care.

### 3. Decision trace is the product (force this on Day 4, not as a query later)

One case detail must reconstruct, in order:

`webhook event → diagnosis → ML P(recovery) → agent reason → expected value → policy ALLOW/BLOCK/ESCALATE → action result → ₹ outcome`

Store it: `audit_event` rows + `recovery_action` (`modelVersion`, `policyVersion`, `idempotencyKey`). This is the 2-minute live demo.

### 4. Scoreboard math (add to Day 5 + 6)

Run the same 300–500 synthetic batch through baseline and AI. Report only numbers the simulation actually produced:

- Events evaluated  
- ₹ at risk  
- Baseline recovered ₹  
- AI recovered ₹  
- Incremental %  
- Unnecessary retries (AI minus baseline, should go down)  
- Policy-blocked count  
- Human escalations  
- Audit coverage %  
- **Unresolved / lost cases** (honest exception list — original checklist has this; put it on the scoreboard screen)



### 5. Three failure demos (keep Step 6.2, film all three)

1. Duplicate webhook → one `recovery_case`
2. AI says retry ₹80,000 → policy **BLOCK** → human approval
3. Agent/Claude down → **fallback to baseline** (must actually work, not a slide)



### 6. Agent never executes (say it in the video)

Already in Step 3.3. Add one sentence on screen: “Agent proposes. Executor + policy move money. Agent has no charge tool.” That is the compliance story.

---



## Calendar overlay (Sep 1–5)


| When         | Finish this                                                                                                                   | Exit you can show                                |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| **Sep 1**    | Day 1 exit + Day 2 topics `recovery.events` / `action.events` + Redis cooldown/retry/lock + **baseline with reason branches** | Duplicate webhook → one case, one sequence       |
| **Sep 2**    | Step 3.4 synthetic 400 events **first**, then features + XGBoost `/predict`, then LangGraph agent structured JSON             | curl ML + curl agent independently               |
| **Sep 3**    | EV optimizer + 4–5 policy rules + Workflow A + Workflow B (checkout timeout) + executor/audit                                 | Both workflows + one ₹80k block + decision trace |
| **Sep 4 AM** | 3 screens only: list, **trace**, scoreboard. JWT 2 roles if time                                                              | 5-minute click-through                           |
| **Sep 4 PM** | Run benchmark for real, film video (problem / arch / live trace+block / one bug you fixed / numbers)                          | Unlisted YouTube + README diagram                |
| **Sep 5**    | Public repo, `docker compose up`, submit form                                                                                 | Done                                             |


If Sep 3 Workflow A is not green, **cut Workflow B, PTP, mandate, RBAC, all of TIER 1.5**. Two solid workflows beat seven half ones. Guide already says this — obey it.

---



## Skip (looks impressive, kills the deadline)


| In original plan                                           | Verdict                                                                                                                                         |
| ---------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Hinglish voice                                             | Skip                                                                                                                                            |
| Mandate sequencer + full PTP as extra workflows            | Only if A+B done Sep 3 night                                                                                                                    |
| Vault, RLS, field encryption, tokenization, extra DB roles | Skip for selection. Pitch **HMAC webhooks + Redis idempotency + immutable append-only** `audit_event` **+ agent cannot execute + policy block** |
| 10k synthetic rows                                         | Stay 300–500 labelled                                                                                                                           |
| Settings / user admin UI                                   | Skip                                                                                                                                            |
| Renaming Flyway to match the guide’s column names          | Skip                                                                                                                                            |


Security beat in video (20s): duplicate webhook, policy block, “audit insert-only / agent never charges.” That maps to “compliant escalation and audit trail” without Vault.

---



## Map guide names → this repo (use as-is)


| Guide                                     | This codebase                                                     |
| ----------------------------------------- | ----------------------------------------------------------------- |
| `source=PAYMENT_FAILED`                   | `RecoverySource.PAYMENT`                                          |
| `CHECKOUT_ABANDONED`                      | `CHECKOUT_SESSION`                                                |
| `SUBSCRIPTION_FAILED`                     | `SUBSCRIPTION`                                                    |
| status `IN_PROGRESS` / `LOST`             | `RECOVERING` / `FAILED`                                           |
| Kafka `recovery.events` / `action.events` | **Add these** (Day 2). Keep ingest topics `payment.events` etc.   |
| Redis 4 keys                              | Webhook SETNX exists; **add** cooldown, retry counter, case lock  |
| Customer LTV / success rate               | Add `customer_features` in ml-service (Day 3), do not block Day 2 |


---



## Non-negotiables (if any are missing, do not submit)

- [ ] Baseline vs AI numbers from a **real** batch run  
- [ ] Decision trace on several cases  
- [ ] One policy-blocked unsafe action (₹80k)  
- [ ] Honest unresolved cases on the scoreboard  
- [ ] Duplicate webhook → single case  
- [ ] Agent down → baseline still recovers something  
- [ ] 8-scenario mix in the demo, not only `card_declined`  
- [ ] Public repo + 5-min video + architecture README before Sep 5  

Original Day 1–6 guide = **how to build**. This overlay = **how to get selected**. Follow the guide in order; only add the 8-pack, reason-aware baseline, decision trace, scoreboard exceptions, and the compressed calendar. Cut everything in the skip table if you slip a day.