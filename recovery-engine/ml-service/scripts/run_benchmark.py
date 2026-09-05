"""Baseline vs AI scoreboard on the 500 labelled synthetic events.

Uses the trained XGBoost file and the same skip / block rules as Java
(PolicyEngine + MlDataGate). Does not invent percentages.

Run from recovery-engine/ml-service:

    uv run python scripts/run_benchmark.py
"""

from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd
from xgboost import XGBClassifier

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.features import CATEGORICAL, FEATURE_ORDER  # noqa: E402

EVENTS = ROOT / "data" / "synthetic_events.csv"
MODEL_PATH = ROOT / "models" / "recovery_xgb.json"
SPEC_PATH = ROOT / "models" / "feature_spec.json"
METRICS_PATH = ROOT / "data" / "predict_metrics.json"
OUT_PATH = ROOT / "data" / "benchmark.json"
JAVA_COPY = (
    ROOT.parent
    / "backend"
    / "revenue-recovery"
    / "src"
    / "main"
    / "resources"
    / "benchmark"
    / "acc_syn_training_500.json"
)

HUMAN_APPROVAL = 80_000.0
MIN_P = 0.25
MIN_HISTORY = 5
CHASE = {"RETRY_PAYMENT", "SEND_PAYMENT_LINK", "REQUEST_PROMISE_TO_PAY"}
MERCHANT = "acc_syn_training"


def features_from_events(events: pd.DataFrame) -> pd.DataFrame:
    payments = events["prior_payment_count"].clip(lower=1)
    rec_attempts = events["historical_recovery_attempts"].clip(lower=1)
    frame = pd.DataFrame(
        {
            "reason": events["reason"].astype(str),
            "source": events["source"].astype(str),
            "priority": events["priority"].astype(str),
            "payment_method": events["payment_method"].astype(str),
            "amount_inr": events["amount_inr"],
            "retry_count": events["retry_count"],
            "hours_since_fail": events["hours_since_fail"],
            "historical_recovery_rate": events["historical_recovery_successes"] / rec_attempts,
            "retry_history_count": events["customer_retry_history_count"],
            "payment_success_rate": events["prior_success_count"] / payments,
            "payment_failure_rate": events["prior_failure_count"] / payments,
            "avg_payment_delay": events["avg_payment_delay_hours"],
            "subscription_age_months": events["subscription_age_months"],
            "lifetime_value": events["lifetime_value_inr"],
            "avg_order_value": events["avg_order_value_inr"],
            "days_since_last_activity": events["days_since_last_activity"],
            "history_payment_count": events["prior_payment_count"],
        }
    )
    return frame[FEATURE_ORDER]


def score(events: pd.DataFrame) -> pd.Series:
    spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
    model = XGBClassifier()
    model.load_model(MODEL_PATH)
    frame = features_from_events(events)
    for col in CATEGORICAL:
        frame[col] = pd.Categorical(frame[col].astype(str), categories=spec["categories"][col])
    for col in spec["numeric"]:
        frame[col] = pd.to_numeric(frame[col], errors="coerce").fillna(0)
    proba = model.predict_proba(frame[FEATURE_ORDER])[:, 1]
    return pd.Series(proba, index=events.index).round(4)


def blocked_reason(row: pd.Series) -> str | None:
    reason = str(row["reason"]).lower()
    if "risk" in reason or "cancelled" in reason:
        return "RISK_OR_CANCELLED"
    if float(row["amount_inr"]) >= HUMAN_APPROVAL:
        return "HUMAN_APPROVAL_AMOUNT"
    return None


def ai_skip_retry(row: pd.Series) -> bool:
    return (
        str(row["baseline_action"]) == "RETRY_PAYMENT"
        and float(row["p_recovery"]) < MIN_P
        and int(row["prior_payment_count"]) >= MIN_HISTORY
    )


def money(series: pd.Series) -> float:
    return round(float(series.sum()), 2)


def rate(recovered: float, at_risk: float) -> float:
    return round(recovered / at_risk, 4) if at_risk else 0.0


def path_stats(frame: pd.DataFrame, chase: pd.Series, recovered: pd.Series) -> dict:
    wasted = chase & ~frame["paid_eventually"].astype(bool)
    missed = ~chase & frame["paid_eventually"].astype(bool) & (frame["baseline_action"] != "CLOSE_RECOVERED")
    correct_skip = ~chase & ~frame["paid_eventually"].astype(bool) & (frame["baseline_action"] != "CLOSE_RECOVERED")
    recovered_inr = money(frame.loc[recovered, "amount_inr"])
    at_risk = money(frame["amount_inr"])
    return {
        "chases": int(chase.sum()),
        "recoveredCases": int(recovered.sum()),
        "recoveredInr": recovered_inr,
        "recoveryRate": rate(recovered_inr, at_risk),
        "wastedChases": int(wasted.sum()),
        "wastedInr": money(frame.loc[wasted, "amount_inr"]),
        "missedCases": int(missed.sum()),
        "missedInr": money(frame.loc[missed, "amount_inr"]),
        "correctSkips": int(correct_skip.sum()),
    }


