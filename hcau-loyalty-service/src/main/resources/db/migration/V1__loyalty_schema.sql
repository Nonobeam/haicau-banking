CREATE TABLE loyalty_formulas (
  formula_id      VARCHAR(36) PRIMARY KEY,
  event_type      VARCHAR(100) NOT NULL,
  country_code    VARCHAR(10)  NOT NULL,
  customer_type   VARCHAR(50)  NOT NULL,
  channel_source  VARCHAR(50)  NOT NULL,
  is_active       BOOLEAN      NOT NULL DEFAULT true,
  published_at    TIMESTAMPTZ,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (event_type, country_code, customer_type, channel_source)
);

CREATE TABLE loyalty_formula_nodes (
  node_id        VARCHAR(36) PRIMARY KEY,
  formula_id     VARCHAR(36) NOT NULL REFERENCES loyalty_formulas(formula_id),
  node_type      VARCHAR(20) NOT NULL,
  node_value     VARCHAR(100),
  parent_node_id VARCHAR(36),
  position       INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_nodes_formula ON loyalty_formula_nodes(formula_id);
CREATE INDEX idx_nodes_parent  ON loyalty_formula_nodes(parent_node_id);

CREATE TABLE loyalty_formula_variables (
  variable_id  VARCHAR(36) PRIMARY KEY,
  formula_id   VARCHAR(36) NOT NULL REFERENCES loyalty_formulas(formula_id),
  name         VARCHAR(100) NOT NULL,
  value        NUMERIC(18,8) NOT NULL,
  UNIQUE (formula_id, name)
);
