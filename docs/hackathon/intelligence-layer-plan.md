Created: **1 Sep 2026, 15:43 IST**
Reason: lock the remaining calendar after we chose to **skip leftover Day 2 Kafka/Redis topics** and go to the **intelligence layer** (synthetic data → ML → agent). Baseline playbooks are already live; extra async plumbing can wait.

Last updated: **2 Sep 2026, 14:40 IST**
Why updated: desk UI before the agent; agent will use **Ollama**, not Claude.
Deadline: **before 5 Sep 2026**

---

# Intelligence layer plan (Sep 1–5)

Java baseline already **detects, diagnoses, acts, and stops**. Kafka ingest + Redis webhook idempotency already exist. What is missing for the pitch is **P(recovery)**, a **propose-only agent**, and a **measured batch** vs that baseline.

---

## Why ML after the playbook (say this in the video)

**First we shipped reason playbooks.** A case has an issue + a `reason`. Java picks the folder and walks a fixed 4-step sequence: NSF retries N times, expired card gets a pay-link, risk **stops**. `/execute` still does that. The playbook answers **what action** for that reason.

**Then we hit the gap.** Two `insufficient_funds` customers get the **same** retries. The playbook does not know the customer: pay history, LTV, whether this fail usually comes back. Blind retries waste attempts on people who never pay, and under-chase people who would.

**So we introduced the ML system.** Same existing Postgres tables (no new table): labelled batch on merchant `acc_syn_training` (`recovery_case` + `payment` history + `recovery_outcome`). That trains `/predict` → **P(recovery)** = should we retry *this customer* or not. Expected value = `P(recovery) × amount`. Policy can ALLOW/BLOCK (₹80k risk). Java still executes; the model only scores.

| Layer | Answers |
|---|---|
| Playbook (Java, already live) | Given this **reason**, which steps (retry / link / stop) |
| ML (`P(recovery)`) | Given this **customer + reason + amount**, is a retry worth it |
| Agent | Propose JSON only — no charge tool |
| Policy | Hard stop even if the model or agent says retry |

Pitch sentence: **Playbook first. ML second, so retry is about the customer, not only the error code.**

Full Q&A card (hurdle → fix → what to say): [hurdles-and-solutions.md](./hurdles-and-solutions.md).

## Doc header rule (use on every new file under `docs/`)

Put these two lines at the top of any new markdown:

```
Created: <date>, <time IST>
Reason: <one sentence — why this file exists in the repo>
```

When you edit an existing doc, add **Last updated:** same date + time, and if the edit changes the plan, one line **Why updated:**.

---

## What we are not doing next

Do **not** start these until intelligence curl-works (or leftover hours on Sep 4):

| Leftover Day 2 item | Why it can wait |
|---|---|
| Kafka `recovery.events` / `action.events` + versioned envelope | Ingest already uses `payment.events` / `invoice.events` / `checkout.events`. New topics only move planner off HTTP. |
| Redis `recovery:cooldown:{customerId}` | Demo stop-rules already live in playbooks (risk/cancel). |
| Redis `recovery:retry:{caseId}` | Attempt number already on `recovery_action`. |
| Redis `lock:recovery:{caseId}` | Single local consumer today; no dual-worker demo. |
| Auto-run all 4 steps on ingest | `/execute` is enough for the live walk. |

**Already counts as Day 2 done for judges:** reason-aware baseline, duplicate webhook → one case (SETNX + `source`+`sourceId`), audit rows, `/plan` + `/execute`.

---

## Timeline

Today is **Tue 1 Sep 2026**. Submit **before Fri 5 Sep 2026**.

