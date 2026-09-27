CREATE TABLE files (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    storage_provider    VARCHAR(30) NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,
    original_name       VARCHAR(255) NOT NULL,
    mime_type           VARCHAR(100) NOT NULL,
    size_bytes          BIGINT NOT NULL CHECK (size_bytes >= 0),
    public_url          VARCHAR(2048),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELETED')),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (storage_provider, storage_key)
);
