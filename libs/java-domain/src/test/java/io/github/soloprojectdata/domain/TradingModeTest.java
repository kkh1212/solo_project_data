package io.github.soloprojectdata.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TradingModeTest {

    @Test
    void mockOnly만허용한다() {
        assertEquals(TradingMode.MOCK_ONLY, TradingMode.requireMockOnly("mock-only"));
    }

    @Test
    void liveAuto문자열은항상거절한다() {
        assertThrows(
                IllegalStateException.class,
                () -> TradingMode.requireMockOnly("live-auto")
        );
    }

    @Test
    void 설정누락도FailClosed한다() {
        assertThrows(
                IllegalStateException.class,
                () -> TradingMode.requireMockOnly(null)
        );
    }
}
