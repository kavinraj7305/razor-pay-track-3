# 3. What was built, in order

Do not invert this when walking the product. Each day sits on the day before it.

## Day 1 — Detect

Postgres schema (13 tables). Razorpay webhook intake.

A failure can enter the book. HMAC is checked. Redis claims the event id. Kafka carries the signed payload. `webhook_event` is the inbox.

Without this day there is no case, no rupees at risk, and nothing to recover.

## Day 1–2 — A failure becomes a case

Ingest opens `recovery_case` with a reason and an amount. Eight desk slugs cover the failures we can walk live:

insufficient funds, card expired, risk failed, subscription pending, subscription halted, invoice expired, checkout abandoned, payment captured.

Captured does not open a new case. It closes the matching open one.

## Day 2 — Playbooks

Reason folders. Four steps each. `/plan` writes the first action. `/execute` runs the next step once.

This is Java if/else, not a language model. Risk and cancelled start as a stop, not a retry. Dead instruments get a payment link, not another debit on the same card.

## Day 2 — Desk

Next.js recovery desk. Open a failure. Press Start. Watch Detect → Score → Act.

The desk is how a person sees the playbook move. It is not a second recovery engine.

## Day 3 morning — Labels

500 labelled events on merchant `acc_syn_training`. Ground truth for scoring. That merchant stays off the live desk so training rows do not mix with the working book.

## Day 3 — Score this customer

Customer features. XGBoost `POST /predict` → `P(recovery)`.

The playbook was blind to the person. Two shortfalls got the same three retries. We did not remove the playbook. We scored the customer on top of it.

## Day 3 — Data-volume gate

Below 400 labelled outcomes, Java will not let the score change retries. Enough data: playbook plus P. If the ML service is down, the playbook still runs.

## Day 3–4 — Agent

LangGraph case graph (`/propose`) and ops briefing (`/ops/briefing`).

The agent diagnoses and recommends. It cannot charge. If the local model is down, fallback rules still return a proposal.

## Day 4 — Guard and a person

PolicyEngine: allow, skip extra retry, or block.

Blocked cases wait in the approval queue. The other person holds or lets through. After a let-through, Java may continue.

Risk, cancelled, and amounts at or above ₹80,000 wait here.

## Day 4–5 — Two seats

CEO: overview, desk, queue.  
Human in the loop: queue only.

Operator seat removed from the product.

## Day 5 — Measured batch

Same 500 labelled events, seed 42. Playbook path versus playbook + P + policy. Real rupee totals on the overview. The skip line stayed 0.25. We did not retune it after seeing the batch.

That comparison is [08-benchmark.md](./08-benchmark.md).

## After that — signed intake and platform

HMAC stamped before Kafka. Inbox can show a Razorpay-signed event separately from a desk-created one. The desk shows Redis, Kafka, and Postgres as live connections when they answer.

## What this order protects

Detect before diagnose. Diagnose before score. Score before propose. Propose before execute. A person only when policy holds. Audit on every hop.
