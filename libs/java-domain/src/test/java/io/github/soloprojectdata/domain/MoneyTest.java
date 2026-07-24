package io.github.soloprojectdata.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void 문자열에서정확한십진값을만든다() {
        Money money = Money.exact("0.10", "KRW")
                .add(Money.exact("0.20", "KRW"));

        assertEquals("0.30", money.amount().toPlainString());
    }

    @Test
    void 반올림규칙을호출자가명시한다() {
        Money money = Money.rounded("10.125", "USD", 2, RoundingMode.HALF_EVEN);

        assertEquals("10.12", money.amount().toPlainString());
    }

    @Test
    void 통화가다르면계산을거절한다() {
        Money krw = Money.exact("1000", "KRW");
        Money usd = Money.exact("1", "USD");

        assertThrows(IllegalArgumentException.class, () -> krw.add(usd));
    }
}
