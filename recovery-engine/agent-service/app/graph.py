"""Compat shim — case agent is the 3-node graph in graph_case.py."""

from app.graph_case import run_case_agent as run_agent

__all__ = ["run_agent"]
