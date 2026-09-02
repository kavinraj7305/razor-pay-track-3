from fastapi import FastAPI
from pydantic import BaseModel, Field

from app.graph import run_agent

app = FastAPI(title="recovery-engine agent-service", version="0.2.0")


class ProposeRequest(BaseModel):
    reason: str
    source: str = "PAYMENT"
    priority: str = "MEDIUM"
    payment_method: str = Field(alias="paymentMethod", default="card")
    amount_inr: float = Field(alias="amountInr")
    retry_count: int = Field(alias="retryCount", default=0)
    hours_since_fail: int = Field(alias="hoursSinceFail", default=0)
    historical_recovery_rate: float = Field(alias="historicalRecoveryRate", default=0)
    retry_history_count: int = Field(alias="retryHistoryCount", default=0)
    payment_success_rate: float = Field(alias="paymentSuccessRate")
    payment_failure_rate: float = Field(alias="paymentFailureRate")
    avg_payment_delay: float = Field(alias="avgPaymentDelay", default=0)
    subscription_age_months: int = Field(alias="subscriptionAgeMonths", default=0)
    lifetime_value: float = Field(alias="lifetimeValue")
    avg_order_value: float = Field(alias="avgOrderValue")
    days_since_last_activity: int = Field(alias="daysSinceLastActivity", default=0)
    history_payment_count: int = Field(alias="historyPaymentCount", default=0)

    model_config = {"populate_by_name": True}


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "agent-service"}


@app.post("/propose")
def propose(body: ProposeRequest) -> dict:
    """Propose only. This service cannot charge, retry, or send a payment link."""
    payload = body.model_dump(by_alias=True)
    payload["reason"] = body.reason
    payload["source"] = body.source
    payload["priority"] = body.priority
    return run_agent(payload)
