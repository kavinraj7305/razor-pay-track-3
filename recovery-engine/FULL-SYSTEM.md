# Recovery Engine — everything we built

This is the complete record of the product: what it is, why it exists, how the repo is laid out, what every folder and important file does, and how the system runs from a failed payment to recovered rupees. Read it as the single source of truth for the desk.

Hackathon: **Razorpay Track 03 — AI Revenue Recovery**. Deadline was **before 5 Sep 2026**.

Pitch sentence we locked:

> **Playbook first. ML second (this customer). Agent proposes. Java executes. Agent has no charge tool.**

---

## 0. The problem

Failed Razorpay payments do not disappear. A card declines, a mandate misses, a checkout is abandoned, an invoice expires. Most systems either retry blindly or escalate late.

This product turns those failures into **recovery cases** and walks a bounded loop:

1. **Detect** — webhook or simulated issue opens a case.
2. **Diagnose** — Java maps the failure reason to a 4-step playbook.
3. **Score** — ML may answer *should we chase this customer?*
4. **Propose** — an agent may suggest a next step. It cannot charge.
5. **Guard** — PolicyEngine allows, skips a retry, or blocks.
6. **Human** — if blocked, a person holds it or lets it through.
7. **Act** — Java runs the next playbook step **once**. No silent infinite loop.
8. **Prove** — every detect / score / propose / block / execute / close writes `audit_event`.

The product is not a chatbot. It is a small two-person recovery desk.

---



## 1. How the pieces sit together

```
Browser  :3000  Next.js desk
    │
    ├─ /api/*            → Spring Boot  :8080
    └─ /agent-api/*      → LangGraph    :8002

Spring Boot
    ├─ Postgres          :5432   cases, audit, desk users
    ├─ Redis             :6379   webhook SETNX (no duplicate event)
    ├─ Kafka             :9092   payment / invoice / checkout events
    ├─ ML service        :8001   XGBoost P(recovery)
    └─ Agent service     :8002   propose only — never execute
```


| Folder                                      | Port               | What it is                                                       |
| ------------------------------------------- | ------------------ | ---------------------------------------------------------------- |
| `recovery-engine/backend/revenue-recovery/` | 8080               | Spring Boot 4 / Java 21. Cases, playbooks, policy, auth          |
| `recovery-engine/ml-service/`               | 8001               | FastAPI + XGBoost. `P(recovery)`                                 |
| `recovery-engine/agent-service/`            | 8002               | FastAPI + LangGraph + Ollama. Diagnosis and a recommended action |
| `recovery-engine/frontend/`                 | 3000               | Next.js 15 desk (login, dashboard, queue, recovery desk)         |
| `recovery-engine/scripts/razorpay/`         | —                  | Test-mode tunnel, fixtures, signed sample webhooks               |
| `recovery-engine/docker-compose.yml`        | 5432 / 6379 / 9092 | Postgres 17, Redis 7, Kafka KRaft                                |
| `docs/hackathon/`                           | —                  | Track brief, plans, service map, reason→action                   |
| `docs/webhook/`                             | —                  | Razorpay webhook field notes                                     |


Infra credentials: user `postgres` / password `postgres` / db `revenue_recovery`.

Do **not** run `backend/revenue-recovery/docker-compose.yml` — it is unused so it cannot steal port 5432.

---



## 2. Timeline — beginning to end

Built in this order. Do not invert it when explaining the system.


| When              | What shipped                                                       | Why                                                                 |
| ----------------- | ------------------------------------------------------------------ | ------------------------------------------------------------------- |
| **Day 1**         | Postgres schema (13 tables) + Razorpay webhook intake              | Detect. HMAC + Redis SETNX + Kafka + `webhook_event` inbox          |
| **Day 1–2**       | Ingest → `recovery_case` + 8 simulate slugs                        | A failure becomes a case with a reason and rupees at risk           |
| **Day 2**         | Baseline playbooks (reason folders, 4 steps, `/plan` + `/execute`) | Diagnose + act + stop. Java if/else, not an LLM                     |
| **Day 2 desk**    | Next.js recovery desk                                              | Click a failure, press Start, watch Detect → Score → Act            |
| **Day 3 morning** | Synthetic 500 labelled cases on merchant `acc_syn_training`        | Ground truth for ML. Excluded from the live desk                    |
| **Day 3**         | Customer features + XGBoost `POST /predict`                        | `P(recovery)` for *this* customer, not only the error code          |
| **Day 3**         | Data-volume gate                                                   | Below 400 labelled rows → playbook only. Enough data → playbook + P |
| **Day 3–4**       | LangGraph case agent (`/propose`) + ops brain (`/ops/briefing`)    | Propose-only. Live fallback if Ollama is down                       |
| **Day 4**         | PolicyEngine + approval queue                                      | Java owns money. Human can override a block                         |
| **Day 4–5**       | Two-role auth, CEO dashboard, teal desk UI                         | CEO + human in the loop. Operator seat removed                      |


Leftover on purpose (not built, not needed for the pitch):

- Extra Kafka topics `recovery.events` / `action.events`
- Redis cooldown / retry / lock keys
- Real Razorpay charges on `/execute` (DEV retry always fails so you can walk all 4 steps)
- Hinglish voice, Vault, RLS, all 80+ Razorpay error codes
- OTP reasons, live `promise_to_pay` tracker, `order.paid` / `subscription.charged` / `invoice.paid` close events

---



## 3. Two people only

We used to have three seats (CEO, policy guard, operator). That was too many. There are **two roles**.


| Role in the UI        | Stored as  | Who         | What they see                                  |
| --------------------- | ---------- | ----------- | ---------------------------------------------- |
| **CEO**               | `ADMIN`    | Priya Shah  | Dashboard, recovery desk, and the policy queue |
| **Human in the loop** | `APPROVER` | Arjun Mehta | The approval queue only                        |


The old **operator** seat is gone from login and from create-user. The enum value `OPERATOR` still exists in the database check constraint so old rows do not break. On startup those users are turned **inactive** and cannot sign in.

