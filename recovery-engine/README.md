# recovery-engine

Revenue recovery platform: Razorpay webhook intake, ML scoring, and LangGraph recovery agents.

```
recovery-engine/
├── backend/revenue-recovery/   Spring Boot 4.1 / Java 21 (existing domain model)
├── ml-service/                 FastAPI + XGBoost
├── agent-service/              FastAPI + LangGraph + Claude
├── frontend/                   Next.js 15
└── docker-compose.yml
```

## Prerequisites

- Docker Desktop
- Java 21
- Python 3.11 + [uv](https://docs.astral.sh/uv/)
- Node 20+

## Step 1 — infrastructure only

Bring up Postgres, Redis, and Kafka (KRaft, no ZooKeeper). Do not start app services until these three are `healthy`.

```bash
docker compose up -d
docker compose ps
```

Wait until `recovery-engine-postgres`, `recovery-engine-redis`, and `recovery-engine-kafka` all show `healthy`.

| Service  | Host (apps on your machine) | Docker network      |
|----------|-----------------------------|---------------------|
| Postgres | `localhost:5432`            | `postgres:5432`     |
| Redis    | `localhost:6379`            | `redis:6379`        |
| Kafka    | `localhost:9092`            | `kafka:29092`       |

Credentials: user `postgres` / password `postgres` / db `revenue_recovery`.

Copy `.env.example` to `.env` before adding secrets (Claude API key, etc.).

Do not run `backend/revenue-recovery/docker-compose.yml` — it is unused so it cannot steal port 5432.

## App services (after infra is healthy)

```bash
# backend — Flyway applies V1 schema on first start
cd backend/revenue-recovery
./gradlew bootRun                 # Windows: gradlew.bat bootRun

# ml-service  -> http://localhost:8001/health
cd ml-service && uv run uvicorn app.main:app --reload --port 8001

# agent-service -> http://localhost:8002/health
cd agent-service && uv run uvicorn app.main:app --reload --port 8002

# frontend -> http://localhost:3000
cd frontend && npm run dev
```

Backend health: `http://localhost:8080/actuator/health`

## Step 1.3 — Razorpay Test Mode

I cannot create the Razorpay account for you. Do this once in the dashboard, then use the scripts in `scripts/razorpay/`.

### 1. Keys

1. Sign up at [dashboard.razorpay.com](https://dashboard.razorpay.com).
2. Toggle **Test Mode** ON.
3. **Account & Settings → API Keys → Generate Test Keys**.
4. Put `RAZORPAY_KEY_ID` (`rzp_test_...`) and `RAZORPAY_KEY_SECRET` in `.env`.
5. Keep `RAZORPAY_WEBHOOK_SECRET` (default `whsec_dev_local`) and use that same value when you register the webhook.

### 2. Webhook endpoint

`POST /webhooks/razorpay` verifies `X-Razorpay-Signature` (HMAC-SHA256 of the **raw** body) and stores the event in `webhook_event`. Duplicates return `200` with `"duplicate": true`.

Start the backend, then expose it:

```powershell
cd scripts/razorpay
.\tunnel.ps1
```

Register in the dashboard (Test Mode): **Account & Settings → Webhooks → Add New Endpoint**

| Field | Value |
|-------|--------|
| URL | `https://<tunnel-host>/webhooks/razorpay` |
| Secret | same as `RAZORPAY_WEBHOOK_SECRET` |
| Events | `payment.failed`, `payment.captured`, `order.paid`, `subscription.pending`, `subscription.halted`, `subscription.charged`, `invoice.paid`, `invoice.expired` |

Test-mode webhook OTP is `754081`. Restart `bootRun` after changing `.env`.

### 3. Generate Orders / Payments / Subscriptions

```powershell
cd scripts/razorpay
.\create-fixtures.ps1
```

That calls Razorpay’s Test Mode API (customer, order, plan, subscription, payment link). Card PAN charges cannot be created from curl (PCI); open the payment-link URL and pay with failing card `4012001037141112`, or Dashboard → Subscriptions → **Charge this now** → Fail.

### 4. Failure events without waiting on Test Mode

Real test-mode failures are rare. Sign and POST sample payloads locally (backend must be running):

```powershell
cd scripts/razorpay
.\simulate-webhooks.ps1
.\simulate-webhooks.ps1 -Event payment.failed
```

Postman: import `scripts/razorpay/Razorpay-Test-Mode.postman_collection.json` and set `keyId`, `keySecret`, `webhookSecret`.
