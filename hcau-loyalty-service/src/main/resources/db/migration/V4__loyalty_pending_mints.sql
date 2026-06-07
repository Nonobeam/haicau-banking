CREATE TABLE loyalty_pending_mints (
  id               VARCHAR(36) PRIMARY KEY,
  event_id         VARCHAR(200) NOT NULL UNIQUE,
  user_id          VARCHAR(50)  NOT NULL,
  points           BIGINT       NOT NULL,
  trace_id         VARCHAR(200) NOT NULL,
  idempotency_key  VARCHAR(250) NOT NULL UNIQUE,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  flushed_at       TIMESTAMPTZ
);
CREATE INDEX idx_pending_mints_unflushed ON loyalty_pending_mints(created_at) WHERE flushed_at IS NULL;
