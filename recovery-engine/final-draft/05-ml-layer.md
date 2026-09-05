# 5. ML layer

Scoring answers one question: **will this customer likely pay eventually?**

It does not pick the playbook. It does not charge. It does not replace the reason folder.

## Where it sits

FastAPI on `:8001`. Trained XGBoost classifier. Java calls `POST /predict` only after a data-volume gate.

```
Case features (this customer + this failure)
        → POST /predict
        → recoveryProbability  (0–1)
        → LIKELY / UNLIKELY
        → Java may skip extra retry if P is low
```

Service: `recovery-engine/ml-service/`  
Model file: `models/recovery_xgb.json`

## What goes in

Same columns for train and predict.

**Categorical:** reason, source, priority, payment method.

**Numeric**

| Feature | Meaning |
|---|---|
| `amount_inr` | Rupees on this failure |
| `retry_count` | Tries already used |
| `hours_since_fail` | Age of this failure |
| `historical_recovery_rate` | How often this customer came back before |
| `retry_history_count` | Past retries on the book |
| `payment_success_rate` / `payment_failure_rate` | This customer’s pay history |
| `avg_payment_delay` | How late they usually pay |
| `subscription_age_months` | Age of the mandate, if any |
| `lifetime_value` / `avg_order_value` | Value of the relationship |
| `days_since_last_activity` | Recency |
| `history_payment_count` | How much history we actually have |

Label used in training: `paid_eventually`.

## What comes out

`POST /predict` returns a probability for class “will pay”.

Java stores that as `P(recovery)` on the case. The desk shows it as a percentage.

The model is a ranking helper. On the measured batch: ROC-AUC **0.70**, PR-AUC **0.67**, F1 **0.47**. That is not a production cutoff engine.

## What Java is allowed to do with it

| Gate | Rule |
|---|---|
| Labelled-data floor | Below 400 labelled outcomes → playbook only. Score is not used to change retries |
| History | Skip-retry needs enough payment history (5 payments) before P can override |
| Skip line | If P < **0.25**, skip extra retry |
| ML down | Playbook still runs |
| Risk / cancelled | Never auto-chased, score or no score |

The skip line stayed 0.25 on the 500-row batch. It was not tightened after seeing the labels.

Scoring mainly moved **insufficient funds** and **subscription pending** — the reasons that still have retries to skip. Card-expired, checkout, invoice, and risk were already “no silent retry” on the playbook path, so P did not change recovered rupees there.

## What it is not

Not a second playbook.  
Not a licence to recover more rupees.  
Not used when there is not enough labelled history.  
Not allowed to execute.

On the measured batch, playbook + P + policy recovered slightly **less** than playbook alone, and wasted less chase. That comparison is [08-benchmark.md](./08-benchmark.md).
