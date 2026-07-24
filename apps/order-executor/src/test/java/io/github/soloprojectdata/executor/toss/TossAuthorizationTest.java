package io.github.soloprojectdata.executor.toss;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TossAuthorizationTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void 주문승인은계좌와clientOrderId와만료시각에묶인다() {
        TossOrderAuthorization authorization = authorized("intent-1");

        assertDoesNotThrow(() -> authorization.requireAllowed(
                1,
                "intent-1",
                NOW
        ));
        assertThrows(
                IllegalStateException.class,
                () -> authorization.requireAllowed(1, "intent-2", NOW)
        );
        assertThrows(
                IllegalStateException.class,
                () -> authorization.requireAllowed(
                        1,
                        "intent-1",
                        NOW.plusSeconds(61)
                )
        );
    }

    @Test
    void 하나의Gate라도내려가면신규주문을막는다() {
        TossAccountAuthorization account = accountAuthorization();
        TossOrderAuthorization killed = new TossOrderAuthorization(
                account,
                "intent-1",
                "approval-1",
                NOW.plusSeconds(60),
                true,
                false,
                true,
                true,
                true
        );

        assertThrows(
                IllegalStateException.class,
                () -> killed.requireAllowed(1, "intent-1", NOW)
        );
        assertDoesNotThrow(() -> account.requireAllowed(1));
    }

    private static TossOrderAuthorization authorized(String clientOrderId) {
        return new TossOrderAuthorization(
                accountAuthorization(),
                clientOrderId,
                "approval-1",
                NOW.plusSeconds(60),
                true,
                true,
                true,
                true,
                true
        );
    }

    static TossAccountAuthorization accountAuthorization() {
        return new TossAccountAuthorization(
                new TossNetworkAuthorization(true, true, true, true),
                1,
                true,
                true,
                true
        );
    }
}
