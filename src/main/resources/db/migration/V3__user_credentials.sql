CREATE TABLE user_credentials (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    password_hash VARCHAR(512) NOT NULL CHECK (password_hash LIKE '$argon2id$%')
);
