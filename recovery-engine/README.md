# Recovery Engine

Razorpay failed payments become **recovery cases**. Each reason has a printed four-step playbook. A score may skip **extra** silent retries after the first try. An agent can propose. **Java executes.** The agent cannot charge a card.

**Playbook first. ML second (this customer). Agent proposes. Java executes. Agent has no charge tool.**

Two seats:

| Seat | Where | Who |
|---|---|---|
| CEO | `/dashboard`, `/desk` | `ceo@recovery.local` / `admin123` |
| Human in the loop | `/approvals` | `policy@recovery.local` / `approve123` |

---

## What this is

A failed Razorpay event opens a case. The planner picks a folder from the reason (`card_not_enrolled`, `insufficient_funds`, `payment_risk_check_failed`, …). `/execute` runs **one** step. DEV retries are built to fail locally so you can walk all four steps without hitting a live card.

Scoring is allowed to cut **extra** silent retries only. The first required step always runs. Risk and cancel never auto-charge. Those sit for a person.

---

## Measured batch

Same labelled pile, two paths. Seed **42**. Merchant `acc_syn_training` (kept off the live desk). **1000** failures.

| Path | Recovered | Cases |
|---|---|---|
| Reason playbook | **₹9.14L** | 422 |
| Playbook + first retry + P | **₹9.38L** | 424 |
| What scoring changed | **+₹24,160** | 2 extra cases |

32 extra silent retries cut. 3 high-P risk holds released to a person. First-try payers were not dropped.

Rules on that board:

- first retry always counts
- extra silent retries skip only if **P < 0.12** and the customer has **≥ 10** payments
- high-P risk holds (**P ≥ 0.55**) go to a person — not an auto-charge

Write-up: [final-draft/09-recover-more.md](./final-draft/09-recover-more.md). The same numbers are on `/dashboard` under **Results**.

---

## Nine notes

Read these in order. Each one is one layer of the product.

| # | Note | What it is |
|---|---|---|
| 1 | [01-schema.md](./final-draft/01-schema.md) | Ledger: tables, why each exists, Flyway |
| 2 | [02-architecture.md](./final-draft/02-architecture.md) | How the pieces sit together |
| 3 | [03-build-order.md](./final-draft/03-build-order.md) | What shipped, in build order |
| 4 | [04-cases-and-playbooks.md](./final-draft/04-cases-and-playbooks.md) | Every failure and its four-step folder |
| 5 | [05-ml-layer.md](./final-draft/05-ml-layer.md) | Scoring, and what it is allowed to change |
| 6 | [06-guardrails.md](./final-draft/06-guardrails.md) | What can stop a charge |
| 7 | [07-langgraph-agent.md](./final-draft/07-langgraph-agent.md) | Agent proposes only |
| 8 | [08-benchmark.md](./final-draft/08-benchmark.md) | Older batch write-up |
| 9 | [09-recover-more.md](./final-draft/09-recover-more.md) | First try always; extra silent retries cut |

Index: [final-draft/README.md](./final-draft/README.md). Deeper dump if you need it: [FULL-SYSTEM.md](./FULL-SYSTEM.md).

---

## Repo map

```
recovery-engine/
├── backend/revenue-recovery/   Java 21 · Spring Boot — cases, playbooks, policy, HMAC, Kafka
├── ml-service/                 FastAPI :8001 — XGBoost P(recovery)
├── agent-service/              FastAPI :8002 — LangGraph, propose only
├── frontend/                   Next.js :3000 — CEO desk + approver queue
├── docker-compose.yml          Postgres, Redis, Kafka
├── final-draft/                Nine notes (schema → recover-more)
└── scripts/razorpay/           Test-mode keys, tunnel, signed webhooks
```

| Piece | Port | What it does |
|---|---|---|
| Postgres | 5432 | Ledger + audit |
| Redis | 6379 | Webhook lock (SETNX) |
| Kafka | 9092 | Signed-event bus |
| Java | 8080 | Money engine — plans and executes |
| ML | 8001 | Score this customer |
| Agent | 8002 | Propose next step (never executes) |
| Desk | 3000 | UI |

---

## How to start

Four terminals after Docker. Local desk + Simulate works with an empty `.env`. You only fill secrets for Razorpay Test Mode or an LLM.

### 0. You need