### Demo logins


| Person            | Email                   | Password     | Lands on     |
| ----------------- | ----------------------- | ------------ | ------------ |
| CEO               | `ceo@recovery.local`    | `admin123`   | `/dashboard` |
| Human in the loop | `policy@recovery.local` | `approve123` | `/approvals` |


Sign-in is two cards. The UI also filters the demo API so a stale backend cannot paint a third card.

CEO can add another human-in-the-loop person from the dashboard. They cannot create an operator.

---



## 4. Full repo tree

```
razor pay backend/
├── docs/
│   ├── hackathon/                 plans, track brief, service map
│   └── webhook/                   Razorpay field notes
└── recovery-engine/
    ├── FULL-SYSTEM.md             this file
    ├── README.md                  how to run infra + apps
    ├── docker-compose.yml         Postgres + Redis + Kafka
    ├── backend/revenue-recovery/  Java money engine
    ├── ml-service/                FastAPI + XGBoost
    ├── agent-service/             FastAPI + LangGraph
    ├── frontend/                  Next.js 15 desk
    └── scripts/razorpay/          Test Mode helpers
```

---



## 5. Pages (what you click)


| Path         | Who                       | File                                                  | What                                                            |
| ------------ | ------------------------- | ----------------------------------------------------- | --------------------------------------------------------------- |
| `/login`     | Anyone signed out         | `frontend/src/app/login/page.tsx`                     | Two people. Click a card or type credentials                    |
| `/`          | Anyone                    | `frontend/src/app/page.tsx`                           | Redirects to the right home, or login                           |
| `/dashboard` | CEO                       | `frontend/src/app/dashboard/page.tsx`                 | Money at risk, why it is stuck, add the other person, directory |
| `/desk`      | CEO                       | `frontend/src/app/desk/page.tsx` + `RecoveryDesk.tsx` | Create the 8 failure types and run the live playbook            |
| `/approvals` | CEO and human in the loop | `frontend/src/app/approvals/page.tsx`                 | Cases policy blocked. Hold or let through                       |




### Nav bar — `frontend/src/components/DeskChrome.tsx`

Sticky at the top.

- Left: role + name
- Center: the links that person is allowed to open
- Right: Sign out

CEO links: **CEO dashboard · Policy queue · Recovery desk**  
Human in the loop: **Policy queue** only

`RoleGate` sends the wrong person home (`/dashboard` for CEO, `/approvals` for the other).

### Look and feel — `frontend/src/app/globals.css`

We dropped the navy + gold “template desk” look.


| Token             | Colour                | Used for                          |
| ----------------- | --------------------- | --------------------------------- |
| Paper             | `#e6efe9` sage        | Page background                   |
| Header / ink teal | `#0f332f` / `#15564f` | Nav, page header, primary buttons |
| Accent teal       | `#2a8f82`             | Pills, left stripe on queue rows  |
| Shine mint        | `#9ee0d4`             | Labels on dark bars               |
| Card              | `#f7fcfa`             | Panels                            |
| Stop              | `#b0423a`             | Holds, errors                     |


Fonts: **Source Serif 4** for titles, **Source Sans 3** for body. Soft 7–8px corners.

---



## 6. CEO dashboard

Page: `frontend/src/app/dashboard/page.tsx`  
API: `GET /api/admin/dashboard` → `DashboardService`

### Stats

- Amount at risk + open cases
- Waiting on policy (queue length)
- Recovered + failed/expired
- People: CEO count · human-in-the-loop count



### Why money is stuck

One row per failure reason, with a count. Hover (or tab to) a row and a short description appears.


| Reason                                 | What it means                                                      |
| -------------------------------------- | ------------------------------------------------------------------ |
| `insufficient_funds`                   | Bank said not enough money. Wait for payday, try once — not a loop |
| `card_expired`                         | Saved card is dead. Send a new payment link                        |
| `payment_risk_check_failed`            | Risk/fraud blocked the charge. Needs the human in the loop         |
| `subscription.pending`                 | Mandate missed. Space out a few mandate retries                    |
| `subscription.halted`                  | Retries already exhausted. A person picks the next step            |
| `invoice.expired`                      | B2B invoice timed out. Chase receivables                           |
| `checkout.abandoned`                   | They left checkout. One pay link, no silent charges                |
| `captured`                             | Money already came back. Close as recovered                        |
| `payment_cancelled`                    | Cancelled. Do not retry                                            |
| `invalid_vpa`                          | Wrong UPI id. Ask for a new VPA                                    |
| `gateway_technical` / `bank_technical` | Hiccup. One short wait, then one retry                             |




### Add the other person

Name, email, temporary password. Always creates `APPROVER`. No role dropdown.

### Directory

Active CEO and human-in-the-loop only. Operator rows are hidden.

Training merchant `acc_syn_training` is excluded from the live desk and from the approval queue so synthetic training rows do not clutter the CEO view.

---



## 7. Recovery desk (CEO)

Page: `/desk` — `frontend/src/components/RecoveryDesk.tsx`

CEO creates the 8 demo failures and presses **Start**. The desk:

1. Diagnoses the reason
2. Asks ML for a score when it can
3. Asks the agent for a proposal (`POST /agent-api/propose`)
4. Writes that proposal onto the case (`POST /api/recovery-cases/{id}/agent-proposal`)
5. Asks PolicyEngine if execute is allowed
6. If blocked → ticker says wait for the human in the loop
7. If allowed → runs the next playbook step **once** (`POST .../execute`)

Also on the desk:

- **Ops patterns** strip from `POST /agent-api/ops/briefing` (SQL spike detection + optional Ollama narration)
- **Live signed intake** strip from `GET /api/webhooks/inbox` — HMAC-verified Razorpay POSTs vs desk simulate
- **Ask agent** card: diagnosis, reasoning, recommended vs default playbook, deviate badge, confidence, ML score, escalate, `actions_available: propose only`, `fallback_used`
- Live ticker: Detect → Score → Act → Done

The 8 create buttons (from `SimulateScenario`):


