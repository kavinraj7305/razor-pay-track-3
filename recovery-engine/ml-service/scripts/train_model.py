"""Train XGBoost on case_features.csv. Writes models/ + data/predict_metrics.json.

Run from recovery-engine/ml-service:

    uv run python scripts/train_model.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pandas as pd
from sklearn.metrics import (
    average_precision_score,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from xgboost import XGBClassifier

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.features import CATEGORICAL, FEATURE_ORDER, LABEL, NUMERIC  # noqa: E402

DATA_PATH = ROOT / "data" / "case_features.csv"
MODEL_DIR = ROOT / "models"
METRICS_PATH = ROOT / "data" / "predict_metrics.json"
SEED = 42


def prepare(frame: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    missing = [c for c in FEATURE_ORDER + [LABEL] if c not in frame.columns]
    if missing:
        raise SystemExit(f"case_features.csv missing columns: {missing}")
    x = frame[FEATURE_ORDER].copy()
    for col in CATEGORICAL:
        x[col] = x[col].astype("category")
    for col in NUMERIC:
        x[col] = pd.to_numeric(x[col], errors="coerce").fillna(0)
    y = pd.to_numeric(frame[LABEL], errors="coerce").fillna(0).astype(int)
    return x, y


def main() -> None:
    x, y = prepare(pd.read_csv(DATA_PATH))
    x_train, x_test, y_train, y_test = train_test_split(
        x, y, test_size=0.25, random_state=SEED, stratify=y
    )
    pos = int(y_train.sum())
    neg = int(len(y_train) - pos)
    model = XGBClassifier(
        n_estimators=80,
        max_depth=4,
        learning_rate=0.1,
        subsample=0.9,
        colsample_bytree=0.9,
        enable_categorical=True,
        tree_method="hist",
        eval_metric="logloss",
        scale_pos_weight=(neg / pos) if pos else 1.0,
        random_state=SEED,
    )
    model.fit(x_train, y_train)
    proba = model.predict_proba(x_test)[:, 1]
    pred = (proba >= 0.5).astype(int)
    metrics = {
        "n_train": int(len(x_train)),
        "n_test": int(len(x_test)),
        "paid_rate_train": round(float(y_train.mean()), 4),
        "precision": round(float(precision_score(y_test, pred, zero_division=0)), 4),
        "recall": round(float(recall_score(y_test, pred, zero_division=0)), 4),
        "f1": round(float(f1_score(y_test, pred, zero_division=0)), 4),
        "roc_auc": round(float(roc_auc_score(y_test, proba)), 4),
        "pr_auc": round(float(average_precision_score(y_test, proba)), 4),
        "threshold": 0.5,
    }
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    model.save_model(MODEL_DIR / "recovery_xgb.json")
    spec = {
        "categorical": CATEGORICAL,
        "numeric": NUMERIC,
        "categories": {col: [str(v) for v in x[col].cat.categories] for col in CATEGORICAL},
    }
    (MODEL_DIR / "feature_spec.json").write_text(json.dumps(spec, indent=2), encoding="utf-8")
    METRICS_PATH.write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    print("test metrics:")
    for key, value in metrics.items():
        print(f"  {key}={value}")
    print(f"wrote {MODEL_DIR / 'recovery_xgb.json'}")
    print(f"wrote {METRICS_PATH}")


if __name__ == "__main__":
    main()