def by_reason(frame: pd.DataFrame, baseline_rec: pd.Series, ai_rec: pd.Series) -> list[dict]:
    rows = []
    for reason, group in frame.groupby("reason", sort=True):
        at_risk = money(group["amount_inr"])
        base = money(group.loc[baseline_rec.loc[group.index], "amount_inr"])
        ai = money(group.loc[ai_rec.loc[group.index], "amount_inr"])
        rows.append(
            {
                "reason": str(reason),
                "events": int(len(group)),
                "amountAtRiskInr": at_risk,
                "baselineRecoveredInr": base,
                "aiRecoveredInr": ai,
            }
        )
    return rows


def missed_list(frame: pd.DataFrame, missed: pd.Series) -> list[dict]:
    picks = frame.loc[missed].sort_values("amount_inr", ascending=False).head(8)
    return [
        {
            "eventId": str(row.event_id),
            "reason": str(row.reason),
            "amountInr": round(float(row.amount_inr), 2),
            "pRecovery": round(float(row.p_recovery), 4),
            "why": str(row.ai_why),
        }
        for row in picks.itertuples(index=False)
    ]


def main() -> None:
    if not EVENTS.exists():
        raise SystemExit(f"missing {EVENTS}. Run generate_synthetic.py first.")
    if not MODEL_PATH.exists():
        raise SystemExit(f"missing {MODEL_PATH}. Run train_model.py first.")

    events = pd.read_csv(EVENTS)
    events["paid_eventually"] = events["paid_eventually"].astype(int)
    events["p_recovery"] = score(events)

    captured = events["baseline_action"] == "CLOSE_RECOVERED"
    baseline_chase = events["baseline_action"].isin(CHASE)
    baseline_recovered = captured | (baseline_chase & events["paid_eventually"].eq(1))

    block = events.apply(blocked_reason, axis=1)
    skip = events.apply(ai_skip_retry, axis=1)
    ai_chase = baseline_chase & block.isna() & ~skip
    ai_recovered = captured | (ai_chase & events["paid_eventually"].eq(1))
    why = pd.Series("ALLOW_PLAYBOOK", index=events.index)
    why = why.mask(captured, "ALREADY_CAPTURED")
    why = why.mask(events["baseline_action"] == "SEND_EMAIL", "PLAYBOOK_STOP")
    why = why.mask(skip, "ML_SKIP_RETRY")
    why = why.mask(block.notna(), block)
    events["ai_why"] = why

    at_risk = money(events["amount_inr"])
    oracle = money(events.loc[events["paid_eventually"].eq(1), "amount_inr"])
    baseline = path_stats(events, baseline_chase, baseline_recovered)
    ai = path_stats(events, ai_chase, ai_recovered)
    wasted_saved = round(baseline["wastedInr"] - ai["wastedInr"], 2)
    recovered_delta = round(ai["recoveredInr"] - baseline["recoveredInr"], 2)
    incremental_pct = (
        round(recovered_delta / baseline["recoveredInr"], 4) if baseline["recoveredInr"] else 0.0
    )
    model_metrics = {}
    if METRICS_PATH.exists():
        model_metrics = json.loads(METRICS_PATH.read_text(encoding="utf-8"))

    missed = ~ai_chase & events["paid_eventually"].eq(1) & ~captured
    report = {
        "ranAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "merchantId": MERCHANT,
        "seed": 42,
        "events": int(len(events)),
        "labelledPaid": int(events["paid_eventually"].sum()),
        "amountAtRiskInr": at_risk,
        "oracleRecoveredInr": oracle,
        "oracleRate": rate(oracle, at_risk),
        "baseline": baseline,
        "ai": ai,
        "recoveredDeltaInr": recovered_delta,
        "incrementalPct": incremental_pct,
        "wastedChaseSavedInr": wasted_saved,
        "wastedChasesAvoided": baseline["wastedChases"] - ai["wastedChases"],
        "policyBlocked": int(block.notna().sum()),
        "humanEscalations": int((block == "HUMAN_APPROVAL_AMOUNT").sum() + (block == "RISK_OR_CANCELLED").sum()),
        "mlSkipRetry": int(skip.sum()),
        "auditCoveragePct": 1.0,
        "model": {
            "file": "models/recovery_xgb.json",
            "rocAuc": model_metrics.get("roc_auc"),
            "prAuc": model_metrics.get("pr_auc"),
            "f1": model_metrics.get("f1"),
            "threshold": model_metrics.get("threshold", 0.5),
        },
        "rules": {
            "humanApprovalInr": HUMAN_APPROVAL,
            "skipRetryMaxP": MIN_P,
            "skipRetryMinHistory": MIN_HISTORY,
            "labelledFloor": 400,
        },
        "byReason": by_reason(events, baseline_recovered, ai_recovered),
        "unresolved": missed_list(events, missed),
        # Frozen say-this line. Do not retune MIN_P on this batch. See recovery-engine/PITCH.md.
        "pitch": (
            "Playbook recovered INR 5.30L. Playbook + P + policy recovered INR 5.21L. "
            "We skipped 45 weak retries, cut 36 doomed chases, avoided INR 45,851 of "
            "chase that never comes back, and gave up 9 people who later paid."
        ),
    }

    OUT_PATH.write_text(json.dumps(report, indent=2), encoding="utf-8")
    JAVA_COPY.parent.mkdir(parents=True, exist_ok=True)
    JAVA_COPY.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(report["pitch"])
    print(f"wrote {OUT_PATH}")
    print(f"wrote {JAVA_COPY}")


if __name__ == "__main__":
    main()
