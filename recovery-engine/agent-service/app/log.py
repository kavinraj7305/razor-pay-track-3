"""Process logger — structured enough to grep in a demo, not a full observability stack."""

from __future__ import annotations

import logging

from app.config import settings

logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)

log = logging.getLogger("agent")
