package io.github.soloprojectdata.domain.id;

import java.util.Objects;
import java.util.UUID;

public record RiskReservationId(UUID value) {

    public RiskReservationId {
        Objects.requireNonNull(value, "value");
    }

    public static RiskReservationId parse(String value) {
        return new RiskReservationId(UUID.fromString(value));
    }
}
