package io.github.soloprojectdata.executor.toss;

import java.util.Objects;

/**
 * 주문 생성 직접 응답의 안전한 분류다.
 */
public record TossOrderSubmissionOutcome(
        Status status,
        String brokerOrderId,
        String clientOrderId,
        String errorCode,
        int httpStatus
) {

    public enum Status {
        ACCEPTED,
        REJECTED_NOT_SUBMITTED,
        UNKNOWN_REQUIRES_RECONCILIATION
    }

    public TossOrderSubmissionOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(clientOrderId, "clientOrderId");
        if (status == Status.ACCEPTED && (brokerOrderId == null || brokerOrderId.isBlank())) {
            throw new IllegalArgumentException("수락 응답에는 Broker Order ID가 필요합니다");
        }
        if (status != Status.ACCEPTED && brokerOrderId != null) {
            throw new IllegalArgumentException("비수락 응답은 Broker Order ID를 가질 수 없습니다");
        }
    }

    static TossOrderSubmissionOutcome accepted(
            String brokerOrderId,
            String clientOrderId
    ) {
        return new TossOrderSubmissionOutcome(
                Status.ACCEPTED,
                brokerOrderId,
                clientOrderId,
                null,
                200
        );
    }

    static TossOrderSubmissionOutcome rejected(
            String clientOrderId,
            String errorCode,
            int httpStatus
    ) {
        return new TossOrderSubmissionOutcome(
                Status.REJECTED_NOT_SUBMITTED,
                null,
                clientOrderId,
                errorCode,
                httpStatus
        );
    }

    static TossOrderSubmissionOutcome unknown(
            String clientOrderId,
            String errorCode,
            int httpStatus
    ) {
        return new TossOrderSubmissionOutcome(
                Status.UNKNOWN_REQUIRES_RECONCILIATION,
                null,
                clientOrderId,
                errorCode,
                httpStatus
        );
    }
}
