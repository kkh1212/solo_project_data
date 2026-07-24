package io.github.soloprojectdata.executor.persistence;

import java.util.Objects;
import java.util.UUID;

public record IntentInboxClaim(Status status, UUID brokerOrderRecordId) {

    public enum Status {
        CLAIMED,
        DUPLICATE_SAME_INTENT
    }

    public IntentInboxClaim {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(brokerOrderRecordId, "brokerOrderRecordId");
    }
}
