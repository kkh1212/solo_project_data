package io.github.soloprojectdata.domain.state;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public enum BrokerOrderStatus {
    SUBMITTING,
    PENDING,
    PARTIALLY_FILLED,
    FILLED,
    CANCEL_PENDING,
    CANCELED,
    REPLACE_PENDING,
    REPLACED,
    BROKER_REJECTED,
    UNKNOWN,
    RECONCILIATION_REQUIRED;

    public BrokerOrderStatus transitionTo(
            BrokerOrderStatus target,
            BrokerStateEvidence evidence
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(evidence, "evidence");
        if (!allowedTransitions().contains(target)) {
            throw new InvalidStateTransitionException(this, target);
        }
        if (
                this == RECONCILIATION_REQUIRED
                        && evidence != BrokerStateEvidence.RECONCILIATION
        ) {
            throw new InvalidStateTransitionException(
                    "RECONCILIATION_REQUIRED 상태는 Reconciliation 근거로만 복구할 수 있습니다"
            );
        }
        return target;
    }

    private Set<BrokerOrderStatus> allowedTransitions() {
        return switch (this) {
            case SUBMITTING -> EnumSet.of(PENDING, BROKER_REJECTED, UNKNOWN);
            case PENDING -> EnumSet.of(
                    PARTIALLY_FILLED,
                    FILLED,
                    CANCEL_PENDING,
                    REPLACE_PENDING,
                    UNKNOWN
            );
            case PARTIALLY_FILLED -> EnumSet.of(
                    FILLED,
                    CANCEL_PENDING,
                    REPLACE_PENDING,
                    UNKNOWN
            );
            case CANCEL_PENDING -> EnumSet.of(
                    PARTIALLY_FILLED,
                    FILLED,
                    CANCELED,
                    UNKNOWN
            );
            case REPLACE_PENDING -> EnumSet.of(
                    PARTIALLY_FILLED,
                    FILLED,
                    REPLACED,
                    UNKNOWN
            );
            case UNKNOWN -> EnumSet.of(RECONCILIATION_REQUIRED);
            case RECONCILIATION_REQUIRED -> EnumSet.of(
                    PENDING,
                    PARTIALLY_FILLED,
                    FILLED,
                    CANCELED,
                    REPLACED,
                    BROKER_REJECTED
            );
            default -> EnumSet.noneOf(BrokerOrderStatus.class);
        };
    }
}
