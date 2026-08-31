"""Entry point kept for `uv run`. Prefer: uv run uvicorn app.main:app --port 8001"""

from app.main import app

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8001)
