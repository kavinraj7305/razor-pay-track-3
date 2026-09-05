# Recovery Engine

Failed Razorpay payments become **recovery cases**. Each reason has a four-step playbook. Scoring may skip **extra** silent retries. An agent may propose. **Java executes.** The agent cannot charge.

**Playbook first. ML second (this customer). Agent proposes. Java executes. Agent has no charge tool.**

Two seats: CEO (`/dashboard`, `/desk`) and the human in the loop (`/approvals`).

On the measured 500-failure batch: playbook recovered **₹5.73L**. First retry always runs, then P only cuts extra silent retries, and two high-P risk holds go to a person. That path recovered **₹5.86L (+₹12,472)**. Eighteen extra retries cut. We do not give up people who pay on the first try.

---

## What lives here

```
recovery-engine/
├── backend/revenue-recovery/   Java 21 · Spring Boot — cases, playbooks, policy, HMAC, Kafka
├── ml-service/                 FastAPI :8001 — XGBoost P(recovery)
├── agent-service/              FastAPI :8002 — LangGraph, propose only
├── frontend/                   Next.js :3000 — CEO desk + approver queue
├── docker-compose.yml          Postgres, Redis, Kafka
├── EXPLAIN.md                  Speak this on camera, in order
├── final-draft/                Nine review notes (schema → recover-more)
└── scripts/razorpay/           Test-mode keys, tunnel, signed webhooks
```

| Piece | Port | What it does |
|---|---|---|
| Postgres | 5432 | Ledger + audit |
| Redis | 6379 | Webhook lock (SETNX) |
| Kafka | 9092 | Signed-event bus |
| Java | 8080 | Money engine |
| ML | 8001 | Score this customer |
| Agent | 8002 | Propose next step |
| Desk | 3000 | UI |

---

## Docs — read one by one

Start here if you want to understand the product before you run it.

| Order | File | What it is |
|---|---|---|
| Speak | [EXPLAIN.md](./EXPLAIN.md) | Video walk. Say this, then stop. |
| 1 | [final-draft/01-schema.md](./final-draft/01-schema.md) | Tables, why each exists, Flyway |
| 2 | [final-draft/02-architecture.md](./final-draft/02-architecture.md) | How the pieces sit together |
| 3 | [final-draft/03-build-order.md](./final-draft/03-build-order.md) | What shipped, in build order |
| 4 | [final-draft/04-cases-and-playbooks.md](./final-draft/04-cases-and-playbooks.md) | Every failure and its four-step folder |
| 5 | [final-draft/05-ml-layer.md](./final-draft/05-ml-layer.md) | Scoring, and what it is allowed to change |
| 6 | [final-draft/06-guardrails.md](./final-draft/06-guardrails.md) | What can stop a charge |
| 7 | [final-draft/07-langgraph-agent.md](./final-draft/07-langgraph-agent.md) | Agent proposes only |
| 8 | [final-draft/08-benchmark.md](./final-draft/08-benchmark.md) | Older batch write-up |
| 9 | [final-draft/09-recover-more.md](./final-draft/09-recover-more.md) | Current board: first try always, +₹12,472 |
| Pack | [final-draft/README.md](./final-draft/README.md) | Index of the nine notes |

Deeper dump: [FULL-SYSTEM.md](./FULL-SYSTEM.md). Index of the pack: [final-draft/README.md](./final-draft/README.md).

---

## Setup — one by one

### 0. You need

- Docker Desktop
- Java 21
- Python 3.11 + [uv](https://docs.astral.sh/uv/)
- Node 20+

### 1. Clone and env

```bash
cd recovery-engine
cp .env.example .env
```

On Windows PowerShell: `Copy-Item .env.example .env`

Fill `.env` only if you need Razorpay live Test Mode or an LLM key. Local desk + simulate works without those.

### 2. Infrastructure (Postgres, Redis, Kafka)

```bash
docker compose up -d
docker compose ps
```

Wait until `recovery-engine-postgres`, `recovery-engine-redis`, and `recovery-engine-kafka` are **healthy**.

| Service | On your machine |
|---|---|
| Postgres | `localhost:5432` — user `postgres` / password `postgres` / db `revenue_recovery` |
| Redis | `localhost:6379` |
| Kafka | `localhost:9092` |

Do not run `backend/revenue-recovery/docker-compose.yml`. It is unused and will fight port 5432.

### 3. Java backend (`:8080`)

Flyway applies schema on first boot.

```bash
cd backend/revenue-recovery
./gradlew bootRun
```

Windows: `gradlew.bat bootRun`

Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

### 4. ML service (`:8001`)

```bash
cd ml-service
uv run uvicorn app.main:app --reload --port 8001
```

Health: [http://localhost:8001/health](http://localhost:8001/health)

### 5. Agent service (`:8002`)

```bash
cd agent-service
uv run uvicorn app.main:app --reload --port 8002
```

Health: [http://localhost:8002/health](http://localhost:8002/health)

If Ollama / the LLM is down, the agent still proposes from fallback rules. It never charges.

### 6. Frontend (`:3000`)

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000)

| Who | Email | Password | Lands on |
|---|---|---|---|
| CEO | `ceo@recovery.local` | `admin123` | `/dashboard` |
| Approver | `policy@recovery.local` | `approve123` | `/approvals` |

---

## What to open after login

1. **`/dashboard`** — money at risk, measured batch (Results / Playbook tabs), why live cases are stuck. Hover **?** next to a failure for the plain-language reason.
2. **`/desk`** — CEO practice panel. Left: simulate a failure. Right: that case. **Start recovery** — first required step always runs.
3. **`/approvals`** — approver only. Risk / high amount / cancelled sits here. Nothing is charged until they let it through.

Desk **Simulate** skips HMAC and Kafka on purpose so the case appears at once. Live Razorpay POSTs still go HMAC → Redis lock → Kafka → Java.

---

## Measured batch (optional re-run)

Already checked in. After you change the model or the 500-row seed:

```bash
cd ml-service
uv run python scripts/run_benchmark.py
```

Restart Java so `GET /api/admin/benchmark` picks up the new file.

Rules on that board: first retry always counts; extra silent retries skip only if **P < 0.12** and the customer has **≥ 10** payments; high-P risk holds (**P ≥ 0.55**) go to a person. Write-up: [final-draft/09-recover-more.md](./final-draft/09-recover-more.md).

---

## Razorpay Test Mode (optional)

You need a Razorpay account. Keys go in `.env` (`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`).

Scripts live in `scripts/razorpay/`:

| Script | What it does |
|---|---|
| `tunnel.ps1` | Expose `:8080` for a dashboard webhook |
| `create-fixtures.ps1` | Test-mode customer / order / plan / link |
| `simulate-webhooks.ps1` | Sign and POST sample events locally |
| `prove-live-webhook.ps1` | Show a HMAC-signed intake (not desk Simulate) |

Webhook path: `POST /webhooks/razorpay`. Events: `payment.failed`, `payment.captured`, `subscription.pending`, `subscription.halted`, `invoice.expired`, plus the paid variants if you register them.

Desk create buttons are **not** that path. They are `/api/webhooks/simulate/{slug}`.

---

## Health checklist

| URL | Expect |
|---|---|
| http://localhost:8080/actuator/health | Java up |
| http://localhost:8001/health | ML up |
| http://localhost:8002/health | Agent up |
| http://localhost:3000 | Desk up |
| `docker compose ps` | Three containers healthy |
