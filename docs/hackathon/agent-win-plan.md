Created: **2 Sep 2026, 18:50 IST**
Reason: enterprise-grade agent plan locked from senior feedback — narrow 3-node case graph + recurring-pattern ops brain. This is what we build next to win.

Last updated: **2 Sep 2026, 19:40 IST**
Why updated: W1–W7 implemented in agent-service + desk (Ask agent card, ops strip). W8 (PolicyEngine + audit JSON) still later.

Deadline: **before 5 Sep 2026**

---

# Agent win plan (senior-aligned)

Judges reward **demonstrable rigor**, not feature count.  
We build a **narrow agent we can defend under questioning**, not a sprawling LangGraph.

Pitch (say exactly):

1. **Playbook first. ML second. Agent proposes. Java executes. Agent has no charge tool.**
2. **When Ollama is down, fallback fires live. When you ask if it can charge — the graph has no execute tool bound.**
3. **`deviates_from_playbook` is how we prove the agent adds value over static rules.**

Model: **Ollama `qwen2.5-coder:7b`**.  
Service: `recovery-engine/agent-service` port **8002**.

---

## 1. What we are building (only this)

| Deliverable | API | Why it wins |
|---|---|---|
| **Case agent** (3 nodes) | `POST /propose` | Per-case diagnosis + recommend + agree/deviate from playbook |
| **Recurring pattern / ops brain** | `POST /ops/briefing` | SQL spike detection + one Ollama narration — same build, two demo beats |
| **Visible safety proof** | in every response + desk | `actions_available: ["propose"]` only; no execute tool in graph |
| **Live fallback proof** | kill Ollama mid-demo | thin proposer still returns valid JSON |

Not building now: 10-tool ReAct loops, Claude, Vault, agent that executes money.

---

## 2. Case agent — exact design

### 2.1 Input (per case)

Pull or accept:

| Field | Source |
|---|---|
| `caseId` | desk / Java |
| `reason` | recovery_case |
| `amountInr` | recovery_case.amount_at_risk |
| `retryHistory` / `retryCount` | recovery_action attempts |
| `priorPlaybookSteps` | recovery_action list (type + status) |
| `mlScore` / `P(recovery)` | ML `:8001/predict` (or cached peek) |
| `customerSegment` | derived: GOOD / WEAK / UNKNOWN from success rate / history count |
| `defaultPlaybookAction` | first planned / next playbook step from Java reason handler |

No LLM in retrieval. Context node is SQL/HTTP only.

### 2.2 Graph — **3 nodes only**

```
START
  → context      (no LLM)   load case + history + ML score + default playbook step
  → diagnose     (Ollama)   what's wrong / why / recommend / deviate? / confidence / escalate?
  → safety       (no LLM)   strip any execute; force actions_available=["propose"]; executes=false
END
```

| Node | LLM? | Responsibility |
|---|---|---|
| **1. context** | No | Postgres (+ ML predict). Build a typed `CaseContext` bag. |
| **2. diagnose** | Yes (Ollama) | One call. Produce schema below. Must ground reasoning in fields from context. |
| **3. safety** | No | Hardcoded. Bind **zero** execute tools. Attach safety fields. If diagnose returned garbage → thin fallback proposal. |

This is what we show judges when they ask “can this ever charge someone?” — open `graph_case.py` / desk JSON: **no execute tool bound**.

### 2.3 Output schema (visible on desk, not buried in logs)

```json
{
  "caseId": "rc_...",
  "diagnosis": "TEMPORARY_FUNDS_SHORTFALL",
  "reasoning": "NSF with WEAK segment, P(recovery)=0.12, already 1 failed retry. Grounded in paymentSuccessRate and retryHistory.",
  "recommended_action": "SKIP_EXTRA_RETRY",
  "default_playbook_action": "DELAYED_RETRY",
  "deviates_from_playbook": true,
  "confidence": 0.81,
  "ml_score": 0.12,
  "escalate": false,
  "actions_available": ["propose"],
  "executes": false,
  "model": "qwen2.5-coder:7b",
  "fallback_used": false
}
```

