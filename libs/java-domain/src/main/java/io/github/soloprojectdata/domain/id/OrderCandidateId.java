package io.github.soloprojectdata.domain.id;

import java.util.Objects;
import java.util.UUID;

public record OrderCandidateId(UUID value) {

    public OrderCandidateId {
        Objects.requireNonNull(value, "value");
    }

    public static OrderCandidateId parse(String value) {
        return new OrderCandidateId(UUID.fromString(value));
    }
}
