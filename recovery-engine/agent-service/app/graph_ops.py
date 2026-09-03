"""Ops brain: SQL patterns → optional Ollama narrate → safety."""

from __future__ import annotations

from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from app.config import settings
from app.diagnose import narrate_ops
from app.log import log
from app.patterns import gather_patterns
from app.safety import apply_ops_safety


class OpsState(TypedDict, total=False):
    window_hours: int
    merchant_id: str | None
    metrics: dict
    patterns: list
    narrated: dict
    briefing: dict[str, Any]
    fallback_used: bool


def node_metrics(state: OpsState) -> dict:
    try:
        metrics, patterns = gather_patterns(state["window_hours"], state.get("merchant_id"))
        return {"metrics": metrics, "patterns": patterns}
    except Exception as exc:
        log.warning("ops metrics failed: %s", exc)
        hours = state["window_hours"]
        return {
            "metrics": {
                "casesOpened": 0,
                "revenueAtRiskInr": 0,
                "topReasons": [],
                "byMethod": [],
                "windowHours": hours,
                "dbUnavailable": True,
            },
            "patterns": [],
        }


def node_narrate(state: OpsState) -> dict:
    if state.get("metrics", {}).get("dbUnavailable"):
        return {
            "narrated": {
                "summary": "Postgres unavailable — could not load ops patterns.",
                "patterns": [],
            },
            "fallback_used": True,
        }
    if not state.get("patterns"):
        return {
            "narrated": {
                "summary": "No recurring spikes in this window. Queue looks quiet.",
                "patterns": [],
            },
            "fallback_used": False,
        }
    narrated = narrate_ops(state["metrics"], state["patterns"])
    if narrated is None:
        return {
            "narrated": {
                "summary": "Rule-based ops briefing (Ollama unavailable).",
                "patterns": state["patterns"],
            },
            "fallback_used": True,
        }
    # Merge proposedSolution from LLM onto SQL patterns when present
    llm_patterns = narrated.get("patterns") or []
    merged = []
    for idx, base in enumerate(state["patterns"]):
        extra = llm_patterns[idx] if idx < len(llm_patterns) and isinstance(llm_patterns[idx], dict) else {}
        item = dict(base)
        if extra.get("proposedSolution") or extra.get("proposed_solution"):
            item["proposedSolution"] = extra.get("proposedSolution") or extra.get("proposed_solution")
        if extra.get("why"):
            item["why"] = extra["why"]
        merged.append(item)
    return {
        "narrated": {
            "summary": narrated.get("summary") or "Ops briefing ready.",
            "patterns": merged or state["patterns"],
        },
        "fallback_used": False,
    }


def node_safety(state: OpsState) -> dict:
    raw = {
        "windowHours": state["window_hours"],
        "summary": state["narrated"]["summary"],
        "patterns": state["narrated"]["patterns"],
        "metrics": state["metrics"],
    }
    model = "fallback-rules" if state.get("fallback_used") else settings.ollama_model
    return {
        "briefing": apply_ops_safety(raw, fallback_used=bool(state.get("fallback_used")), model=model)
    }


def build_graph():
    graph = StateGraph(OpsState)
    graph.add_node("metrics", node_metrics)
    graph.add_node("narrate", node_narrate)
    graph.add_node("safety", node_safety)
    graph.add_edge(START, "metrics")
    graph.add_edge("metrics", "narrate")
    graph.add_edge("narrate", "safety")
    graph.add_edge("safety", END)
    return graph.compile()


OPS_AGENT = build_graph()


def run_ops_briefing(window_hours: int | None = None, merchant_id: str | None = None) -> dict:
    hours = window_hours or settings.ops_window_hours
    final = OPS_AGENT.invoke({"window_hours": hours, "merchant_id": merchant_id})
    return final["briefing"]
