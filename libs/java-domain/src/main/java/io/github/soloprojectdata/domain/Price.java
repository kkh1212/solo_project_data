package io.github.soloprojectdata.domain;

import java.util.Objects;

/**
 * 0보다 큰 통화 가격이다. 시장별 호가 단위와 scale은 아직 TBD다.
 */
public record Price(Money value) {

    public Price {
        Objects.requireNonNull(value, "value");
        if (value.amount().signum() <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 합니다");
        }
    }

    public static Price exact(String amount, String currencyCode) {
        return new Price(Money.exact(amount, currencyCode));
    }
}
