CREATE TABLE authorization_catalog_state (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    version BIGINT NOT NULL CHECK (version > 0)
);
INSERT INTO authorization_catalog_state (singleton, version) VALUES (TRUE, 1);
