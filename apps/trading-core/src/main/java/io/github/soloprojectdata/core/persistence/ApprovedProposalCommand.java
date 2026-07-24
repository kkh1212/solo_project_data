package io.github.soloprojectdata.core.persistence;

import io.github.soloprojectdata.domain.id.OrderIntentId;
import io.github.soloprojectdata.domain.id.RiskDecisionId;
import io.github.soloprojectdata.domain.id.RiskReservationId;
import io.github.soloprojectdata.domain.order.ExternalOrderProposal;
import io.github.soloprojectdata.domain.order.OrderSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 실행 안전 Risk 승인 결과와 원자적으로 저장할 외부 제안이다.
 */
public record ApprovedProposalCommand(
        ExternalOrderProposal proposal,
        RiskDecisionId riskDecisionId,
        RiskReservationId riskReservationId,
        OrderIntentId orderIntentId,
        UUID outboxEventId,
        String safetyPolicyVersion,
        Optional<BigDecimal> reservedAmount,
        Optional<BigDecimal> reservedQuantity,
        Instant acceptedAt
) {

    private static final Pattern VERSION = Pattern.compile(
            "[a-zA-Z0-9][a-zA-Z0-9._\\-]{0,63}"
    );

    public ApprovedProposalCommand {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(riskDecisionId, "riskDecisionId");
        Objects.requireNonNull(riskReservationId, "riskReservationId");
        Objects.requireNonNull(orderIntentId, "orderIntentId");
        Objects.requireNonNull(outboxEventId, "outboxEventId");
        Objects.requireNonNull(safetyPolicyVersion, "safetyPolicyVersion");
        reservedAmount = copy(reservedAmount, "reservedAmount");
        reservedQuantity = copy(reservedQuantity, "reservedQuantity");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        proposal.requireUsableAt(acceptedAt);
        if (!VERSION.matcher(safetyPolicyVersion).matches()) {
            throw new IllegalArgumentException(
                    "safetyPolicyVersion 형식이 올바르지 않습니다"
            );
        }
        if (reservedAmount.isPresent() == reservedQuantity.isPresent()) {
            throw new IllegalArgumentException(
                    "예약 금액과 예약 수량 중 정확히 하나만 필요합니다"
            );
        }
        reservedAmount.ifPresent(value -> requirePositive(value, "reservedAmount"));
        reservedQuantity.ifPresent(
                value -> requirePositive(value, "reservedQuantity")
        );
        if (proposal.order().side() == OrderSide.BUY
                && reservedAmount.isEmpty()) {
            throw new IllegalArgumentException("BUY 제안에는 금액 예약이 필요합니다");
        }
        if (proposal.order().side() == OrderSide.SELL
                && reservedQuantity.isEmpty()) {
            throw new IllegalArgumentException("SELL 제안에는 수량 예약이 필요합니다");
        }
    }

    private static <T> Optional<T> copy(Optional<T> value, String name) {
        return Objects.requireNonNull(value, name).map(Objects::requireNonNull);
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + "는 0보다 커야 합니다");
        }
    }
}
