package io.github.soloprojectdata.core.persistence;

public final class ProposalReplayConflictException extends RuntimeException {

    public ProposalReplayConflictException(String message) {
        super(message);
    }
}