- Docker Desktop (running)
- Java 21
- Python 3.11+ and [uv](https://docs.astral.sh/uv/)
- Node 20+

### 1. Env

```bash
cd recovery-engine
cp .env.example .env
```

Windows PowerShell:

```powershell
cd recovery-engine
Copy-Item .env.example .env
```

Leave the file as-is for a local demo.

### 2. Postgres, Redis, Kafka

From `recovery-engine/`:

```bash
docker compose up -d
docker compose ps
```

Wait until all three are **healthy**:

- `recovery-engine-postgres`
- `recovery-engine-redis`
- `recovery-engine-kafka`

| Service | Host | Auth |
|---|---|---|
| Postgres | `localhost:5432` | user `postgres` / password `postgres` / db `revenue_recovery` |
| Redis | `localhost:6379` | — |
| Kafka | `localhost:9092` | — |

Do **not** run `backend/revenue-recovery/docker-compose.yml`. It is unused and will fight port 5432.

### 3. Java (`:8080`)

Flyway applies schema on first boot. Desk users are seeded on start.

```bash
cd backend/revenue-recovery
./gradlew bootRun
```

Windows:

```powershell
cd backend\revenue-recovery
.\gradlew.bat bootRun
```

Wait until it prints `Started RevenueRecoveryApplication`.

Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) → `"status":"UP"`.

### 4. ML (`:8001`)

```bash
cd ml-service
uv run uvicorn app.main:app --port 8001
```

Health: [http://localhost:8001/health](http://localhost:8001/health) → `"status":"ok"`.

### 5. Agent (`:8002`)

```bash
cd agent-service
uv run uvicorn app.main:app --port 8002
```

Health: [http://localhost:8002/health](http://localhost:8002/health).

If Ollama / the LLM is down, the agent still proposes from fallback rules. It never charges.

### 6. Desk (`:3000`)

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

### 7. Log in

| Who | Email | Password | Lands on |
|---|---|---|---|
| CEO | `ceo@recovery.local` | `admin123` | `/dashboard` |
| Approver | `policy@recovery.local` | `approve123` | `/approvals` |

---

## What to open

1. **`/dashboard`** — money at risk, measured batch (**Results** / **Playbook**), why live cases are stuck. Hover **?** next to a failure for the plain-language reason.
2. **`/desk`** — left: simulate a failure. Right: that case. **Start recovery** — the first required step always runs.
3. **`/approvals`** — approver only. Risk / high amount / cancelled sit here. Nothing is charged until they let it through.

A short judge path:

1. Sign in as CEO.
2. Read the three columns on `/dashboard`.
3. Open `/desk`. Simulate **card not enrolled** (link, no silent retry) and **insufficient funds** (first retry always).
4. Simulate **risk failed**. Start. It should stop for a person.
5. Sign out. Sign in as the approver. Let it through or hold it.
6. Sign back in as CEO and continue the case.

Desk **Simulate** skips HMAC and Kafka on purpose so the case appears at once. Live Razorpay POSTs still go HMAC → Redis lock → Kafka → Java.

---

## Health checklist

| URL | Expect |
|---|---|
| [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) | Java up |
| [http://localhost:8001/health](http://localhost:8001/health) | ML up |
| [http://localhost:8002/health](http://localhost:8002/health) | Agent up |
| [http://localhost:3000](http://localhost:3000) | Desk up |
| `docker compose ps` | Three containers healthy |

If the desk loads but Simulate is empty, Java is not up. If Start runs the playbook but there is no proposal, the agent is down — playbooks still run.

---

## Re-run the measured batch (optional)

Already checked in. After you change the model or the 1000-row seed:

```bash
cd ml-service
uv run python scripts/generate_synthetic.py --n 1000
uv run python scripts/train_model.py
uv run python scripts/run_benchmark.py
```

Restart Java so `GET /api/admin/benchmark` picks up the new file.

---

## Razorpay Test Mode (optional)

Local Simulate does not need keys. Live Test Mode does.

Put `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET` in `.env`. Scripts are in `scripts/razorpay/`:

| Script | What it does |
|---|---|
| `tunnel.ps1` | Expose `:8080` for a dashboard webhook |
| `create-fixtures.ps1` | Test-mode customer / order / plan / link |
| `simulate-webhooks.ps1` | Sign and POST sample events locally |
| `prove-live-webhook.ps1` | Show a HMAC-signed intake (not desk Simulate) |

Webhook path: `POST /webhooks/razorpay`. Events: `payment.failed`, `payment.captured`, `subscription.pending`, `subscription.halted`, `invoice.expired`.
