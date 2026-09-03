from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime config. Local defaults exist so the hackathon boots; production overrides via env."""

    ml_predict_url: str = "http://localhost:8001/predict"
    java_base_url: str = "http://localhost:8080"
    human_approval_amount: float = Field(default=80000.0, ge=0)
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "qwen2.5-coder:7b"
    ollama_timeout_seconds: float = Field(default=45.0, ge=1, le=120)
    ollama_probe_seconds: float = Field(default=2.0, ge=0.5, le=10)
    postgres_host: str = "localhost"
    postgres_port: int = Field(default=5432, ge=1, le=65535)
    postgres_db: str = "revenue_recovery"
    postgres_user: str = "postgres"
    postgres_password: str = "postgres"
    db_pool_min: int = Field(default=1, ge=1, le=8)
    db_pool_max: int = Field(default=8, ge=1, le=32)
    db_statement_timeout_ms: int = Field(default=5000, ge=500, le=30000)
    ops_window_hours: int = Field(default=6, ge=1, le=72)
    nsf_spike_threshold: int = Field(default=3, ge=1)
    checkout_spike_threshold: int = Field(default=3, ge=1)
    exclude_training_merchant: str = "acc_syn_training"
    log_level: str = "INFO"

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
