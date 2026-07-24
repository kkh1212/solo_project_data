package io.github.soloprojectdata.executor.toss;

import io.github.soloprojectdata.domain.Instrument;
import io.github.soloprojectdata.domain.state.BrokerOrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Toss 주문 조회 응답을 내부 상태로 정규화하기 전 보존하는 Snapshot이다.
 */
public record TossOrderSnapshot(
        String brokerOrderId,
        Instrument instrument,
        String rawSide,
        String rawOrderType,
        String rawTimeInForce,
        String rawStatus,
        BigDecimal quantity,
        BigDecimal price,
        OffsetDateTime orderedAt
) {

    public TossOrderSnapshot {
        Objects.requireNonNull(brokerOrderId, "brokerOrderId");
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(rawSide, "rawSide");
        Objects.requireNonNull(rawOrderType, "rawOrderType");
        Objects.requireNonNull(rawTimeInForce, "rawTimeInForce");
        Objects.requireNonNull(rawStatus, "rawStatus");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(orderedAt, "orderedAt");
    }

    public BrokerOrderStatus normalizedStatus() {
        return switch (rawStatus) {
            case "PENDING" -> BrokerOrderStatus.PENDING;
            case "PARTIAL_FILLED" -> BrokerOrderStatus.PARTIALLY_FILLED;
            case "FILLED" -> BrokerOrderStatus.FILLED;
            case "PENDING_CANCEL" -> BrokerOrderStatus.CANCEL_PENDING;
            case "CANCELED" -> BrokerOrderStatus.CANCELED;
            case "PENDING_REPLACE" -> BrokerOrderStatus.REPLACE_PENDING;
            case "REPLACED" -> BrokerOrderStatus.REPLACED;
            case "REJECTED", "CANCEL_REJECTED", "REPLACE_REJECTED" ->
                    BrokerOrderStatus.BROKER_REJECTED;
            default -> BrokerOrderStatus.UNKNOWN;
        };
    }
}
