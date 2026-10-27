CREATE TABLE operations (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('BOOKING', 'CHANGE', 'CANCELLATION')),
    state VARCHAR(32) NOT NULL,
    supplier_code VARCHAR(64),
    request_fingerprint CHAR(64),
    response_ciphertext BYTEA,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX operations_review_idx ON operations (state, updated_at)
    WHERE state IN ('IN_PROGRESS', 'UNKNOWN_OUTCOME', 'MANUAL_REVIEW');
CREATE INDEX operations_tenant_idx ON operations (tenant_id, created_at DESC);

CREATE TABLE idempotency_records (
    tenant_id VARCHAR(128) NOT NULL,
    operation_kind VARCHAR(32) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    operation_id UUID NOT NULL REFERENCES operations(id),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, operation_kind, key_hash)
);
CREATE INDEX idempotency_expiry_idx ON idempotency_records (expires_at);

CREATE TABLE cancellation_jobs (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL UNIQUE REFERENCES operations(id),
    tenant_id VARCHAR(128) NOT NULL,
    booking_hash CHAR(64) NOT NULL,
    supplier_code VARCHAR(64) NOT NULL,
    reservation_ciphertext BYTEA NOT NULL,
    state VARCHAR(32) NOT NULL CHECK (state IN
        ('REQUESTED', 'IN_PROGRESS', 'RETRY_SCHEDULED', 'SUCCEEDED', 'DLQ', 'UNKNOWN_OUTCOME', 'RESOLVED')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_owner UUID,
    lease_until TIMESTAMPTZ,
    last_error_code VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, booking_hash)
);
CREATE INDEX cancellation_claim_idx ON cancellation_jobs (next_attempt_at, created_at)
    WHERE state IN ('REQUESTED', 'RETRY_SCHEDULED');
CREATE INDEX cancellation_lease_idx ON cancellation_jobs (lease_until)
    WHERE state = 'IN_PROGRESS';

CREATE TABLE dead_letters (
    job_id UUID PRIMARY KEY REFERENCES cancellation_jobs(id),
    reason_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by VARCHAR(256),
    resolution_note VARCHAR(1000),
    replay_count INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE audit_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operation_id UUID REFERENCES operations(id),
    tenant_id VARCHAR(128) NOT NULL,
    actor VARCHAR(256) NOT NULL,
    action VARCHAR(64) NOT NULL,
    detail_code VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_operation_idx ON audit_events (operation_id, created_at);
