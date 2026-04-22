CREATE TABLE providers (
    code        VARCHAR(50)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    credentials JSONB        NOT NULL DEFAULT '{}',
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO providers (code, name, credentials, status) VALUES
('stripe', 'Stripe', '{}', 'ACTIVE');
