package io.github.soloprojectdata.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ObservedTimeTest {

    @Test
    void 원본Offset을보존하면서UtcInstant로정규화한다() {
        ObservedTime observedTime = new ObservedTime(
                OffsetDateTime.parse("2026-07-24T09:00:00+09:00"),
                Instant.parse("2026-07-24T00:00:01Z")
        );

        assertEquals(Instant.parse("2026-07-24T00:00:00Z"), observedTime.sourceInstant());
        assertEquals("+09:00", observedTime.sourceTimestamp().getOffset().toString());
    }
}
