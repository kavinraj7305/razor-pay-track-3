Created: **2 Sep 2026, 18:35 IST**
Reason: plain-text brief for a senior to review — what is built, what we will build, how — so they can give suggestions.

# Senior review brief — AI Revenue Recovery (Track 03)

Deadline: **before 5 Sep 2026**.  
Repo area: `recovery-engine/` (Java Spring Boot, Python ML, Python agent, Next.js desk).

Pitch we are locking:
**Playbook first. ML second (this customer). Agent proposes. Java executes. Agent has no charge tool.**
The agent is meant to be the intelligence of the system for humans in the loop: what is wrong, why, recurring alerts, proposed solutions — not an autonomous payer.

---

## 1. Problem we are solving

Revenue leaks from payment failures, subscription mandate issues, checkout abandonment, and overdue invoices. Most systems either retry blindly or escalate late. We want:

1. Reason-aware recovery playbooks (deterministic baseline).
2. Customer-specific recovery probability (ML).
3. An agent that explains and proposes (Ollama), never executes money movement.
4. Policy + human gate before action.
5. Full audit / decision trace for explainability.

---

## 2. What we have already built

### 2.1 Intake and cases (Java, port 8080)

- Razorpay webhook endpoint with HMAC verification.
- Redis SETNX so duplicate webhook event IDs do not create duplicate work.
- Kafka ingest topics already used: `payment.events`, `invoice.events`, `checkout.events`.
- Ingest opens/closes `recovery_case` rows in Postgres.
- Eight local simulate scenarios for demo:
  - insufficient_funds
  - card_expired
  - payment_risk_check_failed (₹80k-style risk case)
  - subscription.pending
  - subscription.halted
  - invoice.expired
  - checkout.abandoned
  - payment.captured (close/recover)
- Demo merchant: `acc_test_recovery`.

### 2.2 Baseline playbooks (Java)

- Reason handlers with a fixed 4-step playbook each (retry / pay-link / SMS nudge / stop / PTP chase / risk block).
- APIs: list/get case, `/plan`, `/execute`.
- DEV retry/SMS/pay-link helpers — no real Razorpay charge in local demo (retry intentionally fails so all steps can be walked).
- Audit events written on plan / execute / ML gate outcomes.

### 2.3 Synthetic data + features + ML (Python, port 8001)

- Generator + Postgres seed: **500** labelled recovery cases / customers under merchant `acc_syn_training`.
- Ground truth outcome stored (`PAYMENT_RECOVERED` / `RECOVERY_FAILED`) so metrics are honest.
- Features computed from existing tables (payment success/fail rates, delays, LTV, AOV, subscription age, etc.). No new “features table” required for training; CSV export exists for inspection.
- FastAPI + XGBoost `POST /predict` → `P(recovery)`.
- Train/test split with Precision, Recall, F1, ROC-AUC, PR-AUC recorded.
- Demo check: same NSF reason, weak history vs strong history get different scores.

### 2.4 Data-volume gate (Java)

- On `/execute`: if labelled `recovery_outcome` count is below floor → playbook only (`ML_SKIPPED_LOW_DATA`).
- At/above floor → also call `/predict` (`ML_SCORED`).
- Local floor 400 so the 500-row seed qualifies; prod knob intended as 10,000.
- Low P may skip extra retry only if customer also has enough history payments (so empty demo users still walk playbooks).
- ML down → playbook continues (`ML_PREDICT_UNAVAILABLE`).

### 2.5 Desk UI (Next.js, port 3000)

- Create issues via simulate catalog.
- List demo cases (training merchant excluded so UI is not flooded).
- Case detail: playbook steps + P(recovery).
- “Start recovery process”: live Detect → Score → Act → Done with compressed real-world waits (e.g. T+48h once, T+96h once), What/Why/Outcome panels, story log, audit.
- Fixed so each playbook step runs once (no infinite wait/retry loop).

### 2.6 Thin agent (Python, port 8002) — partial

- `POST /propose` exists.
- LangGraph skeleton: predictRecovery → getPolicies → propose.
- Calls ML `/predict`.
- Returns structured JSON with `executes: false`.
- Recommendation today is mostly deterministic if/else on reason + P, not a real LLM yet.
- Claude was dropped; plan is Ollama instead.

### 2.7 Docs

- Intelligence calendar, hurdles, reason→action map, service map, NEXT runbook.
- Full LangGraph case + ops plan: `docs/hackathon/langgraph-agent-plan.md`.

---

## 3. What we are going to build next

### 3.1 Case agent (LangGraph + Ollama) — primary

**Model:** `qwen2.5-coder:7b` (already installed locally; middle-size, good at structured JSON).

**Job:** For one RecoveryCase, help a human decide:

- diagnosis (what is wrong)
- why it happened
- recoveryProbability (from ML)
- recommendedAction
- confidence
- always `executes: false`

**How we will build it:**

