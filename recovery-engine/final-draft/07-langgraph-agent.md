# 7. Multi-step LangGraph agent

The agent is a proposer. It never executes.

Service: FastAPI on `:8002`. Two compiled graphs. No charge, retry, or payment-link tool is bound in this process.

## Case agent — three nodes

`POST /propose`  
Graph: `app/graph_case.py`

```
START → context → diagnose → safety → END
```

### Node 1 — context

Load the case. Reason, amount, default playbook action, optional `P(recovery)` from ML. This is read-only.

### Node 2 — diagnose

A local model (Ollama) writes a draft: diagnosis, recommended action, short reasoning.

If the model is down or the draft is empty, the graph marks `fallback_used` and skips to rules.

### Node 3 — safety

Not an LLM. Hardcoded.

- Recommended action must be in a fixed list.
- Risk or cancelled → `DO_NOT_RETRY` and escalate.
- Amount at the human threshold → escalate.
- Compare recommended action to the playbook default. Set `deviatesFromPlaybook`.
- Force `executes: false` and `actions_available: ["propose"]`.

Output is a `CaseProposal`. Java stores it as `AGENT_PROPOSE` on `audit_event`. PolicyEngine reads that row. Start is still what runs `/execute`.

## What it is allowed to recommend

| Action | Meaning |
|---|---|
| `DELAYED_RETRY` | Wait, then one retry — same idea as the NSF playbook |
| `SKIP_EXTRA_RETRY` | Do not spend another chase on this customer |
| `SEND_PAYMENT_LINK` | Change channel |
| `REQUEST_PROMISE_TO_PAY` | B2B chase |
| `DO_NOT_RETRY` | Stop. Usually risk, cancel, or escalate |
| `NO_ACTION` | Nothing further |

Anything else is rewritten to `DELAYED_RETRY` before it leaves the graph.

## What a proposal contains

- Diagnosis (short code-like label)
- Reasoning (why this next step)
- Recommended action
- Playbook default
- Whether it deviates
- Confidence
- Optional ML score
- Escalate yes/no
- Model name, or fallback

The desk shows **Proposes only** and **Cannot charge**. Java remains the executor.

## Ops agent — three nodes

`POST /ops/briefing`  
Graph: `app/graph_ops.py`

```
START → metrics → narrate → safety → END
```

1. **metrics** — SQL over recent cases: repeating reasons, counts, rupees at risk.
2. **narrate** — optional short summary. If the model is down, rule-based text.
3. **safety** — clean pattern objects. Still propose only.

This briefing does not execute. It names what is repeating so a person can look.

## Fallback

If Ollama is unreachable, the case graph still returns a proposal from `propose_fallback.py` (reason rules). The ops graph still returns SQL patterns without narration. The process stays up. Fallback proposals still cannot execute.

## Why multi-step, and why so short

Three nodes keep diagnose separate from safety. The model can be wrong. The last node is code.

We did not give the graph an execute node. Adding one would put money in the model path. That is the opposite of this product.
