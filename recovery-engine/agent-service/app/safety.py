"""Hardcoded safety node. No LLM. No execute tools bound anywhere."""

from __future__ import annotations

from typing import Any

from app.config import settings
from app.propose import diagnosis_of
from app.schemas import CaseProposal, OpsBriefing

ALLOWED_ACTIONS = {
    "DELAYED_RETRY",
    "SKIP_EXTRA_RETRY",
    "SEND_PAYMENT_LINK",
    "REQUEST_PROMISE_TO_PAY",
    "DO_NOT_RETRY",
    "NO_ACTION",
}
STOP_REASON_HINTS = ("risk", "cancelled")


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

    reasoning = str(raw.get("reasoning") or raw.get("reason") or "Proposal generated.")[:2000]
    reason_code = str(raw.get("reasonCode") or raw.get("failureReason") or "")
    diagnosis = str(raw.get("diagnosis") or "")
    if len(diagnosis) > 40 or " " in diagnosis or not diagnosis:
        diagnosis = diagnosis_of(reason_code)

    try:
        amount = float(raw.get("amountInr") or raw.get("amount_inr") or 0)
    except (TypeError, ValueError):
        amount = 0.0

    escalate = bool(raw.get("escalate", False))
    lowered = reason_code.lower()
    if any(hint in lowered for hint in STOP_REASON_HINTS):
        escalate = True
        action = "DO_NOT_RETRY"
    if amount >= settings.human_approval_amount:
        escalate = True

    proposal = CaseProposal.model_validate(
        {
            "caseId": raw.get("caseId") or raw.get("case_id"),
            "diagnosis": diagnosis[:80],
            "reasoning": reasoning,
            "recommendedAction": action,
            "defaultPlaybookAction": default,
            "deviatesFromPlaybook": action != default,
            "confidence": confidence,
            "mlScore": ml_score,
            "escalate": escalate,
            "model": model,
            "fallbackUsed": fallback_used,
            "recoveryProbability": ml_score,
            "reason": reasoning,
        }
    )
    return proposal.model_dump(by_alias=True)


def apply_ops_safety(raw: dict[str, Any], *, fallback_used: bool, model: str) -> dict[str, Any]:
    patterns = raw.get("patterns") or []
    cleaned = []
    for item in patterns:
        if not isinstance(item, dict):
            continue
        try:
            count = int(item.get("count") or 0)
        except (TypeError, ValueError):
            count = 0
        ids = item.get("relatedCaseIds") or item.get("related_case_ids") or []
        if not isinstance(ids, list):
            ids = []
        cleaned.append(
            {
                "severity": str(item.get("severity", "MEDIUM"))[:20],
                "pattern": str(item.get("pattern", "unknown"))[:80],
                "where": str(item.get("where", "unknown"))[:120],
                "count": max(0, count),
                "why": str(item.get("why") or "")[:500],
                "proposedSolution": str(
                    item.get("proposedSolution") or item.get("proposed_solution") or "Review queue."
                )[:500],
                "relatedCaseIds": [str(cid) for cid in ids[:8]],
            }
        )
    briefing = OpsBriefing.model_validate(
        {
            "windowHours": int(raw.get("windowHours") or raw.get("window_hours") or 6),
            "summary": str(raw.get("summary") or "No summary.")[:2000],
            "patterns": cleaned,
            "metrics": dict(raw.get("metrics") or {}),
            "fallbackUsed": fallback_used,
            "model": model,
        }
    )
    return briefing.model_dump(by_alias=True)
