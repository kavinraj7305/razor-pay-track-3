Created: **1 Sep 2026, 19:45 IST**
Reason: one page of real hurdles and how we solved them, written so we can say it to judges without a slide of fake problems.

Last updated: **2 Sep 2026, 13:50 IST**
Why updated: added the small-data gate — playbook until labelled outcomes cross the floor, then also use P.

# Failure recovery

## 1. Playbook was blind to the customer

**What broke.** The playbook ran. That was the bug. Two `insufficient_funds` customers still got the **same** N retries. The folder knows the reason. It does not know who pays back.

**What we saw.** NSF → retry 3 times, every time. A customer who almost never recovers got the same chase as a customer who usually pays. Blind retries waste attempts. Good customers are not treated any differently.

**What we did.** We kept the playbook (reason still picks retry / pay-link / stop). We added labelled history + `P(recovery)` so the extra question is: should we retry **this customer**?

**Say:** “The playbook worked and still wasted retries. We did not rip it out. We scored the customer on top of it.”

---

## 2. Probability is not safe on little data

**What broke.** `/predict` always returned a number. With ~500 labelled rows that number is a demo, not production truth. If we let P drive retries on thin data, we would skip (or chase) the wrong people. Real life needs on the order of **10k** labelled outcomes before the score is trustworthy.

**What we saw.** Same NSF reason: weak history ~0.07, strong ~0.53. Ranking works as a story. A hard cutoff on 500 rows would still misfire. Java `/execute` did not know how many labels we had.

**What we did.** A **data-volume gate** on execute (not a P=0.5 line):

| Labelled `recovery_outcome` rows | What runs |
|---|---|
| Below the floor | **Playbook only.** Audit `ML_SKIPPED_LOW_DATA`. |
| At or above the floor | **Playbook + P.** Call `/predict`. Audit `ML_SCORED`. |
| ML service down | Playbook only. Audit `ML_PREDICT_UNAVAILABLE`. |

Local floor is **400** so the 500-row seed crosses it. Production should be **10,000** (`recovery.ml.min-labelled-outcomes`). Low P may skip a retry only if that customer also has ≥5 history payments — the 8 simulate users still walk the playbook.

**Say:** “Less data, playbook. Enough data, playbook plus probability. We do not let a 500-row model replace retries in production.”


