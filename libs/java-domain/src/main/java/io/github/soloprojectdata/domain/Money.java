package io.github.soloprojectdata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * 통화와 정확한 십진 금액을 함께 보존한다.
 *
 * <p>시장·통화별 scale은 아직 TBD이므로 생성자가 암묵적으로 반올림하지 않는다.
 * 반올림이 필요한 경계에서는 {@link #rounded(String, String, int, RoundingMode)}처럼
 * scale과 반올림 모드를 호출자가 명시해야 한다.</p>
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }

    public static Money exact(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money rounded(
            String amount,
            String currencyCode,
            int scale,
            RoundingMode roundingMode
    ) {
        Objects.requireNonNull(roundingMode, "roundingMode");
        return new Money(
                new BigDecimal(amount).setScale(scale, roundingMode),
                Currency.getInstance(currencyCode)
        );
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("통화가 다른 Money는 계산할 수 없습니다");
        }
    }
}
