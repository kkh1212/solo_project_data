package io.github.soloprojectdata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 0보다 큰 정확한 십진 수량이다. 종목·시장별 step은 Adapter 계약 전까지 TBD다.
 */
public record Quantity(BigDecimal value) {

    public Quantity {
        Objects.requireNonNull(value, "value");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다");
        }
    }

    public static Quantity exact(String value) {
        return new Quantity(new BigDecimal(value));
    }

    public static Quantity rounded(
            String value,
            int scale,
            RoundingMode roundingMode
    ) {
        if (scale < 0) {
            throw new IllegalArgumentException("scale은 0 이상이어야 합니다");
        }
        Objects.requireNonNull(roundingMode, "roundingMode");
        return new Quantity(new BigDecimal(value).setScale(scale, roundingMode));
    }
}
