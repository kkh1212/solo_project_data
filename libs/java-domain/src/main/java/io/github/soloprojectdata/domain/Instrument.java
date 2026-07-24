package io.github.soloprojectdata.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 시장과 Broker 심볼을 함께 보존하는 종목 식별자다.
 */
public record Instrument(Market market, String symbol) {

    private static final Pattern US_SYMBOL = Pattern.compile("[A-Z0-9.\\-]+");

    public Instrument {
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(symbol, "symbol");
        symbol = symbol.strip().toUpperCase(Locale.ROOT);
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("종목 심볼은 비어 있을 수 없습니다");
        }
        if (market == Market.US_EQUITIES && !US_SYMBOL.matcher(symbol).matches()) {
            throw new IllegalArgumentException("미국주식 심볼 형식이 올바르지 않습니다");
        }
    }

    public static Instrument usEquity(String symbol) {
        return new Instrument(Market.US_EQUITIES, symbol);
    }
}
