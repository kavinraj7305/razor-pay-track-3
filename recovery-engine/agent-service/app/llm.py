"""Ollama wrapper. Failures return None so callers can use fallback. Never raises into the graph."""

from __future__ import annotations

import json
import re
from typing import Any

import httpx

from app.config import settings
from app.log import log


def ollama_available() -> bool:
    """Fail fast if the daemon or model is missing — do not wait 45s to learn that."""
    url = f"{settings.ollama_base_url.rstrip('/')}/api/tags"
    try:
        response = httpx.get(url, timeout=settings.ollama_probe_seconds)
        response.raise_for_status()
        names = [str(item.get("name") or "") for item in response.json().get("models", [])]
        return any(settings.ollama_model in name for name in names)
    except (httpx.HTTPError, ValueError, KeyError):
        return False


def _extract_json(text: str) -> dict[str, Any] | None:
    text = text.strip()
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    match = re.search(r"\{[\s\S]*\}", text)
    if not match:
        return None
    try:
        return json.loads(match.group(0))
    except json.JSONDecodeError:
        return None


def chat_json(system: str, user: str) -> dict[str, Any] | None:
    if not ollama_available():
        log.info("ollama unavailable or model missing — skipping LLM")
        return None
    url = f"{settings.ollama_base_url.rstrip('/')}/api/chat"
    payload = {
        "model": settings.ollama_model,
        "stream": False,
        "format": "json",
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "options": {"temperature": 0.1, "num_predict": 350},
        "keep_alive": "30m",
    }
    try:
        response = httpx.post(url, json=payload, timeout=settings.ollama_timeout_seconds)
        response.raise_for_status()
        content = response.json().get("message", {}).get("content", "")
        parsed = _extract_json(content)
        if parsed is None:
            log.warning("ollama returned non-JSON content")
        return parsed
    except httpx.TimeoutException:
        log.warning("ollama timed out after %ss", settings.ollama_timeout_seconds)
        return None
    except (httpx.HTTPError, ValueError, KeyError) as exc:
        log.warning("ollama chat failed: %s", exc)
        return None
