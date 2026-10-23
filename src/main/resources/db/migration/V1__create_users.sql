CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_app_users_email_normalized CHECK (email = lower(trim(email)) AND length(email) > 0),
    CONSTRAINT ck_app_users_password_hash_not_blank CHECK (length(trim(password_hash)) > 0),
    CONSTRAINT ck_app_users_role CHECK (role IN ('CUSTOMER', 'OPERATOR'))
);
