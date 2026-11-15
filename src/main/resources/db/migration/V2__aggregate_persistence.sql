CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9._-]{3,32}$'),
    email VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(email) AND email = btrim(email) AND email ~ '^[!-~]+$'),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    security_version BIGINT NOT NULL CHECK (security_version >= 0),
    lock_version BIGINT NOT NULL CHECK (lock_version >= 0)
);
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'AUDITOR', 'ADMIN')),
    PRIMARY KEY (user_id, role)
);
CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL CHECK (length(btrim(title)) > 0),
    description VARCHAR(4000),
    status VARCHAR(16) NOT NULL CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_nanos SMALLINT NOT NULL CHECK (created_nanos BETWEEN 0 AND 999),
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL CHECK (modified_at >= created_at),
    modified_nanos SMALLINT NOT NULL CHECK (modified_nanos BETWEEN 0 AND 999),
    domain_version BIGINT NOT NULL CHECK (domain_version >= 0),
    lock_version BIGINT NOT NULL CHECK (lock_version >= 0),
    CHECK (modified_at > created_at OR modified_nanos >= created_nanos)
);
CREATE INDEX tasks_owner_id_id_idx ON tasks(owner_id, id);
