"""Step 3.4 — labelled synthetic recovery events (not 10k rows).

Run from recovery-engine/ml-service:

    uv run python scripts/generate_synthetic.py

Seeds Postgres merchant acc_syn_training (real tables + recovery_outcome label).
Also exports data/synthetic_events.csv as a file copy of that batch.
"""

from __future__ import annotations

import argparse
import json
import math
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import pandas as pd

SEED = 42
DEFAULT_N = 400
MERCHANT_ID = "acc_syn_training"
NOW = datetime(2026, 9, 1, 18, 0, tzinfo=timezone.utc)

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / "data"

# Share of 400; scaled with largest-remainder when --n changes.
REASON_WEIGHTS: dict[str, int] = {
    "insufficient_funds": 70,
    "card_expired": 50,
    "payment_risk_check_failed": 40,
    "subscription.pending": 50,
    "subscription.halted": 40,
    "invoice.expired": 40,
    "checkout.abandoned": 80,
    "captured": 30,
}

REASON_META: dict[str, dict] = {
    "insufficient_funds": {
        "workflow": "payment_subscription_fail",
        "event_type": "payment.failed",
        "source": "PAYMENT",
        "baseline_action": "RETRY_PAYMENT",
        "methods": ("card", "upi", "netbanking"),
        "amount": (199.0, 2999.0),
        "retries": (0, 2),
        "logit": 0.55,
    },
    "card_expired": {
        "workflow": "payment_subscription_fail",
        "event_type": "payment.failed",
        "source": "PAYMENT",
        "baseline_action": "SEND_PAYMENT_LINK",
        "methods": ("card",),
        "amount": (499.0, 4999.0),
        "retries": (0, 1),
        "logit": -0.25,
    },
    "payment_risk_check_failed": {
        "workflow": "payment_subscription_fail",
        "event_type": "payment.failed",
        "source": "PAYMENT",
        "baseline_action": "SEND_EMAIL",
        "methods": ("card", "upi"),
        "amount": (999.0, 19999.0),
        "retries": (0, 0),
        "logit": -2.4,
    },
    "subscription.pending": {
        "workflow": "payment_subscription_fail",
        "event_type": "subscription.pending",
        "source": "SUBSCRIPTION",
        "baseline_action": "RETRY_PAYMENT",
        "methods": ("emandate", "card", "upi"),
        "amount": (299.0, 1999.0),
        "retries": (1, 2),
        "logit": 0.25,
    },
    "subscription.halted": {
        "workflow": "payment_subscription_fail",
        "event_type": "subscription.halted",
        "source": "SUBSCRIPTION",
        "baseline_action": "SEND_PAYMENT_LINK",
        "methods": ("emandate", "card"),
        "amount": (499.0, 3999.0),
        "retries": (3, 5),
        "logit": -0.50,
    },
    "invoice.expired": {
        "workflow": "payment_subscription_fail",
        "event_type": "invoice.expired",
        "source": "INVOICE",
        "baseline_action": "REQUEST_PROMISE_TO_PAY",
        "methods": ("invoice",),
        "amount": (1500.0, 25000.0),
        "retries": (0, 2),
        "logit": -0.45,
    },
    "checkout.abandoned": {
        "workflow": "checkout_abandon",
        "event_type": "checkout.abandoned",
        "source": "CHECKOUT_SESSION",
        "baseline_action": "SEND_PAYMENT_LINK",
        "methods": ("checkout", "upi", "card"),
        "amount": (99.0, 2499.0),
        "retries": (0, 0),
        "logit": -0.70,
    },
    "captured": {
        "workflow": "payment_subscription_fail",
        "event_type": "payment.captured",
        "source": "PAYMENT",
        "baseline_action": "CLOSE_RECOVERED",
        "methods": ("card", "upi", "netbanking"),
        "amount": (199.0, 1999.0),
        "retries": (0, 2),
        "logit": 8.0,
    },
}

SOURCE_PREFIX = {
    "PAYMENT": "pay",
    "SUBSCRIPTION": "sub",
    "INVOICE": "inv",
    "CHECKOUT_SESSION": "chk",
}


def allocate_counts(n: int) -> dict[str, int]:
    total_w = sum(REASON_WEIGHTS.values())
    raw = {reason: n * weight / total_w for reason, weight in REASON_WEIGHTS.items()}
    counts = {reason: int(math.floor(value)) for reason, value in raw.items()}
    leftover = n - sum(counts.values())
    remainders = sorted(
        ((raw[reason] - counts[reason], reason) for reason in REASON_WEIGHTS),
        reverse=True,
    )
    for i in range(leftover):
        counts[remainders[i][1]] += 1
    return counts


