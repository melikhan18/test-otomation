CREATE TABLE device.devices (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT       NOT NULL,
    serial          VARCHAR(128) NOT NULL,
    manufacturer    VARCHAR(64)  NOT NULL,
    model           VARCHAR(128) NOT NULL,
    android_version VARCHAR(32)  NOT NULL,
    screen_width    INT          NOT NULL,
    screen_height   INT          NOT NULL,
    agent_version   VARCHAR(32),
    enrolled_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_seen_at    TIMESTAMPTZ,
    UNIQUE (product_id, serial)
);
CREATE INDEX idx_devices_product ON device.devices(product_id);

CREATE TABLE device.enrollment_tokens (
    id                BIGSERIAL PRIMARY KEY,
    token             VARCHAR(255) NOT NULL UNIQUE,
    product_id        BIGINT       NOT NULL,
    issued_by_user_id BIGINT       NOT NULL,
    expires_at        TIMESTAMPTZ  NOT NULL,
    used_at           TIMESTAMPTZ,
    used_by_device_id BIGINT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_enroll_token ON device.enrollment_tokens(token);
