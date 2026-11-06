CREATE TABLE freight_rates (
    id UUID PRIMARY KEY,
    postal_start INTEGER NOT NULL,
    postal_end INTEGER NOT NULL,
    weight_min NUMERIC(7, 3) NOT NULL,
    weight_max NUMERIC(7, 3) NOT NULL,
    base_price NUMERIC(10, 2) NOT NULL,
    price_per_kg NUMERIC(10, 2) NOT NULL,
    CONSTRAINT ck_freight_rates_postal_range
        CHECK (postal_start >= 0 AND postal_end <= 99999999 AND postal_start <= postal_end),
    CONSTRAINT ck_freight_rates_weight_range
        CHECK (weight_min >= 0 AND weight_max > weight_min AND weight_max <= 1000),
    CONSTRAINT ck_freight_rates_nonnegative_prices
        CHECK (base_price >= 0 AND price_per_kg >= 0),
    CONSTRAINT ex_freight_rates_no_overlap EXCLUDE USING GIST (
        int4range(postal_start, postal_end, '[]') WITH &&,
        numrange(weight_min, weight_max, '(]') WITH &&
    )
);
