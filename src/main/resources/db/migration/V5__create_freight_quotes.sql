CREATE TABLE freight_quotes (
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES app_users(id),
    destination_postal_code VARCHAR(8) NOT NULL,
    weight_kg NUMERIC(7,3) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_freight_quotes_postal_code CHECK (destination_postal_code ~ '^[0-9]{8}$'),
    CONSTRAINT ck_freight_quotes_weight CHECK (weight_kg > 0 AND weight_kg <= 1000),
    CONSTRAINT ck_freight_quotes_amount CHECK (amount >= 0),
    CONSTRAINT ck_freight_quotes_expiration CHECK (expires_at > created_at)
);

CREATE INDEX ix_freight_quotes_requester_created ON freight_quotes(requester_id, created_at DESC);
