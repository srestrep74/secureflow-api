CREATE TABLE session_families (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at = created_at + INTERVAL '720 hours'),
    revoked_at TIMESTAMPTZ CHECK (revoked_at >= created_at)
);
CREATE INDEX session_families_user_id_idx ON session_families(user_id);
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES session_families(id),
    token_digest CHAR(64) NOT NULL UNIQUE CHECK (token_digest ~ '^[0-9a-f]{64}$'),
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > issued_at),
    consumed_at TIMESTAMPTZ CHECK (consumed_at >= issued_at)
);
CREATE UNIQUE INDEX refresh_tokens_one_unconsumed_per_family_idx ON refresh_tokens(family_id) WHERE consumed_at IS NULL;
