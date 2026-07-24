package io.github.soloprojectdata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 0 이상 1 이하의 정확한 비율이다.
 */
public record Ratio(BigDecimal value) {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    public Ratio {
        Objects.requireNonNull(value, "value");
        requireRange(value);
    }

    public static Ratio exact(String value) {
        return new Ratio(new BigDecimal(value));
    }

    public static Ratio rounded(
            String value,
            int scale,
            RoundingMode roundingMode
    ) {
        if (scale < 0) {
            throw new IllegalArgumentException("scale은 0 이상이어야 합니다");
        }
        Objects.requireNonNull(roundingMode, "roundingMode");
        BigDecimal unrounded = new BigDecimal(value);
        requireRange(unrounded);
        return new Ratio(unrounded.setScale(scale, roundingMode));
    }

    private static void requireRange(BigDecimal value) {
        if (value.compareTo(ZERO) < 0 || value.compareTo(ONE) > 0) {
            throw new IllegalArgumentException("비율은 0 이상 1 이하여야 합니다");
        }
    }
}