1. Keep FastAPI `:8002` `/propose`.
2. LangGraph nodes: load context (optional) → `predictRecovery` → `getPolicies` → `calculateExpectedValue` → Ollama propose → Pydantic validate.
3. Prompt Ollama as a decision assistant; force JSON schema; reject money-moving actions.
4. If Ollama is down, fall back to current thin proposer so demo never dies.
5. Read-only tools only (predict, policies, EV; later getPayment/getCustomer/history from Postgres). No charge/retry/link tools.

### 3.2 Ops brain (LangGraph + Ollama) — system intelligence

**Job:** Across the book (not one case):

- what’s going wrong overall
- where (reason / method / source)
- whether it is recurring
- alerts with severity
- proposed solutions for humans

**How we will build it:**

1. New `POST /ops/briefing`.
2. LangGraph: SQL `gatherMetrics` → rule `buildAlerts` → Ollama narrate → validate.
3. Metrics from existing Postgres (`recovery_case`, `payment`, etc.), exclude training merchant by default.
4. Simple threshold alerts (e.g. NSF spike, checkout cluster, any risk case, large amount at risk).
5. Desk panel: alerts + hotspots + summary.

### 3.3 Decision / policy / trace (Day 4)

- Expected value with simple per-action costs (retry/link/voice/escalate constants).
- `PolicyEngine.evaluate` → ALLOW | BLOCK | ESCALATE (amount cap, max retries, cooldown, etc.).
- Screen-ready demo: recommend retry on oversized/risk case → BLOCK + log.
- Copy agent proposal JSON onto `audit_event`.
- Single decision-trace view: event → diagnosis → ML → agent → policy → execute → outcome.

### 3.4 End-to-end workflows

- Workflow A: payment/subscription failure through full pipeline.
- Workflow B: checkout abandonment through same pipeline.
- Keep current simulate pack as the reliable demo path.

### 3.5 Benchmark + submission (Day 6)

- Run ~500 synthetic events through baseline-only vs full AI path; report honest ₹ numbers.
- Film: duplicate webhook idempotency, policy block, agent/ML fallback.
- 5-minute video, public repo, docker-compose for judges.

### 3.6 Explicitly skipping for selection (unless spare time)

- Vault, Postgres RLS, field-level encryption, tokenization, extra DB roles.
- Security pitch instead: HMAC webhooks, Redis idempotency, append-only audit story, agent cannot execute, policy block.
- Extra workflows (full PTP tracker, mandate sequencer polish) only if Days 1–4 are green.

---

## 4. How the system works (current + target)

```
Simulate / Razorpay webhook
  → Java ingest → recovery_case
  → Baseline playbook plans first action
  → Desk shows case + P(recovery) from ML
  → [NEXT] Case agent proposes diagnosis + action (Ollama)
  → [NEXT] Ops brain alerts on recurring patterns
  → [NEXT] Policy ALLOW/BLOCK/ESCALATE
  → Java /execute runs playbook step (DEV)
  → Audit / decision trace
```

Ports:

- Postgres / Redis / Kafka — docker-compose
- Java API — 8080
- ML — 8001
- Agent — 8002
- Desk — 3000

---

## 5. Honest gaps / risks (please challenge these)

1. ML is trained on **500 synthetic** rows — useful for demo ranking, not production truth; we gate on labelled volume for that reason.
2. DEV retries always fail so the 4-step walk is visible — recovered path is mostly via captured simulate / outcome labels, not live success.
3. Agent today is thin; LLM case+ops intelligence is planned, not shipped.
4. PolicyEngine and full decision-trace UI are not done yet.
5. Kafka `recovery.events` / Redis cooldown keys from the original Day-2 list were deferred on purpose after playbooks worked.
6. No calibration curve yet for the model.
7. Deadline is tight (before 5 Sep) — we are sequencing agent → policy/trace → benchmark/video over Vault/RLS.

---

## 6. Questions we want senior feedback on

1. Is “playbook first + ML second + propose-only agent” the right architecture story for a fintech-adjacent hackathon, or should agent own more?
2. Is ops-brain alerting (SQL thresholds + Ollama narration) enough, or too shallow without a real rules engine?
3. Should we invest next hours in **Ollama case+ops**, or jump straight to **policy block + decision trace** for judging impact?
4. Any must-fix security/compliance beat we are wrong to skip (beyond HMAC + idempotency + no money tools)?
5. Anything in the demo path that would look fake to a Razorpay/fintech judge?

---

## 7. Immediate build order we intend

1. Ollama case agent (`/propose`) with fallback.  
2. Ops briefing (`/ops/briefing`) metrics + alerts + narration.  
3. Desk: Ask agent + alerts panel.  
4. Policy BLOCK + agent JSON on audit_event.  
5. Benchmark numbers + video.

Full agent design detail: `docs/hackathon/langgraph-agent-plan.md`.
