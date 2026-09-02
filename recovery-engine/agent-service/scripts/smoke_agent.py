"""One-shot smoke: Ollama ping + /propose schema + /ops/briefing safety fields."""

from __future__ import annotations

import json
import sys

import httpx

from app.graph_case import run_case_agent
from app.graph_ops import run_ops_briefing


def main() -> int:
    try:
        response = httpx.post(
            "http://127.0.0.1:11434/api/chat",
            json={
                "model": "qwen2.5-coder:7b",
                "stream": False,
                "format": "json",
                "messages": [{"role": "user", "content": 'Return JSON {"ok": true} only.'}],
                "options": {"num_predict": 40, "temperature": 0},
                "keep_alive": "30m",
            },
            timeout=60,
        )
        content = response.json().get("message", {}).get("content", "")
        print("OLLAMA", response.status_code, content[:200])
    except Exception as exc:  # noqa: BLE001 — smoke
        print("OLLAMA_ERR", type(exc).__name__, exc)

    case_id = sys.argv[1] if len(sys.argv) > 1 else "rc_a2bf3bb323be4422896be99dcb626a53"
    proposal = run_case_agent({"caseId": case_id})
    keys = [
        "caseId",
        "diagnosis",
        "recommendedAction",
        "defaultPlaybookAction",
        "deviatesFromPlaybook",
        "actionsAvailable",
        "executes",
        "fallbackUsed",
        "model",
        "escalate",
        "confidence",
        "mlScore",
    ]
    print("PROPOSE", json.dumps({k: proposal.get(k) for k in keys}, indent=2))
    print("REASONING", (proposal.get("reasoning") or "")[:300])
    assert proposal["actionsAvailable"] == ["propose"]
    assert proposal["executes"] is False

    briefing = run_ops_briefing(6)
    print(
        "OPS",
        json.dumps(
            {
                "fallbackUsed": briefing.get("fallbackUsed"),
                "executes": briefing.get("executes"),
                "actionsAvailable": briefing.get("actionsAvailable"),
                "patternCount": len(briefing.get("patterns") or []),
                "summary": briefing.get("summary"),
            },
            indent=2,
        ),
    )
    assert briefing["actionsAvailable"] == ["propose"]
    assert briefing["executes"] is False
    print("SMOKE_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
