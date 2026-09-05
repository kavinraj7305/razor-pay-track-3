"""Read-only helpers. There is no charge, retry, or payment-link tool."""

from __future__ import annotations

import httpx

from app.config import settings


def get_policies() -> dict:
    return {
        "neverRetryReasons": ["payment_risk_check_failed", "payment_cancelled"],
        "humanApprovalAmount": settings.human_approval_amount,
        "lowProbabilitySkipRetry": 0.12,
        "agentCanExecute": False,
        "actionsAvailable": ["propose"],
    }


def calculate_expected_value(probability: float, amount_inr: float) -> float:
    return round(probability * amount_inr, 2)


def predict_recovery(payload: dict) -> dict | None:
    body = {
        "reason": payload.get("reason", "unknown"),
        "source": payload.get("source", "PAYMENT"),
        "priority": payload.get("priority", "MEDIUM"),
        "paymentMethod": payload.get("paymentMethod", "card"),
        "amountInr": float(payload.get("amountInr") or 0),
        "retryCount": int(payload.get("retryCount") or 0),
        "hoursSinceFail": int(payload.get("hoursSinceFail") or 0),
        "historicalRecoveryRate": float(payload.get("historicalRecoveryRate") or 0),
        "retryHistoryCount": int(payload.get("retryHistoryCount") or 0),
        "paymentSuccessRate": float(payload.get("paymentSuccessRate") or 0),
        "paymentFailureRate": float(payload.get("paymentFailureRate") or 0),
        "avgPaymentDelay": float(payload.get("avgPaymentDelay") or 0),
        "subscriptionAgeMonths": int(payload.get("subscriptionAgeMonths") or 0),
        "lifetimeValue": float(payload.get("lifetimeValue") or 0),
        "avgOrderValue": float(payload.get("avgOrderValue") or 0),
        "daysSinceLastActivity": int(payload.get("daysSinceLastActivity") or 0),
        "historyPaymentCount": int(payload.get("historyPaymentCount") or 0),
    }
    try:
        response = httpx.post(settings.ml_predict_url, json=body, timeout=3.0)
        response.raise_for_status()
        return response.json()
    except httpx.HTTPError:
        return None
