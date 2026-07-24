package io.github.soloprojectdata.domain.id;

import java.util.Objects;
import java.util.UUID;

/**
 * 외부 정책 시스템이 생성한 주문 제안의 전역 고유 식별자다.
 */
public record ExternalOrderProposalId(UUID value) {

    public ExternalOrderProposalId {
        Objects.requireNonNull(value, "value");
    }

    public static ExternalOrderProposalId parse(String value) {
        return new ExternalOrderProposalId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
