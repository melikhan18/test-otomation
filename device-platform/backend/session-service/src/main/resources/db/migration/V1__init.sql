CREATE TABLE session.sessions (
    id          BIGSERIAL PRIMARY KEY,
    device_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    product_id  BIGINT       NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ended_at    TIMESTAMPTZ
);
CREATE INDEX idx_sessions_user    ON session.sessions(user_id);
CREATE INDEX idx_sessions_device  ON session.sessions(device_id);
CREATE INDEX idx_sessions_product ON session.sessions(product_id);
CREATE INDEX idx_sessions_active  ON session.sessions(status) WHERE status = 'ACTIVE';
