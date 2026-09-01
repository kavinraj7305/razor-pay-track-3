"""Load the labelled synthetic batch into the live Postgres schema.

Merchant acc_syn_training is isolated from the 8-case demo (acc_test_recovery).
Ground truth is recovery_outcome.result (PAYMENT_RECOVERED vs RECOVERY_FAILED).
"""

from __future__ import annotations

import os
from datetime import datetime, timedelta

import pandas as pd
import psycopg

TRAINING_MERCHANT_ID = "acc_syn_training"
AS_OF = datetime(2026, 9, 1, 18, 0)


def connect() -> psycopg.Connection:
    return psycopg.connect(
        host=os.environ.get("POSTGRES_HOST", "localhost"),
        port=os.environ.get("POSTGRES_PORT", "5432"),
        dbname=os.environ.get("POSTGRES_DB", "revenue_recovery"),
        user=os.environ.get("POSTGRES_USER", "postgres"),
        password=os.environ.get("POSTGRES_PASSWORD", "postgres"),
    )


def _ts(value: str) -> datetime:
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is not None:
        return parsed.replace(tzinfo=None)
    return parsed


def _wipe(cur: psycopg.Cursor, merchant_id: str) -> None:
    cur.execute(
        """
        DELETE FROM recovery_outcome
        WHERE case_id IN (SELECT case_id FROM recovery_case WHERE merchant_id = %s)
        """,
        (merchant_id,),
    )
    cur.execute(
        """
        DELETE FROM recovery_action
        WHERE case_id IN (SELECT case_id FROM recovery_case WHERE merchant_id = %s)
        """,
        (merchant_id,),
    )
    cur.execute(
        """
        DELETE FROM audit_event
        WHERE case_id IN (SELECT case_id FROM recovery_case WHERE merchant_id = %s)
        """,
        (merchant_id,),
    )
    cur.execute(
        """
        DELETE FROM promise_to_pay
        WHERE case_id IN (SELECT case_id FROM recovery_case WHERE merchant_id = %s)
        """,
        (merchant_id,),
    )
    cur.execute("DELETE FROM recovery_case WHERE merchant_id = %s", (merchant_id,))
    cur.execute(
        """
        DELETE FROM payment_attempt
        WHERE payment_id IN (SELECT payment_id FROM payment WHERE merchant_id = %s)
        """,
        (merchant_id,),
    )
    cur.execute("DELETE FROM payment WHERE merchant_id = %s", (merchant_id,))
    cur.execute("DELETE FROM checkout_session WHERE merchant_id = %s", (merchant_id,))
    cur.execute("DELETE FROM invoice WHERE merchant_id = %s", (merchant_id,))
    cur.execute("DELETE FROM subscription WHERE merchant_id = %s", (merchant_id,))
    cur.execute("DELETE FROM customer WHERE merchant_id = %s", (merchant_id,))


def _upsert_merchant(cur: psycopg.Cursor) -> None:
    cur.execute(
        """
        INSERT INTO merchant (merchant_id, name, default_currency, status)
        VALUES (%s, %s, 'INR', 'ACTIVE')
        ON CONFLICT (merchant_id) DO NOTHING
        """,
        (TRAINING_MERCHANT_ID, "Synthetic training merchant"),
    )


def _insert_customers(cur: psycopg.Cursor, customers: pd.DataFrame, used_ids: set[str]) -> None:
    rows = []
    for _, customer in customers.iterrows():
        if customer["customer_id"] not in used_ids:
            continue
        n = int(str(customer["customer_id"]).split("_")[-1])
        rows.append(
            (
                customer["customer_id"],
                TRAINING_MERCHANT_ID,
                f"Syn Customer {n}",
                f"{customer['customer_id']}@syn.example.com",
                f"+91910{n:07d}"[:30],
                "ACTIVE",
            )
        )
    cur.executemany(
        """
        INSERT INTO customer (customer_id, merchant_id, name, email, phone, status)
        VALUES (%s, %s, %s, %s, %s, %s)
        """,
        rows,
    )