| Slug                   | Reason                      | Intended path                   |
| ---------------------- | --------------------------- | ------------------------------- |
| `insufficient-funds`   | `insufficient_funds`        | Delayed retry (payday)          |
| `card-expired`         | `card_expired`              | Payment link for a new card     |
| `risk-failed`          | `payment_risk_check_failed` | Do not retry — escalate / queue |
| `subscription-pending` | `subscription.pending`      | Retry sequencer                 |
| `subscription-halted`  | `subscription.halted`       | Escalate — retries exhausted    |
| `invoice-expired`      | `invoice.expired`           | B2B receivables chase           |
| `checkout-abandoned`   | `checkout.abandoned`        | Checkout drop-off pay-link      |
| `payment-captured`     | `captured`                  | Close — rupee recovered         |


Pitch pair: **insufficient_funds** (retries then pay link) and **risk-failed** (lands in the queue). High-amount NSF (≥ ₹80,000) also lands in the queue.

---



## 8. Approval queue

Page: `frontend/src/app/approvals/page.tsx`  
API: `GET /api/approvals/pending`, `POST /api/approvals/{id}/approve`, `POST /api/approvals/{id}/reject`

Each row is one blocked case:

- Failure reason in plain words (`payment risk check failed`)
- Short hold label (`Risk hold`, `Agent escalate`, `High amount`)
- Rupees at risk, status, truncated case id
- Recommended action and agent diagnosis
- Fallback-rule chips when the agent text is `key=value` pairs
- Audit note
- **Hold it** or **Let it through**

Layout is a **vertical list** (one case per row). Top strip: how many cases are waiting, and how much rupee is still sitting here.

When the human **lets it through**, we write `POLICY_APPROVED`. Next evaluate hits the human-override rule and allows the playbook.

When they **hold it**, we write `POLICY_REJECTED`. Money stays untouched.

---



## 9. Policy engine — Java owns money

File: `service/plan/PolicyEngine.java`

The agent **cannot** charge, retry, or send money. It writes an `AGENT_PROPOSE` audit row. Policy reads that row plus hard rules.

Default human-approval amount: **₹80,000**.


| Order | If this is true                                             | Verdict    | Reason code              |
| ----- | ----------------------------------------------------------- | ---------- | ------------------------ |
| 1     | A later `POLICY_APPROVED` exists than any `POLICY_REJECTED` | ALLOW      | `HUMAN_OVERRIDE`         |
| 2     | Case reason contains `risk` or `cancelled`                  | BLOCK      | `RISK_OR_CANCELLED`      |
| 3     | Agent said escalate, or recommended `DO_NOT_RETRY`          | BLOCK      | `AGENT_ESCALATE`         |
| 4     | Amount at risk ≥ merchant threshold (or ₹80,000)            | BLOCK      | `HUMAN_APPROVAL_AMOUNT`  |
| 5     | Agent said `SKIP_EXTRA_RETRY`                               | SKIP_RETRY | `AGENT_SKIP_EXTRA_RETRY` |
| 6     | Otherwise                                                   | ALLOW      | `ALLOW_PLAYBOOK`         |


`BLOCK` means the desk **must not** execute. The case appears in the approval queue.

`SKIP_RETRY` cancels planned `RETRY_PAYMENT` rows and writes `POLICY_SKIP_RETRY`.

---



## 10. Auth and who may call what

Session: login returns a token. Frontend keeps it in `localStorage` under `recovery-desk-session`. Requests send `Authorization: Bearer …`.

Interceptor: `auth/AuthInterceptor.java` + `@RequireRole`. Config: `config/AuthWebConfig.java`.


| API                                              | Allowed                              |
| ------------------------------------------------ | ------------------------------------ |
| `GET /api/auth/demo`                             | Public. CEO + human in the loop only |
| `POST /api/auth/login`                           | Public                               |
| `POST /api/auth/logout`, `GET /api/auth/me`      | Signed in                            |
| `GET /api/admin/dashboard`                       | CEO                                  |
| `POST /api/admin/users`                          | CEO — creates `APPROVER` only        |
| `GET /api/approvals/pending`                     | CEO + human in the loop              |
| `POST /api/approvals/{id}/approve` or `/reject`  | CEO + human in the loop              |
| `GET /api/recovery-cases` and Start/plan/execute | CEO                                  |
| `POST /api/webhooks/simulate/{slug}`             | CEO                                  |
| `POST /webhooks/razorpay`                        | Razorpay signature, not a desk role  |


---



## 11. Every HTTP API



### Auth — `AuthController`


| Method | Path               | What                              |
| ------ | ------------------ | --------------------------------- |
| GET    | `/api/auth/demo`   | Two demo cards (ADMIN + APPROVER) |
| POST   | `/api/auth/login`  | Email + password → session token  |
| GET    | `/api/auth/me`     | Current session                   |
| POST   | `/api/auth/logout` | Clear token                       |




### Admin — `AdminController`


| Method | Path                   | What                              |
| ------ | ---------------------- | --------------------------------- |
| GET    | `/api/admin/dashboard` | Snapshot: rupees, reasons, people |
| POST   | `/api/admin/users`     | Create human-in-the-loop user     |




### Approvals — `ApprovalController`


| Method | Path                          | What                        |
| ------ | ----------------------------- | --------------------------- |
| GET    | `/api/approvals/pending`      | Blocked cases for the queue |
| POST   | `/api/approvals/{id}/approve` | Let it through              |
| POST   | `/api/approvals/{id}/reject`  | Hold it                     |




### Cases — `RecoveryCaseController`


| Method   | Path                                          | What                                                                     |
| -------- | --------------------------------------------- | ------------------------------------------------------------------------ |
| GET      | `/api/recovery-cases`                         | Live desk list (excludes training merchant) + ML peek + playbook preview |
| GET      | `/api/recovery-cases/{caseId}`                | Plan, actions, audit, policy peek                                        |
| GET/POST | `/api/recovery-cases/{caseId}/plan`           | Run baseline planner                                                     |
| GET/POST | `/api/recovery-cases/{caseId}/execute`        | Next playbook step **once**, after PolicyEngine                          |
| POST     | `/api/recovery-cases/{caseId}/agent-proposal` | Store agent JSON as `AGENT_PROPOSE` audit                                |




