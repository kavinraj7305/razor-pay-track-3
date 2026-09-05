# 8. Measured batch

One real run. Seed **42**. Merchant `acc_syn_training`. **500** labelled failures. Skip line **0.25**, not retuned after the run.

At risk: **₹27,59,313**.  
Of those, **201** later paid (oracle ceiling ₹5,56,179 / 20.2%).

Risk and cancelled are not auto-chased on either path.

## The two paths

**Reason playbook** — same four-step folders, no score, no policy skip.

**Playbook + P + policy** — same folders, plus skip extra retry when P < 0.25, plus holds for risk / high amount.

## Totals

| | Playbook | Playbook + P + policy |
|---|---|---|
| Recovered | ₹5,29,677 (19.2%) | ₹5,21,284 (18.9%) |
| Recovered cases | 197 | 188 |
| Chases | 413 | 368 |
| Wasted chases | 253 | 217 |
| Wasted rupees (never came back) | ₹8,79,812 | ₹8,33,961 |

| What scoring + policy changed | Amount |
|---|---|
| Recovered vs playbook | −₹8,393 (−1.6%) |
| Doomed chase avoided | ₹45,851 |
| Fewer wasted retries | 36 |
| Low-P skips | 45 |
| Held for a person | 50 |
| Extra people who later paid, and were not chased | 9 |

Playbook recovered **₹5.30L**. Playbook + P + policy recovered **₹5.21L**. We skipped 45 weak retries, cut 36 doomed chases, avoided **₹45,851** of chase that never comes back, and gave up **9** people who later paid.

Recovered rupees went slightly down. Doomed chase went down more. That is the trade the product chose.

## By failure

Scoring only moved reasons that still have retries to skip.

| Failure | Cases | Playbook recovered | With scoring |
|---|---|---|---|
| Captured | 37 | ₹37,423 | ₹37,423 |
| Card expired | 62 | ₹74,327 | ₹74,327 |
| Checkout abandoned | 100 | ₹52,549 | ₹52,549 |
| Insufficient funds | 88 | ₹78,629 | ₹73,168 |
| Invoice expired | 50 | ₹2,35,625 | ₹2,35,625 |
| Risk check failed | 50 | ₹0 | ₹0 |
| Subscription halted | 50 | ₹17,933 | ₹17,933 |
| Subscription pending | 63 | ₹33,191 | ₹30,259 |

Card expired, checkout, invoice, halt, and risk already refuse silent extra retries on the playbook. P has nothing extra to skip there. Risk stays ₹0 on both paths because policy never auto-chases it.

## Model quality on this batch

| Metric | Value |
|---|---|
| ROC-AUC | 0.70 |
| PR-AUC | 0.67 |
| F1 | 0.47 |
| Predict threshold in the file | 0.5 (ranking), skip line for chase is 0.25 |

This is a demo ranking helper, not a production recovery engine. ~500 labels is enough to show the trade. It is not enough to trust a hard cutoff in production (the Java floor for a live override is 400 locally, 10,000 as the stated production intent).

## Not chased — later paid (examples on the board)

These are honest misses: the scored path did not chase, the label said they paid.

| Why it stopped | Typical case |
|---|---|
| `ML_SKIP_RETRY` | Insufficient funds or pending mandate, P below 0.25 |
| `RISK_OR_CANCELLED` | Risk hold — never auto-chased, even if they later paid |

## Frozen rules

- Skip threshold stays **0.25**
- Human approval stays **₹80,000**
- Labelled floor stays **400**
- This JSON is the last real run (`ml-service/data/benchmark.json`). The overview reads it. It is not a slide.

## How to read it

The playbook is the engine. Scoring and policy spent less chase and recovered slightly less. The product is willing to miss nine later payers to avoid thirty-six doomed retries.

That is the number on the CEO overview.
