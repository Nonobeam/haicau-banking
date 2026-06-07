CREATE TABLE loyalty_processed_events (
  event_id     VARCHAR(200) PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
