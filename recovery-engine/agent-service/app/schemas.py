from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, field_validator, model_validator

AllowedAction = Literal[
    "DELAYED_RETRY",
    "SKIP_EXTRA_RETRY",
    "SEND_PAYMENT_LINK",
    "REQUEST_PROMISE_TO_PAY",
    "DO_NOT_RETRY",
    "NO_ACTION",
]


class ProposeRequest(BaseModel):
    case_id: str | None = Field(default=None, alias="caseId", max_length=80)
    reason: str | None = Field(default=None, max_length=100)
    source: str = Field(default="PAYMENT", max_length=50)
    priority: str = Field(default="MEDIUM", max_length=20)
    payment_method: str = Field(default="card", alias="paymentMethod", max_length=40)
    amount_inr: float | None = Field(default=None, alias="amountInr", ge=0)
    retry_count: int = Field(default=0, alias="retryCount", ge=0, le=100)
    hours_since_fail: int = Field(default=0, alias="hoursSinceFail", ge=0, le=8760)
    historical_recovery_rate: float = Field(default=0, alias="historicalRecoveryRate", ge=0, le=1)
    retry_history_count: int = Field(default=0, alias="retryHistoryCount", ge=0, le=100)
    payment_success_rate: float | None = Field(default=None, alias="paymentSuccessRate", ge=0, le=1)
    payment_failure_rate: float | None = Field(default=None, alias="paymentFailureRate", ge=0, le=1)
    avg_payment_delay: float = Field(default=0, alias="avgPaymentDelay", ge=0)
    subscription_age_months: int = Field(default=0, alias="subscriptionAgeMonths", ge=0, le=600)
    lifetime_value: float | None = Field(default=None, alias="lifetimeValue", ge=0)
    avg_order_value: float | None = Field(default=None, alias="avgOrderValue", ge=0)
    days_since_last_activity: int = Field(default=0, alias="daysSinceLastActivity", ge=0, le=3650)
    history_payment_count: int = Field(default=0, alias="historyPaymentCount", ge=0, le=100000)

    model_config = {"populate_by_name": True, "extra": "ignore"}


class CaseProposal(BaseModel):
    case_id: str | None = Field(default=None, alias="caseId")
    diagnosis: str = Field(max_length=80)
    reasoning: str = Field(max_length=2000)
    recommended_action: AllowedAction = Field(alias="recommendedAction")
    default_playbook_action: AllowedAction = Field(alias="defaultPlaybookAction")
    deviates_from_playbook: bool = Field(alias="deviatesFromPlaybook")
    confidence: float = Field(ge=0, le=0.99)
    ml_score: float | None = Field(default=None, alias="mlScore")
    escalate: bool
    actions_available: list[str] = Field(default_factory=lambda: ["propose"], alias="actionsAvailable")
    executes: bool = False
    model: str = "qwen2.5-coder:7b"
    fallback_used: bool = Field(default=False, alias="fallbackUsed")
    recovery_probability: float | None = Field(default=None, alias="recoveryProbability")
    reason: str | None = None

    model_config = {"populate_by_name": True, "extra": "ignore"}

    @field_validator("ml_score", "recovery_probability", mode="before")
    @classmethod
    def _clip_optional_score(cls, value: object) -> object:
        if value is None:
            return None
        return max(0.0, min(float(value), 1.0))

    @model_validator(mode="after")
    def force_propose_only(self) -> CaseProposal:
        """Last line of defense: the LLM cannot make this agent execute money."""
        self.executes = False
        self.actions_available = ["propose"]
        self.deviates_from_playbook = self.recommended_action != self.default_playbook_action
        return self


class OpsBriefingRequest(BaseModel):
    window_hours: int | None = Field(default=None, alias="windowHours", ge=1, le=72)
    merchant_id: str | None = Field(default=None, alias="merchantId", max_length=50)

    model_config = {"populate_by_name": True, "extra": "ignore"}


class OpsPattern(BaseModel):
    severity: str
    pattern: str
    where: str
    count: int = Field(ge=0)
    why: str
    proposed_solution: str = Field(alias="proposedSolution")
    related_case_ids: list[str] = Field(default_factory=list, alias="relatedCaseIds")

    model_config = {"populate_by_name": True, "extra": "ignore"}

    @field_validator("related_case_ids", mode="before")
    @classmethod
    def _cap_ids(cls, value: object) -> object:
        if not isinstance(value, list):
            return []
        return [str(item) for item in value[:8]]


class OpsBriefing(BaseModel):
    window_hours: int = Field(alias="windowHours", ge=1, le=72)
    summary: str = Field(max_length=2000)
    patterns: list[OpsPattern]
    metrics: dict
    actions_available: list[str] = Field(default_factory=lambda: ["propose"], alias="actionsAvailable")
    executes: bool = False
    fallback_used: bool = Field(default=False, alias="fallbackUsed")
    model: str = "qwen2.5-coder:7b"

    model_config = {"populate_by_name": True, "extra": "ignore"}

    @model_validator(mode="after")
    def force_propose_only(self) -> OpsBriefing:
        self.executes = False
        self.actions_available = ["propose"]
        return self
