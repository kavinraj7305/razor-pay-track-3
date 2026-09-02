"""Hardcoded safety node. No LLM. No execute tools bound anywhere."""

from __future__ import annotations

from typing import Any

from app.propose import diagnosis_of

ALLOWED_ACTIONS = {
    "DELAYED_RETRY",
    "SKIP_EXTRA_RETRY",
    "SEND_PAYMENT_LINK",
    "REQUEST_PROMISE_TO_PAY",
    "DO_NOT_RETRY",
    "NO_ACTION",
}


def apply_safety(raw: dict[str, Any], *, fallback_used: bool, model: str) -> dict[str, Any]:
    action = str(raw.get("recommendedAction") or raw.get("recommended_action") or "DELAYED_RETRY")
    if action not in ALLOWED_ACTIONS:
        action = "DELAYED_RETRY"
    default = str(raw.get("defaultPlaybookAction") or raw.get("default_playbook_action") or action)
    if default not in ALLOWED_ACTIONS:
        default = action
    ml = raw.get("mlScore", raw.get("ml_score", raw.get("recoveryProbability")))
    try:
        ml_score = float(ml) if ml is not None else None
    except (TypeError, ValueError):
        ml_score = None
    try:
        confidence = float(raw.get("confidence") or 0.5)
    except (TypeError, ValueError):
        confidence = 0.5
    confidence = max(0.0, min(confidence, 0.99))
    reasoning = str(raw.get("reasoning") or raw.get("reason") or "Proposal generated.")
    diagnosis = str(raw.get("diagnosis") or "")
    if len(diagnosis) > 40 or " " in diagnosis or not diagnosis:
        diagnosis = diagnosis_of(str(raw.get("reasonCode") or raw.get("failureReason") or ""))
    escalate = bool(raw.get("escalate", False))
    deviates = bool(
        raw.get("deviatesFromPlaybook", raw.get("deviates_from_playbook", action != default))
    )
    return {
        "caseId": raw.get("caseId") or raw.get("case_id"),
        "diagnosis": diagnosis,
        "reasoning": reasoning,
        "recommendedAction": action,
        "defaultPlaybookAction": default,
        "deviatesFromPlaybook": deviates,
        "confidence": round(confidence, 4),
        "mlScore": round(ml_score, 4) if ml_score is not None else None,
        "escalate": escalate,
        "actionsAvailable": ["propose"],
        "executes": False,
        "model": model,
        "fallbackUsed": fallback_used,
        "recoveryProbability": round(ml_score, 4) if ml_score is not None else None,
        "reason": reasoning,
    }


def apply_ops_safety(raw: dict[str, Any], *, fallback_used: bool, model: str) -> dict[str, Any]:
    patterns = raw.get("patterns") or []
    cleaned = []
    for item in patterns:
        cleaned.append(
            {
                "severity": str(item.get("severity", "MEDIUM")),
                "pattern": str(item.get("pattern", "unknown")),
                "where": str(item.get("where", "unknown")),
                "count": int(item.get("count") or 0),
                "why": str(item.get("why") or ""),
                "proposedSolution": str(
                    item.get("proposedSolution") or item.get("proposed_solution") or "Review queue."
                ),
                "relatedCaseIds": list(item.get("relatedCaseIds") or item.get("related_case_ids") or []),
            }
        )
    return {
        "windowHours": int(raw.get("windowHours") or raw.get("window_hours") or 6),
        "summary": str(raw.get("summary") or "No summary."),
        "patterns": cleaned,
        "metrics": dict(raw.get("metrics") or {}),
        "actionsAvailable": ["propose"],
        "executes": False,
        "fallbackUsed": fallback_used,
        "model": model,
    }
