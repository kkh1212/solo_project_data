package io.github.soloprojectdata.executor.toss;

import java.time.Instant;
import java.util.Objects;

/**
 * 한 주문에만 사용할 수 있는 제출 승인 Snapshot이다.
 */
public record TossOrderAuthorization(
        TossAccountAuthorization accountAccess,
        String approvedClientOrderId,
        String approvalId,
        Instant expiresAt,
        boolean explicitUserActivation,
        boolean killSwitchAllowsNewOrders,
        boolean policyApproved,
        boolean dataFresh,
        boolean reconciliationHealthy
) {

    public TossOrderAuthorization {
        Objects.requireNonNull(accountAccess, "accountAccess");
        approvedClientOrderId = requireNonBlank(
                approvedClientOrderId,
                "approvedClientOrderId"
        );
        approvalId = requireNonBlank(approvalId, "approvalId");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public void requireAllowed(
            long requestedAccountSequence,
            String requestedClientOrderId,
            Instant now
    ) {
        accountAccess.requireAllowed(requestedAccountSequence);
        Objects.requireNonNull(requestedClientOrderId, "requestedClientOrderId");
        Objects.requireNonNull(now, "now");
        if (!approvedClientOrderId.equals(requestedClientOrderId)
                || !expiresAt.isAfter(now)
                || !explicitUserActivation
                || !killSwitchAllowsNewOrders
                || !policyApproved
                || !dataFresh
                || !reconciliationHealthy) {
            throw new IllegalStateException(
                    "Toss 신규 주문 다중 Gate가 모두 충족되지 않았습니다"
            );
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + "는 비어 있을 수 없습니다");
        }
        return value;
    }
}
