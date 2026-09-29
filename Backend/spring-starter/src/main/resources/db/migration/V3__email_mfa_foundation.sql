ALTER TABLE users ADD COLUMN email_mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_mfa_enabled_at DATETIME(6) NULL;
ALTER TABLE users ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE auth_challenges (
    id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    security_version BIGINT NOT NULL,
    origin_session_id VARCHAR(36) NULL,
    secret_hash CHAR(64) NOT NULL,
    code_hmac CHAR(64) NOT NULL,
    code_generation INT NOT NULL,
    delivery_state VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    invalidated_at DATETIME(6) NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    resend_count INT NOT NULL DEFAULT 0,
    last_sent_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_auth_challenges_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_auth_challenges_origin_session FOREIGN KEY (origin_session_id) REFERENCES auth_sessions (id),
    CONSTRAINT ck_auth_challenges_origin CHECK (
        (purpose = 'LOGIN' AND origin_session_id IS NULL)
        OR (purpose IN ('ENABLE', 'DISABLE') AND origin_session_id IS NOT NULL)
    )
);

CREATE INDEX idx_auth_challenges_user_purpose ON auth_challenges (user_id, purpose, consumed_at, invalidated_at, expires_at);
CREATE INDEX idx_auth_challenges_origin_session ON auth_challenges (origin_session_id);
CREATE INDEX idx_auth_challenges_expires_at ON auth_challenges (expires_at);

CREATE TABLE auth_rate_limits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scope VARCHAR(32) NOT NULL,
    subject_key VARCHAR(320) NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_rate_limits_scope_subject UNIQUE (scope, subject_key)
);

CREATE INDEX idx_auth_rate_limits_window ON auth_rate_limits (window_started_at);
