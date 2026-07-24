package io.github.soloprojectdata.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.soloprojectdata.domain.id.OrderCandidateId;
import io.github.soloprojectdata.domain.id.OrderIntentId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentifierAndTradingTimeTest {

    @Test
    void 같은Uuid라도업무식별자타입은서로다르다() {
        String rawId = "00000000-0000-0000-0000-000000000001";
        OrderCandidateId candidateId = OrderCandidateId.parse(rawId);
        OrderIntentId intentId = OrderIntentId.parse(rawId);

        assertEquals(UUID.fromString(rawId), candidateId.value());
        assertNotEquals(candidateId, intentId);
    }

    @Test
    void 거래일을원본시각과별도로보존한다() {
        TradingTime tradingTime = new TradingTime(
                new ObservedTime(
                        OffsetDateTime.parse("2026-07-24T09:00:00+09:00"),
                        Instant.parse("2026-07-24T00:00:01Z")
                ),
                ZoneId.of("Asia/Seoul"),
                LocalDate.parse("2026-07-23")
        );

        assertEquals(LocalDate.parse("2026-07-23"), tradingTime.businessDate());
        assertEquals("Asia/Seoul", tradingTime.exchangeZone().getId());
        assertEquals(
                Instant.parse("2026-07-24T00:00:00Z"),
                tradingTime.observedTime().sourceInstant()
        );
    }

    @Test
    void 미국주식Instrument는시장시간대와통화를고정한다() {
        Instrument instrument = Instrument.usEquity("brk.b");

        assertEquals("BRK.B", instrument.symbol());
        assertEquals("America/New_York", instrument.market().exchangeZone().getId());
        assertEquals("USD", instrument.market().settlementCurrency().getCurrencyCode());
        assertThrows(
                IllegalArgumentException.class,
                () -> Instrument.usEquity("AAPL/USD")
        );
    }
}
