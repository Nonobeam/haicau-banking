CREATE TABLE loyalty_user_pts_accounts (
  user_id     VARCHAR(50) PRIMARY KEY,
  account_id  VARCHAR(50) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
