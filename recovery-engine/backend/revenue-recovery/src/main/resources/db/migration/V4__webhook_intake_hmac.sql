-- Mark how a webhook arrived. Desk simulate skips HMAC. Real Razorpay POSTs do not.

ALTER TABLE webhook_event
    ALTER COLUMN event_id TYPE VARCHAR(80);

ALTER TABLE webhook_event
    ADD COLUMN intake VARCHAR(30) NOT NULL DEFAULT 'DESK_SIMULATE';

ALTER TABLE webhook_event
    ADD COLUMN signature_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE webhook_event
    ADD CONSTRAINT chk_webhook_intake
        CHECK (intake IN ('DESK_SIMULATE', 'HMAC_SIGNED'));

CREATE INDEX idx_webhook_intake
    ON webhook_event(intake, received_at DESC);
