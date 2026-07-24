package io.github.soloprojectdata.executor.persistence;

public final class IntentInboxConflictException extends RuntimeException {

    public IntentInboxConflictException(String message) {
        super(message);
    }
}
