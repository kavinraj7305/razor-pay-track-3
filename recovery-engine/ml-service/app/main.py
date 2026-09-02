from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from app.scoring import MODEL_PATH, predict_proba

app = FastAPI(title="recovery-engine ml-service", version="0.2.0")


class PredictRequest(BaseModel):
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


class PredictResponse(BaseModel):
    recovery_probability: float = Field(serialization_alias="recoveryProbability")
    label: str


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "ml-service"}


@app.post("/predict", response_model=PredictResponse)
def predict(body: PredictRequest) -> PredictResponse:
    if not MODEL_PATH.exists():
        raise HTTPException(
            status_code=503,
            detail="Model not trained. Run: uv run python scripts/train_model.py",
        )
    probability = predict_proba(body.model_dump())
    return PredictResponse(
        recovery_probability=probability,
        label="LIKELY" if probability >= 0.5 else "UNLIKELY",
    )
