# 2. Architecture

## What this is

A two-person recovery desk. Failed Razorpay payments become cases. A reason playbook runs first. Scoring may change chase intensity for this customer. An agent may propose the next step. Java is the only process that executes. The agent has no charge tool.

## How the pieces sit

```
Browser  Next.js desk                         :3000
    /api/*        → Spring Boot               :8080
    /agent-api/*  → LangGraph agent           :8002

Spring Boot  (Java 21)  — the money engine
    Postgres 17                               :5432   case ledger + audit
    Redis 7                                   :6379   webhook lock (one event, one case)
    Kafka KRaft                               :9092   payment / invoice / checkout events
    ML service   FastAPI + XGBoost            :8001   P(recovery)
    Agent        FastAPI + LangGraph          :8002   propose only
```

Postgres, Redis, and Kafka run in `docker-compose.yml`. The four app processes run on the host.

## Why this shape

**Java in the centre.** Money, policy, playbooks, and audit stay in one process with a schema. An LLM does not own retries. If the model is down, the playbook still runs.

**Playbook as code, not a prompt.** Each failure reason has a four-step folder. `/execute` runs one step. That is how we stop silent retry loops.

**ML as a side input.** `P(recovery)` answers “will this customer likely pay?”, not “what should we charge?”. Below a labelled-data floor, Java ignores the score.

**Agent as a proposer.** LangGraph can diagnose and recommend. It cannot call `/execute`. The last line of its schema forces `executes: false` and `actions_available: ["propose"]`.

**Redis only as a lock.** `SETNX` on `idempotency:webhook:{eventId}`. Same Razorpay event cannot open two cases.

**Kafka only as the signed-event bus.** Three topics: `payment.events`, `invoice.events`, `checkout.events`. Desk “create issue” skips Kafka on purpose so the case appears in the same request. Signed Razorpay POSTs do not skip it.

**Postgres as the book.** If you cannot show the case, the action, and the audit row, the step did not happen.

**Two seats, not three.** CEO runs the book and the desk. One other person signs off holds. An extra operator seat made the product look like a call centre.

## Two intake paths

**Signed webhook**

Razorpay → HMAC-SHA256 → inbox stamp → Redis SETNX → Kafka → consumer → same ingest.

**Desk create-issue**

CEO picks a failure type → `/api/webhooks/simulate/{slug}` → same ingest in-process. No HMAC. No Kafka.

Both paths write the same case tables. They are labelled differently on `webhook_event.intake`.

## Control loop

```
Detect → Diagnose (playbook) → Score (optional)
      → Propose (optional) → Guard (PolicyEngine)
      → Human (only if blocked) → Act (Java, one step) → Audit
```

The desk **Ask agent** writes `AGENT_PROPOSE` on the audit trail. **Start** is what runs Java. Policy reads the proposal, then allows, skips a retry, or holds the case.

## What we did not add

No extra Kafka topics. No Redis cooldown keys. No charge tool on the agent. No third seat. No model that replaces the playbook.
