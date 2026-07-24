package io.github.soloprojectdata.domain;

import java.util.Locale;

/**
 * 1단계에서 허용되는 유일한 실행 모드다.
 *
 * <p>실거래 모드는 Enum에 존재하지 않으며 단일 설정값으로 활성화할 수 없다.</p>
 */
public enum TradingMode {
    MOCK_ONLY("mock-only");

    private final String configValue;

    TradingMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static TradingMode requireMockOnly(String rawMode) {
        if (rawMode == null) {
            throw new IllegalStateException("trading.mode가 없으므로 안전하게 시작을 중단합니다");
        }

        String normalized = rawMode.strip().toLowerCase(Locale.ROOT);
        if (MOCK_ONLY.configValue.equals(normalized)) {
            return MOCK_ONLY;
        }

        throw new IllegalStateException(
                "1단계에서는 trading.mode=mock-only만 허용됩니다"
        );
    }
}
