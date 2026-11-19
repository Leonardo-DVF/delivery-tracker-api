-- History is listed in the order events were applied (received_at). Carrier clocks may disagree with ours,
-- so ordering by occurred_at could show a status before the one that preceded it.
CREATE INDEX ix_tracking_events_shipment_received ON tracking_events(shipment_id, received_at, occurred_at);
DROP INDEX ix_tracking_events_shipment_time;
