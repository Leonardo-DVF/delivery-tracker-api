CREATE TABLE shipments (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES app_users(id),
    tracking_code VARCHAR(32) NOT NULL UNIQUE,
    origin_contact_name VARCHAR(100) NOT NULL,
    origin_postal_code VARCHAR(8) NOT NULL,
    origin_street VARCHAR(150) NOT NULL,
    origin_number VARCHAR(20) NOT NULL,
    origin_complement VARCHAR(100),
    origin_neighborhood VARCHAR(100) NOT NULL,
    origin_city VARCHAR(100) NOT NULL,
    origin_state VARCHAR(2) NOT NULL,
    destination_contact_name VARCHAR(100) NOT NULL,
    destination_postal_code VARCHAR(8) NOT NULL,
    destination_street VARCHAR(150) NOT NULL,
    destination_number VARCHAR(20) NOT NULL,
    destination_complement VARCHAR(100),
    destination_neighborhood VARCHAR(100) NOT NULL,
    destination_city VARCHAR(100) NOT NULL,
    destination_state VARCHAR(2) NOT NULL,
    weight_kg NUMERIC(7,3) NOT NULL CHECK (weight_kg > 0 AND weight_kg <= 1000),
    length_cm NUMERIC(6,2) NOT NULL CHECK (length_cm > 0 AND length_cm <= 1000),
    width_cm NUMERIC(6,2) NOT NULL CHECK (width_cm > 0 AND width_cm <= 1000),
    height_cm NUMERIC(6,2) NOT NULL CHECK (height_cm > 0 AND height_cm <= 1000),
    freight_amount NUMERIC(14,2) NOT NULL CHECK (freight_amount >= 0),
    status VARCHAR(30) NOT NULL CHECK (status IN ('CREATED','POSTED','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERY_ATTEMPT_FAILED','DELIVERED','RETURNED','CANCELED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (tracking_code ~ '^[0-9A-F]{32}$'),
    CHECK (updated_at >= created_at),
    CHECK (origin_postal_code ~ '^[0-9]{8}$'),
    CHECK (origin_state IN ('AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO')),
    CHECK (length(trim(origin_contact_name)) > 0 AND length(trim(origin_street)) > 0
        AND length(trim(origin_number)) > 0 AND length(trim(origin_neighborhood)) > 0 AND length(trim(origin_city)) > 0),
    CHECK (destination_postal_code ~ '^[0-9]{8}$'),
    CHECK (destination_state IN ('AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO')),
    CHECK (length(trim(destination_contact_name)) > 0 AND length(trim(destination_street)) > 0
        AND length(trim(destination_number)) > 0 AND length(trim(destination_neighborhood)) > 0 AND length(trim(destination_city)) > 0)
);

CREATE INDEX ix_shipments_customer_created ON shipments(customer_id, created_at DESC, id);
CREATE INDEX ix_shipments_status_created ON shipments(status, created_at DESC, id);

CREATE TABLE tracking_events (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipments(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('CREATED','POSTED','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERY_ATTEMPT_FAILED','DELIVERED','RETURNED','CANCELED')),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source VARCHAR(30) NOT NULL CHECK (length(trim(source)) > 0),
    location VARCHAR(150)
);

CREATE INDEX ix_tracking_events_shipment_time ON tracking_events(shipment_id, occurred_at, received_at);
