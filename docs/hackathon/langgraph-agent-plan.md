Created: **2 Sep 2026, 17:30 IST**
Reason: full plan for the LangGraph agent — **case intelligence** + **ops brain** — using Ollama (`qwen2.5-coder:7b`), propose-only, human-in-the-loop.

Last updated: **2 Sep 2026, 18:50 IST**
Why updated: **Implementation superseded** by senior-aligned [agent-win-plan.md](./agent-win-plan.md) (3-node case graph + recurring patterns). Keep this file as wider background only.

Deadline: **before 5 Sep 2026**

---

# LangGraph agent plan — case + ops intelligence

This is the intelligence layer of the whole recovery system. It is **not** an executor.

Pitch lines (use these):

1. **Playbook first. ML second (this customer). Agent proposes. Java executes. Agent has no charge tool.**
2. **The agent is the intelligence of the system: it tells humans what is wrong, why it is happening, when problems are recurring, and what to do — humans and policy stay in control.**

---

## 1. What we are building

Two LangGraph flows in one FastAPI service (`recovery-engine/agent-service`, port **8002**):

| Graph | API | Job |
|---|---|---|
| **Case agent** | `POST /propose` | For one RecoveryCase: what’s wrong, why, P(recovery), proposed action |
| **Ops brain** | `POST /ops/briefing` | Across the system: recurring problems, hotspots, alerts, proposed fixes |

Shared model: **Ollama `qwen2.5-coder:7b`** (already installed locally).  
Shared safety: **`executes: false` always**. No money tools.

```
                    ┌─────────────────────┐
  Webhook / case ──►│  Case agent graph   │──► structured propose JSON
                    └──────────┬──────────┘
                               │ predictRecovery → ML :8001
                    ┌──────────▼──────────┐
  Desk / cron    ──►│  Ops brain graph    │──► alerts + summary JSON
                    └──────────┬──────────┘
                               │ SQL metrics from Postgres
                    ┌──────────▼──────────┐
                    │ Human / Policy /    │
                    │ Java /execute       │  ← only path that acts
                    └─────────────────────┘
```

---

## 2. Roles in the stack (do not mix)

| Layer | Owns | Does not own |
|---|---|---|
| **Java playbook** | Safe action *type* for a failure reason (retry / link / stop / PTP) | Customer-specific probability wording |
| **ML `/predict` (8001)** | `P(recovery)` for **this** customer + case features | Whether to execute |
| **Case agent** | Diagnosis + recommendation + why for one case | Charging, retrying, sending links |
| **Ops brain** | Recurring problems, hotspots, alerts, proposed system fixes | Changing policy or auto-executing |
| **Policy / human** | ALLOW / BLOCK / ESCALATE | Inventing the diagnosis |
| **Java `/execute`** | Actually running DEV retry / SMS / pay-link | LLM reasoning |

---

## 3. Model choice (Ollama)

Installed locally:

| Model | Size | Use |
|---|---|---|
| `mistral:7b` | 4.4 GB | Backup if coder is slow |
| **`qwen2.5-coder:7b`** | **4.7 GB** | **Default — best for structured JSON** |
| `qwen3-vl:8b` | 6.1 GB | Skip (vision, not needed) |
| `qwen3-coder:30b` | 18 GB | Skip for live demo (too heavy) |

Config:

```env
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5-coder:7b
ML_PREDICT_URL=http://localhost:8001/predict
HUMAN_APPROVAL_AMOUNT=80000
```

Fallback: if Ollama is down, return the existing thin if/else proposer (`propose.py`) / rule-based ops alerts — still valid JSON for the demo.

---

## 4. Case agent — full plan

### 4.1 Purpose

Help a human decide **accurately** on one case:

- What is wrong?
- Why is this happening?
- How likely will they pay? (`P(recovery)`)
- What should we do next?
- How confident are we?

### 4.2 Input

Preferred (after caseId wiring):

```json
{ "caseId": "rc_..." }
```

Also keep feature blob (current) so Postman works without Java:

```json
{
  "reason": "insufficient_funds",
  "amountInr": 499,
  "paymentSuccessRate": 0.8,
  "paymentFailureRate": 0.2,
  "lifetimeValue": 12000,
  "avgOrderValue": 499,
  "historyPaymentCount": 10
}
```

### 4.3 LangGraph A — nodes

