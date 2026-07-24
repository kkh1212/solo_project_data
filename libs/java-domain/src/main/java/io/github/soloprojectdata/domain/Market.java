package io.github.soloprojectdata.domain;

import java.time.ZoneId;
import java.util.Currency;

/**
 * 현재 확정된 거래 시장이다.
 *
 * <p>미국 주식은 여러 거래소를 포함하므로 이 값은 개별 거래소 코드가 아니라
 * 주문·시간·통화 규칙을 묶는 시장 범위다.</p>
 */
public enum Market {
    US_EQUITIES(ZoneId.of("America/New_York"), Currency.getInstance("USD"));

    private final ZoneId exchangeZone;
    private final Currency settlementCurrency;

    Market(ZoneId exchangeZone, Currency settlementCurrency) {
        this.exchangeZone = exchangeZone;
        this.settlementCurrency = settlementCurrency;
    }

    public ZoneId exchangeZone() {
        return exchangeZone;
    }

    public Currency settlementCurrency() {
        return settlementCurrency;
    }
}
