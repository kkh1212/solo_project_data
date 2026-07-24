package io.github.soloprojectdata.domain.id;

import java.util.Objects;
import java.util.UUID;

public record RiskDecisionId(UUID value) {

    public RiskDecisionId {
        Objects.requireNonNull(value, "value");
    }

    public static RiskDecisionId parse(String value) {
        return new RiskDecisionId(UUID.fromString(value));
    }
}
