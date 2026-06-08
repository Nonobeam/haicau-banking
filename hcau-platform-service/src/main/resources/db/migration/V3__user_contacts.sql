CREATE TABLE user_contacts (
    user_id        VARCHAR(40)   PRIMARY KEY,
    email          VARCHAR(255)  NULL,
    telegram_chat_id VARCHAR(100) NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_contacts_email ON user_contacts (email) WHERE email IS NOT NULL;
