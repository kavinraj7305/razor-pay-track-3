"""Thin rule fallback when Ollama is down. Still propose-only."""

from __future__ import annotations

from typing import Any

from app.config import settings
from app.propose import diagnosis_of, recommended_action
from app.safety import apply_safety


def default_playbook_action(reason: str) -> str:
    return recommended_action(reason, 0, 1.0, ml_available=False)


def fallback_from_context(ctx: dict[str, Any]) -> dict[str, Any]:
    reason = str(ctx.get("reason") or "unknown")
    amount = float(ctx.get("amountInr") or 0)
    ml_available = bool(ctx.get("mlAvailable"))
    probability = float(ctx.get("mlScore") or 0)
    default = str(ctx.get("defaultPlaybookAction") or default_playbook_action(reason))
    action = recommended_action(reason, amount, probability, ml_available)
    escalate = action == "DO_NOT_RETRY" or amount >= settings.human_approval_amount
    if "risk" in reason.lower() or "cancelled" in reason.lower():
        escalate = True
    reasoning = (
        f"Fallback rules (Ollama unavailable). reason={reason}, segment={ctx.get('customerSegment')}, "
        f"P={probability:.2f}, defaultPlaybook={default}, recommended={action}. "
        "Agent proposes only — Java executes."
    )
    confidence = 0.92 if action == "DO_NOT_RETRY" else (0.55 if not ml_available else min(0.9, 0.5 + abs(probability - 0.5)))
    raw = {
        "caseId": ctx.get("caseId"),
        "diagnosis": diagnosis_of(reason),
        "reasoning": reasoning,
        "recommendedAction": action,
        "defaultPlaybookAction": default,
        "deviatesFromPlaybook": action != default,
        "confidence": confidence,
        "mlScore": probability if ml_available else None,
        "escalate": escalate,
    }
    return apply_safety(raw, fallback_used=True, model="fallback-rules")
