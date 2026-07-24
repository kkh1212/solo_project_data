package io.github.soloprojectdata.core;

import io.github.soloprojectdata.domain.TradingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
public class TradingCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingCoreApplication.class, args);
    }

    @Bean
    ApplicationRunner tradingModeSafetyGate(
            @Value("${trading.mode}") String configuredMode
    ) {
        return new MockOnlyStartupGate(configuredMode);
    }

    static final class MockOnlyStartupGate implements ApplicationRunner {
        private final String configuredMode;

        MockOnlyStartupGate(String configuredMode) {
            this.configuredMode = configuredMode;
        }

        @Override
        public void run(ApplicationArguments args) {
            TradingMode.requireMockOnly(configuredMode);
        }
    }
}
