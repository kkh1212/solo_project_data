package io.github.soloprojectdata.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class DecimalValueTypesTest {

    @Test
    void 가격은0보다커야한다() {
        assertEquals("100.25", Price.exact("100.25", "USD").value().amount().toPlainString());
        assertThrows(IllegalArgumentException.class, () -> Price.exact("0", "USD"));
        assertThrows(IllegalArgumentException.class, () -> Price.exact("-1", "USD"));
    }

    @Test
    void 수량은Decimal과명시적반올림을사용한다() {
        Quantity quantity = Quantity.rounded("1.25", 1, RoundingMode.HALF_EVEN);

        assertEquals("1.2", quantity.value().toPlainString());
        assertThrows(IllegalArgumentException.class, () -> Quantity.exact("0"));
        assertThrows(IllegalArgumentException.class, () -> Quantity.exact("-0.1"));
    }

    @Test
    void 비율은0과1경계를포함한다() {
        assertEquals("0", Ratio.exact("0").value().toPlainString());
        assertEquals("1", Ratio.exact("1").value().toPlainString());
        assertThrows(IllegalArgumentException.class, () -> Ratio.exact("-0.0001"));
        assertThrows(IllegalArgumentException.class, () -> Ratio.exact("1.0001"));
        assertThrows(
                IllegalArgumentException.class,
                () -> Ratio.rounded("-0.04", 1, RoundingMode.HALF_EVEN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Ratio.rounded("1.04", 1, RoundingMode.HALF_EVEN)
        );
    }
}
