ALTER TABLE order_event_outbox ADD COLUMN lease_token varchar(36) NULL, ADD COLUMN lease_until datetime NULL;
