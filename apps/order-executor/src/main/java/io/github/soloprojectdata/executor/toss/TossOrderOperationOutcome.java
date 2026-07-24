package io.github.soloprojectdata.executor.toss;

import java.util.Objects;

/**
 * 취소 직접 응답의 안전한 분류다.
 */
public record TossOrderOperationOutcome(
        Status status,
        String resultingOrderId,
        String errorCode,
        int httpStatus
) {

    public enum Status {
        ACCEPTED,
        REJECTED,
        UNKNOWN_REQUIRES_RECONCILIATION
    }

    public TossOrderOperationOutcome {
        Objects.requireNonNull(status, "status");
        if (status == Status.ACCEPTED
                && (resultingOrderId == null || resultingOrderId.isBlank())) {
            throw new IllegalArgumentException("수락 응답에는 결과 주문 ID가 필요합니다");
        }
    }

    static TossOrderOperationOutcome accepted(String orderId) {
        return new TossOrderOperationOutcome(Status.ACCEPTED, orderId, null, 200);
    }

    static TossOrderOperationOutcome rejected(String errorCode, int httpStatus) {
        return new TossOrderOperationOutcome(
                Status.REJECTED,
                null,
                errorCode,
                httpStatus
        );
    }

    static TossOrderOperationOutcome unknown(String errorCode, int httpStatus) {
        return new TossOrderOperationOutcome(
                Status.UNKNOWN_REQUIRES_RECONCILIATION,
                null,
                errorCode,
                httpStatus
        );
    }
}