def make_customers(rng: np.random.Generator, n_events: int) -> pd.DataFrame:
    n = max(80, n_events // 3)
    rows = []
    for i in range(1, n + 1):
        prior_payments = int(rng.integers(2, 28))
        success_rate = float(np.clip(rng.beta(5, 3), 0.15, 0.95))
        prior_success = int(round(prior_payments * success_rate))
        prior_failure = prior_payments - prior_success
        rec_attempts = int(rng.integers(0, 8))
        rec_rate = float(np.clip(rng.beta(3, 4), 0.0, 0.9))
        rec_success = int(round(rec_attempts * rec_rate)) if rec_attempts else 0
        aov = float(np.round(rng.uniform(250, 4500), 2))
        ltv = float(np.round(aov * prior_success, 2))
        sub_age = int(rng.choice([0, 0, 1, 3, 6, 12, 18, 24]))
        rows.append(
            {
                "customer_id": f"cust_syn_{i:04d}",
                "merchant_id": MERCHANT_ID,
                "prior_payment_count": prior_payments,
                "prior_success_count": prior_success,
                "prior_failure_count": prior_failure,
                "avg_payment_delay_hours": float(np.round(rng.uniform(0.5, 72.0), 2)),
                "historical_recovery_attempts": rec_attempts,
                "historical_recovery_successes": rec_success,
                "subscription_age_months": sub_age,
                "lifetime_value_inr": ltv,
                "avg_order_value_inr": aov,
                "customer_retry_history_count": int(rng.integers(0, 6)),
                "days_since_last_activity": int(rng.integers(0, 45)),
            }
        )
    return pd.DataFrame(rows)


def pick_customer(rng: np.random.Generator, customers: pd.DataFrame, reason: str) -> pd.Series:
    if reason.startswith("subscription"):
        subs = customers[customers["subscription_age_months"] > 0]
        pool = subs if not subs.empty else customers
    else:
        pool = customers
    return pool.iloc[int(rng.integers(0, len(pool)))]


def sample_amount(rng: np.random.Generator, reason: str, meta: dict, risk_80k_left: list[int]) -> float:
    if reason == "payment_risk_check_failed" and risk_80k_left[0] > 0:
        risk_80k_left[0] -= 1
        return 80000.0
    low, high = meta["amount"]
    return float(np.round(rng.uniform(low, high), 2))


def sigmoid(x: float) -> float:
    return 1.0 / (1.0 + math.exp(-x))


def label_paid(
    rng: np.random.Generator,
    reason: str,
    amount: float,
    retry_count: int,
    customer: pd.Series,
    logit_base: float,
) -> tuple[int, float]:
    payments = max(int(customer["prior_payment_count"]), 1)
    success_rate = int(customer["prior_success_count"]) / payments
    rec_attempts = max(int(customer["historical_recovery_attempts"]), 1)
    rec_rate = int(customer["historical_recovery_successes"]) / rec_attempts
    logit = logit_base
    logit += 1.4 * (success_rate - 0.5)
    logit += 0.6 * (rec_rate - 0.4)
    logit -= 0.9 * min(amount / 50_000.0, 2.0)
    logit -= 0.18 * retry_count
    if reason == "payment_risk_check_failed" and amount >= 80_000:
        logit -= 2.0
    logit += float(rng.normal(0.0, 0.35))
    p = float(np.clip(sigmoid(logit), 0.01, 0.99))
    if reason == "captured":
        p = 1.0
    paid = int(rng.random() < p)
    return paid, p


def priority_of(reason: str, amount: float) -> str:
    if reason == "payment_risk_check_failed" or amount >= 80_000:
        return "CRITICAL"
    if amount >= 10_000 or reason in {"subscription.halted", "invoice.expired"}:
        return "HIGH"
    return "MEDIUM"


def build_events(
    rng: np.random.Generator, customers: pd.DataFrame, counts: dict[str, int]
) -> pd.DataFrame:
    risk_n = counts.get("payment_risk_check_failed", 0)
    risk_80k_left = [min(10, risk_n)]
    rows: list[dict] = []
    seq = 1
    for reason, n in counts.items():
        meta = REASON_META[reason]
        lo, hi = meta["retries"]
        methods = meta["methods"]
        for _ in range(n):
            customer = pick_customer(rng, customers, reason)
            amount = sample_amount(rng, reason, meta, risk_80k_left)
            retry_count = int(rng.integers(lo, hi + 1))
            paid, _p_true = label_paid(
                rng, reason, amount, retry_count, customer, meta["logit"]
            )
            hours_since = int(rng.integers(0, 73))
            failed_at = NOW - timedelta(days=int(rng.integers(0, 60)), hours=hours_since)
            paid_after = int(rng.integers(2, 169)) if paid else None
            source = meta["source"]
            rows.append(
                {
                    "event_id": f"evt_syn_{seq:04d}",
                    "customer_id": customer["customer_id"],
                    "merchant_id": MERCHANT_ID,
                    "workflow": meta["workflow"],
                    "event_type": meta["event_type"],
                    "source": source,
                    "source_id": f"{SOURCE_PREFIX[source]}_syn_{seq:04d}",
                    "reason": reason,
                    "amount_inr": amount,
                    "currency": "INR",
                    "payment_method": str(rng.choice(methods)),
                    "retry_count": retry_count,
                    "hours_since_fail": hours_since,
                    "failed_at": failed_at.isoformat(),
                    "baseline_action": meta["baseline_action"],
                    "priority": priority_of(reason, amount),
                    "prior_payment_count": int(customer["prior_payment_count"]),
                    "prior_success_count": int(customer["prior_success_count"]),
                    "prior_failure_count": int(customer["prior_failure_count"]),
                    "avg_payment_delay_hours": float(customer["avg_payment_delay_hours"]),
                    "historical_recovery_attempts": int(
                        customer["historical_recovery_attempts"]
                    ),
                    "historical_recovery_successes": int(
                        customer["historical_recovery_successes"]
                    ),
                    "subscription_age_months": int(customer["subscription_age_months"]),
                    "lifetime_value_inr": float(customer["lifetime_value_inr"]),
                    "avg_order_value_inr": float(customer["avg_order_value_inr"]),
                    "customer_retry_history_count": int(
                        customer["customer_retry_history_count"]
                    ),
                    "days_since_last_activity": int(customer["days_since_last_activity"]),
                    "paid_eventually": paid,
                    "paid_after_hours": paid_after,
                    "amount_recovered_inr": amount if paid else 0.0,
                }
            )
            seq += 1
    return pd.DataFrame(rows)


def summary_of(events: pd.DataFrame) -> dict:
    by_reason = (
        events.groupby("reason")
        .agg(
            n=("event_id", "count"),
            paid=("paid_eventually", "sum"),
            amount_at_risk_inr=("amount_inr", "sum"),
            amount_recovered_inr=("amount_recovered_inr", "sum"),
        )
        .reset_index()
    )
    by_reason["paid_rate"] = (by_reason["paid"] / by_reason["n"]).round(4)
    by_reason["amount_at_risk_inr"] = by_reason["amount_at_risk_inr"].round(2)
    by_reason["amount_recovered_inr"] = by_reason["amount_recovered_inr"].round(2)
    by_reason["paid"] = by_reason["paid"].astype(int)
    by_workflow = events.groupby("workflow").size().to_dict()
    return {
        "n_events": int(len(events)),
        "n_customers": int(events["customer_id"].nunique()),
        "paid_eventually_rate": round(float(events["paid_eventually"].mean()), 4),
        "amount_at_risk_inr": round(float(events["amount_inr"].sum()), 2),
        "amount_recovered_inr": round(float(events["amount_recovered_inr"].sum()), 2),
        "risk_80k_count": int(
            ((events["reason"] == "payment_risk_check_failed") & (events["amount_inr"] >= 80000)).sum()
        ),
        "by_workflow": {k: int(v) for k, v in by_workflow.items()},
        "by_reason": by_reason.to_dict(orient="records"),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate labelled synthetic recovery events")
    parser.add_argument("--n", type=int, default=DEFAULT_N, help="row count (300-500)")
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument(
        "--no-postgres",
        action="store_true",
        help="skip seeding Postgres (files only)",
    )
    args = parser.parse_args()
    if args.n < 300 or args.n > 500:
        raise SystemExit("--n must be between 300 and 500")

    rng = np.random.default_rng(args.seed)
    counts = allocate_counts(args.n)
    customers = make_customers(rng, args.n)
    events = build_events(rng, customers, counts)
    events = events.sample(frac=1.0, random_state=args.seed).reset_index(drop=True)

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    csv_path = DATA_DIR / "synthetic_events.csv"
    json_path = DATA_DIR / "synthetic_events.json"
    summary_path = DATA_DIR / "synthetic_summary.json"

    events.to_csv(csv_path, index=False)
    events.to_json(json_path, orient="records", indent=2)
    summary = summary_of(events)
    summary["seed"] = args.seed
    summary["merchant_id"] = MERCHANT_ID
    summary["files"] = {
        "csv": str(csv_path.relative_to(ROOT).as_posix()),
        "json": str(json_path.relative_to(ROOT).as_posix()),
    }

    if not args.no_postgres:
        from seed_postgres import seed as seed_postgres

        db_counts = seed_postgres(events, customers)
        summary["postgres"] = db_counts
        print(
            f"postgres merchant={MERCHANT_ID} cases={db_counts['cases']} "
            f"paid={db_counts['paid']} customers={db_counts['customers']} "
            f"payments={db_counts['payments']}"
        )

    summary_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")

    print(f"wrote {len(events)} rows -> {csv_path}")
    print(f"wrote {json_path}")
    print(f"paid_eventually_rate={summary['paid_eventually_rate']}")
    print("counts per reason:")
    for row in summary["by_reason"]:
        print(
            f"  {row['reason']:32} n={row['n']:3}  paid={int(row['paid']):3}  "
            f"rate={row['paid_rate']:.2f}"
        )
    print(f"workflows: {summary['by_workflow']}")
    print(f"risk 80k INR rows: {summary['risk_80k_count']}")


if __name__ == "__main__":
    main()
