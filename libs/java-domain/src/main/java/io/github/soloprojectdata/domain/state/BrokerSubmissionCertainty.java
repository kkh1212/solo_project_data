package io.github.soloprojectdata.domain.state;

/**
 * Reservation 해제 판단에 사용하는 Broker 제출 여부의 증명 수준이다.
 */
public enum BrokerSubmissionCertainty {
    NOT_SUBMITTED,
    TERMINAL_CONFIRMED,
    UNKNOWN
}
