-- Apply once to an existing Derby database before starting the V10 patch.
ALTER TABLE app.users ADD COLUMN remember_token_hash VARCHAR(64);
ALTER TABLE app.users ADD CONSTRAINT users_remember_token_unique UNIQUE (remember_token_hash);
ALTER TABLE app.users ADD COLUMN remember_expires_at BIGINT;
