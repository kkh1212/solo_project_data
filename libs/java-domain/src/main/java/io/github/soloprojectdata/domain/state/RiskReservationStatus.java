package io.github.soloprojectdata.domain.state;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public enum RiskReservationStatus {
    ACTIVE,
    PARTIALLY_CONSUMED,
    CONSUMED,
    RELEASED,
    EXPIRED;

    public RiskReservationStatus transitionTo(
            RiskReservationStatus target,
            BrokerSubmissionCertainty submissionCertainty
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(submissionCertainty, "submissionCertainty");
        if (!allowedTransitions().contains(target)) {
            throw new InvalidStateTransitionException(this, target);
        }
        if (
                (target == RELEASED || target == EXPIRED)
                        && submissionCertainty == BrokerSubmissionCertainty.UNKNOWN
        ) {
            throw new InvalidStateTransitionException(
                    "Broker 제출 여부가 UNKNOWN인 Reservation은 해제·만료할 수 없습니다"
            );
        }
        return target;
    }

    private Set<RiskReservationStatus> allowedTransitions() {
        return switch (this) {
            case ACTIVE -> EnumSet.of(
                    PARTIALLY_CONSUMED,
                    CONSUMED,
                    RELEASED,
                    EXPIRED
            );
            case PARTIALLY_CONSUMED -> EnumSet.of(CONSUMED, RELEASED, EXPIRED);
            default -> EnumSet.noneOf(RiskReservationStatus.class);
        };
    }
}
