from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

AllowedAction = Literal[
    "DELAYED_RETRY",
    "SKIP_EXTRA_RETRY",
    "SEND_PAYMENT_LINK",
    "REQUEST_PROMISE_TO_PAY",
    "DO_NOT_RETRY",
    "NO_ACTION",
]


class ProposeRequest(BaseModel):
    case_id: str | None = Field(default=None, alias="caseId")
    reason: str | None = None
    source: str = "PAYMENT"
    priority: str = "MEDIUM"
    payment_method: str = Field(default="card", alias="paymentMethod")
    amount_inr: float | None = Field(default=None, alias="amountInr")
    retry_count: int = Field(default=0, alias="retryCount")
    hours_since_fail: int = Field(default=0, alias="hoursSinceFail")
    historical_recovery_rate: float = Field(default=0, alias="historicalRecoveryRate")
    retry_history_count: int = Field(default=0, alias="retryHistoryCount")
    payment_success_rate: float | None = Field(default=None, alias="paymentSuccessRate")
    payment_failure_rate: float | None = Field(default=None, alias="paymentFailureRate")
    avg_payment_delay: float = Field(default=0, alias="avgPaymentDelay")
    subscription_age_months: int = Field(default=0, alias="subscriptionAgeMonths")
    lifetime_value: float | None = Field(default=None, alias="lifetimeValue")
    avg_order_value: float | None = Field(default=None, alias="avgOrderValue")
    days_since_last_activity: int = Field(default=0, alias="daysSinceLastActivity")
    history_payment_count: int = Field(default=0, alias="historyPaymentCount")

    model_config = {"populate_by_name": True}


class CaseProposal(BaseModel):
    case_id: str | None = Field(default=None, alias="caseId")
    diagnosis: str
    reasoning: str
    recommended_action: AllowedAction = Field(alias="recommendedAction")
    default_playbook_action: AllowedAction = Field(alias="defaultPlaybookAction")
    deviates_from_playbook: bool = Field(alias="deviatesFromPlaybook")
    confidence: float
    ml_score: float | None = Field(default=None, alias="mlScore")
    escalate: bool
    actions_available: list[str] = Field(default_factory=lambda: ["propose"], alias="actionsAvailable")
    executes: bool = False
    model: str = "qwen2.5-coder:7b"
    fallback_used: bool = Field(default=False, alias="fallbackUsed")
    # Compat aliases for older thin client / pitch slides
    recovery_probability: float | None = Field(default=None, alias="recoveryProbability")
    reason: str | None = None
    recommended_action_legacy: str | None = Field(default=None, alias="recommendedActionLegacy")

    model_config = {"populate_by_name": True}


class OpsBriefingRequest(BaseModel):
    window_hours: int | None = Field(default=None, alias="windowHours")
    merchant_id: str | None = Field(default=None, alias="merchantId")

    model_config = {"populate_by_name": True}


class OpsPattern(BaseModel):
    severity: str
    pattern: str
    where: str
    count: int
    why: str
    proposed_solution: str = Field(alias="proposedSolution")
    related_case_ids: list[str] = Field(default_factory=list, alias="relatedCaseIds")

    model_config = {"populate_by_name": True}


class OpsBriefing(BaseModel):
    window_hours: int = Field(alias="windowHours")
    summary: str
    patterns: list[OpsPattern]
    metrics: dict
    actions_available: list[str] = Field(default_factory=lambda: ["propose"], alias="actionsAvailable")
    executes: bool = False
    fallback_used: bool = Field(default=False, alias="fallbackUsed")
    model: str = "qwen2.5-coder:7b"

    model_config = {"populate_by_name": True}
