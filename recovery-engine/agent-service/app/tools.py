"""Read-only tools. There is no charge, retry, or payment-link tool."""

from __future__ import annotations

import httpx

from app.config import settings


def get_policies() -> dict:
    return {
        "neverRetryReasons": ["payment_risk_check_failed", "payment_cancelled"],
        "humanApprovalAmount": settings.human_approval_amount,
        "minLabelledOutcomesProd": 10000,
        "minLabelledOutcomesDemo": 400,
        "lowProbabilitySkipRetry": 0.25,
        "agentCanExecute": False,
    }


def calculate_expected_value(probability: float, amount_inr: float) -> float:
    return round(probability * amount_inr, 2)


def predict_recovery(payload: dict) -> dict | None:
    body = {
        "reason": payload["reason"],
        "source": payload.get("source", "PAYMENT"),
        "priority": payload.get("priority", "MEDIUM"),
        "paymentMethod": payload.get("paymentMethod", "card"),
        "amountInr": payload["amountInr"],
        "retryCount": payload.get("retryCount", 0),
        "hoursSinceFail": payload.get("hoursSinceFail", 0),
        "historicalRecoveryRate": payload.get("historicalRecoveryRate", 0),
        "retryHistoryCount": payload.get("retryHistoryCount", 0),
        "paymentSuccessRate": payload["paymentSuccessRate"],
        "paymentFailureRate": payload["paymentFailureRate"],
        "avgPaymentDelay": payload.get("avgPaymentDelay", 0),
        "subscriptionAgeMonths": payload.get("subscriptionAgeMonths", 0),
        "lifetimeValue": payload["lifetimeValue"],
        "avgOrderValue": payload["avgOrderValue"],
        "daysSinceLastActivity": payload.get("daysSinceLastActivity", 0),
        "historyPaymentCount": payload.get("historyPaymentCount", 0),
    }
    try:
        response = httpx.post(settings.ml_predict_url, json=body, timeout=3.0)
        response.raise_for_status()
        return response.json()
    except httpx.HTTPError:
        return None
