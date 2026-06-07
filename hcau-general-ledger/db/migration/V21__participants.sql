CREATE TABLE participants (
    id          VARCHAR(50)  NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    health_url  VARCHAR(500) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO participants (id, name, health_url, active, created_at) VALUES
  ('banking-reconcile', 'Banking Reconciliation Service', 'http://banking-reconcile:8080/health', true, now()),
  ('platform-service',  'Platform Service',               'http://platform-service:8080/health',  true, now());
