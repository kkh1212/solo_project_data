package io.github.soloprojectdata.domain.state;

public final class InvalidStateTransitionException extends IllegalStateException {

    public InvalidStateTransitionException(Enum<?> from, Enum<?> to) {
        super("허용되지 않은 상태 전이: " + from.name() + " -> " + to.name());
    }

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
