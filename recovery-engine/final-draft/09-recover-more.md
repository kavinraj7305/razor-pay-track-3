# 9. First try always — recover more

## How to explain this on video

Open `/dashboard`. Point at the three columns. Speak this in order. Do not skip.

**1. What this block is**

> This is not live desk money. It is 500 labelled failures we already know paid or did not. Same pile, two paths, then we compare.

**2. Left column — reason playbook**

> Blind folders. NSF waits and retries. Expired card gets a link. No score. No skip. It recovered ₹5.73L from 200 people. It also ran 250 chases that never came back.

**3. The rule I added — first try always**

> A silent retry is cheap. Lost recovery is not. So the first payday try always runs. Java never skips step 1. If they would have paid on that first try, we still count the rupee.

**4. Middle column — playbook + first retry + P**

> Same folders. First try kept. P only cuts the extra silent hits — T+96h and T+5d — and only when P is below 12% and this customer has 10 payments of their own. Eighteen extra retries dropped. NSF recovered rupees stayed the same as playbook. We did not give those people away.

**5. Where the extra money came from**

> Risk is never auto-charged. If P is high — 55% or more — the other person releases the hold. Two holds. Both paid. That is +₹12,472. Two more risk payers with lower P stayed held. Those are honest misses.

**6. Right column — say this and stop**

> Playbook recovered ₹5.73L. First retry always runs — that try is cheap — then P only cuts extra silent retries on the weakest cards, and high-P risk holds go to a person. Recovered ₹5.86L. Plus ₹12,472. Skipped 18 extra retries. We do not give up people who pay on the first try.

Do not say “AI recovered more by charging more.” Do not say we skipped the first try. The lift is first try kept + two high-P holds released.

If they ask “is a retry expensive?” — no. That is why we never drop step 1.

---

The CEO overview numbers you see:

| Path | Recovered | Cases |
|---|---|---|
| Reason playbook | **₹5,73,297** (21% of ₹27,68,443) | 200 |
| Playbook + first retry + P | **₹5,85,768** (21%) | 202 |
| What scoring changed | **+₹12,472** | 2 extra cases |

18 extra silent retries cut. 2 high-P risk holds released. 48 still waiting on a person. First-try payers were not dropped.

## What this sim includes

Same 500 labelled failures. Seed **42**. Merchant `acc_syn_training`. Same trained XGBoost file.

### 1. First retry always counts

If the playbook action is `RETRY_PAYMENT`, the scored path still chases the case once. Live Java matches this: `PlaybookRunner.skipUpcomingRetries` never skips **step 1**.

`paid_after_hours` ≤ **96** (T+96h, after the first window) = first try would have reached them. We do **not** subtract that rupee when extras are skipped.

The first payday try is cheap. We keep it.

### 2. Only extra silent retries can be skipped

Skip extras only when **all** of these are true:

- playbook action is `RETRY_PAYMENT`
- **P(recovery) < 0.12**
- this customer has **≥ 10** of their own payment records

Each skip removes **two** later silent hits (T+96h and T+5d), not the first try. On this run: **9** cases × 2 = **18** extra retries cut.

NSF and pending-mandate recovered rupees stay **equal** to playbook (₹78,629 and ₹32,694).

### 3. High-P risk holds go to a person — not an auto-charge

Risk and cancelled are still not silent-retried.

If the reason is risk **and P ≥ 0.55**, the other person **releases** the hold. On this batch that is **2** cases, both paid, **₹12,471.82**, **₹0** wasted.

That is the recovered lift vs playbook. Score tells the human which holds are worth opening.

The other two risk payers (P = 0.37 and 0.34) stay held. Honest misses. They sit on the overview under “Not chased — later paid.”

### 4. What we did not include

- No auto-charge on risk or cancelled
- No skip of step 1
- No retune of the model file after seeing labels
- No fake rupees — totals come from `ml-service/scripts/run_benchmark.py` → `data/benchmark.json` → Java `GET /api/admin/benchmark`

## Totals (this run)

At risk: **₹27,68,443**. Labelled paid: **204**. Oracle ceiling: **₹5,99,799** (21.7%).

| | Playbook | First retry + P |
|---|---|---|
| Recovered | ₹5,73,297 (20.7%) | ₹5,85,768 (21.2%) |
| Recovered cases | 200 | 202 |
| Case-level chases | 413 | 415 |
| Extra silent retries skipped | 0 | 18 |
| High-P holds released | 0 | 2 |
| Still held for a person | 50 | 48 |
| Recovered vs playbook | — | **+₹12,472** |
| First-try payers given up | — | **0** |

Say this and stop:

> Playbook recovered ₹5.73L. First retry always runs — that try is cheap — then P only cuts extra silent retries on the weakest cards, and high-P risk holds go to a person. Recovered ₹5.86L (+₹12,472). Skipped 18 extra retries. We do not give up people who pay on the first try.

## By failure

Scoring only **moved money** on risk (the two released holds). Retry folders stayed flat because the first try still ran.

| Failure | Cases | Playbook | With scoring |
|---|---|---|---|
| Captured | 37 | ₹38,473 | ₹38,473 |
| Card expired | 62 | ₹74,327 | ₹74,327 |
| Checkout abandoned | 100 | ₹54,814 | ₹54,814 |
| Insufficient funds | 88 | ₹78,629 | ₹78,629 |
| Invoice expired | 50 | ₹2,72,767 | ₹2,72,767 |
| Risk check failed | 50 | ₹0 | **₹12,472** |
| Subscription halted | 50 | ₹21,593 | ₹21,593 |
| Subscription pending | 63 | ₹32,694 | ₹32,694 |

## Frozen rules (live + this batch)

| Rule | Value | Where |
|---|---|---|
| First retry | always | `PlaybookRunner.skipUpcomingRetries` — step 1 never skipped |
| Extra-retry skip line | **P < 0.12** | `recovery.ml.consider-min-probability`, agent `propose.py` |
| Personal history before P can skip extras | **≥ 10** payments | `MlDataGate` |
| High-P hold release | **P ≥ 0.55** | benchmark only — Java still **blocks** auto-charge on risk |
| Human approval amount | ₹80,000 | policy |
| Labelled floor to score at all | 400 local / 10,000 stated prod | `MlDataGate` |

## How to re-run

```text
cd recovery-engine/ml-service
uv run python scripts/run_benchmark.py
```

Writes the same file Java serves:

- `ml-service/data/benchmark.json`
- `backend/revenue-recovery/src/main/resources/benchmark/acc_syn_training_500.json`

Restart Java after a new run. The overview is not a slide.

## How to read the three columns

**Reason playbook** — blind folders. 250 wasted chases, ₹8,45,322 that never came back.

**Playbook + first retry + P** — same folders, first try kept, 18 extra silent hits dropped, 2 high-P holds released.

**What scoring changed** — **+₹12,472**. Goal is more recovered money. Extra retries are what we cut, not the first payday try, and not people who pay.
