-- Add the credential column to existing local databases without deleting account or finance rows.
ALTER TABLE IF EXISTS app_users ADD COLUMN IF NOT EXISTS password VARCHAR(255);
