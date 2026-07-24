package io.github.soloprojectdata.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 직렬화 형식을 확정하지 않은 언어 내부 공통 이벤트 Envelope의 최소 계약이다.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String aggregateId,
        String correlationId,
        String causationId,
        String traceId,
        String source,
        Instant sourceTimestamp,
        Instant ingestedAt,
        Instant producedAt,
        String pipelineRunId,
        boolean replay,
        String checksum,
        T payload
) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        eventType = requireText(eventType, "eventType");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion은 1 이상이어야 합니다");
        }
        aggregateId = requireText(aggregateId, "aggregateId");
        correlationId = requireText(correlationId, "correlationId");
        traceId = requireText(traceId, "traceId");
        source = requireText(source, "source");
        Objects.requireNonNull(sourceTimestamp, "sourceTimestamp");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        Objects.requireNonNull(producedAt, "producedAt");
        checksum = requireText(checksum, "checksum");
        Objects.requireNonNull(payload, "payload");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 비어 있을 수 없습니다");
        }
        return value;
    }
}
