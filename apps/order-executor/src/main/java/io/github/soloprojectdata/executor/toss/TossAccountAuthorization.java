package io.github.soloprojectdata.executor.toss;

import java.util.Objects;

/**
 * 계좌 조회·주문 조회·취소에 필요한 계좌 단위 접근 Gate다.
 *
 * <p>Kill Switch가 내려간 중에도 조회와 취소 경로는 유지되어야 하므로 이 Gate에는
 * 신규 주문 허용 상태를 포함하지 않는다.</p>
 */
public record TossAccountAuthorization(
        TossNetworkAuthorization network,
        long accountSequence,
        boolean accountAllowlisted,
        boolean auditReady,
        boolean directControlPathHealthy
) {

    public TossAccountAuthorization {
        Objects.requireNonNull(network, "network");
        if (accountSequence <= 0) {
            throw new IllegalArgumentException("accountSequence는 0보다 커야 합니다");
        }
    }

    public void requireAllowed(long requestedAccountSequence) {
        network.requireAllowed();
        if (accountSequence != requestedAccountSequence
                || !accountAllowlisted
                || !auditReady
                || !directControlPathHealthy) {
            throw new IllegalStateException(
                    "Toss 계좌 접근 Gate가 모두 충족되지 않았습니다"
            );
        }
    }
}
