Created: **1 Sep 2026** (local demo checklist)
Reason: one page of URLs so we can prove Day 1–2 baseline in the browser.
Last updated: **1 Sep 2026, 19:40 IST**
Why updated: point at the playbook-first → ML story in the intelligence plan.

# NEXT — do this now

Deadline: **before 5 Sep 2026**.

**Build order now → [intelligence-layer-plan.md](./intelligence-layer-plan.md)** (dated timeline + **why ML after playbooks**). Do **not** start `recovery.events` / cooldown Redis next.

---

## Done

- Day 1 webhook → `recovery_case`
- 8 simulate scenarios
- **Baseline actions** (no Kafka / Redis yet): each new case gets a `recovery_action` + `audit_event`
- **Synthetic batch** (Step 3.4): 500 cases / 500 customers on `acc_syn_training`. `uv run python scripts/generate_synthetic.py`
- **Customer features** (Step 3.1): from existing Postgres tables (no new table). `uv run python scripts/refresh_features.py`. Export: `ml-service/data/customer_features.csv` + `case_features.csv`.
- **`POST /predict`** (Step 3.2): XGBoost `P(recovery)`. Train: `uv run python scripts/train_model.py`. Serve: `uv run uvicorn app.main:app --port 8001`. Metrics: `data/predict_metrics.json`.
- **Data-volume gate:** labelled outcomes **&lt; 400** → playbook only; **≥ 400** → playbook + `/predict`. Prod: `10000`.
- **Propose-only agent** (Step 3.3): `POST http://localhost:8002/propose`. Calls `/predict`. **executes: false**. No charge tool. `uv run uvicorn app.main:app --port 8002` from `agent-service/`. ML should be on 8001.

Restart `bootRun`, then [http://localhost:8080/api/webhooks/simulate/all](http://localhost:8080/api/webhooks/simulate/all)

Action plan API:
- [http://localhost:8080/api/recovery-cases](http://localhost:8080/api/recovery-cases)
- `GET /api/recovery-cases/{caseId}` — read
- `GET or POST /api/recovery-cases/{caseId}/plan` — run dumb planner
- `GET or POST /api/recovery-cases/{caseId}/execute` — run the next 4-step playbook action for that case’s reason (DEV retry/SMS/pay-link)

Beekeeper: `recovery_case` (reason + status) and **`recovery_action`** (`action_type`).

Service folders + what each file is for: **[service-map.md](./service-map.md)**

| Scenario | Action |
|---|---|
| insufficient_funds | `RETRY_PAYMENT` |
| card_expired | `SEND_PAYMENT_LINK` |
| risk-failed | `SEND_EMAIL` **CANCELLED** (stop) |
| subscription.pending | `RETRY_PAYMENT` |
| subscription.halted | `SEND_PAYMENT_LINK` |
| invoice.expired | `REQUEST_PROMISE_TO_PAY` |
| checkout.abandoned | `SEND_PAYMENT_LINK` |
| payment.captured | audit `RECOVERY_CASE_RECOVERED` |

---

## Next

1. Wire agent JSON onto Java `audit_event` (decision trace). Policy ALLOW/BLOCK. Java still `/execute`.

Kafka `recovery.events` / `action.events` and Redis cooldown/lock wait until those curl. Detail: [intelligence-layer-plan.md](./intelligence-layer-plan.md).

---

## Not next

Voice, Vault, RLS, 80 error codes, new Kafka topics, frontend until `/predict` works.
