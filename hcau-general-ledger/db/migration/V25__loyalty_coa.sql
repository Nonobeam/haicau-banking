-- V25: Loyalty CoA — PTS currency, system accounts, transaction types

-- Register PTS currency
INSERT INTO currencies (code, name, decimal_places, display_decimals, is_active)
VALUES ('PTS', 'Loyalty Points', 0, 0, true)
ON CONFLICT (code) DO NOTHING;

-- GL system accounts for PTS
INSERT INTO accounts (id, owner_id, coa_path, ledger, status)
VALUES
  ('acct_00000000000EXP_SYSTEM_LOYALTY', 'user_00000000000000000000000000SYSTEM', 'expense:system:loyalty', 'GL', 'ACTIVE'),
  ('acct_0000000000000PTS_CTRL_RESERVE',  'user_00000000000000000000000000SYSTEM', 'pts:control:reserve',    'GL', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- Seed zero PTS balance snapshots for both system accounts
INSERT INTO balance_snapshots (account_id, currency, balance)
VALUES
  ('acct_00000000000EXP_SYSTEM_LOYALTY', 'PTS', 0),
  ('acct_0000000000000PTS_CTRL_RESERVE',  'PTS', 0)
ON CONFLICT DO NOTHING;

-- Transaction types for loyalty
INSERT INTO transaction_types (id, name, description, gl_required, sub_required)
VALUES
  ('ttype_0000000000000LOYALTY_MINT',   'LOYALTY_MINT',   'Loyalty point mint: expense debited, user PTS credited',       true, null),
  ('ttype_00000000000LOYALTY_REDEEM',   'LOYALTY_REDEEM', 'Loyalty point redemption: user PTS debited, reserve credited', true, null)
ON CONFLICT (id) DO NOTHING;
