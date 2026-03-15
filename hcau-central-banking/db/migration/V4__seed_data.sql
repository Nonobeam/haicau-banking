-- V4 migration — Reference data

-- System User
INSERT INTO user_types (id, name, description) VALUES ('user_type_0000000000000000000000SYSTEM', 'SYSTEM', 'System accounts');
INSERT INTO user_types (id, name, description) VALUES ('user_type_000000000000000000000000USER', 'USER', 'End users');

INSERT INTO users (id, name, user_type) VALUES ('user_00000000000000000000000000SYSTEM', 'SYSTEM', 'user_type_0000000000000000000000SYSTEM');

-- Domain & Bucket
INSERT INTO domain_types (id, name, description) VALUES ('domain_000000000000000000000000FIAT', 'FIAT', 'Fiat currencies');
INSERT INTO domain_types (id, name, description) VALUES ('domain_0000000000000000000000CRYPTO', 'CRYPTO', 'Cryptocurrencies');

INSERT INTO bucket_types (id, name, description) VALUES ('bucket_000000000000000000000AVAILABLE', 'AVAILABLE', 'Available balance');
INSERT INTO bucket_types (id, name, description) VALUES ('bucket_0000000000000000000000RESERVED', 'RESERVED', 'Reserved balance');

-- Transaction types
INSERT INTO transaction_types (id, name, description) VALUES ('ttype_000000000000000INBOUND_DEPOSIT', 'INBOUND_DEPOSIT', 'Inbound deposit via external provider');
INSERT INTO transaction_types (id, name, description) VALUES ('ttype_00000000000000000INBOUND_SWEEP', 'INBOUND_SWEEP', 'Sweep from reserved to available');

-- Job config defaults
INSERT INTO job_config (key, value, description) VALUES 
('session_timeout_minutes', '30', 'PENDING deposits with session ID expire after this many minutes'),
('no_session_timeout_minutes', '5', 'PENDING deposits without a session ID expire after this many minutes'),
('expiry_check_interval_seconds', '60', 'How often the expiry job runs'),
('sweep_interval_seconds', '120', 'How often the sweep job runs');

-- Buffer account for USD
INSERT INTO accounts (id, owner_id, domain, currency, bucket_type, status) VALUES 
('acct_0000000000000000000BUFFER_USD', 'user_00000000000000000000000000SYSTEM', 'domain_000000000000000000000000FIAT', 'USD', 'bucket_000000000000000000000AVAILABLE', 'ACTIVE');