| When | Date | Finish | Exit you can show |
|---|---|---|---|
| Now (afternoon) | **1 Sep, ~15:45 IST** | This plan in repo; NEXT.md points here | Team agrees: ML next, not new Kafka topics |
| Tonight | **1 Sep, 16:00–22:00 IST** | **Step 3.4** synthetic generator: **300–500** labelled events, 2 workflows (payment/sub fail + checkout abandon). Ground truth: did they pay. Mix of our 8 reasons (NSF, expired card, risk, pending, halted, invoice, abandoned, captured). Not 10k rows. | CSV/JSON in repo; counts per reason; `paid_eventually` column |
| Morning | **2 Sep, 09:00–14:00 IST** | **Step 3.1** features: `paymentSuccessRate`, `paymentFailureRate`, `avgPaymentDelay`, `historicalRecoveryRate`, `subscriptionAgeMonths`, `lifetimeValue`, `avgOrderValue`, `retryHistoryCount`, `daysSinceLastActivity`. Store `customer_features` **or** Redis `customer:features:{id}`. | Script/job refreshes features from synthetic (or Postgres) |
| Afternoon | **2 Sep, 14:00–19:00 IST** | **Step 3.2** FastAPI + XGBoost `POST /predict` → `P(recovery)`. Train/test split. Report Precision, Recall, F1, ROC-AUC, PR-AUC. Input = features + amount, reason, method, retry count, time since fail. | `curl` `/predict` without Java |
| Evening | **2 Sep, 19:00–23:00 IST** | **Step 3.3** LangGraph + Claude. Tools: getPayment/Customer/Subscription/history/invoice/checkout/recoveryHistory, **predictRecovery**, getPolicies, calculateExpectedValue. **No charge/retry/link tool.** Structured JSON only. | `curl` agent; JSON has diagnosis + probability + recommendedAction + reason + confidence |
| Day | **3 Sep** | Wire agent output into `audit_event` (decision trace). 4–5 policy rules (block ₹80k risk retry). EV = P(recovery)×amount. Java still **executes**; agent only **proposes**. Keep 8-scenario demo. | One case: webhook → baseline plan → ML P → agent JSON → policy ALLOW/BLOCK → `/execute` |
| Morning | **4 Sep AM** | Three screens if time: case list, **trace**, scoreboard. Same synthetic batch through **baseline vs AI**. | Click-through + numbers that came from a real run |
| Afternoon | **4 Sep PM** | Film video. Say on camera: **agent proposes, never executes**. Duplicate webhook, one ₹80k block, scoreboard. | Unlisted video + README diagram |
| | **5 Sep** | `docker compose up`, public repo, submit | Done |

If **2 Sep `/predict` is not green**, cut LangGraph polish and ship a thin agent that calls `/predict` + returns JSON. If **3 Sep trace is not green**, cut frontend to one trace page. If **4 Sep AM scoreboard is weak**, run the batch in a script and screenshot numbers — do not invent %.

---

## Day 3 build order (do not invert)

1. **Synthetic batch + ground truth** (everything else trains/measures on this)
2. **Customer features**
3. **`POST /predict`**
4. **Agent structured propose-only**
5. **Audit trace in Java** (agent JSON copied onto `audit_event`, no money tools)

ml-service and agent-service today are `/health` stubs only. Work lives under `recovery-engine/ml-service/` and `recovery-engine/agent-service/`.

### Agent output (hard contract)

```json
{
  "diagnosis": "TEMPORARY_ISSUER_FAILURE",
  "recoveryProbability": 0.87,
  "recommendedAction": "DELAYED_RETRY",
  "reason": "...",
  "confidence": 0.91
}
```

Pitch sentence: **Agent proposes. Java playbooks + policy move money. Agent has no charge tool.**

---

## How this maps to the original Day 2 / Day 3 guide

| Guide step | Status after 1 Sep 19:05 |
|---|---|
| 2.1 Kafka `recovery.events` / `action.events` | **Deferred** (ingest Kafka already exists) |
| 2.2 Redis 4 keys | **1/4 done** (webhook SETNX). Cooldown/retry/lock deferred |
| 2.3 Baseline engine | **Done** (reason folders, 4 steps each, `/execute`) |
| 3.1 Features | **Done** (from Postgres `payment` / `recovery_outcome`; CSV export only) |
| 3.2 XGBoost `/predict` | **Done** (test ROC-AUC in `data/predict_metrics.json`; curl without Java) |
| 3.3 LLM agent | **Paused** — desk UI first. Thin `/propose` exists; next pass is **Ollama** (not Claude) + caseId tools |
| 3.4 Synthetic 300–500 | **Done** (500 cases + 500 customers in Postgres `acc_syn_training`) |

---

## Sticky note (only if spare hours 4 Sep)

- `recovery.events` / `action.events` versioned envelope
- `recovery:cooldown:{customerId}` + `lock:recovery:{caseId}`

Do not open these tickets until `/predict` and agent curl work.