| Field | Purpose |
|---|---|
| `diagnosis` | What’s wrong |
| `reasoning` | Why — must cite concrete context fields |
| `recommended_action` | One of playbook’s existing action vocabulary |
| `default_playbook_action` | What static playbook would do next |
| `deviates_from_playbook` | **true/false — proof agent adds value** |
| `confidence` | Tied to ML P + rule certainty (risk = high confidence stop) |
| `escalate` | Bool → feeds PolicyEngine later |
| `actions_available` | Always `["propose"]` only |
| `executes` | Always `false` |
| `fallback_used` | true when Ollama skipped / failed |

**Allowed `recommended_action` values** (same vocabulary as playbooks):  
`DELAYED_RETRY`, `SKIP_EXTRA_RETRY`, `SEND_PAYMENT_LINK`, `REQUEST_PROMISE_TO_PAY`, `DO_NOT_RETRY`, `NO_ACTION`.

### 2.4 Diagnose prompt rules (enterprise-tight)

System prompt must include:

- You are a recovery **decision assistant** for a human operator.
- You **propose only**. You cannot charge, retry, or send links.
- Ground every claim in the provided context fields. No invented history.
- Prefer playbook action unless ML/history clearly justifies a deviation; if you deviate, set `deviates_from_playbook: true` and explain why.
- Risk / cancelled → `DO_NOT_RETRY`, `escalate: true`.
- Amount ≥ human-approval threshold → `escalate: true`.
- Return **JSON only**, matching the schema.

### 2.5 Fallback (must be demoable live)

If Ollama timeout / down / bad JSON:

1. Safety node runs thin mapper (existing `propose.py` logic).
2. Set `fallback_used: true`, `model: "fallback-rules"`.
3. Still return full schema with `actions_available: ["propose"]`.

**Demo script:** start propose with Ollama up → show JSON → stop Ollama → propose again → show `fallback_used: true` on screen.

---

## 3. Highest-leverage add — recurring pattern detection

This is **not extra work**. It **is** the ops brain, and it separates “agent” from “fancy classifier.”

### 3.1 SQL first (no LLM)

Window: last **6 hours** (demo) / 24h (default).

Examples:

| Pattern | Query idea | Severity |
|---|---|---|
| NSF spike | count `insufficient_funds` in window ≥ threshold | HIGH |
| Checkout cluster | count `checkout.abandoned` ≥ threshold | MEDIUM |
| Risk present | any `payment_risk_check_failed` | HIGH |
| Same reason recurring for one customer | ≥3 cases same customer+reason | MEDIUM |
| Method hotspot | group by payment method if available | MEDIUM |

(If BIN is not in schema, use **reason × method × merchant** — do not invent BINs. Say “method/merchant spike” honestly.)

Exclude `acc_syn_training` by default.

### 3.2 One Ollama narration call

Input: metrics JSON + alert list from SQL.  
Output: plain-English `summary` + per-alert `proposed_solution`.

### 3.3 Ops API

`POST /ops/briefing`

```json
{
  "windowHours": 6,
  "summary": "NSF failures are spiking for card on the demo merchant.",
  "patterns": [
    {
      "severity": "HIGH",
      "pattern": "insufficient_funds_spike",
      "where": "PAYMENT / card",
      "count": 7,
      "why": "7 NSF cases in 6h vs quiet baseline",
      "proposed_solution": "Keep payday delays; skip extra retries for WEAK segment; watch queue",
      "relatedCaseIds": ["rc_..."]
    }
  ],
  "actions_available": ["propose"],
  "executes": false,
  "fallback_used": false,
  "model": "qwen2.5-coder:7b"
}
```

If Ollama down: still return SQL patterns + template solutions; `fallback_used: true`.

---

## 4. How it connects to what we already have

| Existing | Agent uses it as |
|---|---|
| Java playbooks | `default_playbook_action` + allowed action vocabulary |
| ML `/predict` | `ml_score` / confidence anchor |
| Desk Start process | Human still runs execute; agent only advises |
| Policy (next) | Consumes `escalate` + amount/risk |
| Audit (next) | Store full propose JSON on `audit_event` |

Flow:

```
Case exists (Java)
  → POST /propose
      context → diagnose(Ollama) → safety
  → Desk shows diagnosis + deviate flag + actions_available
  → Human / Policy
  → Java /execute (unchanged)
```

Parallel:

```
Desk load / refresh
  → POST /ops/briefing
      SQL patterns → Ollama narrate → safety fields
  → Alerts strip on desk
```

