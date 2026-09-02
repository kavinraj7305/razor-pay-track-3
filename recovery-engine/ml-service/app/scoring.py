"""Load the trained booster and score one row → P(recovery)."""

from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path

import pandas as pd
from xgboost import XGBClassifier

from app.features import CATEGORICAL, FEATURE_ORDER

ROOT = Path(__file__).resolve().parents[1]
MODEL_PATH = ROOT / "models" / "recovery_xgb.json"
SPEC_PATH = ROOT / "models" / "feature_spec.json"


@lru_cache(maxsize=1)
def load_model() -> tuple[XGBClassifier, dict]:
    spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
    model = XGBClassifier()
    model.load_model(MODEL_PATH)
    return model, spec


def frame_from_payload(payload: dict, spec: dict) -> pd.DataFrame:
    row = {name: payload[name] for name in FEATURE_ORDER}
    frame = pd.DataFrame([row])
    for col in CATEGORICAL:
        known = spec["categories"][col]
        frame[col] = pd.Categorical(frame[col].astype(str), categories=known)
    for col in spec["numeric"]:
        frame[col] = pd.to_numeric(frame[col], errors="coerce").fillna(0)
    return frame[FEATURE_ORDER]


def predict_proba(payload: dict) -> float:
    model, spec = load_model()
    frame = frame_from_payload(payload, spec)
    proba = model.predict_proba(frame)[0, 1]
    return round(float(proba), 4)
