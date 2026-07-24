package io.github.soloprojectdata.core.persistence;

import io.github.soloprojectdata.domain.id.OrderIntentId;
import java.util.Objects;

public record PersistedOrderIntent(Status status, OrderIntentId orderIntentId) {

    public enum Status {
        CREATED,
        DUPLICATE_SAME_PROPOSAL
    }

    public PersistedOrderIntent {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(orderIntentId, "orderIntentId");
    }
}