### Live inbox — `WebhookInboxController`

| Method | Path | What |
|---|---|---|
| GET | `/api/webhooks/inbox` | Last 20 webhook rows + HMAC / origin / linked case. CEO only |

Three origins a judge can read on the desk:

| `origin` | How it arrived | HMAC? |
|---|---|---|
| `DESK_SIMULATE` | Create-issue buttons / `/api/webhooks/simulate` | No. Skips signature and Kafka |
| `LOCAL_SCRIPT` | `simulate-webhooks.ps1` signed with our secret | Yes, but we signed it |
| `RAZORPAY` | Razorpay Test Mode servers POSTed `/webhooks/razorpay` | Yes. This is the credibility proof |

### Simulate — `WebhookSimulateController`


| Method   | Path                            | What                                                 |
| -------- | ------------------------------- | ---------------------------------------------------- |
| GET      | `/api/webhooks/simulate`        | List the 8 slugs                                     |
| GET/POST | `/api/webhooks/simulate/{slug}` | Build fake Razorpay JSON, ingest in the same request |
| GET/POST | `/api/webhooks/simulate/all`    | Run all 8                                            |


Simulate skips Kafka on purpose so the case + action appear immediately.

### Real Razorpay — `RazorpayWebhookController`


| Method | Path                 | What                                                                   |
| ------ | -------------------- | ---------------------------------------------------------------------- |
| POST   | `/webhooks/razorpay` | HMAC-SHA256 of raw body → Redis SETNX → Kafka → consumer → same ingest |


Duplicates return `200` with `"duplicate": true`.

### ML — FastAPI `:8001`


| Method | Path       | What                                                        |
| ------ | ---------- | ----------------------------------------------------------- |
| GET    | `/health`  | Liveness                                                    |
| POST   | `/predict` | Features in → `recoveryProbability` + `LIKELY` / `UNLIKELY` |




### Agent — FastAPI `:8002`


| Method | Path            | What                                                               |
| ------ | --------------- | ------------------------------------------------------------------ |
| GET    | `/health`       | Postgres / Ollama / ML picture. Process stays up if Ollama is down |
| POST   | `/propose`      | 3-node case graph. Propose only                                    |
| POST   | `/ops/briefing` | SQL recurring patterns + optional narration                        |


Frontend proxies (`frontend/next.config.ts`):

- `/api/*` → `http://127.0.0.1:8080/api/*`
- `/agent-api/*` → `http://127.0.0.1:8002/*`

---



## 12. How a failure becomes money recovered

```
controller
  → RazorpayWebhookService          (or WebhookSimulateService)
      → Kafka → RecoveryEventConsumer   (simulate skips Kafka)
          → RecoveryCaseIngestService
              → draft + merchant/customer + payment/checkout
              → BaselineActionPlanner     (writes recovery_action)
  → RecoveryActionPlanService       (GET the plan / execute later)
      → PolicyEngine.apply
      → handler.executeNext         (one step)
```



### Tables written on ingest (in order)


| Step | Table                 | What is stored                                          |
| ---- | --------------------- | ------------------------------------------------------- |
| 1    | `webhook_event`       | raw event inbox                                         |
| 2    | `merchant`            | upsert by `account_id`                                  |
| 3    | `customer`            | upsert by email/contact                                 |
| 4a   | `payment`             | only for `payment.failed`                               |
| 4b   | `checkout_session`    | only for checkout abandoned                             |
| 5    | `recovery_case`       | the problem (`reason`, `amount_at_risk`, status `OPEN`) |
| 6    | `recovery_action`     | the first move (`PLANNED` or `CANCELLED`)               |
| 7    | `audit_event`         | `BASELINE_ACTION_PLANNED` or `BASELINE_ACTION_BLOCKED`  |
| 8    | `recovery_case` again | usually `ACTION_PLANNED`; risk stays `OPEN`             |


`payment.captured` does not open a new case. It finds the matching open case, sets `RECOVERED`, writes `RECOVERY_CASE_RECOVERED`.

Duplicate same `source` + `source_id` → skip. No second case.

---



## 13. Database

Flyway on first boot. Three migrations.

### V1 — `V1__create_phase1_schema.sql` (13 tables)


| Table              | Why it exists                                           |
| ------------------ | ------------------------------------------------------- |
| `merchant`         | Who owns the money                                      |
| `customer`         | Who we are chasing                                      |
| `payment`          | Failed / captured charge                                |
| `payment_attempt`  | Per-try failure code                                    |
| `subscription`     | Mandate / recurring                                     |
| `invoice`          | B2B receivable                                          |
| `checkout_session` | Abandoned cart                                          |
| `recovery_case`    | The problem: reason, rupees at risk, status             |
| `recovery_action`  | The move: retry / link / PTP / email                    |
| `recovery_outcome` | Did money come back                                     |
| `recovery_policy`  | Max retries, human-approval threshold                   |
| `promise_to_pay`   | Schema ready; live tracker not wired                    |
| `audit_event`      | Every detect / plan / propose / block / execute / close |


`recovery_case.status`: `OPEN` → `ACTION_PLANNED` → `RECOVERING` → `RECOVERED` (or `FAILED` / `EXPIRED` / `PROMISE_TO_PAY`).

`recovery_action.status`: `PLANNED` / `APPROVED` / `EXECUTING` / `EXECUTED` / `FAILED` / `CANCELLED`.

### V2 — `V2__create_webhook_event.sql`

Inbox. Does not alter the 13 Phase 1 tables.

### V3 — `V3__desk_user_roles.sql`

`desk_user`: email, hash, role (`ADMIN` / `APPROVER` / `OPERATOR`), session token, active flag.

### V4 — `V4__webhook_intake_hmac.sql`

