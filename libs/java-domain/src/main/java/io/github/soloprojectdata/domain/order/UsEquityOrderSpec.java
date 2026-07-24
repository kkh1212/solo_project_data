package io.github.soloprojectdata.domain.order;

import io.github.soloprojectdata.domain.Money;
import io.github.soloprojectdata.domain.Price;
import io.github.soloprojectdata.domain.Quantity;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * 외부 정책 주문 제안에 포함되는 미국주식 주문 의미 계약이다.
 *
 * <p>Broker가 묵시적으로 절삭하도록 두지 않고 공식 Toss OAS 1.2.4 범위를
 * 입력 경계에서 먼저 검증한다.</p>
 */
public record UsEquityOrderSpec(
        OrderSide side,
        OrderType orderType,
        Optional<TimeInForce> timeInForce,
        Optional<Quantity> quantity,
        Optional<Money> orderAmount,
        Optional<Price> limitPrice
) {

    private static final BigDecimal ONE_DOLLAR = BigDecimal.ONE;

    public UsEquityOrderSpec {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(orderType, "orderType");
        timeInForce = copy(timeInForce, "timeInForce");
        quantity = copy(quantity, "quantity");
        orderAmount = copy(orderAmount, "orderAmount");
        limitPrice = copy(limitPrice, "limitPrice");
        validate(
                side,
                orderType,
                timeInForce,
                quantity,
                orderAmount,
                limitPrice
        );
    }

    public static UsEquityOrderSpec quantityBased(
            OrderSide side,
            OrderType orderType,
            TimeInForce timeInForce,
            Quantity quantity,
            Price limitPrice
    ) {
        return new UsEquityOrderSpec(
                side,
                orderType,
                Optional.ofNullable(timeInForce),
                Optional.ofNullable(quantity),
                Optional.empty(),
                Optional.ofNullable(limitPrice)
        );
    }

    public static UsEquityOrderSpec amountBasedMarket(
            OrderSide side,
            Money orderAmount
    ) {
        return new UsEquityOrderSpec(
                side,
                OrderType.MARKET,
                Optional.empty(),
                Optional.empty(),
                Optional.ofNullable(orderAmount),
                Optional.empty()
        );
    }

    private static void validate(
            OrderSide side,
            OrderType orderType,
            Optional<TimeInForce> timeInForce,
            Optional<Quantity> quantity,
            Optional<Money> orderAmount,
            Optional<Price> limitPrice
    ) {
        if (quantity.isPresent() == orderAmount.isPresent()) {
            throw new IllegalArgumentException(
                    "quantity와 orderAmount 중 정확히 하나만 필요합니다"
            );
        }
        if (orderAmount.isPresent()) {
            Money amount = orderAmount.orElseThrow();
            requirePositiveUsd(amount, "orderAmount");
            if (orderType != OrderType.MARKET
                    || timeInForce.isPresent()
                    || limitPrice.isPresent()) {
                throw new IllegalArgumentException(
                        "금액 주문은 미국주식 MARKET 주문만 허용합니다"
                );
            }
            return;
        }

        if (timeInForce.isEmpty()) {
            throw new IllegalArgumentException("수량 주문에는 timeInForce가 필요합니다");
        }
        validateQuantity(side, orderType, quantity.orElseThrow());
        if (orderType == OrderType.LIMIT) {
            if (limitPrice.isEmpty()) {
                throw new IllegalArgumentException("LIMIT 주문에는 가격이 필요합니다");
            }
            validateLimitPrice(limitPrice.orElseThrow());
        } else {
            if (limitPrice.isPresent() || timeInForce.orElseThrow() != TimeInForce.DAY) {
                throw new IllegalArgumentException(
                        "수량 기반 MARKET 주문은 가격 없이 DAY만 허용합니다"
                );
            }
        }
        if (timeInForce.orElseThrow() == TimeInForce.CLS
                && orderType != OrderType.LIMIT) {
            throw new IllegalArgumentException("CLS는 LIMIT 주문에만 허용됩니다");
        }
    }

    private static void validateQuantity(
            OrderSide side,
            OrderType orderType,
            Quantity quantity
    ) {
        int scale = normalizedScale(quantity.value());
        boolean fractionalAllowed = side == OrderSide.SELL
                && orderType == OrderType.MARKET;
        if (!fractionalAllowed && scale > 0) {
            throw new IllegalArgumentException(
                    "소수점 수량은 미국주식 MARKET SELL에만 허용됩니다"
            );
        }
        if (fractionalAllowed && scale > 6) {
            throw new IllegalArgumentException("소수점 수량은 6자리까지 허용됩니다");
        }
    }

    private static void validateLimitPrice(Price price) {
        requireUsd(price.value(), "limitPrice");
        BigDecimal amount = price.value().amount();
        int maximumScale = amount.compareTo(ONE_DOLLAR) < 0 ? 4 : 2;
        if (normalizedScale(amount) > maximumScale) {
            throw new IllegalArgumentException(
                    "미국주식 지정가 소수 자릿수가 공식 허용 범위를 초과합니다"
            );
        }
    }

    private static void requirePositiveUsd(Money money, String name) {
        requireUsd(money, name);
        if (money.amount().signum() <= 0) {
            throw new IllegalArgumentException(name + "는 0보다 커야 합니다");
        }
    }

    private static void requireUsd(Money money, String name) {
        if (!"USD".equals(money.currency().getCurrencyCode())) {
            throw new IllegalArgumentException(name + " 통화는 USD여야 합니다");
        }
    }

    private static int normalizedScale(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    private static <T> Optional<T> copy(Optional<T> value, String name) {
        return Objects.requireNonNull(value, name).map(Objects::requireNonNull);
    }
}
