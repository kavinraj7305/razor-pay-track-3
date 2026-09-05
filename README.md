# Razorpay revenue recovery

Failed Razorpay payments become **recovery cases**. Each failure reason has a four-step playbook. A score may skip **extra** silent retries after the first try. An agent can propose the next step. **Java is the only thing that executes.** The agent has no charge tool.

**Playbook first. ML second. Agent proposes. Java executes.**

The code lives in [`recovery-engine/`](./recovery-engine/).

| Seat | Where | Login |
|---|---|---|
| CEO | `/dashboard`, `/desk` | `ceo@recovery.local` / `admin123` |
| Human in the loop | `/approvals` | `policy@recovery.local` / `approve123` |

| Read next | What you get |
|---|---|
| [recovery-engine/final-draft/](./recovery-engine/final-draft/README.md) | Nine notes, in order — schema through the measured batch |
| [recovery-engine/final-draft/09-recover-more.md](./recovery-engine/final-draft/09-recover-more.md) | How the first-try rule recovers more without skipping payday |

---

## What this product does

A failed Razorpay event opens a case. The planner picks a folder from the reason — `card_not_enrolled`, `insufficient_funds`, `payment_risk_check_failed`, `card_declined`, `currency_not_supported`, and the rest. `/execute` runs **one** step. Local retries are built to fail so you can walk all four steps without charging a live card.

Scoring is allowed to cut **extra** silent retries only. The first required step always runs. Risk and cancel never auto-charge. Those sit for a person.

On the measured **1000**-failure batch (seed 42): playbook recovered **₹9.14L**. First try always runs, then P only cuts extra silent retries. That path recovered **₹9.38L (+₹24,160)**. Thirty-two extra retries cut. First-try payers were not dropped. Those numbers are on `/dashboard` under **Results**.

---

## How to start

Four terminals after Docker. Local desk + Simulate works with an empty `.env`.

### You need

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

```bash
cd recovery-engine
docker compose up -d
docker compose ps
```

Wait until `recovery-engine-postgres`, `recovery-engine-redis`, and `recovery-engine-kafka` are **healthy**.

Do not run `recovery-engine/backend/revenue-recovery/docker-compose.yml`. It will fight port 5432.

### 3. Java (`:8080`)

```bash
cd recovery-engine/backend/revenue-recovery
./gradlew bootRun
```

Windows: `.\gradlew.bat bootRun`

Wait for `Started RevenueRecoveryApplication`.  
Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

### 4. ML (`:8001`)

```bash
cd recovery-engine/ml-service
uv run uvicorn app.main:app --port 8001
```

Health: [http://localhost:8001/health](http://localhost:8001/health)

### 5. Agent (`:8002`)

```bash
cd recovery-engine/agent-service
uv run uvicorn app.main:app --port 8002
```

Health: [http://localhost:8002/health](http://localhost:8002/health)

If Ollama is down, the agent still proposes from fallback rules. It never charges.

### 6. Desk (`:3000`)

```bash
cd recovery-engine/frontend
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000)

| Who | Email | Password | Lands on |
|---|---|---|---|
| CEO | `ceo@recovery.local` | `admin123` | `/dashboard` |
| Approver | `policy@recovery.local` | `approve123` | `/approvals` |

---

## What to open

1. **`/dashboard`** — money at risk, measured batch (Results / Playbook), why live cases are stuck.
2. **`/desk`** — simulate a failure on the left. Start recovery on the right. The first required step always runs.
3. **`/approvals`** — risk / high amount / cancelled. Nothing is charged until a person lets it through.

Short path: CEO → dashboard columns → desk (`card not enrolled`, then `risk failed`) → sign in as approver → let it through or hold it.

| URL | Expect |
|---|---|
| [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) | Java up |
| [http://localhost:8001/health](http://localhost:8001/health) | ML up |
| [http://localhost:8002/health](http://localhost:8002/health) | Agent up |
| [http://localhost:3000](http://localhost:3000) | Desk up |
| `docker compose ps` (inside `recovery-engine/`) | Three containers healthy |
