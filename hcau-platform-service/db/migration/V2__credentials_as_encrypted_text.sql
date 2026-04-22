-- Change credentials from JSONB (with NOT NULL) to nullable TEXT for encrypted blob storage.
ALTER TABLE providers ALTER COLUMN credentials DROP NOT NULL;
ALTER TABLE providers ALTER COLUMN credentials TYPE TEXT USING NULL;
