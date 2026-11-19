-- Carrier webhooks are delivered at least once, so the carrier's own event id is stored and made unique:
-- a redelivered event hits the constraint instead of creating a duplicate history entry.
-- There is a single fictional carrier, so the id is unique on its own; several carriers would need (carrier, id).
ALTER TABLE tracking_events ADD COLUMN carrier_event_id VARCHAR(100);
ALTER TABLE tracking_events ADD CONSTRAINT uq_tracking_events_carrier_event UNIQUE (carrier_event_id);
ALTER TABLE tracking_events ADD CONSTRAINT ck_tracking_events_carrier_event_id
    CHECK (carrier_event_id ~ '^[A-Za-z0-9._:-]{1,100}$');
-- Every carrier event has an id, and only carrier events have one.
ALTER TABLE tracking_events ADD CONSTRAINT ck_tracking_events_carrier_source
    CHECK ((source = 'CARRIER') = (carrier_event_id IS NOT NULL));
