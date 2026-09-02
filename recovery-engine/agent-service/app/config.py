from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    kafka_bootstrap_servers: str = "localhost:9092"
    redis_url: str = "redis://localhost:6379/0"
    anthropic_api_key: str = ""
    ml_predict_url: str = "http://localhost:8001/predict"
    human_approval_amount: float = 80000.0

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
