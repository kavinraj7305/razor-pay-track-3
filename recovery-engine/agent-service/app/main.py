from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.db import close_pool, init_pool, ping as postgres_ping
from app.graph_case import run_case_agent
from app.graph_ops import run_ops_briefing
from app.llm import ollama_available
from app.log import log
from app.schemas import CaseProposal, OpsBriefing, OpsBriefingRequest, ProposeRequest


@asynccontextmanager
async def lifespan(_app: FastAPI):
    init_pool()
    yield
    close_pool()


app = FastAPI(
    title="recovery-engine agent-service",
    version="0.3.0",
    lifespan=lifespan,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://127.0.0.1:3000"],
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)


def _ml_up() -> bool:
    try:
        base = settings.ml_predict_url.rsplit("/", 1)[0]
        response = httpx.get(f"{base}/health", timeout=2.0)
        return response.is_success
    except httpx.HTTPError:
        return False


@app.get("/health")
def health() -> dict:
    """Liveness + dependency picture. Process stays up even if Ollama is down (fallback path)."""
    postgres = postgres_ping()
    ollama = ollama_available()
    ml = _ml_up()
    status = "ok" if postgres else "degraded"
    return {
        "status": status,
        "service": "agent-service",
        "model": settings.ollama_model,
        "postgres": "up" if postgres else "down",
        "ollama": "up" if ollama else "down",
        "ml": "up" if ml else "down",
        "actionsAvailable": ["propose"],
        "executes": False,
    }


@app.post("/propose", response_model=CaseProposal)
def propose(body: ProposeRequest) -> CaseProposal:
    """Case agent: context → diagnose → safety. Propose only. No execute tools."""
    payload = body.model_dump(by_alias=True)
    log.info("propose caseId=%s", payload.get("caseId"))
    return CaseProposal.model_validate(run_case_agent(payload))


@app.post("/ops/briefing", response_model=OpsBriefing)
def ops_briefing(body: OpsBriefingRequest | None = None) -> OpsBriefing:
    """Ops brain: SQL recurring patterns + optional Ollama narration. Propose only."""
    req = body or OpsBriefingRequest()
    log.info("ops briefing windowHours=%s", req.window_hours)
    return OpsBriefing.model_validate(run_ops_briefing(req.window_hours, req.merchant_id))
