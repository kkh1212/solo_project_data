package io.github.soloprojectdata.executor.toss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.soloprojectdata.domain.Instrument;
import io.github.soloprojectdata.domain.Money;
import io.github.soloprojectdata.domain.Price;
import io.github.soloprojectdata.domain.Quantity;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TossOrderRequestTest {

    @Test
    void 미국주식지정가주문을Decimal문자열로만든다() {
        TossOrderRequest request = TossOrderRequest.quantityBased(
                "intent-0001",
                Instrument.usEquity("aapl"),
                TossOrderSide.BUY,
                TossOrderType.LIMIT,
                TossTimeInForce.DAY,
                Quantity.exact("2"),
                Price.exact("185.50", "USD")
        );

        Map<String, Object> payload = request.toPayload();

        assertEquals("AAPL", payload.get("symbol"));
        assertEquals("2", payload.get("quantity"));
        assertEquals("185.5", payload.get("price"));
        assertEquals(false, payload.get("confirmHighValueOrder"));
        assertFalse(payload.containsKey("orderAmount"));
    }

    @Test
    void clientOrderId는항상필수이며공식형식을지킨다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TossOrderRequest.quantityBased(
                        "공백 포함",
                        Instrument.usEquity("AAPL"),
                        TossOrderSide.BUY,
                        TossOrderType.LIMIT,
                        TossTimeInForce.DAY,
                        Quantity.exact("1"),
                        Price.exact("10", "USD")
                )
        );
    }

    @Test
    void 소수수량은시장가매도만허용하고최대6자리다() {
        TossOrderRequest request = TossOrderRequest.quantityBased(
                "fractional-sell",
                Instrument.usEquity("AAPL"),
                TossOrderSide.SELL,
                TossOrderType.MARKET,
                TossTimeInForce.DAY,
                Quantity.exact("0.123456"),
                null
        );

        assertEquals("0.123456", request.toPayload().get("quantity"));
        assertThrows(
                IllegalArgumentException.class,
                () -> TossOrderRequest.quantityBased(
                        "fractional-buy",
                        Instrument.usEquity("AAPL"),
                        TossOrderSide.BUY,
                        TossOrderType.MARKET,
                        TossTimeInForce.DAY,
                        Quantity.exact("0.5"),
                        null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> TossOrderRequest.quantityBased(
                        "fractional-overflow",
                        Instrument.usEquity("AAPL"),
                        TossOrderSide.SELL,
                        TossOrderType.MARKET,
                        TossTimeInForce.DAY,
                        Quantity.exact("0.1234567"),
                        null
                )
        );
    }

    @Test
    void 미국주식가격자릿수를묵시적으로절삭하지않는다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TossOrderRequest.quantityBased(
                        "price-overflow",
                        Instrument.usEquity("AAPL"),
                        TossOrderSide.BUY,
                        TossOrderType.LIMIT,
                        TossTimeInForce.DAY,
                        Quantity.exact("1"),
                        Price.exact("1.001", "USD")
                )
        );

        TossOrderRequest underDollar = TossOrderRequest.quantityBased(
                "under-dollar",
                Instrument.usEquity("ABCD"),
                TossOrderSide.BUY,
                TossOrderType.LIMIT,
                TossTimeInForce.DAY,
                Quantity.exact("1"),
                Price.exact("0.1234", "USD")
        );
        assertEquals("0.1234", underDollar.toPayload().get("price"));
    }

    @Test
    void 금액주문은미국주식시장가와Usd만허용한다() {
        TossOrderRequest request = TossOrderRequest.amountBasedMarket(
                "amount-buy",
                Instrument.usEquity("AAPL"),
                TossOrderSide.BUY,
                Money.exact("100.50", "USD")
        );

        assertEquals("100.5", request.toPayload().get("orderAmount"));
        assertFalse(request.toPayload().containsKey("quantity"));
        assertThrows(
                IllegalArgumentException.class,
                () -> TossOrderRequest.amountBasedMarket(
                        "wrong-currency",
                        Instrument.usEquity("AAPL"),
                        TossOrderSide.BUY,
                        Money.exact("100", "KRW")
                )
        );
    }
}