`webhook_event.intake` (`DESK_SIMULATE` / `HMAC_SIGNED`) and `signature_verified`. HMAC is stamped at the door in `RazorpayWebhookService` *before* Kafka, so a judge can see a signed receipt even if ingest is still catching up.

---



## 14. Reason → first action (planner)

There is **no mapping table** in Postgres. The picker is code: `BaselineActionPlanner.decide()`. First match wins.


| Priority | If this is true                                    | `action_type`            | Action status    |
| -------- | -------------------------------------------------- | ------------------------ | ---------------- |
| 1        | `payment_risk_check_failed` or `payment_cancelled` | `SEND_EMAIL`             | CANCELLED (stop) |
| 2        | invoice / `invoice.expired`                        | `REQUEST_PROMISE_TO_PAY` | PLANNED          |
| 3        | checkout / `checkout.abandoned`                    | `SEND_PAYMENT_LINK`      | PLANNED          |
| 4        | `subscription.halted`                              | `SEND_PAYMENT_LINK`      | PLANNED          |
| 5        | `card_expired` or `invalid_vpa`                    | `SEND_PAYMENT_LINK`      | PLANNED          |
| 6        | NSF / gateway / bank / `subscription.pending`      | `RETRY_PAYMENT`          | PLANNED          |
| 7        | else (`card_declined`, unknown)                    | `RETRY_PAYMENT`          | PLANNED          |




### Action types in code


| `action_type`            | When                                                                   |
| ------------------------ | ---------------------------------------------------------------------- |
| `RETRY_PAYMENT`          | Transient fail — try the same instrument later                         |
| `SEND_PAYMENT_LINK`      | Instrument is dead, checkout dropped, or sub retries exhausted         |
| `REQUEST_PROMISE_TO_PAY` | B2B invoice expired                                                    |
| `SEND_EMAIL`             | Risk / cancel — do not retry; escalate only                            |
| `SEND_SMS`               | In the enum; used inside playbook steps, not as the first planner pick |


---



## 15. The 4-step playbooks

Each reason has a folder under `service/plan/handler/`. `PlaybookRunner` reuses the planned row, bumps attempt, runs **one** step per `/execute`.

DEV helpers (`DevPaymentRetryService`, `DevSmsService`) do **not** call Razorpay. Retry is built to fail locally so you can walk all four steps.


| Folder                 | Reason                                 | Step 1       | Step 2              | Step 3         | Step 4       |
| ---------------------- | -------------------------------------- | ------------ | ------------------- | -------------- | ------------ |
| `insufficientfunds/`   | `insufficient_funds`                   | Silent retry | Second retry        | Third retry    | Payment link |
| `cardexpired/`         | `card_expired`                         | Pay link     | SMS nudge           | Second nudge   | Stop         |
| `riskfailed/`          | `payment_risk_check_failed`            | Block retry  | Ops SMS             | Wait for human | Stop         |
| `paymentcancelled/`    | `payment_cancelled`                    | Block retry  | Low-pri SMS         | Audit wait     | Stop         |
| `invalidvpa/`          | `invalid_vpa`                          | Pay link     | SMS nudge           | Second nudge   | Stop         |
| `gatewaytechnical/`    | `gateway_technical` / `bank_technical` | Retry        | Retry               | Retry          | Pay link     |
| `subscriptionpending/` | `subscription.pending`                 | Silent retry | Second retry        | Third retry    | Warning SMS  |
| `subscriptionhalted/`  | `subscription.halted`                  | Pay link     | SMS nudge           | Second nudge   | Stop         |
| `invoiceexpired/`      | `invoice.expired`                      | PTP chase    | Follow-up           | Escalate       | Stop         |
| `checkoutabandoned/`   | `checkout.abandoned`                   | Pay link     | SMS nudge           | Second nudge   | Stop         |
| `DefaultReasonHandler` | unknown                                | Plan retry   | no execute playbook | —              | —            |


Shared files in `handler/`:


| File                         | What                                             |
| ---------------------------- | ------------------------------------------------ |
| `BaselineReasonHandler.java` | Interface: `supports` + `decide` + `executeNext` |
| `PlaybookRunner.java`        | Shared 4-step execute                            |
| `PlaybookStep.java`          | One step contract                                |
| `PlaybookPreviews.java`      | Step list for the desk UI                        |
| `DevPlaybookOps.java`        | DEV retry / SMS / pay-link / block helpers       |


---



## 16. The 8 demo cases (this is the product)


| #   | Slug                   | Webhook event          | `reason`                    | First action                     | After plan                  |
| --- | ---------------------- | ---------------------- | --------------------------- | -------------------------------- | --------------------------- |
| 1   | `insufficient-funds`   | `payment.failed`       | `insufficient_funds`        | `RETRY_PAYMENT` PLANNED          | `ACTION_PLANNED`            |
| 2   | `card-expired`         | `payment.failed`       | `card_expired`              | `SEND_PAYMENT_LINK` PLANNED      | `ACTION_PLANNED`            |
| 3   | `risk-failed`          | `payment.failed`       | `payment_risk_check_failed` | `SEND_EMAIL` CANCELLED           | stays `OPEN`                |
| 4   | `subscription-pending` | `subscription.pending` | `subscription.pending`      | `RETRY_PAYMENT` PLANNED          | `ACTION_PLANNED`            |
| 5   | `subscription-halted`  | `subscription.halted`  | `subscription.halted`       | `SEND_PAYMENT_LINK` PLANNED      | `ACTION_PLANNED`            |
| 6   | `invoice-expired`      | `invoice.expired`      | `invoice.expired`           | `REQUEST_PROMISE_TO_PAY` PLANNED | `ACTION_PLANNED`            |
| 7   | `checkout-abandoned`   | `checkout.abandoned`   | `checkout.abandoned`        | `SEND_PAYMENT_LINK` PLANNED      | `ACTION_PLANNED`            |
| 8   | `payment-captured`     | `payment.captured`     | *(no new case)*             | none                             | matching case → `RECOVERED` |


Case 8 closes the open `insufficient_funds` case from the same `/all` run.

