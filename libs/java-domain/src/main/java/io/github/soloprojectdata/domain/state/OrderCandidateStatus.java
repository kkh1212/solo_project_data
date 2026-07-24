package io.github.soloprojectdata.domain.state;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public enum OrderCandidateStatus {
    CREATED,
    EVALUATING,
    APPROVED,
    REJECTED,
    BLOCKED_UNCERTAIN,
    MANUAL_REVIEW_REQUIRED,
    EXPIRED;

    public boolean canTransitionTo(OrderCandidateStatus target) {
        Objects.requireNonNull(target, "target");
        return allowedTransitions().contains(target);
    }

    public OrderCandidateStatus transitionTo(OrderCandidateStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(this, target);
        }
        return target;
    }

    private Set<OrderCandidateStatus> allowedTransitions() {
        return switch (this) {
            case CREATED -> EnumSet.of(EVALUATING);
            case EVALUATING -> EnumSet.of(
                    APPROVED,
                    REJECTED,
                    BLOCKED_UNCERTAIN,
                    MANUAL_REVIEW_REQUIRED,
                    EXPIRED
            );
            default -> EnumSet.noneOf(OrderCandidateStatus.class);
        };
    }
}
