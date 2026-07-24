package io.github.soloprojectdata.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class TradingCoreApplicationTest {

    @Test
    void mockOnly구성은시작Gate를통과한다() {
        var gate = new TradingCoreApplication.MockOnlyStartupGate("mock-only");

        assertDoesNotThrow(() -> gate.run(new DefaultApplicationArguments()));
    }

    @Test
    void liveAuto구성은시작Gate에서차단한다() {
        var gate = new TradingCoreApplication.MockOnlyStartupGate("live-auto");

        assertThrows(
                IllegalStateException.class,
                () -> gate.run(new DefaultApplicationArguments())
        );
    }
}
