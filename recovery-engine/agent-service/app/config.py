from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ml_predict_url: str = "http://localhost:8001/predict"
    java_base_url: str = "http://localhost:8080"
    human_approval_amount: float = 80000.0
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "qwen2.5-coder:7b"
    ollama_timeout_seconds: float = 45.0
    postgres_host: str = "localhost"
    postgres_port: int = 5432
    postgres_db: str = "revenue_recovery"
    postgres_user: str = "postgres"
    postgres_password: str = "postgres"
    ops_window_hours: int = 6
    nsf_spike_threshold: int = 3
    checkout_spike_threshold: int = 3
    exclude_training_merchant: str = "acc_syn_training"

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