---

## 5. Desk changes (enterprise demo surface)

On case detail, show a card **Agent proposal** (not logs):

- Diagnosis  
- Reasoning (short)  
- Recommended vs default playbook  
- Badge: **Agrees with playbook** / **Deviates**  
- Confidence + ML score  
- Escalate yes/no  
- Chip: `actions_available: propose only`  
- Chip: `fallback_used` when true  

On top of desk: **Ops patterns** from `/ops/briefing`.

Button: **Ask agent** → `/propose`.  
Keep **Start recovery process** as Java execute path (separate).

---

## 6. File plan (keep small)

```
agent-service/app/
  main.py              # /propose, /ops/briefing, /health
  config.py            # OLLAMA_MODEL=qwen2.5-coder:7b, thresholds
  schemas.py           # CaseProposal, OpsBriefing (Pydantic)
  context.py           # context node — SQL/HTTP load
  diagnose.py          # Ollama call + parse
  safety.py            # force propose-only fields
  graph_case.py        # 3-node StateGraph
  patterns.py          # SQL recurring detection
  graph_ops.py         # metrics → narrate → safety
  propose_fallback.py  # rename/keep thin mapper
  llm.py               # ChatOllama wrapper
```

Deps: `langchain-ollama`, `langgraph`, `httpx`, DB driver for read-only Postgres (reuse same DSN as ml-service).

---

## 7. Build order (do not invert)

| Step | Deliverable | Judge-ready exit |
|---|---|---|
| **W1** | schemas + safety.py + fallback | Every response has `actions_available:["propose"]`, `executes:false` |
| **W2** | context node (case + ML + playbook default) | curl loads real case fields |
| **W3** | 3-node graph + Ollama diagnose | `/propose` returns full schema; `deviates_from_playbook` works |
| **W4** | Live fallback demo path | Kill Ollama → `fallback_used:true` |
| **W5** | SQL patterns + `/ops/briefing` | Spike alert without LLM |
| **W6** | Ollama narrate on patterns | Plain-English summary |
| **W7** | Desk: Agent proposal card + Ops strip | Visible, not logs |
| **W8** | (Day 4) PolicyEngine reads `escalate`; audit stores JSON | Trace complete |

**Win bar for agent track:** W1–W7 green. W8 is the trace closer.

---

## 8. Demo script under questioning (memorize)

| Judge asks | You show |
|---|---|
| What does the agent do? | Desk Ask agent → diagnosis + reasoning + recommend |
| How is it better than rules? | Case where `deviates_from_playbook: true` (weak NSF → skip extra retry) |
| Can it charge? | JSON `actions_available: ["propose"]` + open graph: no execute tool |
| Ollama down? | Stop Ollama → propose → `fallback_used: true` live |
| System intelligence? | Ops strip: NSF spike last 6h + proposed solution |
| Who executes? | Start recovery process / Java `/execute` only |

---

## 9. Explicit non-goals (say no if asked to expand)

- Multi-agent swarms / 10+ tools  
- Agent calling Razorpay  
- Custom fine-tuned model  
- BIN analytics if column does not exist (use method/merchant instead — stay honest)  
- Vault/RLS before W7  

---

## 10. Relation to older plan

`langgraph-agent-plan.md` remains background context.  
**Implementation authority is this file.**  
Narrow 3-node case graph + recurring SQL patterns beat a wide tool loop we cannot defend.

---

## 11. Status

| Item | Status |
|---|---|
| Senior feedback absorbed | Yes |
| Plan locked | **Yes — this doc** |
| W1 schemas + safety + fallback | **Done** |
| W2 context node | **Done** |
| W3 3-node `/propose` + Ollama diagnose | **Done** |
| W4 live fallback (`fallbackUsed`) | **Done** (safety + thin mapper; demo by stopping Ollama) |
| W5 SQL patterns + `/ops/briefing` | **Done** |
| W6 Ollama narrate on patterns | **Done** |
| W7 Desk Ask agent + Ops strip | **Done** |
| W8 PolicyEngine + audit JSON | Later (Day 4) |

Run: `uv run uvicorn app.main:app --port 8002 --reload` in `recovery-engine/agent-service`. Desk proxies `/agent-api/*` → `:8002`.
