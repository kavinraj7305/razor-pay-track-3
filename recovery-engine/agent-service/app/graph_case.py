"""3-node case agent: context → diagnose → safety. No execute tools."""

from __future__ import annotations

from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from app.config import settings
from app.context import build_context
from app.diagnose import diagnose
from app.propose_fallback import fallback_from_context
from app.safety import apply_safety


class CaseState(TypedDict, total=False):
    request: dict
    context: dict
    draft: dict
    proposal: dict[str, Any]
    fallback_used: bool


def node_context(state: CaseState) -> dict:
    ctx = build_context(state["request"])
    return {"context": ctx}


def node_diagnose(state: CaseState) -> dict:
    draft = diagnose(state["context"])
    if draft is None:
        return {"draft": {}, "fallback_used": True}
    return {"draft": draft, "fallback_used": False}


def node_safety(state: CaseState) -> dict:
    ctx = state["context"]
    if state.get("fallback_used") or not state.get("draft"):
        return {"proposal": fallback_from_context(ctx)}
    draft = dict(state["draft"])
    draft["reasonCode"] = ctx.get("reason")
    safe = apply_safety(
        draft,
        fallback_used=False,
        model=settings.ollama_model,
    )
    # Ensure default playbook filled from context
    safe["defaultPlaybookAction"] = ctx.get("defaultPlaybookAction") or safe["defaultPlaybookAction"]
    safe["deviatesFromPlaybook"] = safe["recommendedAction"] != safe["defaultPlaybookAction"]
    if safe.get("mlScore") is None and ctx.get("mlScore") is not None:
        safe["mlScore"] = ctx["mlScore"]
        safe["recoveryProbability"] = ctx["mlScore"]
    safe["caseId"] = ctx.get("caseId")
    return {"proposal": safe}


def build_graph():
    graph = StateGraph(CaseState)
    graph.add_node("context", node_context)
    graph.add_node("diagnose", node_diagnose)
    graph.add_node("safety", node_safety)
    graph.add_edge(START, "context")
    graph.add_edge("context", "diagnose")
    graph.add_edge("diagnose", "safety")
    graph.add_edge("safety", END)
    return graph.compile()


CASE_AGENT = build_graph()


def run_case_agent(request: dict) -> dict:
    final = CASE_AGENT.invoke({"request": request})
    return final["proposal"]
