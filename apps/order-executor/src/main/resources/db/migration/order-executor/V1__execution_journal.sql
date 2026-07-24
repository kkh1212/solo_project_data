CREATE SCHEMA IF NOT EXISTS order_executor;

CREATE TABLE order_executor.account_mapping (
    account_alias VARCHAR(32) PRIMARY KEY,
    broker VARCHAR(16) NOT NULL,
    broker_account_sequence BIGINT NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_account_mapping_alias
        CHECK (account_alias ~ '^[a-z][a-z0-9_-]{2,31}$'),
    CONSTRAINT ck_account_mapping_broker CHECK (broker = 'TOSS'),
    CONSTRAINT ck_account_mapping_sequence
        CHECK (broker_account_sequence > 0),
    CONSTRAINT ck_account_mapping_version CHECK (version >= 0)
);

CREATE TABLE order_executor.intent_inbox (
    event_id UUID PRIMARY KEY,
    order_intent_id UUID NOT NULL UNIQUE,
    account_alias VARCHAR(32) NOT NULL
        REFERENCES order_executor.account_mapping(account_alias),
    client_order_id VARCHAR(36) NOT NULL UNIQUE,
    payload_sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT ck_intent_inbox_client_order_id
        CHECK (client_order_id ~ '^[A-Za-z0-9_-]{1,36}$'),
    CONSTRAINT ck_intent_inbox_payload_sha
        CHECK (payload_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_intent_inbox_status
        CHECK (status IN ('RECEIVED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_intent_inbox_processed_at CHECK (
        (status IN ('RECEIVED', 'PROCESSING') AND processed_at IS NULL)
        OR (status IN ('COMPLETED', 'FAILED') AND processed_at IS NOT NULL)
    )
);

CREATE TABLE order_executor.broker_order (
    broker_order_record_id UUID PRIMARY KEY,
    order_intent_id UUID NOT NULL UNIQUE
        REFERENCES order_executor.intent_inbox(order_intent_id),
    account_alias VARCHAR(32) NOT NULL
        REFERENCES order_executor.account_mapping(account_alias),
    client_order_id VARCHAR(36) NOT NULL UNIQUE,
    broker_order_id VARCHAR(256) UNIQUE,
    status VARCHAR(32) NOT NULL,
    submission_certainty VARCHAR(24) NOT NULL,
    last_error_code VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_broker_order_status CHECK (
        status IN (
            'RECEIVED',
            'SUBMITTING',
            'PENDING',
            'PARTIALLY_FILLED',
            'FILLED',
            'CANCEL_PENDING',
            'CANCELED',
            'REPLACE_PENDING',
            'REPLACED',
            'BROKER_REJECTED',
            'UNKNOWN',
            'RECONCILIATION_REQUIRED'
        )
    ),
    CONSTRAINT ck_broker_order_certainty CHECK (
        submission_certainty IN ('NOT_SUBMITTED', 'SUBMITTED', 'UNKNOWN')
    ),
    CONSTRAINT ck_broker_order_identity CHECK (
        (status = 'RECEIVED' AND broker_order_id IS NULL
            AND submission_certainty = 'NOT_SUBMITTED')
        OR (status = 'SUBMITTING' AND broker_order_id IS NULL
            AND submission_certainty = 'UNKNOWN')
        OR (status IN (
                'PENDING',
                'PARTIALLY_FILLED',
                'FILLED',
                'CANCEL_PENDING',
                'CANCELED',
                'REPLACE_PENDING',
                'REPLACED'
            ) AND broker_order_id IS NOT NULL
            AND submission_certainty = 'SUBMITTED')
        OR (status = 'BROKER_REJECTED' AND broker_order_id IS NULL
            AND submission_certainty = 'NOT_SUBMITTED')
        OR (status = 'BROKER_REJECTED' AND broker_order_id IS NOT NULL
            AND submission_certainty = 'SUBMITTED')
        OR (status IN ('UNKNOWN', 'RECONCILIATION_REQUIRED')
            AND submission_certainty = 'UNKNOWN')
    ),
    CONSTRAINT ck_broker_order_version CHECK (version >= 0)
);

CREATE TABLE order_executor.order_attempt_journal (
    attempt_id UUID PRIMARY KEY,
    broker_order_record_id UUID NOT NULL
        REFERENCES order_executor.broker_order(broker_order_record_id),
    attempt_type VARCHAR(16) NOT NULL,
    request_sha256 VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    http_status INTEGER,
    error_code VARCHAR(128),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_order_attempt_type
        CHECK (attempt_type IN ('SUBMIT', 'CANCEL', 'QUERY', 'RECONCILE')),
    CONSTRAINT ck_order_attempt_request_sha
        CHECK (request_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_order_attempt_outcome CHECK (
        outcome IN (
            'STARTED',
            'ACCEPTED',
            'REJECTED_NOT_SUBMITTED',
            'UNKNOWN_REQUIRES_RECONCILIATION'
        )
    ),
    CONSTRAINT ck_order_attempt_completion CHECK (
        (outcome = 'STARTED' AND completed_at IS NULL)
        OR (outcome <> 'STARTED' AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_order_attempt_http_status
        CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599)
);

CREATE UNIQUE INDEX uq_order_attempt_single_submit
    ON order_executor.order_attempt_journal(broker_order_record_id)
    WHERE attempt_type = 'SUBMIT';

CREATE TABLE order_executor.reconciliation_case (
    reconciliation_case_id UUID PRIMARY KEY,
    broker_order_record_id UUID NOT NULL
        REFERENCES order_executor.broker_order(broker_order_record_id),
    reason VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    evidence_type VARCHAR(32),
    evidence_reference VARCHAR(256),
    CONSTRAINT ck_reconciliation_case_status
        CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_reconciliation_case_resolution CHECK (
        (status = 'OPEN' AND resolved_at IS NULL
            AND evidence_type IS NULL AND evidence_reference IS NULL)
        OR (status = 'RESOLVED' AND resolved_at IS NOT NULL
            AND evidence_type IS NOT NULL AND evidence_reference IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_reconciliation_case_open
    ON order_executor.reconciliation_case(broker_order_record_id)
    WHERE status = 'OPEN';

CREATE INDEX ix_reconciliation_case_opened
    ON order_executor.reconciliation_case(opened_at)
    WHERE status = 'OPEN';

CREATE FUNCTION order_executor.protect_attempt_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF (
        OLD.broker_order_record_id,
        OLD.attempt_type,
        OLD.request_sha256,
        OLD.started_at
    ) IS DISTINCT FROM (
        NEW.broker_order_record_id,
        NEW.attempt_type,
        NEW.request_sha256,
        NEW.started_at
    ) THEN
        RAISE EXCEPTION 'order_attempt_journal identity cannot change';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_order_attempt_identity
BEFORE UPDATE ON order_executor.order_attempt_journal
FOR EACH ROW EXECUTE FUNCTION order_executor.protect_attempt_identity();
