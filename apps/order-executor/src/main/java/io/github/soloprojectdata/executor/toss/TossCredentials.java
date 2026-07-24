package io.github.soloprojectdata.executor.toss;

import java.util.Objects;

/**
 * Order Executor 프로세스 안에서만 사용하는 Toss OAuth 자격증명이다.
 *
 * <p>로그나 예외에 값이 노출되지 않도록 {@link #toString()}은 항상 마스킹한다.</p>
 */
public final class TossCredentials {

    private final String clientId;
    private final String clientSecret;

    public TossCredentials(String clientId, String clientSecret) {
        this.clientId = requireNonBlank(clientId, "clientId");
        this.clientSecret = requireNonBlank(clientSecret, "clientSecret");
    }

    public String clientId() {
        return clientId;
    }

    String clientSecret() {
        return clientSecret;
    }

    @Override
    public String toString() {
        return "TossCredentials[REDACTED]";
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + "는 비어 있을 수 없습니다");
        }
        return value;
    }
}
