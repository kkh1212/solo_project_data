package io.github.soloprojectdata.domain.state;

/**
 * Broker Order 상태 변경의 근거다.
 */
public enum BrokerStateEvidence {
    DIRECT_RESPONSE,
    BROKER_QUERY,
    RECONCILIATION
}
