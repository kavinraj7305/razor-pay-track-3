# Recovery Engine — review pack

Nine notes. Read them in this order. Each one is one layer of the product.

| # | File | What it is |
|---|---|---|
| 1 | [01-schema.md](./01-schema.md) | Ledger: tables, why each exists, Flyway SQL |
| 2 | [02-architecture.md](./02-architecture.md) | How the pieces sit together, and why this shape |
| 3 | [03-build-order.md](./03-build-order.md) | What shipped, in the order it was built |
| 4 | [04-cases-and-playbooks.md](./04-cases-and-playbooks.md) | Every recovery case and its four-step playbook |
| 5 | [05-ml-layer.md](./05-ml-layer.md) | How scoring works, and what it is allowed to change |
| 6 | [06-guardrails.md](./06-guardrails.md) | What can stop a charge, and who owns money |
| 7 | [07-langgraph-agent.md](./07-langgraph-agent.md) | The multi-step agent: propose only |
| 8 | [08-benchmark.md](./08-benchmark.md) | Measured batch: playbook vs playbook + P + policy |
| 9 | [09-recover-more.md](./09-recover-more.md) | First try always runs; extra silent retries cut; recovered +₹12,472 |

The control order never inverts:

**Playbook first. ML second (this customer). Agent proposes. Java executes. Agent has no charge tool.**
