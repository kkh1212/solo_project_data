package io.github.soloprojectdata.domain.id;

import java.util.Objects;
import java.util.UUID;

public record OrderIntentId(UUID value) {

    public OrderIntentId {
        Objects.requireNonNull(value, "value");
    }

    public static OrderIntentId parse(String value) {
        return new OrderIntentId(UUID.fromString(value));
    }
}
