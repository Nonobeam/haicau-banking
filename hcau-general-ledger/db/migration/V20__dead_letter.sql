CREATE TABLE saga_dead_letter (
    id             BIGSERIAL      PRIMARY KEY,
    saga_id        VARCHAR(40)    NOT NULL UNIQUE,
    saga_type      VARCHAR(50)    NOT NULL,
    user_id        VARCHAR(40)    NOT NULL,
    full_payload   JSONB          NOT NULL DEFAULT '{}',
    state_history  JSONB          NOT NULL DEFAULT '[]',
    failure_reason TEXT           NOT NULL,
    retry_count    INT            NOT NULL DEFAULT 0,
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN ('PENDING','REPLAYING','RESOLVED','ARCHIVED')),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_dead_letter_status    ON saga_dead_letter (status);
CREATE INDEX idx_dead_letter_saga_type ON saga_dead_letter (saga_type, status);
CREATE INDEX idx_dead_letter_user      ON saga_dead_letter (user_id, created_at);
CREATE INDEX idx_dead_letter_created   ON saga_dead_letter (created_at);
CREATE INDEX idx_dead_letter_retention ON saga_dead_letter (created_at)
    WHERE status IN ('RESOLVED', 'ARCHIVED');
