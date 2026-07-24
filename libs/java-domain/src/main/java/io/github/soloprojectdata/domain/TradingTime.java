package io.github.soloprojectdata.domain;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 원본·수집 시각과 거래소 시간대·거래일을 서로 다른 의미로 보존한다.
 *
 * <p>야간 세션에서는 거래일과 현지 달력 날짜가 다를 수 있으므로 둘의 일치를
 * 암묵적으로 가정하지 않는다.</p>
 */
public record TradingTime(
        ObservedTime observedTime,
        ZoneId exchangeZone,
        LocalDate businessDate
) {

    public TradingTime {
        Objects.requireNonNull(observedTime, "observedTime");
        Objects.requireNonNull(exchangeZone, "exchangeZone");
        Objects.requireNonNull(businessDate, "businessDate");
    }
}