```
START
  → loadContext          (optional: getPayment / getCustomer / getRecoveryHistory)
  → predictRecovery      (HTTP → ML :8001)
  → getPolicies          (never-retry reasons, ₹80k, P<0.25, agentCanExecute=false)
  → calculateExpectedValue
  → ollamaPropose        (qwen2.5-coder:7b writes diagnosis + reason text)
  → validateProposal     (Pydantic; bad JSON → thin mapper fallback)
END
```

| Node | Type | What it does |
|---|---|---|
| `loadContext` | tool(s) | Read-only case/customer/payment snapshot (phase 2) |
| `predictRecovery` | tool | Calls ML; sets `recoveryProbability` or `ml_available=false` |
| `getPolicies` | tool | Hard constants — agent must respect them |
| `calculateExpectedValue` | tool | `EV = P × amountInr` (costs later in Day 4) |
| `ollamaPropose` | LLM | Fills diagnosis / recommendedAction / reason / confidence |
| `validateProposal` | code | Schema + force `executes: false` |

### 4.4 Tools allowed (read-only)

| Tool | Source | For |
|---|---|---|
| `predictRecovery` | ML service | P(recovery) |
| `getPolicies` | config | Guardrails |
| `calculateExpectedValue` | pure fn | EV |
| `getPayment` | Postgres / Java | This failure (phase 2) |
| `getCustomer` | Postgres / Java | Who (no raw PII in prompt) |
| `getPaymentHistory` | Postgres | Recent success/fail |
| `getRecoveryHistory` | Postgres | Past outcomes |
| `getSubscription` | Postgres | Mandate age/status |
| `getInvoice` | Postgres | Overdue invoice |
| `getCheckoutHistory` | Postgres | Abandon pattern |

### 4.5 Tools that must never exist

`retryPayment`, `charge`, `capturePayment`, `sendPaymentLink`, `sendSms`, `closeCase`, `executePlaybook`.

### 4.6 Output contract (hard)

```json
{
  "diagnosis": "TEMPORARY_FUNDS_SHORTFALL",
  "recoveryProbability": 0.53,
  "recommendedAction": "DELAYED_RETRY",
  "reason": "NSF on a customer with strong history. P=0.53, EV positive vs cheap retry. Agent does not execute.",
  "confidence": 0.88,
  "expectedValueInr": 264.47,
  "executes": false,
  "model": "qwen2.5-coder:7b",
  "humanInTheLoop": true
}
```

**Allowed `recommendedAction` values:**

| Action | When |
|---|---|
| `DELAYED_RETRY` | NSF / gateway / mandate pending, P not dead |
| `SKIP_EXTRA_RETRY` | Same family, P &lt; 0.25 and enough history |
| `SEND_PAYMENT_LINK` | Dead instrument, checkout abandon, halted |
| `REQUEST_PROMISE_TO_PAY` | Invoice overdue |
| `DO_NOT_RETRY` | Risk / cancel / policy block |
| `NO_ACTION` | Already captured |

**Allowed `diagnosis` values:**  
`TEMPORARY_FUNDS_SHORTFALL`, `TEMPORARY_ISSUER_FAILURE`, `DEAD_INSTRUMENT`, `RISK_BLOCK`, `CUSTOMER_CANCELLED`, `MANDATE_RETRY`, `RETRIES_EXHAUSTED`, `RECEIVABLE_OVERDUE`, `CHECKOUT_DROPOFF`, `ALREADY_RECOVERED`, `UNKNOWN_FAILURE`.

### 4.7 How case agent works (runtime)

1. Desk or Postman calls `POST /propose`.
2. Graph loads context (if caseId) and calls ML.
3. Policies + EV are attached to state.
4. Ollama is prompted: *you are a recovery decision assistant; propose only; respect policies; return JSON schema only.*
5. Validator rejects free text / money-moving actions.
6. Human sees the proposal on the desk; Java `/execute` runs only if they (or policy) allow the playbook step.

---

## 5. Ops brain — full plan

### 5.1 Purpose

Be the **system intelligence**:

- What is going wrong **across** cases?
- Why is it happening?
- Is it **recurring**?
- **Where** (reason, method, source, merchant)?
- What should we do about it?

This is how we can say in the video:  
*“Alert: NSF is recurring on card. Proposed solution: stretch payday retries and review weak-history customers.”*