def _insert_history(cur: psycopg.Cursor, customers: pd.DataFrame, used_ids: set[str]) -> None:
    rows = []
    for _, customer in customers.iterrows():
        if customer["customer_id"] not in used_ids:
            continue
        aov = max(float(customer["avg_order_value_inr"]), 1.0)
        delay_h = max(float(customer["avg_payment_delay_hours"]), 0.5)
        last_at = AS_OF - timedelta(days=int(customer["days_since_last_activity"]))
        cust = customer["customer_id"]
        suffix = str(cust).split("_")[-1]
        n_success = int(customer["prior_success_count"])
        n_fail = int(customer["prior_failure_count"])
        total = n_success + n_fail
        statuses = ["SUCCESS"] * n_success + ["FAILED"] * n_fail
        for i, status in enumerate(statuses):
            created = last_at - timedelta(hours=delay_h * (total - 1 - i))
            rows.append(
                (
                    f"pay_h_{suffix}_{i + 1:02d}"[:50],
                    TRAINING_MERCHANT_ID,
                    cust,
                    round(aov, 2),
                    "INR",
                    status,
                    "card",
                    created,
                    created,
                )
            )
    if rows:
        cur.executemany(
            """
            INSERT INTO payment (
                payment_id, merchant_id, customer_id, amount, currency, status, payment_type,
                created_at, updated_at
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            """,
            rows,
        )


def _insert_source(cur: psycopg.Cursor, row: pd.Series, failed_at: datetime) -> None:
    source = row["source"]
    source_id = row["source_id"]
    amount = round(float(row["amount_inr"]), 2)
    paid = int(row["paid_eventually"]) == 1
    method = str(row["payment_method"])[:30]
    cust = row["customer_id"]
    if source == "PAYMENT":
        status = "SUCCESS" if row["reason"] == "captured" or paid else "FAILED"
        cur.execute(
            """
            INSERT INTO payment (
                payment_id, merchant_id, customer_id, amount, currency, status, payment_type,
                created_at, updated_at
            )
            VALUES (%s, %s, %s, %s, 'INR', %s, %s, %s, %s)
            """,
            (source_id, TRAINING_MERCHANT_ID, cust, amount, status, method, failed_at, failed_at),
        )
        return
    if source == "CHECKOUT_SESSION":
        status = "COMPLETED" if paid else "ABANDONED"
        cur.execute(
            """
            INSERT INTO checkout_session (
                checkout_session_id, merchant_id, customer_id, amount, currency, status,
                started_at, abandoned_at, completed_at
            )
            VALUES (%s, %s, %s, %s, 'INR', %s, %s, %s, %s)
            """,
            (
                source_id,
                TRAINING_MERCHANT_ID,
                cust,
                amount,
                status,
                failed_at,
                None if paid else failed_at,
                failed_at if paid else None,
            ),
        )
        return
    if source == "SUBSCRIPTION":
        sub_status = "ACTIVE" if paid else (
            "PAST_DUE" if row["reason"] == "subscription.pending" else "CANCELLED"
        )
        cur.execute(
            """
            INSERT INTO subscription (
                subscription_id, merchant_id, customer_id, amount, currency,
                billing_interval, status, created_at, updated_at
            )
            VALUES (%s, %s, %s, %s, 'INR', 'MONTHLY', %s, %s, %s)
            """,
            (source_id, TRAINING_MERCHANT_ID, cust, amount, sub_status, failed_at, failed_at),
        )
        return
    issued = failed_at - timedelta(days=14)
    inv_status = "PAID" if paid else "OVERDUE"
    paid_amt = amount if paid else 0.0
    cur.execute(
        """
        INSERT INTO invoice (
            invoice_id, merchant_id, customer_id, amount, amount_paid, currency,
            issued_at, due_date, status, created_at, updated_at
        )
        VALUES (%s, %s, %s, %s, %s, 'INR', %s, %s, %s, %s, %s)
        """,
        (
            source_id,
            TRAINING_MERCHANT_ID,
            cust,
            amount,
            paid_amt,
            issued,
            failed_at,
            inv_status,
            failed_at,
            failed_at,
        ),
    )


