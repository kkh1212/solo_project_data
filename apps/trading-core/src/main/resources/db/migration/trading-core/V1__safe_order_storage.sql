CREATE SCHEMA IF NOT EXISTS trading_core;

CREATE TABLE trading_core.account_scope (
    account_alias VARCHAR(32) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_account_scope_alias
        CHECK (account_alias ~ '^[a-z][a-z0-9_-]{2,31}$'),
    CONSTRAINT ck_account_scope_version CHECK (version >= 0)
);

CREATE TABLE trading_core.external_proposal_inbox (
    proposal_id UUID PRIMARY KEY,
    schema_version SMALLINT NOT NULL,
    producer_id VARCHAR(64) NOT NULL,
    policy_id VARCHAR(64) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    evidence_sha256 VARCHAR(64) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    account_alias VARCHAR(32) NOT NULL
        REFERENCES trading_core.account_scope(account_alias),
    market VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    semantic_payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_external_proposal_schema_version CHECK (schema_version = 1),
    CONSTRAINT ck_external_proposal_evidence_sha
        CHECK (evidence_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_external_proposal_content_sha
        CHECK (content_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_external_proposal_expiry CHECK (expires_at > generated_at),
    CONSTRAINT ck_external_proposal_market CHECK (market = 'US_EQUITIES'),
    CONSTRAINT ck_external_proposal_symbol CHECK (symbol ~ '^[A-Z0-9.-]+$')
);

CREATE TABLE trading_core.risk_decision (
    risk_decision_id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL UNIQUE
        REFERENCES trading_core.external_proposal_inbox(proposal_id),
    decision VARCHAR(16) NOT NULL,
    safety_policy_version VARCHAR(64) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_risk_decision_result CHECK (decision = 'APPROVED')
);

CREATE TABLE trading_core.order_intent (
    order_intent_id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL UNIQUE
        REFERENCES trading_core.external_proposal_inbox(proposal_id),
    risk_decision_id UUID NOT NULL UNIQUE
        REFERENCES trading_core.risk_decision(risk_decision_id),
    account_alias VARCHAR(32) NOT NULL
        REFERENCES trading_core.account_scope(account_alias),
    client_order_id VARCHAR(36) NOT NULL UNIQUE,
    market VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(4) NOT NULL,
    order_type VARCHAR(8) NOT NULL,
    time_in_force VARCHAR(4),
    quantity NUMERIC,
    order_amount NUMERIC,
    limit_price NUMERIC,
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_order_intent_client_id
        CHECK (client_order_id = proposal_id::TEXT),
    CONSTRAINT ck_order_intent_market CHECK (market = 'US_EQUITIES'),
    CONSTRAINT ck_order_intent_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_order_intent_type CHECK (order_type IN ('LIMIT', 'MARKET')),
    CONSTRAINT ck_order_intent_tif
        CHECK (time_in_force IS NULL OR time_in_force IN ('DAY', 'CLS')),
    CONSTRAINT ck_order_intent_currency CHECK (currency = 'USD'),
    CONSTRAINT ck_order_intent_status CHECK (
        status IN (
            'READY',
            'DISPATCHED',
            'ACCEPTED_BY_EXECUTOR',
            'REJECTED_BY_EXECUTOR',
            'EXPIRED',
            'CANCELED'
        )
    ),
    CONSTRAINT ck_order_intent_version CHECK (version >= 0),
    CONSTRAINT ck_order_intent_quantity_positive
        CHECK (quantity IS NULL OR quantity > 0),
    CONSTRAINT ck_order_intent_amount_positive
        CHECK (order_amount IS NULL OR order_amount > 0),
    CONSTRAINT ck_order_intent_price_positive
        CHECK (limit_price IS NULL OR limit_price > 0),
    CONSTRAINT ck_order_intent_value_shape CHECK (
        (quantity IS NOT NULL AND order_amount IS NULL)
        OR (quantity IS NULL AND order_amount IS NOT NULL)
    ),
    CONSTRAINT ck_order_intent_order_shape CHECK (
        (
            order_type = 'LIMIT'
            AND quantity IS NOT NULL
            AND limit_price IS NOT NULL
            AND time_in_force IN ('DAY', 'CLS')
        )
        OR (
            order_type = 'MARKET'
            AND quantity IS NOT NULL
            AND limit_price IS NULL
            AND time_in_force = 'DAY'
        )
        OR (
            order_type = 'MARKET'
            AND order_amount IS NOT NULL
            AND limit_price IS NULL
            AND time_in_force IS NULL
        )
    )
);

CREATE TABLE trading_core.risk_reservation (
    risk_reservation_id UUID PRIMARY KEY,
    order_intent_id UUID NOT NULL UNIQUE
        REFERENCES trading_core.order_intent(order_intent_id),
    account_alias VARCHAR(32) NOT NULL
        REFERENCES trading_core.account_scope(account_alias),
    reserved_amount NUMERIC,
    reserved_quantity NUMERIC,
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    submission_certainty VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_risk_reservation_value CHECK (
        (reserved_amount IS NOT NULL AND reserved_amount > 0
            AND reserved_quantity IS NULL)
        OR (reserved_quantity IS NOT NULL AND reserved_quantity > 0
            AND reserved_amount IS NULL)
    ),
    CONSTRAINT ck_risk_reservation_currency CHECK (currency = 'USD'),
    CONSTRAINT ck_risk_reservation_status CHECK (
        status IN (
            'ACTIVE',
            'PARTIALLY_CONSUMED',
            'CONSUMED',
            'RELEASED',
            'EXPIRED'
        )
    ),
    CONSTRAINT ck_risk_reservation_certainty CHECK (
        submission_certainty IN (
            'NOT_SUBMITTED',
            'TERMINAL_CONFIRMED',
            'UNKNOWN'
        )
    ),
    CONSTRAINT ck_risk_reservation_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_risk_reservation_version CHECK (version >= 0)
);

CREATE TABLE trading_core.transactional_outbox (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version SMALLINT NOT NULL,
    partition_key VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT uq_transactional_outbox_aggregate_event
        UNIQUE (aggregate_id, event_type, event_version),
    CONSTRAINT ck_transactional_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED')),
    CONSTRAINT ck_transactional_outbox_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_transactional_outbox_publish_time CHECK (
        (status = 'PENDING' AND published_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL)
    )
);

CREATE INDEX ix_transactional_outbox_pending
    ON trading_core.transactional_outbox(next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE FUNCTION trading_core.reject_all_updates()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is immutable', TG_TABLE_NAME
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER tr_external_proposal_immutable
BEFORE UPDATE OR DELETE ON trading_core.external_proposal_inbox
FOR EACH ROW EXECUTE FUNCTION trading_core.reject_all_updates();

CREATE TRIGGER tr_risk_decision_immutable
BEFORE UPDATE OR DELETE ON trading_core.risk_decision
FOR EACH ROW EXECUTE FUNCTION trading_core.reject_all_updates();

CREATE FUNCTION trading_core.protect_order_intent_fields()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF (
        OLD.proposal_id,
        OLD.risk_decision_id,
        OLD.account_alias,
        OLD.client_order_id,
        OLD.market,
        OLD.symbol,
        OLD.side,
        OLD.order_type,
        OLD.time_in_force,
        OLD.quantity,
        OLD.order_amount,
        OLD.limit_price,
        OLD.currency,
        OLD.expires_at,
        OLD.created_at
    ) IS DISTINCT FROM (
        NEW.proposal_id,
        NEW.risk_decision_id,
        NEW.account_alias,
        NEW.client_order_id,
        NEW.market,
        NEW.symbol,
        NEW.side,
        NEW.order_type,
        NEW.time_in_force,
        NEW.quantity,
        NEW.order_amount,
        NEW.limit_price,
        NEW.currency,
        NEW.expires_at,
        NEW.created_at
    ) THEN
        RAISE EXCEPTION 'order_intent immutable fields cannot change'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_order_intent_immutable_fields
BEFORE UPDATE ON trading_core.order_intent
FOR EACH ROW EXECUTE FUNCTION trading_core.protect_order_intent_fields();

CREATE FUNCTION trading_core.protect_outbox_payload()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF (
        OLD.aggregate_type,
        OLD.aggregate_id,
        OLD.event_type,
        OLD.event_version,
        OLD.partition_key,
        OLD.payload,
        OLD.created_at
    ) IS DISTINCT FROM (
        NEW.aggregate_type,
        NEW.aggregate_id,
        NEW.event_type,
        NEW.event_version,
        NEW.partition_key,
        NEW.payload,
        NEW.created_at
    ) THEN
        RAISE EXCEPTION 'transactional_outbox payload cannot change'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_transactional_outbox_immutable_payload
BEFORE UPDATE ON trading_core.transactional_outbox
FOR EACH ROW EXECUTE FUNCTION trading_core.protect_outbox_payload();
