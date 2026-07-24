package io.github.soloprojectdata.executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class OrderExecutorApplicationTest {

    @Test
    void 기본Gateway는외부네트워크능력이없다() {
        BrokerGateway gateway = new MockBrokerGateway();

        assertFalse(gateway.externalNetworkEnabled());
        assertDoesNotThrow(() -> new OrderExecutorApplication.MockOnlyExecutorGate(
                "mock-only",
                gateway
        ).run(new DefaultApplicationArguments()));
    }

    @Test
    void 외부네트워크능력을표시한Gateway는차단한다() {
        BrokerGateway unsafeGateway = new BrokerGateway() {
            @Override
            public BrokerGatewayType type() {
                return BrokerGatewayType.MOCK;
            }

            @Override
            public boolean externalNetworkEnabled() {
                return true;
            }
        };

        var gate = new OrderExecutorApplication.MockOnlyExecutorGate(
                "mock-only",
                unsafeGateway
        );

        assertThrows(
                IllegalStateException.class,
                () -> gate.run(new DefaultApplicationArguments())
        );
    }

    @Test
    void liveAuto구성은차단한다() {
        var gate = new OrderExecutorApplication.MockOnlyExecutorGate(
                "live-auto",
                new MockBrokerGateway()
        );

        assertThrows(
                IllegalStateException.class,
                () -> gate.run(new DefaultApplicationArguments())
        );
    }
}
