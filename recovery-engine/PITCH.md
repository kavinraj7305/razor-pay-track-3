# Pitch — say this, not the softer version

Skip threshold stays **0.25**. Do not retune it on this 500-row batch. The strength is that the number is small and not juiced.

## Say this (verbatim)

> Playbook recovered ₹5.30L. Playbook + P + policy recovered ₹5.21L. We skipped 45 weak retries, cut 36 doomed chases, avoided ₹45,851 of chase that never comes back, and gave up 9 people who later paid.

That is one real run (seed 42, merchant `acc_syn_training`). ₹5.30L = ₹5,29,677. ₹5.21L = ₹5,21,284. Recovered cases 197 → 188. The nine are the extra misses vs playbook; four of the larger ones sit on the dashboard exception list as `ML_SKIP_RETRY`.

## Do not say

- “AI improved recovery.”
- “We recovered more rupees.”
- “The model beats the playbook.”
- Any % that is not 19.2% playbook / 18.9% AI / −1.6% recovered.
- “This is production-ready.” (500 labels, ROC-AUC 0.70.)

## If they push

| They say | You say |
|---|---|
| Why is recovered ₹ down? | We refused weak retries. Same playbook. Fewer doomed chases. Net we gave up ₹8,393 and avoided ₹45,851 of chase that never comes back. |
| Why not tighten the skip line? | That would be tuning a cutoff after seeing this batch. We froze 0.25. |
| Is the model good? | Ranking helper, not a recovery engine. Playbook first. 10k labels before we trust a hard cutoff in production. |

Dashboard shows the same sentence (`GET /api/admin/benchmark` → `pitch`).
