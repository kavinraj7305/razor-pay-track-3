from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.graph_case import run_case_agent
from app.graph_ops import run_ops_briefing
from app.schemas import OpsBriefingRequest, ProposeRequest

app = FastAPI(title="recovery-engine agent-service", version="0.3.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://127.0.0.1:3000"],
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "service": "agent-service",
        "model": settings.ollama_model,
        "actionsAvailable": ["propose"],
        "executes": False,
    }


@app.post("/propose")
def propose(body: ProposeRequest) -> dict:
    """Case agent: context → diagnose → safety. Propose only. No execute tools."""
    payload = body.model_dump(by_alias=True)
    return run_case_agent(payload)


@app.post("/ops/briefing")
def ops_briefing(body: OpsBriefingRequest = OpsBriefingRequest()) -> dict:
    """Ops brain: SQL recurring patterns + optional Ollama narration. Propose only."""
    return run_ops_briefing(body.window_hours, body.merchant_id)
