package io.github.soloprojectdata.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void 필수식별자와버전을보존한다() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        UUID eventId = UUID.randomUUID();

        EventEnvelope<Map<String, Boolean>> envelope = new EventEnvelope<>(
                eventId,
                "synthetic.test.v1",
                1,
                "synthetic-aggregate",
                "synthetic-correlation",
                null,
                "synthetic-trace",
                "unit-test",
                now,
                now,
                now,
                null,
                false,
                "synthetic-checksum",
                Map.of("synthetic", true)
        );

        assertEquals(eventId, envelope.eventId());
        assertEquals(1, envelope.schemaVersion());
    }

    @Test
    void 잘못된Schema버전을거절한다() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");

        assertThrows(
                IllegalArgumentException.class,
                () -> new EventEnvelope<>(
                        UUID.randomUUID(),
                        "synthetic.test.v1",
                        0,
                        "synthetic-aggregate",
                        "synthetic-correlation",
                        null,
                        "synthetic-trace",
                        "unit-test",
                        now,
                        now,
                        now,
                        null,
                        false,
                        "synthetic-checksum",
                        Map.of("synthetic", true)
                )
        );
    }
}
