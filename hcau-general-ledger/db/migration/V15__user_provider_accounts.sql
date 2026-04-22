CREATE TABLE user_provider_accounts (
    id                  VARCHAR(40)  PRIMARY KEY,
    user_id             VARCHAR(40)  NOT NULL REFERENCES users(id),
    provider_code       VARCHAR(50)  NOT NULL,
    external_account_id VARCHAR(255) NOT NULL,
    status              VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, provider_code)
);

CREATE INDEX user_provider_accounts_user_idx ON user_provider_accounts(user_id);