### 5.2 Input

```json
{
  "windowHours": 24,
  "merchantId": null
}
```

Defaults: last **24 hours**, demo merchant only (`acc_test_recovery`) — never flood with `acc_syn_training` unless explicitly asked.

### 5.3 LangGraph B — nodes

```
START
  → gatherMetrics        (SQL aggregates from Postgres)
  → buildAlerts          (deterministic rules → alert list)
  → ollamaNarrate        (qwen turns metrics+alerts into summary + solutions)
  → validateBriefing     (Pydantic; force executes:false)
END
```

| Node | Type | What it does |
|---|---|---|
| `gatherMetrics` | code/SQL | Counts by reason, source, method; open vs recovered; top amounts |
| `buildAlerts` | rules | Thresholds → severity + problem + where |
| `ollamaNarrate` | LLM | Human-readable summary + proposedSolution per alert |
| `validateBriefing` | code | Schema lock |

### 5.4 Metrics we compute (no new warehouse)

From existing tables (`recovery_case`, `payment`, `recovery_action`, `recovery_outcome`):

| Metric | Query idea |
|---|---|
| Cases opened in window | `count(*)` where `created_at` in window |
| Top reasons | group by `reason` |
| By source | PAYMENT / SUBSCRIPTION / INVOICE / CHECKOUT_SESSION |
| By method | from `payment.payment_type` when available |
| Open vs recovered | group by `status` |
| Revenue at risk | `sum(amount_at_risk)` for open cases |
| Repeat customers | customers with ≥2 open/failed cases in window |

Training merchant `acc_syn_training` **excluded** by default (same as Java list API).

### 5.5 Alert rules (simple, not a rules DSL)

| Rule | Severity | Example message |
|---|---|---|
| `insufficient_funds` count ≥ 5 in 24h | HIGH | NSF spike — recurring funds failures |
| `checkout.abandoned` count ≥ 5 in 24h | MEDIUM | Checkout drop-off cluster |
| Any `payment_risk_check_failed` | HIGH | Risk cases need human — do not auto-retry |
| Same reason ≥ 3 for one customer | MEDIUM | Recurring failure for one payer |
| Open amount at risk ≥ ₹50,000 | HIGH | Large book at risk — review queue |
| ML unavailable while cases open | LOW | Scoring blind — playbook-only mode |

Thresholds live in config (`OpsAlertProperties` / env). Easy to tune for demo.

### 5.6 Output contract (hard)

```json
{
  "windowHours": 24,
  "generatedAt": "2026-09-02T17:30:00+05:30",
  "summary": "NSF is the dominant failure today; risk cases need human review.",
  "metrics": {
    "casesOpened": 12,
    "revenueAtRiskInr": 48000,
    "topReasons": [
      { "reason": "insufficient_funds", "count": 7 },
      { "reason": "checkout.abandoned", "count": 3 }
    ]
  },
  "alerts": [
    {
      "severity": "HIGH",
      "problem": "insufficient_funds spike",
      "where": "PAYMENT / card",
      "why": "7 cases in 24h — recurring temporary funds shortfall pattern",
      "proposedSolution": "Keep payday delayed retries; skip extra retries for weak-history customers; watch P(recovery)",
      "relatedCaseIds": ["rc_..."]
    }
  ],
  "executes": false,
  "model": "qwen2.5-coder:7b",
  "humanInTheLoop": true
}
```

### 5.7 How ops brain works (runtime)

1. Desk loads → calls `POST /ops/briefing`.
2. `gatherMetrics` runs SQL (read-only DB URL).
3. `buildAlerts` applies thresholds (deterministic — demos even if LLM is down).
4. Ollama narrates *summary* + strengthens `proposedSolution` text.
5. UI shows Alerts strip + Hotspots + “what’s wrong / why / fix”.
6. Nothing is executed. Human decides next drill-down case.

---

## 6. Service layout (files to add/change)

```
recovery-engine/agent-service/
  app/
    main.py              # /health, /propose, /ops/briefing
    config.py            # OLLAMA_*, ML_*, alert thresholds
    graph_case.py        # LangGraph A
    graph_ops.py         # LangGraph B
    tools.py             # predict, policies, EV (+ read tools)
    metrics.py           # SQL gatherMetrics
    alerts.py            # buildAlerts rules
    propose.py           # thin fallback (keep)
    schemas.py           # Pydantic Proposal + OpsBriefing
    llm.py               # ChatOllama wrapper + JSON parse
```

