"""Step 3.1 — customer features from existing Postgres tables (no new table).

History payments exclude each case's own source payment so LTV / success rate
do not leak paid_eventually. Prior-recovery features exclude the current case.

Run from recovery-engine/ml-service:

    uv run python scripts/refresh_features.py
"""

from __future__ import annotations

import json
from pathlib import Path

import pandas as pd
from psycopg.rows import dict_row

from seed_postgres import TRAINING_MERCHANT_ID, connect

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / "data"
AS_OF = "2026-09-01 18:00:00"

CUSTOMER_SQL = f"""
WITH history_pay AS (
    SELECT p.*
    FROM payment p
    WHERE p.merchant_id = %(m)s
      AND p.payment_id NOT IN (
          SELECT c.source_id
          FROM recovery_case c
          WHERE c.merchant_id = %(m)s
            AND c.source = 'PAYMENT'
      )
),
pay_agg AS (
    SELECT
        customer_id,
        COUNT(*)::int AS payment_n,
        COUNT(*) FILTER (WHERE status = 'SUCCESS')::int AS success_n,
        COUNT(*) FILTER (WHERE status = 'FAILED')::int AS fail_n,
        COALESCE(SUM(amount) FILTER (WHERE status = 'SUCCESS'), 0) AS lifetime_value,
        COALESCE(AVG(amount) FILTER (WHERE status = 'SUCCESS'), 0) AS avg_order_value,
        MAX(created_at) AS last_pay_at
    FROM history_pay
    GROUP BY customer_id
),
pay_delay AS (
    SELECT customer_id,
           AVG(EXTRACT(EPOCH FROM (created_at - prev_at)) / 3600.0) AS avg_delay_hours
    FROM (
        SELECT customer_id,
               created_at,
               LAG(created_at) OVER (PARTITION BY customer_id ORDER BY created_at) AS prev_at
        FROM history_pay
    ) d
    WHERE prev_at IS NOT NULL
    GROUP BY customer_id
),
sub_age AS (
    SELECT customer_id,
           MAX(
               GREATEST(
                   0,
                   EXTRACT(YEAR FROM AGE(TIMESTAMP '{AS_OF}', created_at)) * 12
                   + EXTRACT(MONTH FROM AGE(TIMESTAMP '{AS_OF}', created_at))
               )
           )::int AS subscription_age_months
    FROM subscription
    WHERE merchant_id = %(m)s
    GROUP BY customer_id
)
SELECT
    cu.customer_id,
    cu.merchant_id,
    ROUND((COALESCE(p.success_n, 0)::numeric / NULLIF(p.payment_n, 0))::numeric, 4)
        AS payment_success_rate,
    ROUND((COALESCE(p.fail_n, 0)::numeric / NULLIF(p.payment_n, 0))::numeric, 4)
        AS payment_failure_rate,
    ROUND(COALESCE(d.avg_delay_hours, 0)::numeric, 2) AS avg_payment_delay,
    COALESCE(s.subscription_age_months, 0) AS subscription_age_months,
    ROUND(COALESCE(p.lifetime_value, 0)::numeric, 2) AS lifetime_value,
    ROUND(COALESCE(p.avg_order_value, 0)::numeric, 2) AS avg_order_value,
    GREATEST(
        0,
        EXTRACT(DAY FROM (TIMESTAMP '{AS_OF}' - COALESCE(p.last_pay_at, cu.created_at)))
    )::int AS days_since_last_activity,
    COALESCE(p.payment_n, 0) AS history_payment_count
FROM customer cu
LEFT JOIN pay_agg p ON p.customer_id = cu.customer_id
LEFT JOIN pay_delay d ON d.customer_id = cu.customer_id
LEFT JOIN sub_age s ON s.customer_id = cu.customer_id
WHERE cu.merchant_id = %(m)s
ORDER BY cu.customer_id
"""

