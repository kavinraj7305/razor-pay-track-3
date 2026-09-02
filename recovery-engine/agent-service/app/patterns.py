"""SQL recurring pattern detection — no LLM."""

from __future__ import annotations

from typing import Any

from app.config import settings
from app.db import connect


def gather_patterns(window_hours: int, merchant_id: str | None = None) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    exclude = settings.exclude_training_merchant
    with connect() as conn, conn.cursor() as cur:
        params: list[Any] = [window_hours, exclude]
        merchant_sql = ""
        merchant_sql_c = ""
        if merchant_id:
            merchant_sql = " AND merchant_id = %s "
            merchant_sql_c = " AND c.merchant_id = %s "
            params.append(merchant_id)

        cur.execute(
            f"""
            SELECT reason, source, count(*)::int AS cnt,
                   coalesce(sum(amount_at_risk), 0) AS revenue_at_risk,
                   array_agg(case_id ORDER BY created_at DESC) AS case_ids
            FROM recovery_case
            WHERE created_at >= now() - (%s * interval '1 hour')
              AND merchant_id <> %s
              {merchant_sql}
            GROUP BY reason, source
            ORDER BY cnt DESC
            """,
            params,
        )
        rows = cur.fetchall()

        cur.execute(
            f"""
            SELECT count(*)::int AS opened,
                   coalesce(sum(amount_at_risk) FILTER (WHERE status NOT IN ('RECOVERED','FAILED','EXPIRED')), 0) AS open_risk
            FROM recovery_case
            WHERE created_at >= now() - (%s * interval '1 hour')
              AND merchant_id <> %s
              {merchant_sql}
            """,
            params,
        )
        totals = cur.fetchone() or {"opened": 0, "open_risk": 0}

        cur.execute(
            f"""
            SELECT p.payment_type, count(*)::int AS cnt
            FROM recovery_case c
            LEFT JOIN payment p ON p.payment_id = c.source_id AND c.source = 'PAYMENT'
            WHERE c.created_at >= now() - (%s * interval '1 hour')
              AND c.merchant_id <> %s
              {merchant_sql_c}
            GROUP BY p.payment_type
            ORDER BY cnt DESC
            """,
            params,
        )
        methods = cur.fetchall()

        cur.execute(
            f"""
            SELECT customer_id, reason, count(*)::int AS cnt,
                   array_agg(case_id ORDER BY created_at DESC) AS case_ids
            FROM recovery_case
            WHERE created_at >= now() - (%s * interval '1 hour')
              AND merchant_id <> %s
              AND customer_id IS NOT NULL
              {merchant_sql}
            GROUP BY customer_id, reason
            HAVING count(*) >= 3
            ORDER BY cnt DESC
            LIMIT 8
            """,
            params,
        )
        repeats = cur.fetchall()

    metrics = {
        "casesOpened": int(totals.get("opened") or 0),
        "revenueAtRiskInr": float(totals.get("open_risk") or 0),
        "topReasons": [
            {"reason": r["reason"], "source": r["source"], "count": r["cnt"]} for r in rows[:8]
        ],
        "byMethod": [
            {"paymentMethod": m["payment_type"] or "unknown", "count": m["cnt"]} for m in methods[:8]
        ],
        "windowHours": window_hours,
    }

    patterns: list[dict[str, Any]] = []
    for row in rows:
        reason = str(row["reason"] or "")
        count = int(row["cnt"] or 0)
        case_ids = [cid for cid in (row.get("case_ids") or []) if cid][:8]
        where = f"{row['source']} / {reason}"
        key = reason.lower()
        if "insufficient_funds" in key and count >= settings.nsf_spike_threshold:
            patterns.append(
                {
                    "severity": "HIGH",
                    "pattern": "insufficient_funds_spike",
                    "where": where,
                    "count": count,
                    "why": f"{count} NSF cases in {window_hours}h — recurring temporary funds shortfall",
                    "proposedSolution": "Keep payday delayed retries; skip extra retries for WEAK segment; watch P(recovery)",
                    "relatedCaseIds": case_ids,
                }
            )
        if "checkout.abandoned" in key and count >= settings.checkout_spike_threshold:
            patterns.append(
                {
                    "severity": "MEDIUM",
                    "pattern": "checkout_abandon_cluster",
                    "where": where,
                    "count": count,
                    "why": f"{count} checkout abandonments in {window_hours}h",
                    "proposedSolution": "Send pay-link reminders; do not silent-retry cards",
                    "relatedCaseIds": case_ids,
                }
            )
        if "risk" in key and count >= 1:
            patterns.append(
                {
                    "severity": "HIGH",
                    "pattern": "risk_cases_present",
                    "where": where,
                    "count": count,
                    "why": "Risk-flagged cases must not auto-retry",
                    "proposedSolution": "Escalate to human; block autonomous retry",
                    "relatedCaseIds": case_ids,
                }
            )

    for row in repeats:
        count = int(row["cnt"] or 0)
        case_ids = [cid for cid in (row.get("case_ids") or []) if cid][:8]
        patterns.append(
            {
                "severity": "MEDIUM",
                "pattern": "customer_reason_repeat",
                "where": f"{row['customer_id']} / {row['reason']}",
                "count": count,
                "why": f"{count} cases for the same customer+reason in {window_hours}h",
                "proposedSolution": "Stop extra retries; ask human to review instrument / mandate",
                "relatedCaseIds": case_ids,
            }
        )

    if float(metrics["revenueAtRiskInr"]) >= 50000:
        patterns.append(
            {
                "severity": "HIGH",
                "pattern": "large_book_at_risk",
                "where": "OPEN cases",
                "count": int(metrics["casesOpened"]),
                "why": f"Open amount at risk ₹{metrics['revenueAtRiskInr']:.0f}",
                "proposedSolution": "Prioritize high-amount queue for human review",
                "relatedCaseIds": [],
            }
        )

    return metrics, patterns
