package io.github.soloprojectdata.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 원본 offset 시각과 UTC 수집 시각을 구분한다.
 */
public record ObservedTime(
        OffsetDateTime sourceTimestamp,
        Instant ingestedAt
) {

    public ObservedTime {
        Objects.requireNonNull(sourceTimestamp, "sourceTimestamp");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
    }

    public Instant sourceInstant() {
        return sourceTimestamp.toInstant();
    }
}
