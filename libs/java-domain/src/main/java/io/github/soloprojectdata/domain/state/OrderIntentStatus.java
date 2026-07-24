package io.github.soloprojectdata.domain.state;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public enum OrderIntentStatus {
    READY,
    DISPATCHED,
    ACCEPTED_BY_EXECUTOR,
    REJECTED_BY_EXECUTOR,
    EXPIRED,
    CANCELED;

    public boolean canTransitionTo(OrderIntentStatus target) {
        Objects.requireNonNull(target, "target");
        return allowedTransitions().contains(target);
    }

    public OrderIntentStatus transitionTo(OrderIntentStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(this, target);
        }
        return target;
    }

    private Set<OrderIntentStatus> allowedTransitions() {
        return switch (this) {
            case READY -> EnumSet.of(DISPATCHED, EXPIRED, CANCELED);
            case DISPATCHED -> EnumSet.of(
                    ACCEPTED_BY_EXECUTOR,
                    REJECTED_BY_EXECUTOR
            );
            default -> EnumSet.noneOf(OrderIntentStatus.class);
        };
    }
}
