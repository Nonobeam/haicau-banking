CREATE TABLE saga_instances (
    saga_id              VARCHAR(40)   NOT NULL PRIMARY KEY,
    seq                  BIGSERIAL,
    saga_type            VARCHAR(50)   NOT NULL,
    user_id              VARCHAR(40)   NOT NULL,
    current_state        VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    payload              JSONB         NOT NULL DEFAULT '{}',
    sequence_number      BIGINT        NOT NULL DEFAULT 0,
    retry_count          INT           NOT NULL DEFAULT 0,
    retry_eligible_after TIMESTAMPTZ   NULL,
    parked_on_participant VARCHAR(50)  NULL,
    failure_reason       TEXT          NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_saga_seq           ON saga_instances (seq);
CREATE INDEX idx_saga_user_state    ON saga_instances (user_id, current_state);
CREATE INDEX idx_saga_state_retry   ON saga_instances (current_state, retry_eligible_after)
    WHERE current_state = 'PARKED';
CREATE INDEX idx_saga_created       ON saga_instances (created_at);
CREATE INDEX idx_saga_user_created  ON saga_instances (user_id, created_at DESC);

CREATE TABLE saga_rules (
    saga_type        VARCHAR(50)  NOT NULL,
    failure_type     VARCHAR(50)  NOT NULL,
    retry_count_lte  INT          NOT NULL,
    decision         VARCHAR(20)  NOT NULL CHECK (decision IN ('RETRY','PARK','UNDO','DEAD_LETTER')),
    retry_delay_ms   INT          NULL,
    PRIMARY KEY (saga_type, failure_type, retry_count_lte)
);

INSERT INTO saga_rules (saga_type, failure_type, retry_count_lte, decision, retry_delay_ms) VALUES
  ('TRANSFER', 'TIMEOUT',        3, 'RETRY',       1000),
  ('TRANSFER', 'TIMEOUT',        5, 'PARK',         NULL),
  ('TRANSFER', 'TIMEOUT',       99, 'DEAD_LETTER',  NULL),
  ('TRANSFER', 'CLIENT_ERROR',   0, 'DEAD_LETTER',  NULL),
  ('TRANSFER', 'INTERNAL_ERROR', 3, 'RETRY',        500),
  ('TRANSFER', 'INTERNAL_ERROR', 99,'DEAD_LETTER',  NULL);
