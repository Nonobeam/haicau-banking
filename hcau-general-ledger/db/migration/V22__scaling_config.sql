CREATE TABLE scaling_config (
    saga_type             VARCHAR(50)  NOT NULL PRIMARY KEY,
    auto_scaling_enabled  BOOLEAN      NOT NULL DEFAULT false,
    max_instances         INT          NOT NULL DEFAULT 3,
    scaling_api_endpoint  VARCHAR(500),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO scaling_config (saga_type, auto_scaling_enabled, max_instances, scaling_api_endpoint, created_at) VALUES
  ('TRANSFER', false, 3, NULL, now()),
  ('REFUND',   false, 1, NULL, now()),
  ('PAYMENT',  false, 2, NULL, now());
