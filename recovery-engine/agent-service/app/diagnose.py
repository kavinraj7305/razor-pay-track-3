"""Diagnose node — single Ollama call. Returns None on failure."""

from __future__ import annotations

import json
from typing import Any

from app.llm import chat_json

SYSTEM = """You are a recovery decision assistant for a human operator in a fintech revenue-recovery system.
You PROPOSE only. You cannot charge, retry, capture, or send payment links.
Ground every claim in the provided context fields. Do not invent history.
Prefer the default playbook action unless ML/history clearly justifies a deviation.
If you deviate, set deviatesFromPlaybook=true and explain why using concrete fields.
Risk or cancelled reasons => recommendedAction=DO_NOT_RETRY and escalate=true.
insufficient_funds with low mlScore (<0.25) or WEAK segment => recommendedAction=SKIP_EXTRA_RETRY (not DO_NOT_RETRY).
Amount >= humanApprovalAmount => escalate=true.
diagnosis must be a SHORT_SNAKE token such as TEMPORARY_FUNDS_SHORTFALL, RISK_BLOCK, DEAD_INSTRUMENT — never a paragraph.
Return JSON only with keys:
diagnosis, reasoning, recommendedAction, defaultPlaybookAction, deviatesFromPlaybook,
confidence, mlScore, escalate
recommendedAction must be one of:
DELAYED_RETRY, SKIP_EXTRA_RETRY, SEND_PAYMENT_LINK, REQUEST_PROMISE_TO_PAY, DO_NOT_RETRY, NO_ACTION
"""


def diagnose(ctx: dict[str, Any]) -> dict[str, Any] | None:
    user = (
        "Case context (JSON). Diagnose and propose one action for the human.\n"
        + json.dumps(ctx, default=str)
    )
    parsed = chat_json(SYSTEM, user)
    if not parsed:
        return None
    parsed.setdefault("defaultPlaybookAction", ctx.get("defaultPlaybookAction"))
    parsed.setdefault("mlScore", ctx.get("mlScore"))
    parsed["caseId"] = ctx.get("caseId")
    return parsed


def narrate_ops(metrics: dict[str, Any], patterns: list[dict[str, Any]]) -> dict[str, Any] | None:
    system = (
        "You are an ops intelligence assistant for payment recovery. "
        "Narrate recurring problems in plain English. Propose solutions for humans. "
        "You cannot execute charges. Return JSON with keys: summary, patterns "
        "(each pattern may include proposedSolution). Keep it short."
    )
    user = json.dumps({"metrics": metrics, "patterns": patterns}, default=str)
    return chat_json(system, user)
