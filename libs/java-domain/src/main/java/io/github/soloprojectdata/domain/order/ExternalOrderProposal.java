package io.github.soloprojectdata.domain.order;

import io.github.soloprojectdata.domain.Instrument;
import io.github.soloprojectdata.domain.Market;
import io.github.soloprojectdata.domain.id.ExternalOrderProposalId;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 외부 정책 시스템의 제안을 내부 Order Candidate로 바꾸기 전 검증하는 불변 계약이다.
 */
public record ExternalOrderProposal(
        ExternalOrderProposalId proposalId,
        String producerId,
        PolicyReference policy,
        Instant generatedAt,
        Instant expiresAt,
        String accountAlias,
        Instrument instrument,
        UsEquityOrderSpec order
) {

    private static final Pattern PRODUCER_ID = Pattern.compile(
            "[a-zA-Z][a-zA-Z0-9._\\-]{0,63}"
    );
    private static final Pattern ACCOUNT_ALIAS = Pattern.compile(
            "[a-z][a-z0-9_\\-]{2,31}"
    );

    public ExternalOrderProposal {
        Objects.requireNonNull(proposalId, "proposalId");
        producerId = requirePattern(producerId, PRODUCER_ID, "producerId");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        accountAlias = requirePattern(accountAlias, ACCOUNT_ALIAS, "accountAlias");
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(order, "order");
        if (!expiresAt.isAfter(generatedAt)) {
            throw new IllegalArgumentException(
                    "expiresAt은 generatedAt보다 뒤여야 합니다"
            );
        }
        if (instrument.market() != Market.US_EQUITIES) {
            throw new IllegalArgumentException("외부 주문 제안은 미국주식만 허용합니다");
        }
    }

    public void requireUsableAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(generatedAt) || !now.isBefore(expiresAt)) {
            throw new IllegalStateException(
                    "외부 주문 제안이 아직 유효하지 않거나 만료되었습니다"
            );
        }
    }

    /**
     * Broker clientOrderId의 공식 36자 제한에 맞는 안정적인 내부 멱등성 키다.
     */
    public String clientOrderId() {
        return proposalId.toString();
    }

    private static String requirePattern(
            String value,
            Pattern pattern,
            String name
    ) {
        Objects.requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " 형식이 올바르지 않습니다");
        }
        return value;
    }
}
