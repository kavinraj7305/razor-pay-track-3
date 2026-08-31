-- Inbox for Razorpay (and later other providers). Does not alter the 13 Phase 1 tables.

CREATE TABLE webhook_event (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(50) NOT NULL UNIQUE,
    provider        VARCHAR(30) NOT NULL DEFAULT 'RAZORPAY',
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    received_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_webhook_event_type
    ON webhook_event(event_type);

CREATE INDEX idx_webhook_received_at
    ON webhook_event(received_at);

CREATE INDEX idx_webhook_processed
    ON webhook_event(processed);
