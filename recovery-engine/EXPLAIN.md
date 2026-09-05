# Explain this, one by one

Speak this in order. Do not skip ahead. Each step is something that actually exists.

---

## 1. The problem

Failed Razorpay payments do not disappear. A card declines, a mandate misses, a checkout is abandoned, an invoice expires.

Most systems either retry blindly or escalate late. We turn the failure into a **recovery case** and walk a bounded loop.

---

## 2. The rule the whole system follows

Say this, then keep it on screen in your head for every later step:

**Playbook first. ML second (this customer). Agent proposes. Java executes. Agent has no charge tool.**

Never invert that.

---

## 3. The loop

1. **Detect** — a webhook or a desk issue opens a case.
2. **Diagnose** — Java maps the failure reason to a 4-step playbook.
3. **Score** — ML may answer: will *this* customer likely pay?
4. **Propose** — an agent may suggest the next step. It cannot charge.
5. **Guard** — PolicyEngine allows, skips a retry, or blocks.
6. **Human** — if blocked, a person holds it or lets it through.
7. **Act** — Java runs the next playbook step **once**. No silent infinite loop.
8. **Prove** — every detect / score / propose / block / execute / close writes `audit_event`.

This is not a chatbot. It is a two-person recovery desk.

---

## 4. Two people

| Who | Role | What they see |
|---|---|---|
| Priya Shah | CEO (`ADMIN`) | Overview, recovery desk, policy queue |
| Arjun Mehta | Human in the loop (`APPROVER`) | Policy queue only |

There is no operator seat.

Sign in: `ceo@recovery.local` / `admin123` and `policy@recovery.local` / `approve123`.

---

## 5. What is running

Show the platform strip if it is on screen: Redis, Kafka, Postgres — Connected.

| Piece | Port | What it is |
|---|---|---|
| Next.js desk | 3000 | Login, overview, desk, approvals |
| Spring Boot / Java 21 | 8080 | Cases, playbooks, policy, auth. **This is the money engine.** |
| ML service | 8001 | FastAPI + XGBoost. `P(recovery)` |
| Agent service | 8002 | FastAPI + LangGraph. Propose only |
| Postgres | 5432 | Case ledger + audit |
| Redis | 6379 | Duplicate-event lock (one webhook, one case) |
| Kafka | 9092 | Payment / invoice / checkout event bus |

Browser `/api/*` goes to Java. `/agent-api/*` goes to the agent. Java talks to ML, Postgres, Redis, and Kafka.

---

## 6. Day 1 — detect

We started with a ledger, not a model.

Postgres: 13 tables. A failed payment becomes a `recovery_case` with a reason and rupees at risk.

Signed webhook path:

1. Razorpay POST `/webhooks/razorpay`
2. HMAC-SHA256 of the raw body
3. Inbox stamp **before** Kafka (`HMAC_SIGNED`, signature verified)
4. Redis SETNX so the same event cannot open two cases
5. Kafka publish → consumer → same ingest

Desk buttons skip HMAC and Kafka on purpose so the case appears immediately. That is Path B. Do not say the desk button *is* the Razorpay-signed path.

---

## 7. Day 1–2 — a failure becomes a case

Eight failure types on the desk:

1. Insufficient funds
2. Card expired
3. Risk failed
4. Subscription pending
5. Subscription halted
6. Invoice expired
7. Checkout abandoned
8. Payment captured (this one **closes** a matching open case)

Ingest writes, in order: webhook inbox → merchant → customer → payment/source → recovery case → first action → audit.

Same source + source id → no second case. `payment.captured` does not open a new case. It marks the matching one recovered.

---

## 8. Day 2 — playbooks

Java if/else. Not an LLM.

Each reason has a folder and **four steps**. `/execute` runs **one** step, then stops.

Examples you can point at:

- Insufficient funds — wait, retry, retry, then a payment link
- Card expired — do not retry the dead card; send a link
- Risk failed — do not auto-charge; hold for a person
- Checkout abandoned — one pay link, then nudges, then stop

First action comes from `BaselineActionPlanner`. Risk and cancelled start as a stop, not a retry.

DEV retries are built to fail locally so you can walk all four steps without charging a real card.

---

## 9. Day 2 — the desk