def seed(events: pd.DataFrame, customers: pd.DataFrame) -> dict[str, int]:
    used_ids = set(events["customer_id"].tolist())
    with connect() as conn:
        with conn.cursor() as cur:
            _wipe(cur, TRAINING_MERCHANT_ID)
            _upsert_merchant(cur)
            _insert_customers(cur, customers, used_ids)
            _insert_history(cur, customers, used_ids)
            for _, row in events.iterrows():
                failed_at = _ts(str(row["failed_at"]))
                paid = int(row["paid_eventually"]) == 1
                case_id = str(row["event_id"]).replace("evt_", "case_", 1)
                amount = round(float(row["amount_inr"]), 2)
                case_status = "RECOVERED" if paid else "FAILED"
                paid_after = row["paid_after_hours"]
                extra_hours = 24 if pd.isna(paid_after) else int(paid_after)
                closed_at = failed_at + timedelta(hours=extra_hours)
                _insert_source(cur, row, failed_at)
                cur.execute(
                    """
                    INSERT INTO recovery_case (
                        case_id, merchant_id, customer_id, source, source_id,
                        amount_at_risk, currency, reason, status, priority,
                        created_at, updated_at, closed_at
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, 'INR', %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        case_id,
                        TRAINING_MERCHANT_ID,
                        row["customer_id"],
                        row["source"],
                        row["source_id"],
                        amount,
                        row["reason"],
                        case_status,
                        row["priority"],
                        failed_at,
                        failed_at,
                        closed_at,
                    ),
                )
                action = str(row["baseline_action"])
                if action != "CLOSE_RECOVERED":
                    action_status = "CANCELLED" if action == "SEND_EMAIL" else "EXECUTED"
                    cur.execute(
                        """
                        INSERT INTO recovery_action (
                            action_id, case_id, action_type, status, attempt_number, reason, executed_at
                        )
                        VALUES (%s, %s, %s, %s, %s, %s, %s)
                        """,
                        (
                            str(row["event_id"]).replace("evt_", "act_", 1),
                            case_id,
                            action,
                            action_status,
                            max(int(row["retry_count"]), 0),
                            f"synthetic baseline {row['reason']}",
                            failed_at,
                        ),
                    )
                result = "PAYMENT_RECOVERED" if paid else "RECOVERY_FAILED"
                recovered = round(float(row["amount_recovered_inr"]), 2)
                cur.execute(
                    """
                    INSERT INTO recovery_outcome (
                        outcome_id, case_id, result, amount_recovered, currency,
                        resolution_reason, resolved_at
                    )
                    VALUES (%s, %s, %s, %s, 'INR', %s, %s)
                    """,
                    (
                        str(row["event_id"]).replace("evt_", "out_", 1),
                        case_id,
                        result,
                        recovered,
                        "synthetic ground truth",
                        closed_at,
                    ),
                )
            cur.execute(
                "SELECT COUNT(*) FROM recovery_case WHERE merchant_id = %s",
                (TRAINING_MERCHANT_ID,),
            )
            n_cases = int(cur.fetchone()[0])
            cur.execute(
                """
                SELECT COUNT(*) FROM recovery_outcome o
                JOIN recovery_case c ON c.case_id = o.case_id
                WHERE c.merchant_id = %s AND o.result = 'PAYMENT_RECOVERED'
                """,
                (TRAINING_MERCHANT_ID,),
            )
            n_paid = int(cur.fetchone()[0])
            cur.execute(
                "SELECT COUNT(*) FROM customer WHERE merchant_id = %s",
                (TRAINING_MERCHANT_ID,),
            )
            n_customers = int(cur.fetchone()[0])
            cur.execute(
                "SELECT COUNT(*) FROM payment WHERE merchant_id = %s",
                (TRAINING_MERCHANT_ID,),
            )
            n_payments = int(cur.fetchone()[0])
        conn.commit()
    return {
        "cases": n_cases,
        "paid": n_paid,
        "customers": n_customers,
        "payments": n_payments,
    }