CASE_SQL = f"""
SELECT
    c.case_id,
    c.customer_id,
    c.reason,
    c.source,
    c.amount_at_risk AS amount_inr,
    c.priority,
    COALESCE(
        pay.payment_type,
        CASE c.source
            WHEN 'CHECKOUT_SESSION' THEN 'checkout'
            WHEN 'INVOICE' THEN 'invoice'
            WHEN 'SUBSCRIPTION' THEN 'emandate'
            ELSE 'unknown'
        END
    ) AS payment_method,
    COALESCE(a.attempt_number, 0) AS retry_count,
    GREATEST(
        0,
        EXTRACT(EPOCH FROM (TIMESTAMP '{AS_OF}' - c.created_at)) / 3600.0
    )::int AS hours_since_fail,
    CASE WHEN o.result = 'PAYMENT_RECOVERED' THEN 1 ELSE 0 END AS paid_eventually,
    COALESCE((
        SELECT COUNT(*) FILTER (WHERE o2.result = 'PAYMENT_RECOVERED')::numeric
               / NULLIF(COUNT(*), 0)
        FROM recovery_case c2
        JOIN recovery_outcome o2 ON o2.case_id = c2.case_id
        WHERE c2.merchant_id = %(m)s
          AND c2.customer_id = c.customer_id
          AND c2.case_id <> c.case_id
    ), 0) AS historical_recovery_rate,
    COALESCE((
        SELECT SUM(a2.attempt_number)
        FROM recovery_case c2
        JOIN recovery_action a2 ON a2.case_id = c2.case_id
        WHERE c2.merchant_id = %(m)s
          AND c2.customer_id = c.customer_id
          AND c2.case_id <> c.case_id
    ), 0)::int AS retry_history_count
FROM recovery_case c
JOIN recovery_outcome o ON o.case_id = c.case_id
LEFT JOIN recovery_action a ON a.case_id = c.case_id
LEFT JOIN payment pay ON pay.payment_id = c.source_id AND c.source = 'PAYMENT'
WHERE c.merchant_id = %(m)s
ORDER BY c.case_id
"""


def fetch_df(conn, sql: str) -> pd.DataFrame:
    with conn.cursor(row_factory=dict_row) as cur:
        cur.execute(sql, {"m": TRAINING_MERCHANT_ID})
        rows = cur.fetchall()
    return pd.DataFrame(rows)


def main() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with connect() as conn:
        customers = fetch_df(conn, CUSTOMER_SQL)
        cases = fetch_df(conn, CASE_SQL)

    cases = cases.merge(customers, on="customer_id", how="left")

    feat_path = DATA_DIR / "customer_features.csv"
    case_path = DATA_DIR / "case_features.csv"
    summary_path = DATA_DIR / "feature_summary.json"
    customers.to_csv(feat_path, index=False)
    cases.to_csv(case_path, index=False)

    summary = {
        "merchant_id": TRAINING_MERCHANT_ID,
        "n_customers": int(len(customers)),
        "n_cases": int(len(cases)),
        "mean_payment_success_rate": round(float(customers["payment_success_rate"].mean()), 4),
        "mean_lifetime_value": round(float(customers["lifetime_value"].mean()), 2),
        "customers_with_subscription": int((customers["subscription_age_months"] > 0).sum()),
        "source": "existing tables payment / recovery_case / recovery_outcome / subscription",
        "files": {
            "customer_features": str(feat_path.relative_to(ROOT).as_posix()),
            "case_features": str(case_path.relative_to(ROOT).as_posix()),
        },
    }
    summary_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(f"customers={summary['n_customers']} cases={summary['n_cases']}")
    print(f"mean paymentSuccessRate={summary['mean_payment_success_rate']}")
    print(f"mean lifetimeValue={summary['mean_lifetime_value']}")
    print(f"wrote {feat_path}")
    print(f"wrote {case_path}")
    print(customers.head(3).to_string(index=False))


if __name__ == "__main__":
    main()
