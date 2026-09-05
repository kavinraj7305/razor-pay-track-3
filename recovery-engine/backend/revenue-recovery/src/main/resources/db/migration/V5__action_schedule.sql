ALTER TABLE recovery_action
    ADD COLUMN wait_hours INT,
    ADD COLUMN schedule_label VARCHAR(40);
