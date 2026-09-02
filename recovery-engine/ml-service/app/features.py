"""Shared columns for train and POST /predict. Keep in lockstep."""

from __future__ import annotations

CATEGORICAL = ["reason", "source", "priority", "payment_method"]
NUMERIC = [
    "amount_inr",
    "retry_count",
    "hours_since_fail",
    "historical_recovery_rate",
    "retry_history_count",
    "payment_success_rate",
    "payment_failure_rate",
    "avg_payment_delay",
    "subscription_age_months",
    "lifetime_value",
    "avg_order_value",
    "days_since_last_activity",
    "history_payment_count",
]
LABEL = "paid_eventually"
FEATURE_ORDER = CATEGORICAL + NUMERIC