Next.js. CEO opens a failure, presses **Start**, and watches Detect → Score → Act.

**Ask agent** writes a proposal. **Start** is what actually runs the playbook.

Waits are shortened so the next step can run. Each step still executes once.

---

## 10. Day 3 — score this customer

The playbook was blind to the person. Two shortfalls got the same three retries.

We did not rip the playbook out. We scored the customer on top of it.

- 500 labelled events on merchant `acc_syn_training` (kept off the live desk)
- XGBoost `POST /predict` → `P(recovery)`
- Below 400 labelled rows → playbook only
- P below 0.25 → skip extra retry
- If ML is down, the playbook still runs

The skip line stays **0.25**. We did not retune it after seeing this batch.

---

## 11. Day 3–4 — the agent proposes

LangGraph case agent: `/propose`.

It can agree with the playbook or deviate. It cannot charge. `actions_available` is propose only.

If the model is down, fallback rules still return a proposal. The process stays up.

Ops briefing can name repeating patterns. Same rule: propose only.

---

## 12. Day 4 — policy and a person

PolicyEngine reads the stored proposal, then:

- **Allow** — Java may run the next step
- **Skip retry** — no extra chase; playbook may continue
- **Block** — nothing is charged; the case waits in the queue

Risk, cancelled, and high-amount cases wait. The other person **Holds it** or **Lets it through**. After a let-through, the CEO starts again and Java continues.

The agent never executes. Java never silently retries forever.

---

## 13. Day 4–5 — two seats and the overview

Login is two people. CEO sees money at risk, why it is stuck, the measured batch, and the platform strip (Redis / Kafka / Postgres).

The other person only sees cases that policy held.

---

## 14. Day 5 — the measured batch

Open `/dashboard`. Point at the three columns. Full spoken walk is [final-draft/09-recover-more.md](./final-draft/09-recover-more.md).

Real run. Seed 42. 500 labelled failures. At risk ₹27,68,443.

**First try always runs.** That try is cheap. P only cuts extra silent retries. High-P risk holds go to a person.

Say this and stop:

> Playbook recovered ₹5.73L. First retry always runs — that try is cheap — then P only cuts extra silent retries on the weakest cards, and high-P risk holds go to a person. Recovered ₹5.86L (+₹12,472). Skipped 18 extra retries. We do not give up people who pay on the first try.

- Playbook: ₹5,73,297 (21%), 200 cases, 250 wasted chases
- First retry + P: ₹5,85,768 (21%), 202 cases, 18 extra retries cut, 2 high-P holds released
- Model: ROC-AUC 0.70 — ranking helper, not a recovery engine

---

## 15. How a case actually moves (say this while Start is running)

1. Failure opens a case.
2. Agent proposal is stored if it was not already.
3. Policy allows, skips, or holds.
4. If held, stop. The other person decides.
5. If allowed, Java runs **this** playbook step once.
6. Wait for the next window (shortened on the desk).
7. Repeat until recovered, stopped, or the playbook ends.
8. Audit has every hop.

---

## 16. Architecture in four boxes

If you need a picture, this is the picture:

```
Razorpay ──HMAC──► Java ──SETNX──► Redis
                     │
                     ├──publish──► Kafka ──► Java consumer ──► case
                     ├──JDBC────► Postgres (ledger + audit)
                     ├──HTTP────► ML :8001   (score, optional)
                     └──HTTP────► Agent :8002 (propose, never charge)

CEO / human ──► Next.js :3000 ──► Java :8080
                              └──► Agent :8002
```

Java is the centre. Redis is the lock. Kafka is the bus. Postgres is the book. ML and the agent are side inputs.

---

## 17. What we did not build (say only if asked)

- Extra Kafka topics
- Redis cooldown / retry locks
- Real Razorpay charges on execute
- All 80+ Razorpay error codes
- A third seat
- Tuning the 0.25 skip line on this batch

---

## 18. One pass on the screens

1. Sign in as CEO.
2. Overview — rupees at risk, platform connected, measured batch sentence.
3. Desk — open insufficient funds or risk failed. Ask agent. Start.
4. Risk / high amount stops. Sign out.
5. Sign in as the other person. Hold or let through.
6. CEO again. Start continues. Captured closes the case.

That is the product, in the order we built it.
