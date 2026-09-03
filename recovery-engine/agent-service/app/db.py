"""Read-only Postgres access. Agent never writes recovery rows — Java owns money mutations."""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager

import psycopg
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from app.config import settings
from app.log import log

_pool: ConnectionPool | None = None


def _conninfo() -> str:
    return (
        f"host={settings.postgres_host} "
        f"port={settings.postgres_port} "
        f"dbname={settings.postgres_db} "
        f"user={settings.postgres_user} "
        f"password={settings.postgres_password}"
    )


def init_pool() -> None:
    global _pool
    if _pool is not None:
        return
    _pool = ConnectionPool(
        conninfo=_conninfo(),
        min_size=settings.db_pool_min,
        max_size=settings.db_pool_max,
        kwargs={"row_factory": dict_row, "autocommit": True},
        open=True,
    )
    log.info("postgres pool opened min=%s max=%s", settings.db_pool_min, settings.db_pool_max)


def close_pool() -> None:
    global _pool
    if _pool is not None:
        _pool.close()
        _pool = None
        log.info("postgres pool closed")


@contextmanager
def connect() -> Iterator[psycopg.Connection]:
    if _pool is None:
        init_pool()
    assert _pool is not None
    with _pool.connection() as conn:
        conn.execute("SET default_transaction_read_only = on")
        conn.execute(f"SET statement_timeout = {int(settings.db_statement_timeout_ms)}")
        yield conn


def ping() -> bool:
    try:
        with connect() as conn, conn.cursor() as cur:
            cur.execute("SELECT 1")
            cur.fetchone()
        return True
    except psycopg.Error as exc:
        log.warning("postgres ping failed: %s", exc)
        return False