Deps to add: `langchain-ollama`, `langchain-core` (keep `langgraph`).  
Claude / Anthropic become optional leftovers — not required to run.

---

## 7. Desk UI wiring (after APIs are green)

On `http://localhost:3000`:

1. **Ops panel (top)**  
   - Alerts (severity chips)  
   - Hotspots table (reason × count)  
   - Summary sentence from `/ops/briefing`

2. **Case panel (existing)**  
   - Button: **Ask agent** → `POST /propose`  
   - Show diagnosis / P / recommendedAction / reason  
   - Keep **Start recovery process** (Java playbook sim)

3. Copy agent JSON into audit later (Java Day-4 trace) — not blocking for first curl.

---

## 8. Safety + human-in-the-loop (non-negotiable)

| Rule | How we enforce |
|---|---|
| Propose only | `executes: false` forced in validator |
| No money tools | tools.py whitelist only |
| Policy respected | `getPolicies` in graph; risk / ₹80k → `DO_NOT_RETRY` |
| LLM down | thin mapper / rule alerts still return JSON |
| ML down | case agent still proposes playbook action; `recoveryProbability: null` |
| Audit | every proposal can be stored; Java remains source of execution |

Say on camera: **“Even if the agent is compromised or wrong, it cannot charge. Policy and humans decide.”**

---

## 9. Build order (do not invert)

| Step | Deliverable | Exit check |
|---|---|---|
| **A1** | Ollama config + `llm.py` + keep fallback | `ollama run qwen2.5-coder:7b` works |
| **A2** | Case graph: predict → policies → EV → Ollama → validate | `curl POST :8002/propose` → JSON `executes:false` |
| **A3** | Ops `gatherMetrics` + `buildAlerts` (no LLM yet) | `curl POST :8002/ops/briefing` → metrics+alerts |
| **A4** | Ops `ollamaNarrate` + validate | briefing has summary + proposedSolution |
| **A5** | Desk: Alerts + Ask agent | visible in browser for pitch |
| **A6** | Java: copy propose JSON onto `audit_event` | one case decision trace |

If time is short: **A1–A4 are enough for curl/Postman.** A5 makes the video. A6 is the Day-4 trace.

---

## 10. Local runbook

```bash
# 1) Ollama
ollama serve
ollama run qwen2.5-coder:7b

# 2) ML
cd recovery-engine/ml-service
uv run uvicorn app.main:app --port 8001

# 3) Agent
cd recovery-engine/agent-service
uv run uvicorn app.main:app --port 8002

# 4) Java + desk (for UI)
# IntelliJ bootRun :8080
# cd recovery-engine/frontend && npm run dev  → :3000
```

Smoke tests:

```bash
curl -s http://localhost:8002/health
curl -s -X POST http://localhost:8002/propose -H "Content-Type: application/json" -d "{...features...}"
curl -s -X POST http://localhost:8002/ops/briefing -H "Content-Type: application/json" -d "{\"windowHours\":24}"
```

---

## 11. Demo script (60–90s inside the 5‑min video)

1. Desk: create `insufficient_funds` → show P(recovery).  
2. **Ask agent** → diagnosis + DELAYED_RETRY + why (human decision aid).  
3. Ops panel: show alert “NSF recurring” + proposed solution.  
4. Say: agent proposes; Start recovery process / Java executes under policy.  
5. Optional: risk ₹80k → agent says DO_NOT_RETRY / ops alert HIGH.

---

## 12. Explicitly out of scope (this pass)

- Agent executing retries or payment links  
- Vault / RLS / field encryption  
- Full Claude cloud  
- Real-time Kafka ops stream (Postgres window queries are enough)  
- Training a custom LLM  

---

## 13. Status snapshot (2 Sep evening)

| Piece | Status |
|---|---|
| Thin `/propose` (if/else + ML call) | Exists |
| Desk live playbook sim | Exists |
| Ollama LangGraph case graph | **To build** (this plan) |
| Ops briefing graph | **To build** (this plan) |
| Desk ops widgets | After A3/A4 |
| Java audit copy of agent JSON | After A2 |

**Next code step when you say go:** A1 + A2 (Ollama case agent with fallback), then A3 + A4 (ops brain).
