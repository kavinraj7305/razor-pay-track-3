"""LangGraph: predict → policy/EV → propose JSON. No money tools."""

from __future__ import annotations

from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from app.propose import propose
from app.tools import calculate_expected_value, get_policies, predict_recovery


class AgentState(TypedDict, total=False):
    request: dict
    probability: float
    ml_available: bool
    expected_value: float
    policies: dict
    proposal: dict[str, Any]


def node_predict(state: AgentState) -> dict:
    scored = predict_recovery(state["request"])
    if scored is None:
        return {"probability": 0.0, "ml_available": False}
    return {
        "probability": float(scored.get("recoveryProbability") or 0),
        "ml_available": True,
    }


def node_policy(state: AgentState) -> dict:
    policies = get_policies()
    amount = float(state["request"]["amountInr"])
    expected = calculate_expected_value(state.get("probability") or 0.0, amount)
    return {"policies": policies, "expected_value": expected}


def node_propose(state: AgentState) -> dict:
    req = state["request"]
    proposal = propose(
        reason=req["reason"],
        amount_inr=float(req["amountInr"]),
        probability=float(state.get("probability") or 0.0),
        ml_available=bool(state.get("ml_available")),
        policies=state["policies"],
    )
    return {"proposal": proposal}


def build_graph():
    graph = StateGraph(AgentState)
    graph.add_node("predictRecovery", node_predict)
    graph.add_node("getPolicies", node_policy)
    graph.add_node("propose", node_propose)
    graph.add_edge(START, "predictRecovery")
    graph.add_edge("predictRecovery", "getPolicies")
    graph.add_edge("getPolicies", "propose")
    graph.add_edge("propose", END)
    return graph.compile()


AGENT = build_graph()


def run_agent(request: dict) -> dict:
    final = AGENT.invoke({"request": request})
    return final["proposal"]
