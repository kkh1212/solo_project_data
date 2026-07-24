package io.github.soloprojectdata.executor.toss;

import io.github.soloprojectdata.domain.Instrument;
import io.github.soloprojectdata.domain.Money;
import io.github.soloprojectdata.domain.Price;
import io.github.soloprojectdata.domain.Quantity;
import io.github.soloprojectdata.domain.order.ExternalOrderProposal;
import io.github.soloprojectdata.domain.order.UsEquityOrderSpec;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Toss OAS 1.2.4의 미국주식 주문 생성 요청을 Fail-Closed로 제한한다.
 *
 * <p>Broker가 선택 사항으로 정의한 {@code clientOrderId}를 내부 계약에서는
 * 필수로 강제한다. 가격 자릿수는 Broker의 묵시적 절삭에 의존하지 않고 초과 입력을
 * 거절한다.</p>
 */
public final class TossOrderRequest {

    private static final Pattern CLIENT_ORDER_ID = Pattern.compile(
            "[a-zA-Z0-9\\-_]{1,36}"
    );
    private static final BigDecimal ONE_DOLLAR = BigDecimal.ONE;

    private final String clientOrderId;
    private final Instrument instrument;
    private final TossOrderSide side;
    private final TossOrderType orderType;
    private final TossTimeInForce timeInForce;
    private final Quantity quantity;
    private final Money orderAmount;
    private final Price price;

    private TossOrderRequest(
            String clientOrderId,
            Instrument instrument,
            TossOrderSide side,
            TossOrderType orderType,
            TossTimeInForce timeInForce,
            Quantity quantity,
            Money orderAmount,
            Price price
    ) {
        this.clientOrderId = requireClientOrderId(clientOrderId);
        this.instrument = Objects.requireNonNull(instrument, "instrument");
        this.side = Objects.requireNonNull(side, "side");
        this.orderType = Objects.requireNonNull(orderType, "orderType");
        this.timeInForce = timeInForce;
        this.quantity = quantity;
        this.orderAmount = orderAmount;
        this.price = price;
        requireUsEquity();
        validateShape();
    }

    public static TossOrderRequest quantityBased(
            String clientOrderId,
            Instrument instrument,
            TossOrderSide side,
            TossOrderType orderType,
            TossTimeInForce timeInForce,
            Quantity quantity,
            Price price
    ) {
        return new TossOrderRequest(
                clientOrderId,
                instrument,
                side,
                orderType,
                timeInForce,
                Objects.requireNonNull(quantity, "quantity"),
                null,
                price
        );
    }

    public static TossOrderRequest amountBasedMarket(
            String clientOrderId,
            Instrument instrument,
            TossOrderSide side,
            Money orderAmount
    ) {
        return new TossOrderRequest(
                clientOrderId,
                instrument,
                side,
                TossOrderType.MARKET,
                null,
                null,
                Objects.requireNonNull(orderAmount, "orderAmount"),
                null
        );
    }

    /**
     * 유효한 외부 정책 제안을 Toss 요청으로 결정론적으로 변환한다.
     */
    public static TossOrderRequest fromExternalProposal(
            ExternalOrderProposal proposal,
            Instant now
    ) {
        Objects.requireNonNull(proposal, "proposal").requireUsableAt(now);
        UsEquityOrderSpec order = proposal.order();
        TossOrderSide side = TossOrderSide.valueOf(order.side().name());
        if (order.orderAmount().isPresent()) {
            return amountBasedMarket(
                    proposal.clientOrderId(),
                    proposal.instrument(),
                    side,
                    order.orderAmount().orElseThrow()
            );
        }
        return quantityBased(
                proposal.clientOrderId(),
                proposal.instrument(),
                side,
                TossOrderType.valueOf(order.orderType().name()),
                TossTimeInForce.valueOf(order.timeInForce().orElseThrow().name()),
                order.quantity().orElseThrow(),
                order.limitPrice().orElse(null)
        );
    }

    public String clientOrderId() {
        return clientOrderId;
    }

