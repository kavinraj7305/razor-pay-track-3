"""Ollama wrapper. Failures return None so callers can use fallback."""

from __future__ import annotations

import json
import re
from typing import Any

import httpx

from app.config import settings


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
        return _extract_json(content)
    except (httpx.HTTPError, ValueError, KeyError):
        return None