Planner also understands (no desk button yet): `payment_cancelled`, `gateway_technical`, `bank_technical`, `invalid_vpa`.

---



## 17. Java backend — every important file

Root package: `com.razorpayhackthon.revenue_recovery`

### Controllers (HTTP only)


| File                                        | What                                     |
| ------------------------------------------- | ---------------------------------------- |
| `controller/AuthController.java`            | Demo, login, me, logout                  |
| `controller/AdminController.java`           | Dashboard + create user                  |
| `controller/ApprovalController.java`        | Pending / approve / reject               |
| `controller/RecoveryCaseController.java`    | List, get, plan, execute, agent-proposal |
| `controller/WebhookSimulateController.java` | The 8 slugs + `/all`                     |
| `controller/RazorpayWebhookController.java` | Real signed webhook                      |




### Auth


| File                                           | What                                                 |
| ---------------------------------------------- | ---------------------------------------------------- |
| `auth/AuthInterceptor.java`                    | Bearer token → current user                          |
| `auth/AuthContext.java`                        | Thread-local session                                 |
| `auth/RequireRole.java`                        | Method annotation                                    |
| `config/AuthWebConfig.java`                    | Register interceptor + public paths                  |
| `service/auth/AuthService.java`                | Login, demo people, assignable role                  |
| `service/auth/DeskUserSeeder.java`             | Seeds CEO + approver; deactivates leftover operators |
| `service/auth/PasswordHasher.java`             | Hash / verify                                        |
| `service/auth/ApprovalService.java`            | Queue + approve/reject audit                         |
| `service/auth/DashboardService.java`           | CEO snapshot                                         |
| `entity/DeskUser.java` + `enums/DeskRole.java` | Stored user                                          |
| `repository/DeskUserRepository.java`           | Lookup by email / token                              |




### Webhook + ingest


| File                                            | What                              |
| ----------------------------------------------- | --------------------------------- |
| `webhook/SimulateScenario.java`                 | The 8 slugs                       |
| `webhook/SimulatedWebhookFactory.java`          | Fake Razorpay JSON                |
| `webhook/RazorpaySignatureVerifier.java`        | HMAC-SHA256                       |
| `webhook/RedisWebhookIdempotencyStore.java`     | SETNX so one event = one case     |
| `webhook/WebhookTopicRouter.java`               | Which Kafka topic                 |
| `service/webhook/RazorpayWebhookService.java`   | HMAC verify + stamp inbox + Kafka |
| `service/webhook/WebhookInboxService.java`      | Last signed events for the desk   |
| `controller/WebhookInboxController.java`        | `GET /api/webhooks/inbox`         |
| `service/webhook/WebhookSimulateService.java`   | Local demo pack                   |
| `service/webhook/RecoveryEventConsumer.java`    | Kafka → ingest                    |
| `service/ingest/RecoveryCaseIngestService.java` | Open / close the case             |
| `service/ingest/RecoveryCaseDraftFactory.java`  | Reason, amount, source            |
| `service/ingest/MerchantCustomerService.java`   | Upsert owners                     |
| `service/ingest/RecoverySourceWriter.java`      | Write payment or checkout_session |




### Plan + policy + ML gate


| File                                          | What                                  |
| --------------------------------------------- | ------------------------------------- |
| `service/plan/BaselineActionPlanner.java`     | Pick handler, write first action      |
| `service/plan/RecoveryActionPlanService.java` | List/get/plan/execute/record proposal |
| `service/plan/PolicyEngine.java`              | Allow / skip / block                  |
| `service/plan/PolicyDecision.java`            | Verdict bag                           |
| `service/plan/AuditWriter.java`               | Append `audit_event`                  |
| `service/ml/MlDataGate.java`                  | Below floor → playbook only           |
| `service/retry/DevPaymentRetryService.java`   | Fake retry (always fails)             |
| `service/plan/notify/DevSmsService.java`      | Log-only SMS                          |




### Frontend glue files (desk)


| File                            | What                                         |
| ------------------------------- | -------------------------------------------- |
| `frontend/src/lib/api.ts`       | All fetch helpers + auth header              |
| `frontend/src/lib/session.ts`   | localStorage session, home path, role labels |
| `frontend/src/lib/types.ts`     | TypeScript shapes                            |
| `frontend/src/lib/narrative.ts` | Ticker copy per step                         |
| `frontend/src/lib/progress.ts`  | Step progress helpers                        |
| `frontend/src/app/layout.tsx`   | Fonts + chrome                               |
| `frontend/next.config.ts`       | `/api` and `/agent-api` rewrites             |


---



## 18. ML service — `P(recovery)`

Why it exists: two `insufficient_funds` customers still got the same N retries. The playbook knows the reason. It does not know who pays back.


| Layer           | Answers                                                        |
| --------------- | -------------------------------------------------------------- |
| Playbook (Java) | Given this **reason**, which steps                             |
| ML              | Given this **customer + reason + amount**, is a retry worth it |
| Agent           | Propose JSON only                                              |
| Policy          | Hard stop even if the model or agent says retry                |




### Data-volume gate (`MlDataGate`)


| Labelled `recovery_outcome` rows | What runs                                            |
| -------------------------------- | ---------------------------------------------------- |
| Below the floor                  | **Playbook only.** Audit `ML_SKIPPED_LOW_DATA`       |
| At or above the floor            | **Playbook + P.** Call `/predict`. Audit `ML_SCORED` |
| ML service down                  | Playbook only. Audit `ML_PREDICT_UNAVAILABLE`        |


Local floor is **400** so the 500-row seed crosses it. Production should be **10,000** (`recovery.ml.min-labelled-outcomes`).

Low P may skip a retry only if that customer also has ≥5 history payments — the 8 simulate users still walk the playbook.

### Files