    public Instrument instrument() {
        return instrument;
    }

    Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientOrderId", clientOrderId);
        payload.put("symbol", instrument.symbol());
        payload.put("side", side.name());
        payload.put("orderType", orderType.name());
        if (timeInForce != null) {
            payload.put("timeInForce", timeInForce.name());
        }
        if (quantity != null) {
            payload.put("quantity", decimalString(quantity.value()));
        }
        if (orderAmount != null) {
            payload.put("orderAmount", decimalString(orderAmount.amount()));
        }
        if (price != null) {
            payload.put("price", decimalString(price.value().amount()));
        }
        payload.put("confirmHighValueOrder", false);
        return Map.copyOf(payload);
    }

    private void requireUsEquity() {
        if (instrument.market() != io.github.soloprojectdata.domain.Market.US_EQUITIES) {
            throw new IllegalArgumentException("Toss 주문 Adapter는 미국주식만 허용합니다");
        }
    }

    private void validateShape() {
        if ((quantity == null) == (orderAmount == null)) {
            throw new IllegalArgumentException(
                    "quantity와 orderAmount 중 정확히 하나만 필요합니다"
            );
        }
        if (orderAmount != null) {
            requireUsd(orderAmount);
            if (orderAmount.amount().signum() <= 0) {
                throw new IllegalArgumentException("주문 금액은 0보다 커야 합니다");
            }
            if (orderType != TossOrderType.MARKET || timeInForce != null) {
                throw new IllegalArgumentException(
                        "금액 주문은 미국주식 MARKET 주문만 허용합니다"
                );
            }
            return;
        }

        validateQuantityScale();
        if (orderType == TossOrderType.LIMIT) {
            if (price == null) {
                throw new IllegalArgumentException("LIMIT 주문에는 가격이 필요합니다");
            }
            validateUsPrice(price);
            if (timeInForce == null) {
                throw new IllegalArgumentException("LIMIT 주문에는 유효 조건이 필요합니다");
            }
        } else {
            if (price != null) {
                throw new IllegalArgumentException("MARKET 주문에는 가격을 지정할 수 없습니다");
            }
            if (timeInForce != TossTimeInForce.DAY) {
                throw new IllegalArgumentException("수량 기반 MARKET 주문은 DAY만 허용합니다");
            }
        }
        if (timeInForce == TossTimeInForce.CLS && orderType != TossOrderType.LIMIT) {
            throw new IllegalArgumentException("CLS는 LIMIT 주문에만 사용할 수 있습니다");
        }
    }

    private void validateQuantityScale() {
        int scale = normalizedScale(quantity.value());
        boolean fractionalAllowed = orderType == TossOrderType.MARKET
                && side == TossOrderSide.SELL;
        if (!fractionalAllowed && scale > 0) {
            throw new IllegalArgumentException(
                    "소수점 수량은 미국주식 MARKET SELL에만 허용됩니다"
            );
        }
        if (fractionalAllowed && scale > 6) {
            throw new IllegalArgumentException("소수점 수량은 6자리까지 허용됩니다");
        }
    }

    private static void validateUsPrice(Price price) {
        requireUsd(price.value());
        BigDecimal amount = price.value().amount();
        int allowedScale = amount.compareTo(ONE_DOLLAR) < 0 ? 4 : 2;
        if (normalizedScale(amount) > allowedScale) {
            throw new IllegalArgumentException(
                    "미국주식 가격 소수 자릿수가 공식 허용 범위를 초과합니다"
            );
        }
    }

    private static void requireUsd(Money money) {
        if (!"USD".equals(money.currency().getCurrencyCode())) {
            throw new IllegalArgumentException("미국주식 주문 금액과 가격은 USD여야 합니다");
        }
    }

    private static String requireClientOrderId(String value) {
        Objects.requireNonNull(value, "clientOrderId");
        if (!CLIENT_ORDER_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "clientOrderId는 1~36자의 영숫자, '-', '_'만 허용합니다"
            );
        }
        return value;
    }

    private static int normalizedScale(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    private static String decimalString(BigDecimal value) {
        String result = value.stripTrailingZeros().toPlainString();
        if (result.length() > 30) {
            throw new IllegalArgumentException("Decimal 문자열 길이는 30 이하여야 합니다");
        }
        return result;
    }
}
