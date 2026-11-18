-- Shipments are only created from a stored freight quote. The application has not been deployed,
-- so no existing shipment rows need a backfill and the column can be NOT NULL immediately.
ALTER TABLE shipments ADD COLUMN quote_id UUID NOT NULL;
ALTER TABLE shipments ADD CONSTRAINT fk_shipments_quote FOREIGN KEY (quote_id) REFERENCES freight_quotes(id);
ALTER TABLE shipments ADD CONSTRAINT uq_shipments_quote UNIQUE (quote_id);

-- Align the lifecycle with CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED, or CANCELED.
ALTER TABLE shipments DROP CONSTRAINT shipments_status_check;
ALTER TABLE shipments ADD CONSTRAINT ck_shipments_status
    CHECK (status IN ('CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','CANCELED'));

ALTER TABLE tracking_events DROP CONSTRAINT tracking_events_status_check;
ALTER TABLE tracking_events ADD CONSTRAINT ck_tracking_events_status
    CHECK (status IN ('CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','CANCELED'));
