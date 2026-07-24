package io.github.soloprojectdata.domain.order;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.soloprojectdata.domain.Instrument;
import io.github.soloprojectdata.domain.Money;
import io.github.soloprojectdata.domain.Price;
import io.github.soloprojectdata.domain.Quantity;
import io.github.soloprojectdata.domain.id.ExternalOrderProposalId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExternalOrderProposalTest {

    private static final Instant GENERATED_AT = Instant.parse(
            "2026-07-24T00:00:00Z"
    );
    private static final String EVIDENCE_HASH = "a".repeat(64);

    @Test
    void 유효한외부제안은Uuid를안정적인ClientOrderId로사용한다() {
        ExternalOrderProposal proposal = proposal("brokerage-main");

        assertEquals(
                "00000000-0000-0000-0000-000000000101",
                proposal.clientOrderId()
        );
        assertDoesNotThrow(
                () -> proposal.requireUsableAt(GENERATED_AT.plusSeconds(1))
        );
    }

    @Test
    void 아직유효하지않거나만료된제안은차단한다() {
        ExternalOrderProposal proposal = proposal("brokerage-main");

        assertThrows(
                IllegalStateException.class,
                () -> proposal.requireUsableAt(GENERATED_AT.minusNanos(1))
        );
        assertThrows(
                IllegalStateException.class,
                () -> proposal.requireUsableAt(GENERATED_AT.plusSeconds(60))
        );
    }

    @Test
    void 계좌번호형태대신명시적별칭만허용한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> proposal("12345678901")
        );
    }

    @Test
    void 정책근거Hash와버전을검증한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyReference("profitPolicy", "v1", "A".repeat(64))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyReference("profitPolicy", "v1", "abc")
        );
    }

    @Test
    void 미국주식주문형태와Decimal경계를입력에서차단한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UsEquityOrderSpec.quantityBased(
                        OrderSide.BUY,
                        OrderType.MARKET,
                        TimeInForce.DAY,
                        Quantity.exact("0.5"),
                        null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> UsEquityOrderSpec.quantityBased(
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        TimeInForce.DAY,
                        Quantity.exact("1"),
                        Price.exact("1.001", "USD")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> UsEquityOrderSpec.amountBasedMarket(
                        OrderSide.BUY,
                        Money.exact("100", "KRW")
                )
        );
    }

    private static ExternalOrderProposal proposal(String accountAlias) {
        return new ExternalOrderProposal(
                ExternalOrderProposalId.parse(
                        "00000000-0000-0000-0000-000000000101"
                ),
                "externalPolicy",
                new PolicyReference("profitPolicy", "v1", EVIDENCE_HASH),
                GENERATED_AT,
                GENERATED_AT.plusSeconds(60),
                accountAlias,
                Instrument.usEquity("AAPL"),
                UsEquityOrderSpec.quantityBased(
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        TimeInForce.DAY,
                        Quantity.exact("1"),
                        Price.exact("185.50", "USD")
                )
        );
    }
}
