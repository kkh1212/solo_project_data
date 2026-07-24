package io.github.soloprojectdata.core.persistence;

import io.github.soloprojectdata.domain.order.ExternalOrderProposal;
import io.github.soloprojectdata.domain.order.UsEquityOrderSpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Transport 표현과 무관한 외부 제안 의미 필드의 SHA-256이다.
 */
final class ExternalProposalFingerprint {

    private static final String SEPARATOR = "\u001f";

    private ExternalProposalFingerprint() {
    }

    static String sha256(ExternalOrderProposal proposal) {
        UsEquityOrderSpec order = proposal.order();
        String canonical = String.join(
                SEPARATOR,
                proposal.proposalId().toString(),
                proposal.producerId(),
                proposal.policy().policyId(),
                proposal.policy().policyVersion(),
                proposal.policy().evidenceSha256(),
                proposal.generatedAt().toString(),
                proposal.expiresAt().toString(),
                proposal.accountAlias(),
                proposal.instrument().market().name(),
                proposal.instrument().symbol(),
                order.side().name(),
                order.orderType().name(),
                order.timeInForce().map(Enum::name).orElse(""),
                decimal(order.quantity().map(value -> value.value())),
                decimal(order.orderAmount().map(value -> value.amount())),
                decimal(
                        order.limitPrice().map(value -> value.value().amount())
                ),
                "USD"
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", exception);
        }
    }

    private static String decimal(Optional<BigDecimal> value) {
        return value
                .map(number -> number.stripTrailingZeros().toPlainString())
                .orElse("");
    }
}