| File                                       | What                                                  |
| ------------------------------------------ | ----------------------------------------------------- |
| `ml-service/app/main.py`                   | `/health`, `/predict`                                 |
| `ml-service/app/scoring.py`                | Load XGBoost, score one row                           |
| `ml-service/app/features.py`               | Feature order + categoricals                          |
| `ml-service/app/config.py`                 | DSN / paths                                           |
| `ml-service/scripts/generate_synthetic.py` | 500 cases + 500 customers on `acc_syn_training`       |
| `ml-service/scripts/refresh_features.py`   | Features from existing Postgres tables (no new table) |
| `ml-service/scripts/train_model.py`        | Train/test, write metrics                             |
| `ml-service/scripts/seed_postgres.py`      | Seed helper                                           |
| `ml-service/models/recovery_xgb.json`      | Trained booster                                       |
| `ml-service/models/feature_spec.json`      | Categories + numeric list                             |
| `ml-service/data/predict_metrics.json`     | Precision / recall / F1 / ROC-AUC / PR-AUC            |


Features used: `paymentSuccessRate`, `paymentFailureRate`, `avgPaymentDelay`, `historicalRecoveryRate`, `subscriptionAgeMonths`, `lifetimeValue`, `avgOrderValue`, `retryHistoryCount`, `daysSinceLastActivity`, plus amount, reason, method, retry count, hours since fail.

---



## 19. Agent service — propose only

Model: **Ollama** `qwen2.5-coder:7b`. Port **8002**.

The graph has **no execute tool bound**. Every response includes `actions_available: ["propose"]` and `executes: false`.

### Case agent — 3 nodes (`POST /propose`)

```
START
  → context      (no LLM)   load case + history + ML score + default playbook step
  → diagnose     (Ollama)   what's wrong / why / recommend / deviate? / confidence / escalate?
  → safety       (no LLM)   strip any execute; force propose-only fields
END
```

If Ollama is down or returns garbage, safety runs the thin mapper (`propose_fallback.py`), sets `fallbackUsed: true`, `model: "fallback-rules"`. Demo: stop Ollama mid-run and the desk still shows a valid proposal.

Allowed `recommended_action` values: `DELAYED_RETRY`, `SKIP_EXTRA_RETRY`, `SEND_PAYMENT_LINK`, `REQUEST_PROMISE_TO_PAY`, `DO_NOT_RETRY`, `NO_ACTION`.

`deviates_from_playbook` is how we prove the agent adds value over static rules (e.g. weak NSF → skip extra retry).

### Ops brain — `POST /ops/briefing`

SQL first (last 6 hours in the demo): NSF spike, checkout cluster, any risk case, same customer+reason recurring. Excludes `acc_syn_training`. Then one optional Ollama narration. If Ollama is down, SQL patterns + template solutions still return.

### Files


| File                                    | What                                   |
| --------------------------------------- | -------------------------------------- |
| `agent-service/app/main.py`             | `/propose`, `/ops/briefing`, `/health` |
| `agent-service/app/graph_case.py`       | 3-node StateGraph                      |
| `agent-service/app/context.py`          | SQL/HTTP load (no LLM)                 |
| `agent-service/app/diagnose.py`         | One Ollama call                        |
| `agent-service/app/safety.py`           | Force propose-only                     |
| `agent-service/app/propose_fallback.py` | Thin rules mapper                      |
| `agent-service/app/graph_ops.py`        | Patterns → narrate → safety            |
| `agent-service/app/patterns.py`         | SQL spike detection                    |
| `agent-service/app/schemas.py`          | Pydantic contracts                     |
| `agent-service/app/llm.py`              | ChatOllama wrapper                     |
| `agent-service/app/db.py`               | Read-only Postgres pool                |
| `agent-service/app/config.py`           | Model name, thresholds, URLs           |
| `agent-service/scripts/smoke_agent.py`  | Local smoke                            |


---



## 20. Infra and Razorpay scripts



### `docker-compose.yml` (recovery-engine root)


| Service                    | Image                                | Host port |
| -------------------------- | ------------------------------------ | --------- |
| `recovery-engine-postgres` | postgres:17-alpine                   | 5432      |
| `recovery-engine-redis`    | redis:7-alpine                       | 6379      |
| `recovery-engine-kafka`    | cp-kafka:7.8.0 (KRaft, no ZooKeeper) | 9092      |


Wait until all three are `healthy` before starting apps.

### `scripts/razorpay/`


| File                                         | What                                                                                                                 |
| -------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `tunnel.ps1`                                 | Expose `:8080` so Razorpay Test Mode can POST                                                                        |
| `prove-live-webhook.ps1`                     | Wait until a **Razorpay-signed** event hits the inbox (not `/simulate`)                                              |
| `create-fixtures.ps1`                        | Customer, order, plan, subscription, payment link via Test API                                                       |
| `simulate-webhooks.ps1`                      | Sign and POST sample payloads locally — HMAC yes, origin stays `LOCAL_SCRIPT`                                        |
| `payloads/*.json`                            | `payment.failed`, `payment.captured`, `order.paid`, `subscription.pending`, `subscription.halted`, `invoice.expired` |
| `Razorpay-Test-Mode.postman_collection.json` | Same events in Postman                                                                                               |


Webhook events we register: `payment.failed`, `payment.captured`, `order.paid`, `subscription.pending`, `subscription.halted`, `subscription.charged`, `invoice.paid`, `invoice.expired`.

Secret default: `whsec_dev_local`. Header: `X-Razorpay-Signature`.

---



## 21. Docs folder (how we planned it)

Every new file under `docs/` starts with `Created:` and `Reason:`.


