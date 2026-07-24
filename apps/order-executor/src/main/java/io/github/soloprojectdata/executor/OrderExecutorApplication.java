package io.github.soloprojectdata.executor;

import io.github.soloprojectdata.domain.TradingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class OrderExecutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderExecutorApplication.class, args);
    }

    @Bean
    BrokerGateway brokerGateway() {
        return new MockBrokerGateway();
    }

    @Bean
    ApplicationRunner executorSafetyGate(
            @Value("${trading.mode}") String configuredMode,
            BrokerGateway brokerGateway
    ) {
        return new MockOnlyExecutorGate(configuredMode, brokerGateway);
    }

    static final class MockOnlyExecutorGate implements ApplicationRunner {
        private final String configuredMode;
        private final BrokerGateway brokerGateway;

        MockOnlyExecutorGate(String configuredMode, BrokerGateway brokerGateway) {
            this.configuredMode = configuredMode;
            this.brokerGateway = brokerGateway;
        }

        @Override
        public void run(ApplicationArguments args) {
            TradingMode.requireMockOnly(configuredMode);
            if (brokerGateway.type() != BrokerGatewayType.MOCK
                    || brokerGateway.externalNetworkEnabled()) {
                throw new IllegalStateException(
                        "기본 Order Executor는 외부 Broker Gateway를 사용할 수 없습니다"
                );
            }
        }
    }
}
