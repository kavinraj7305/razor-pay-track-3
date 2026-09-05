# 6. Guardrails

Java owns money. Everything else can recommend. Nothing else can charge.

## Who is allowed to execute

| Actor | Can propose | Can execute a charge / retry / link |
|---|---|---|
| Reason playbook (Java) | — | Yes, one step at a time, after policy |
| XGBoost | Score only | No |
| LangGraph agent | Yes | No. No charge tool is bound |
| CEO | Start on the desk | Start calls Java `/execute` |
| Human in the loop | Hold or let through | No. After let-through, CEO Start continues |

The agent schema ends with `executes: false` and `actions_available: ["propose"]`. That is not a prompt suggestion. It is validated on every response.

## PolicyEngine

Reads the latest `AGENT_PROPOSE` audit row, plus hard rules on the case. Verdicts:

| Verdict | What happens |
|---|---|
| `ALLOW` | Java may run the next playbook step |
| `SKIP_RETRY` | Planned payment retries are cancelled. Playbook may continue by another channel |
| `BLOCK` | Nothing is charged. Case waits in the approval queue |

Hard rules, in order:

1. A later **human let-through** (`POLICY_APPROVED`) allows execute.
2. **Risk or cancelled** → block. Never auto-charge.
3. Agent said **escalate** or **do not retry** → block.
4. Amount at or above **₹80,000** (merchant policy, default) → block.
5. Agent said **skip extra retry** → skip retry.
6. Otherwise allow the playbook.

Every block or skip writes `audit_event`.

## Intake guards

- HMAC-SHA256 on `/webhooks/razorpay`. Bad signature → rejected.
- Redis `SETNX` on the event id. Duplicate → 200 and no second case.
- Same `source` + `source_id` → no second case.
- Desk create-issue is labelled `DESK_SIMULATE`. It is not presented as a Razorpay-signed event.

## Playbook guards

- One execution per step. No tight retry loop.
- Max three spaced auto-retries on transient failures, then a different channel or a stop.
- Dead instrument / wrong VPA / abandoned checkout never silent-retry the same method.
- DEV execute helpers do not call Razorpay charges.

## ML guards

- Below 400 labelled outcomes → score cannot change retries.
- Skip extra retry only if P < 0.25 and history is deep enough.
- Skip line was not retuned on the evaluation batch.
- If `:8001` is down, playbook continues.

## Agent guards

- Allowed recommendations only: delayed retry, skip extra retry, payment link, promise-to-pay, do not retry, no action.
- Risk or cancelled in the reason → forced `DO_NOT_RETRY` and escalate.
- Amount at the human threshold → escalate.
- No retry, pay-link, or charge tool in the agent process.
- If the local model is down, fallback rules still propose. They still cannot execute.

## Seats

- CEO sees overview, desk, and queue.
- Human in the loop sees the queue only.
- A blocked case needs a written note to hold or let through.

## What “safe” means here

A weak score cannot invent a new charge.  
An agent sentence cannot debit a card.  
A risk flag cannot be overridden by Start until a person lets it through.  
A duplicate webhook cannot open two cases.  
Every hop is on `audit_event`.