| File                                             | What it is                                                     |
| ------------------------------------------------ | -------------------------------------------------------------- |
| `docs/hackathon/track-03-ai-revenue-recovery.md` | Official track brief. Detect → diagnose → act → stop → prove ₹ |
| `docs/hackathon/intelligence-layer-plan.md`      | Sep 1–5 calendar. Why ML after playbooks                       |
| `docs/hackathon/agent-win-plan.md`               | Locked 3-node + ops brain plan (implementation authority)      |
| `docs/hackathon/langgraph-agent-plan.md`         | Wider background (superseded)                                  |
| `docs/hackathon/senior-review-brief.md`          | Plain brief we sent for senior review                          |
| `docs/hackathon/reason-to-action.md`             | Each reason → first action + coverage tracker                  |
| `docs/hackathon/service-map.md`                  | Java `service/` folders                                        |
| `docs/hackathon/hurdles-and-solutions.md`        | Blind playbook + small-data gate (judge Q&A)                   |
| `docs/hackathon/NEXT.md`                         | Local URLs + what was already running mid-build                |
| `docs/hackathon/winning-overlay-plan.md`         | Judging bar + skip list                                        |
| `docs/webhook/razorpay-webhook.md`               | Envelope fields + which events open vs close a case            |
| `recovery-engine/README.md`                      | How to run the stack                                           |


---



## 22. How to run it

From `recovery-engine/`, after Docker infra is healthy:

```bash
docker compose up -d
docker compose ps
```

Then four processes:

```bash
# backend  :8080
cd backend/revenue-recovery
./gradlew bootRun          # Windows: gradlew.bat bootRun

# ml       :8001
cd ml-service && uv run uvicorn app.main:app --reload --port 8001

# agent    :8002
cd agent-service && uv run uvicorn app.main:app --reload --port 8002

# desk     :3000
cd frontend && npm run dev
```

Open `http://localhost:3000`.

Health checks:

- Backend: `http://localhost:8080/actuator/health`
- ML: `http://localhost:8001/health`
- Agent: `http://localhost:8002/health`

If the login API still feels stale after a role change, restart `bootRun` once. The seeder will keep the two demo people and deactivate leftover operators.

Train / seed ML (already done in-repo; re-run only if you wipe Postgres):

```bash
cd ml-service
uv run python scripts/generate_synthetic.py
uv run python scripts/refresh_features.py
uv run python scripts/train_model.py
```

---



## 23. A full demo path (both people)

1. Sign in as **CEO** (`ceo@recovery.local` / `admin123`).
2. Open **Recovery desk**.
3. Create **risk-failed** and a high amount **insufficient-funds** (₹80,000+).
4. Press **Start** on a blocked case. Desk stops. Policy owns it.
5. Optional: **Ask agent** on a weak NSF case — show `deviates_from_playbook`. Stop Ollama and ask again — show `fallback_used`.
6. Sign out. Sign in as **human in the loop** (`policy@recovery.local` / `approve123`).
7. Open the queue. Read the row, write a note, **Let it through** or **Hold it**.
8. Sign back in as CEO. Start again. Approved cases continue the playbook.
9. Create **payment-captured** (or `/simulate/all`) to show a case close as recovered.

### 23b. Real signed webhook (the HMAC credibility close)

Desk buttons are `/simulate`. They skip signature + Kafka on purpose. To show a judge Razorpay itself signed the POST:

1. Backend, Redis, Kafka up. `.env` has Test Mode keys + `RAZORPAY_WEBHOOK_SECRET`.
2. `scripts/razorpay/tunnel.ps1` — copy the `https://*.trycloudflare.com` host.
3. Dashboard (Test Mode) → Webhooks → URL `https://<tunnel>/webhooks/razorpay`, same secret, events include `payment.failed`.
4. In another window: `.\prove-live-webhook.ps1`
5. Either **Send Test Webhook** on that endpoint, or open the printed payment link with card `4012001037141112`.
6. Script exits green when `origin=RAZORPAY` and `signatureVerified=true`.
7. Desk **Live signed intake** shows the **Razorpay HMAC** chip. Case audit has `WEBHOOK_HMAC_VERIFIED`.

`simulate-webhooks.ps1` is still HMAC — but origin is `LOCAL_SCRIPT`. Do not use that as the Razorpay-server proof.

Judge questions we can answer live:


| They ask              | You show                                                     |
| --------------------- | ------------------------------------------------------------ |
| What slipped away?    | Dashboard rupees at risk + open cases                        |
| Why?                  | Reason on the case / “why money is stuck”                    |
| What did you do?      | Different first action per reason                            |
| Did it stop?          | Risk case never auto-executes; ₹80k waits in the queue       |
| Can the agent charge? | JSON `actions_available: ["propose"]` + open `graph_case.py` |
| Ollama down?          | `fallback_used: true` on the desk                            |
| Did money come back?  | Captured simulate → `RECOVERED` + audit                      |
| Show me HMAC firing   | Desk **Razorpay HMAC** chip + `prove-live-webhook.ps1`       |


---



## 24. Rules we do not break

- The agent proposes. Java decides. A person can override a block.
- Risk and cancelled cases never auto-execute.
- Amounts at or above the human threshold wait in the queue.
- One execution per playbook step. No retry hammering.
- Only two seats on the desk.
- Training merchant `acc_syn_training` stays off the live desk.
- Duplicate webhook event id → one case.
- Desk simulate is not HMAC. A Razorpay-signed event must show `origin=RAZORPAY`.
- Less data, playbook. Enough data, playbook plus probability.

---



## 25. Hurdles we actually hit (say this, not fake problems)

**The playbook was blind to the customer.** Two NSF customers got the same 3 retries. We did not rip the playbook out. We scored the customer on top of it.

**Probability is not safe on little data.** ~500 labelled rows is a demo, not production truth. Java will not let P drive retries until labelled outcomes cross the floor (400 local / 10,000 prod). If ML is down, playbook still runs.

---



## 26. What “done” means for this track

Official bar: **detect → diagnose → act → stop → prove ₹ recovered**, with compliant escalation and an audit trail.


| Judge bar           | Where it lives                                                     |
| ------------------- | ------------------------------------------------------------------ |
| What slipped away   | `recovery_case.amount_at_risk` on the dashboard                    |
| Why                 | `recovery_case.reason` + hover blurbs                              |
| What we did         | Distinct `recovery_action` per reason                              |
| Did it stop         | PolicyEngine + approval queue + max 3 retries                      |
| Did money come back | `payment.captured` → `RECOVERED`                                   |
| Can we trust it     | `audit_event` for detect / ML / propose / policy / execute / close |


