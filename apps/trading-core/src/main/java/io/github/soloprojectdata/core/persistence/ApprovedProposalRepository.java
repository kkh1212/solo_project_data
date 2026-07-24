package io.github.soloprojectdata.core.persistence;

import io.github.soloprojectdata.domain.id.ExternalOrderProposalId;
import io.github.soloprojectdata.domain.id.OrderIntentId;
import io.github.soloprojectdata.domain.order.ExternalOrderProposal;
import io.github.soloprojectdata.domain.order.UsEquityOrderSpec;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 외부 Proposal Inbox, Risk, Reservation, Intent와 Outbox를 원자적으로 저장한다.
 */
public final class ApprovedProposalRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final TransactionTemplate transaction;

    public ApprovedProposalRepository(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        this.jdbc = new JdbcTemplate(dataSource);
        this.namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource)
        );
    }

    public void upsertAccountScope(
            String accountAlias,
            boolean enabled,
            Instant changedAt
    ) {
        Objects.requireNonNull(accountAlias, "accountAlias");
        Objects.requireNonNull(changedAt, "changedAt");
        jdbc.update(
                """
                INSERT INTO trading_core.account_scope (
                    account_alias, enabled, version, created_at, updated_at
                ) VALUES (?, ?, 0, ?, ?)
                ON CONFLICT (account_alias) DO UPDATE SET
                    enabled = EXCLUDED.enabled,
                    version = trading_core.account_scope.version + 1,
                    updated_at = EXCLUDED.updated_at
                """,
                accountAlias,
                enabled,
                atUtc(changedAt),
                atUtc(changedAt)
        );
    }

    public PersistedOrderIntent persist(ApprovedProposalCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = ExternalProposalFingerprint.sha256(
                command.proposal()
        );
        PersistedOrderIntent existing = findExisting(
                command.proposal().proposalId(),
                fingerprint
        );
        if (existing != null) {
            return existing;
        }

        try {
            return Objects.requireNonNull(
                    transaction.execute(status -> persistNew(command, fingerprint))
            );
        } catch (DuplicateKeyException exception) {
            PersistedOrderIntent raced = findExisting(
                    command.proposal().proposalId(),
                    fingerprint
            );
            if (raced != null) {
                return raced;
            }
            throw exception;
        }
    }

    private PersistedOrderIntent persistNew(
            ApprovedProposalCommand command,
            String fingerprint
    ) {
        ExternalOrderProposal proposal = command.proposal();
        Boolean enabled;
        try {
            enabled = jdbc.queryForObject(
                    """
                    SELECT enabled
                    FROM trading_core.account_scope
                    WHERE account_alias = ?
                    FOR UPDATE
                    """,
                    Boolean.class,
                    proposal.accountAlias()
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException(
                    "등록되지 않은 accountAlias입니다",
                    exception
            );
        }
        if (!Boolean.TRUE.equals(enabled)) {
            throw new IllegalStateException("비활성 accountAlias입니다");
        }

        insertProposal(proposal, fingerprint, command.acceptedAt());
        jdbc.update(
                """
                INSERT INTO trading_core.risk_decision (
                    risk_decision_id, proposal_id, decision,
                    safety_policy_version, decided_at
                ) VALUES (?, ?, 'APPROVED', ?, ?)
                """,
                command.riskDecisionId().value(),
                proposal.proposalId().value(),
                command.safetyPolicyVersion(),
                atUtc(command.acceptedAt())
        );
        insertIntent(command);
        insertReservation(command);
        jdbc.update(
                """
                INSERT INTO trading_core.transactional_outbox (
                    event_id, aggregate_type, aggregate_id, event_type,
                    event_version, partition_key, payload, status,
                    attempt_count, next_attempt_at, created_at, published_at
                ) VALUES (
                    ?, 'OrderIntent', ?, 'order.intent.v1',
                    1, ?,
                    jsonb_build_object(
                        'schemaVersion', 1,
                        'orderIntentId', CAST(? AS TEXT),
                        'proposalId', CAST(? AS TEXT),
                        'accountAlias', ?
                    ),
                    'PENDING', 0, ?, ?, NULL
                )
                """,
                command.outboxEventId(),
                command.orderIntentId().value(),
                proposal.accountAlias(),
                command.orderIntentId().value(),
                proposal.proposalId().value(),
                proposal.accountAlias(),
                atUtc(command.acceptedAt()),
                atUtc(command.acceptedAt())
        );
        return new PersistedOrderIntent(
                PersistedOrderIntent.Status.CREATED,
                command.orderIntentId()
        );
    }

    private void insertProposal(
            ExternalOrderProposal proposal,
            String fingerprint,
            Instant receivedAt
    ) {
        UsEquityOrderSpec order = proposal.order();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("proposalId", proposal.proposalId().value())
                .addValue("producerId", proposal.producerId())
                .addValue("policyId", proposal.policy().policyId())
                .addValue("policyVersion", proposal.policy().policyVersion())
                .addValue("evidenceSha", proposal.policy().evidenceSha256())
                .addValue("generatedAt", atUtc(proposal.generatedAt()))
                .addValue("expiresAt", atUtc(proposal.expiresAt()))
                .addValue("accountAlias", proposal.accountAlias())
                .addValue("market", proposal.instrument().market().name())
                .addValue("symbol", proposal.instrument().symbol())
                .addValue("contentSha", fingerprint)
                .addValue("side", order.side().name())
                .addValue("orderType", order.orderType().name())
                .addValue(
                        "timeInForce",
                        order.timeInForce().map(Enum::name).orElse(null),
                        Types.VARCHAR
                )
                .addValue(
                        "quantity",
                        decimalText(order.quantity().map(value -> value.value())),
                        Types.VARCHAR
                )
                .addValue(
                        "orderAmount",
                        decimalText(
                                order.orderAmount().map(value -> value.amount())
                        ),
                        Types.VARCHAR
                )
                .addValue(
                        "limitPrice",
                        decimalText(
                                order.limitPrice().map(
                                        value -> value.value().amount()
                                )
                        ),
                        Types.VARCHAR
                )
                .addValue("receivedAt", atUtc(receivedAt));
        namedJdbc.update(
                """
                INSERT INTO trading_core.external_proposal_inbox (
                    proposal_id, schema_version, producer_id, policy_id,
                    policy_version, evidence_sha256, generated_at, expires_at,
                    account_alias, market, symbol, content_sha256,
                    semantic_payload, received_at
                ) VALUES (
                    :proposalId, 1, :producerId, :policyId,
                    :policyVersion, :evidenceSha, :generatedAt, :expiresAt,
                    :accountAlias, :market, :symbol, :contentSha,
                    jsonb_build_object(
                        'schemaVersion', 1,
                        'proposalId', CAST(:proposalId AS TEXT),
                        'producerId', :producerId,
                        'policy', jsonb_build_object(
                            'policyId', :policyId,
                            'policyVersion', :policyVersion,
                            'evidenceSha256', :evidenceSha
                        ),
                        'generatedAt', CAST(:generatedAt AS TEXT),
                        'expiresAt', CAST(:expiresAt AS TEXT),
                        'accountAlias', :accountAlias,
                        'instrument', jsonb_build_object(
                            'market', :market,
                            'symbol', :symbol
                        ),
                        'order', jsonb_strip_nulls(jsonb_build_object(
                            'side', :side,
                            'orderType', :orderType,
                            'timeInForce', :timeInForce,
                            'quantity', :quantity,
                            'orderAmount', :orderAmount,
                            'limitPrice', :limitPrice,
                            'currency', 'USD'
                        ))
                    ),
                    :receivedAt
                )
                """,
                parameters
        );
    }

    private void insertIntent(ApprovedProposalCommand command) {
        ExternalOrderProposal proposal = command.proposal();
        UsEquityOrderSpec order = proposal.order();
        MapSqlParameterSource parameters = orderParameters(proposal, order)
                .addValue("orderIntentId", command.orderIntentId().value())
                .addValue("riskDecisionId", command.riskDecisionId().value())
                .addValue("clientOrderId", proposal.clientOrderId())
                .addValue("expiresAt", atUtc(proposal.expiresAt()))
                .addValue("createdAt", atUtc(command.acceptedAt()));
        namedJdbc.update(
                """
                INSERT INTO trading_core.order_intent (
                    order_intent_id, proposal_id, risk_decision_id,
                    account_alias, client_order_id, market, symbol, side,
                    order_type, time_in_force, quantity, order_amount,
                    limit_price, currency, status, expires_at, created_at, version
                ) VALUES (
                    :orderIntentId, :proposalId, :riskDecisionId,
                    :accountAlias, :clientOrderId, :market, :symbol, :side,
                    :orderType, :timeInForce, :quantity, :orderAmount,
                    :limitPrice, 'USD', 'READY', :expiresAt, :createdAt, 0
                )
                """,
                parameters
        );
    }

    private void insertReservation(ApprovedProposalCommand command) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(
                        "reservationId",
                        command.riskReservationId().value()
                )
                .addValue("orderIntentId", command.orderIntentId().value())
                .addValue("accountAlias", command.proposal().accountAlias())
                .addValue(
                        "reservedAmount",
                        command.reservedAmount().orElse(null),
                        Types.NUMERIC
                )
                .addValue(
                        "reservedQuantity",
                        command.reservedQuantity().orElse(null),
                        Types.NUMERIC
                )
                .addValue("expiresAt", atUtc(command.proposal().expiresAt()))
                .addValue("createdAt", atUtc(command.acceptedAt()));
        namedJdbc.update(
                """
                INSERT INTO trading_core.risk_reservation (
                    risk_reservation_id, order_intent_id, account_alias,
                    reserved_amount, reserved_quantity, currency, status,
                    submission_certainty, expires_at, created_at, version
                ) VALUES (
                    :reservationId, :orderIntentId, :accountAlias,
                    :reservedAmount, :reservedQuantity, 'USD', 'ACTIVE',
                    'NOT_SUBMITTED', :expiresAt, :createdAt, 0
                )
                """,
                parameters
        );
    }

    private PersistedOrderIntent findExisting(
            ExternalOrderProposalId proposalId,
            String expectedFingerprint
    ) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT p.content_sha256, i.order_intent_id
                FROM trading_core.external_proposal_inbox p
                LEFT JOIN trading_core.order_intent i
                    ON i.proposal_id = p.proposal_id
                WHERE p.proposal_id = ?
                """,
                proposalId.value()
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.getFirst();
        String actualFingerprint = (String) row.get("content_sha256");
        if (!expectedFingerprint.equals(actualFingerprint)) {
            throw new ProposalReplayConflictException(
                    "같은 proposalId에 다른 의미 내용이 감지되었습니다"
            );
        }
        Object rawIntentId = row.get("order_intent_id");
        if (!(rawIntentId instanceof java.util.UUID intentId)) {
            throw new IllegalStateException(
                    "외부 Proposal은 있으나 원자적 Order Intent가 없습니다"
            );
        }
        return new PersistedOrderIntent(
                PersistedOrderIntent.Status.DUPLICATE_SAME_PROPOSAL,
                new OrderIntentId(intentId)
        );
    }

    private static MapSqlParameterSource orderParameters(
            ExternalOrderProposal proposal,
            UsEquityOrderSpec order
    ) {
        return new MapSqlParameterSource()
                .addValue("proposalId", proposal.proposalId().value())
                .addValue("accountAlias", proposal.accountAlias())
                .addValue("market", proposal.instrument().market().name())
                .addValue("symbol", proposal.instrument().symbol())
                .addValue("side", order.side().name())
                .addValue("orderType", order.orderType().name())
                .addValue(
                        "timeInForce",
                        order.timeInForce().map(Enum::name).orElse(null),
                        Types.VARCHAR
                )
                .addValue(
                        "quantity",
                        order.quantity().map(value -> value.value()).orElse(null),
                        Types.NUMERIC
                )
                .addValue(
                        "orderAmount",
                        order.orderAmount().map(value -> value.amount()).orElse(null),
                        Types.NUMERIC
                )
                .addValue(
                        "limitPrice",
                        order.limitPrice()
                                .map(value -> value.value().amount())
                                .orElse(null),
                        Types.NUMERIC
                );
    }

    private static OffsetDateTime atUtc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String decimalText(
            java.util.Optional<BigDecimal> value
    ) {
        return value
                .map(number -> number.stripTrailingZeros().toPlainString())
                .orElse(null);
    }
}
