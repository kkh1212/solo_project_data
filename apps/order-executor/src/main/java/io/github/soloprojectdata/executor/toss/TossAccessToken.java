package io.github.soloprojectdata.executor.toss;

import java.time.Instant;
import java.util.Objects;

/**
 * 로그 출력이 금지된 Bearer Token과 만료 시각이다.
 */
public final class TossAccessToken {

    private final String value;
    private final Instant expiresAt;

    TossAccessToken(String value, Instant expiresAt) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Access Token은 비어 있을 수 없습니다");
        }
        this.value = value;
        this.expiresAt = expiresAt;
    }

    String authorizationValue() {
        return "Bearer " + value;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isUsableAt(Instant instant) {
        return expiresAt.isAfter(Objects.requireNonNull(instant, "instant"));
    }

    @Override
    public String toString() {
        return "TossAccessToken[expiresAt=" + expiresAt + ", value=REDACTED]";
    }
}
