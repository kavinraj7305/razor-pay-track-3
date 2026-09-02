"""Context node — no LLM. Load case + history + ML score + default playbook step."""

from __future__ import annotations

from typing import Any

from app.config import settings
from app.db import connect
from app.propose_fallback import default_playbook_action
from app.tools import predict_recovery


def _segment(success_rate: float, history_count: int) -> str:
    if history_count >= 5 and success_rate >= 0.6:
        return "GOOD"
    if history_count < 2 or success_rate < 0.4:
        return "WEAK"
    return "UNKNOWN"


def _load_case_row(case_id: str) -> dict[str, Any] | None:
    try:
        return _load_case_row_inner(case_id)
    except Exception:
        return None


def _load_case_row_inner(case_id: str) -> dict[str, Any] | None:
    with connect() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT c.case_id, c.reason, c.source, c.status, c.amount_at_risk, c.priority,
                   c.customer_id, c.merchant_id, c.created_at
            FROM recovery_case c
            WHERE c.case_id = %s
            """,
            (case_id,),
        )
        row = cur.fetchone()
        if not row:
            return None
        cur.execute(
            """
            SELECT action_type, status, attempt_number, reason
            FROM recovery_action
            WHERE case_id = %s
            ORDER BY attempt_number NULLS LAST, created_at
            """,
            (case_id,),
        )
        actions = cur.fetchall()
        customer_id = row.get("customer_id")
        payments: list[dict] = []
        if customer_id:
            cur.execute(
                """
                SELECT status, payment_type, amount, created_at
                FROM payment
                WHERE customer_id = %s
                ORDER BY created_at
                """,
                (customer_id,),
            )
            payments = cur.fetchall()
        return {"case": row, "actions": actions, "payments": payments}


def _features_from_payments(payments: list[dict], amount: float) -> dict[str, Any]:
    n = len(payments)
    success = sum(1 for p in payments if str(p.get("status") or "").upper() == "SUCCESS")
    fail = sum(1 for p in payments if str(p.get("status") or "").upper() == "FAILED")
    success_rate = (success / n) if n else 0.0
    fail_rate = (fail / n) if n else 0.0
    ltv = sum(float(p.get("amount") or 0) for p in payments if str(p.get("status") or "").upper() == "SUCCESS")
    aov = (ltv / success) if success else 0.0
    method = "card"
    if payments and payments[-1].get("payment_type"):
        method = str(payments[-1]["payment_type"])
    return {
        "paymentSuccessRate": round(success_rate, 4),
        "paymentFailureRate": round(fail_rate, 4),
        "lifetimeValue": round(ltv, 2),
        "avgOrderValue": round(aov, 2),
        "historyPaymentCount": n,
        "paymentMethod": method,
        "amountInr": amount,
        "avgPaymentDelay": 0,
        "subscriptionAgeMonths": 0,
        "daysSinceLastActivity": 0,
        "historicalRecoveryRate": 0,
        "retryHistoryCount": 0,
        "retryCount": 0,
        "hoursSinceFail": 0,
    }


def build_context(request: dict[str, Any]) -> dict[str, Any]:
    case_id = request.get("caseId") or request.get("case_id")
    loaded = _load_case_row(case_id) if case_id else None

    if loaded:
        case = loaded["case"]
        actions = loaded["actions"]
        payments = loaded["payments"]
        amount = float(case.get("amount_at_risk") or 0)
        reason = str(case.get("reason") or "unknown")
        feats = _features_from_payments(payments, amount)
        retry_count = sum(
            1
            for a in actions
            if str(a.get("action_type") or "").upper() == "RETRY_PAYMENT"
            and str(a.get("status") or "").upper() in {"FAILED", "EXECUTED"}
        )
        feats["retryCount"] = retry_count
        feats["retryHistoryCount"] = retry_count
        prior = [
            {
                "actionType": a.get("action_type"),
                "status": a.get("status"),
                "attempt": a.get("attempt_number"),
                "note": a.get("reason"),
            }
            for a in actions
        ]
        source = str(case.get("source") or "PAYMENT")
        priority = str(case.get("priority") or "MEDIUM")
        merchant_id = case.get("merchant_id")
        customer_id = case.get("customer_id")
        status = case.get("status")
    else:
        reason = str(request.get("reason") or "unknown")
        amount = float(request.get("amountInr") or 0)
        feats = {
            "paymentSuccessRate": float(request.get("paymentSuccessRate") or 0),
            "paymentFailureRate": float(request.get("paymentFailureRate") or 0),
            "lifetimeValue": float(request.get("lifetimeValue") or 0),
            "avgOrderValue": float(request.get("avgOrderValue") or 0),
            "historyPaymentCount": int(request.get("historyPaymentCount") or 0),
            "paymentMethod": str(request.get("paymentMethod") or "card"),
            "amountInr": amount,
            "avgPaymentDelay": float(request.get("avgPaymentDelay") or 0),
            "subscriptionAgeMonths": int(request.get("subscriptionAgeMonths") or 0),
            "daysSinceLastActivity": int(request.get("daysSinceLastActivity") or 0),
            "historicalRecoveryRate": float(request.get("historicalRecoveryRate") or 0),
            "retryHistoryCount": int(request.get("retryHistoryCount") or 0),
            "retryCount": int(request.get("retryCount") or 0),
            "hoursSinceFail": int(request.get("hoursSinceFail") or 0),
        }
        prior = []
        source = str(request.get("source") or "PAYMENT")
        priority = str(request.get("priority") or "MEDIUM")
        merchant_id = None
        customer_id = None
        status = None

    default = default_playbook_action(reason)
    segment = _segment(float(feats["paymentSuccessRate"]), int(feats["historyPaymentCount"]))

    predict_body = {
        "reason": reason,
        "source": source,
        "priority": priority,
        **feats,
    }
    scored = predict_recovery(predict_body)
    ml_available = scored is not None
    ml_score = float(scored["recoveryProbability"]) if scored else None

    return {
        "caseId": case_id,
        "reason": reason,
        "source": source,
        "priority": priority,
        "status": status,
        "amountInr": amount,
        "merchantId": merchant_id,
        "customerId": customer_id,
        "customerSegment": segment,
        "priorPlaybookSteps": prior,
        "retryCount": feats["retryCount"],
        "paymentMethod": feats["paymentMethod"],
        "paymentSuccessRate": feats["paymentSuccessRate"],
        "paymentFailureRate": feats["paymentFailureRate"],
        "historyPaymentCount": feats["historyPaymentCount"],
        "lifetimeValue": feats["lifetimeValue"],
        "avgOrderValue": feats["avgOrderValue"],
        "defaultPlaybookAction": default,
        "mlAvailable": ml_available,
        "mlScore": ml_score,
        "humanApprovalAmount": settings.human_approval_amount,
        "features": predict_body,
    }
