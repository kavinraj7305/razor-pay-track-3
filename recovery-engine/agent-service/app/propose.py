"""Propose-only mapping. No charge / retry / pay-link execution."""

from __future__ import annotations

DIAGNOSIS = {
    "insufficient_funds": "TEMPORARY_FUNDS_SHORTFALL",
    "card_expired": "DEAD_INSTRUMENT",
    "invalid_vpa": "DEAD_INSTRUMENT",
    "payment_risk_check_failed": "RISK_BLOCK",
    "payment_cancelled": "CUSTOMER_CANCELLED",
    "subscription.pending": "MANDATE_RETRY",
    "subscription.halted": "RETRIES_EXHAUSTED",
    "invoice.expired": "RECEIVABLE_OVERDUE",
    "checkout.abandoned": "CHECKOUT_DROPOFF",
    "captured": "ALREADY_RECOVERED",
    "gateway_technical": "TEMPORARY_ISSUER_FAILURE",
    "gateway_technical_error": "TEMPORARY_ISSUER_FAILURE",
    "bank_technical": "TEMPORARY_ISSUER_FAILURE",
}

RETRY_REASONS = {
    "insufficient_funds",
    "subscription.pending",
    "gateway_technical",
    "gateway_technical_error",
    "bank_technical",
}
STOP_REASONS = {"payment_risk_check_failed", "payment_cancelled"}
LINK_REASONS = {"card_expired", "invalid_vpa", "checkout.abandoned", "subscription.halted"}


def diagnosis_of(reason: str) -> str:
    key = (reason or "").lower()
    for needle, label in DIAGNOSIS.items():
        if needle in key:
            return label
    return "UNKNOWN_FAILURE"


def recommended_action(reason: str, amount_inr: float, probability: float, ml_available: bool) -> str:
    key = (reason or "").lower()
    if any(stop in key for stop in STOP_REASONS):
        return "DO_NOT_RETRY"
    if "captured" in key:
        return "NO_ACTION"
    if "invoice.expired" in key:
        return "REQUEST_PROMISE_TO_PAY"
    if any(link in key for link in LINK_REASONS):
        return "SEND_PAYMENT_LINK"
    if ml_available and any(retry in key for retry in RETRY_REASONS) and probability < 0.12:
        return "SKIP_EXTRA_RETRY"
    if any(retry in key for retry in RETRY_REASONS):
        return "DELAYED_RETRY"
    return "DELAYED_RETRY"


def propose(
    reason: str,
    amount_inr: float,
    probability: float,
    ml_available: bool,
    policies: dict,
) -> dict:
    action = recommended_action(reason, amount_inr, probability, ml_available)
    expected = round(probability * amount_inr, 2)
    blocked = action == "DO_NOT_RETRY" and amount_inr >= policies["humanApprovalAmount"]
    why = _why(reason, action, probability, expected, amount_inr, blocked, ml_available)
    confidence = 0.92 if action == "DO_NOT_RETRY" else (0.5 if not ml_available else round(0.55 + abs(probability - 0.5), 2))
    return {
        "diagnosis": diagnosis_of(reason),
        "recoveryProbability": round(probability, 4) if ml_available else None,
        "recommendedAction": action,
        "reason": why,
        "confidence": min(confidence, 0.95),
        "expectedValueInr": expected if ml_available else None,
        "executes": False,
    }


def _why(
    reason: str,
    action: str,
    probability: float,
    expected: float,
    amount_inr: float,
    blocked: bool,
    ml_available: bool,
) -> str:
    if not ml_available:
        return (
            f"ML unavailable. Propose playbook action for reason={reason}. "
            "Java must execute. Agent has no charge tool."
        )
    if blocked:
        return (
            f"Policy BLOCK: reason={reason} amount={amount_inr:.2f} INR. "
            "Do not retry. Agent does not execute."
        )
    if action == "DO_NOT_RETRY":
        return f"Stop rule for {reason}. Probability={probability:.2f} is not allowed to retry."
    if action == "SKIP_EXTRA_RETRY":
        return (
            f"P(recovery)={probability:.2f} is below 0.12 for {reason}. "
            f"EV={expected:.2f} INR. Propose skip extra retries; Java playbook still owns execute."
        )
    return (
        f"Reason={reason}. P(recovery)={probability:.2f}, EV={expected:.2f} INR. "
        f"Propose {action}. Agent does not charge, retry, or send a link."
    )
