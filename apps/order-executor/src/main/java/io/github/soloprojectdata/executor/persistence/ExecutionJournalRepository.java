package io.github.soloprojectdata.executor.persistence;

import io.github.soloprojectdata.domain.id.OrderIntentId;
import io.github.soloprojectdata.executor.toss.TossOrderSubmissionOutcome;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executor Inbox, 단일 제출 시도와 UNKNOWN Reconciliation을 원자적으로 기록한다.
 */
public final class ExecutionJournalRepository {

    public enum BeginSubmissionResult {
        STARTED,
        DUPLICATE_ALREADY_STARTED
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public ExecutionJournalRepository(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource)
        );
    }

    public void upsertAccountMapping(
            String accountAlias,
            long brokerAccountSequence,
            boolean enabled,
            Instant changedAt
    ) {
        Objects.requireNonNull(accountAlias, "accountAlias");
        Objects.requireNonNull(changedAt, "changedAt");
        if (brokerAccountSequence <= 0) {
            throw new IllegalArgumentException(
                    "brokerAccountSequence는 0보다 커야 합니다"
            );
        }
        jdbc.update(
                """
                INSERT INTO order_executor.account_mapping (
                    account_alias, broker, broker_account_sequence,
                    enabled, version, created_at, updated_at
                ) VALUES (?, 'TOSS', ?, ?, 0, ?, ?)
                ON CONFLICT (account_alias) DO UPDATE SET
                    broker_account_sequence = EXCLUDED.broker_account_sequence,
                    enabled = EXCLUDED.enabled,
                    version = order_executor.account_mapping.version + 1,
                    updated_at = EXCLUDED.updated_at
                """,
                accountAlias,
                brokerAccountSequence,
                enabled,
                atUtc(changedAt),
                atUtc(changedAt)
        );
    }

    public IntentInboxClaim claimIntent(ClaimIntentCommand command) {
        Objects.requireNonNull(command, "command");
        IntentInboxClaim existing = findExistingClaim(command);
        if (existing != null) {
            return existing;
        }
        try {
            return Objects.requireNonNull(
                    transaction.execute(status -> claimNew(command))
            );
        } catch (DuplicateKeyException exception) {
            IntentInboxClaim raced = findExistingClaim(command);
            if (raced != null) {
                return raced;
            }
            throw exception;
        }
    }

    public BeginSubmissionResult beginSubmission(
            UUID brokerOrderRecordId,
            UUID attemptId,
            String requestSha256,
            Instant startedAt
    ) {
        Objects.requireNonNull(brokerOrderRecordId, "brokerOrderRecordId");
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(requestSha256, "requestSha256");
        Objects.requireNonNull(startedAt, "startedAt");
        if (!requestSha256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException(
                    "requestSha256 형식이 올바르지 않습니다"
            );
        }
        ExistingAttempt existing = findSubmitAttempt(brokerOrderRecordId);
        if (existing != null) {
            return requireSameAttempt(existing, attemptId, requestSha256);
        }
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                int updated = jdbc.update(
                        """
                        UPDATE order_executor.broker_order
                        SET status = 'SUBMITTING',
                            submission_certainty = 'UNKNOWN',
                            updated_at = ?,
                            version = version + 1
                        WHERE broker_order_record_id = ?
                          AND status = 'RECEIVED'
                          AND submission_certainty = 'NOT_SUBMITTED'
                        """,
                        atUtc(startedAt),
                        brokerOrderRecordId
                );
                if (updated != 1) {
                    ExistingAttempt concurrent = findSubmitAttempt(
                            brokerOrderRecordId
                    );
                    if (concurrent != null) {
                        return requireSameAttempt(
                                concurrent,
                                attemptId,
                                requestSha256
                        );
                    }
                    throw new IllegalStateException(
                            "Broker Order가 제출 시작 가능한 상태가 아닙니다"
                    );
                }
                jdbc.update(
                        """
                        INSERT INTO order_executor.order_attempt_journal (
                            attempt_id, broker_order_record_id, attempt_type,
                            request_sha256, outcome, http_status, error_code,
                            started_at, completed_at
                        ) VALUES (?, ?, 'SUBMIT', ?, 'STARTED', NULL, NULL, ?, NULL)
                        """,
                        attemptId,
                        brokerOrderRecordId,
                        requestSha256,
                        atUtc(startedAt)
                );
                return BeginSubmissionResult.STARTED;
            }));
        } catch (DuplicateKeyException exception) {
            ExistingAttempt raced = findSubmitAttempt(brokerOrderRecordId);
            if (raced != null) {
                return requireSameAttempt(raced, attemptId, requestSha256);
            }
            throw exception;
        }
    }

    public void completeSubmission(SubmissionCompletionCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(transaction.execute(status -> {
            AttemptForCompletion attempt = loadAttemptForUpdate(
                    command.attemptId()
            );
            if (!command.outcome().clientOrderId().equals(
                    attempt.clientOrderId()
            )) {
                throw new IntentInboxConflictException(
                        "제출 결과의 clientOrderId가 Intent와 일치하지 않습니다"
                );
            }
            if (!"STARTED".equals(attempt.outcome())) {
                requireSameCompletedOutcome(attempt, command.outcome());
                return Boolean.TRUE;
            }
            TossOrderSubmissionOutcome outcome = command.outcome();
            int completed = jdbc.update(
                    """
                    UPDATE order_executor.order_attempt_journal
                    SET outcome = ?, http_status = ?, error_code = ?, completed_at = ?
                    WHERE attempt_id = ? AND outcome = 'STARTED'
                    """,
                    outcome.status().name(),
                    nullableHttpStatus(outcome.httpStatus()),
                    outcome.errorCode(),
                    atUtc(command.completedAt()),
                    command.attemptId()
            );
            if (completed != 1) {
                throw new IllegalStateException(
                        "제출 시도 완료 상태가 동시에 변경되었습니다"
                );
            }
            updateBrokerOrder(attempt, outcome, command.completedAt());
            updateInbox(attempt.orderIntentId(), outcome, command.completedAt());
            if (outcome.status()
                    == TossOrderSubmissionOutcome.Status
                    .UNKNOWN_REQUIRES_RECONCILIATION) {
                jdbc.update(
                        """
                        INSERT INTO order_executor.reconciliation_case (
                            reconciliation_case_id, broker_order_record_id,
                            reason, status, opened_at, resolved_at,
                            evidence_type, evidence_reference
                        ) VALUES (
                            ?, ?, 'SUBMISSION_OUTCOME_UNKNOWN', 'OPEN', ?,
                            NULL, NULL, NULL
                        )
                        """,
                        command.reconciliationCaseId().orElseThrow(),
                        attempt.brokerOrderRecordId(),
                        atUtc(command.completedAt())
                );
            }
            return Boolean.TRUE;
        }));
    }

    private IntentInboxClaim claimNew(ClaimIntentCommand command) {
        Boolean enabled;
        try {
            enabled = jdbc.queryForObject(
                    """
                    SELECT enabled
                    FROM order_executor.account_mapping
                    WHERE account_alias = ?
                    FOR UPDATE
                    """,
                    Boolean.class,
                    command.accountAlias()
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException(
                    "Executor에 등록되지 않은 accountAlias입니다",
                    exception
            );
        }
        if (!Boolean.TRUE.equals(enabled)) {
            throw new IllegalStateException("Executor accountAlias가 비활성 상태입니다");
        }
        jdbc.update(
                """
                INSERT INTO order_executor.intent_inbox (
                    event_id, order_intent_id, account_alias, client_order_id,
                    payload_sha256, status, received_at, processed_at
                ) VALUES (?, ?, ?, ?, ?, 'RECEIVED', ?, NULL)
                """,
                command.eventId(),
                command.orderIntentId().value(),
                command.accountAlias(),
                command.clientOrderId(),
                command.payloadSha256(),
                atUtc(command.receivedAt())
        );
        jdbc.update(
                """
                INSERT INTO order_executor.broker_order (
                    broker_order_record_id, order_intent_id, account_alias,
                    client_order_id, broker_order_id, status,
                    submission_certainty, last_error_code,
                    created_at, updated_at, version
                ) VALUES (
                    ?, ?, ?, ?, NULL, 'RECEIVED',
                    'NOT_SUBMITTED', NULL, ?, ?, 0
                )
                """,
                command.brokerOrderRecordId(),
                command.orderIntentId().value(),
                command.accountAlias(),
                command.clientOrderId(),
                atUtc(command.receivedAt()),
                atUtc(command.receivedAt())
        );
        return new IntentInboxClaim(
                IntentInboxClaim.Status.CLAIMED,
                command.brokerOrderRecordId()
        );
    }

    private IntentInboxClaim findExistingClaim(ClaimIntentCommand command) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT i.event_id, i.order_intent_id, i.account_alias,
                       i.client_order_id, i.payload_sha256,
                       b.broker_order_record_id
                FROM order_executor.intent_inbox i
                JOIN order_executor.broker_order b
                  ON b.order_intent_id = i.order_intent_id
                WHERE i.event_id = ? OR i.order_intent_id = ?
                """,
                command.eventId(),
                command.orderIntentId().value()
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.getFirst();
        boolean same = command.eventId().equals(row.get("event_id"))
                && command.orderIntentId().value().equals(row.get("order_intent_id"))
                && command.accountAlias().equals(row.get("account_alias"))
                && command.clientOrderId().equals(row.get("client_order_id"))
                && command.payloadSha256().equals(row.get("payload_sha256"));
        if (!same) {
            throw new IntentInboxConflictException(
                    "같은 Inbox 식별자에 다른 Intent 내용이 감지되었습니다"
            );
        }
        return new IntentInboxClaim(
                IntentInboxClaim.Status.DUPLICATE_SAME_INTENT,
                (UUID) row.get("broker_order_record_id")
        );
    }

    private ExistingAttempt findSubmitAttempt(UUID brokerOrderRecordId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT attempt_id, request_sha256
                FROM order_executor.order_attempt_journal
                WHERE broker_order_record_id = ? AND attempt_type = 'SUBMIT'
                """,
                brokerOrderRecordId
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.getFirst();
        return new ExistingAttempt(
                (UUID) row.get("attempt_id"),
                (String) row.get("request_sha256")
        );
    }

    private BeginSubmissionResult requireSameAttempt(
            ExistingAttempt existing,
            UUID attemptId,
            String requestSha256
    ) {
        if (!attemptId.equals(existing.attemptId())
                || !requestSha256.equals(existing.requestSha256())) {
            throw new IntentInboxConflictException(
                    "Broker Order에 다른 제출 시도가 이미 존재합니다"
            );
        }
        return BeginSubmissionResult.DUPLICATE_ALREADY_STARTED;
    }

    private AttemptForCompletion loadAttemptForUpdate(UUID attemptId) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT a.broker_order_record_id, a.outcome,
                           a.http_status, a.error_code,
                           b.order_intent_id, b.client_order_id,
                           b.broker_order_id
                    FROM order_executor.order_attempt_journal a
                    JOIN order_executor.broker_order b
                      ON b.broker_order_record_id = a.broker_order_record_id
                    WHERE a.attempt_id = ?
                    FOR UPDATE OF a, b
                    """,
                    (resultSet, rowNumber) -> new AttemptForCompletion(
                            resultSet.getObject(
                                    "broker_order_record_id",
                                    UUID.class
                            ),
                            new OrderIntentId(
                                    resultSet.getObject(
                                            "order_intent_id",
                                            UUID.class
                                    )
                            ),
                            resultSet.getString("outcome"),
                            resultSet.getObject("http_status", Integer.class),
                            resultSet.getString("error_code"),
                            resultSet.getString("client_order_id"),
                            resultSet.getString("broker_order_id")
                    ),
                    attemptId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException(
                    "완료할 제출 시도를 찾을 수 없습니다",
                    exception
            );
        }
    }

    private void updateBrokerOrder(
            AttemptForCompletion attempt,
            TossOrderSubmissionOutcome outcome,
            Instant completedAt
    ) {
        String status;
        String certainty;
        String brokerOrderId = null;
        switch (outcome.status()) {
            case ACCEPTED -> {
                status = "PENDING";
                certainty = "SUBMITTED";
                brokerOrderId = outcome.brokerOrderId();
            }
            case REJECTED_NOT_SUBMITTED -> {
                status = "BROKER_REJECTED";
                certainty = "NOT_SUBMITTED";
            }
            case UNKNOWN_REQUIRES_RECONCILIATION -> {
                status = "UNKNOWN";
                certainty = "UNKNOWN";
            }
            default -> throw new IllegalStateException("지원하지 않는 제출 결과입니다");
        }
        int updated = jdbc.update(
                """
                UPDATE order_executor.broker_order
                SET broker_order_id = ?, status = ?, submission_certainty = ?,
                    last_error_code = ?, updated_at = ?, version = version + 1
                WHERE broker_order_record_id = ? AND status = 'SUBMITTING'
                """,
                brokerOrderId,
                status,
                certainty,
                outcome.errorCode(),
                atUtc(completedAt),
                attempt.brokerOrderRecordId()
        );
        if (updated != 1) {
            throw new IllegalStateException(
                    "Broker Order가 제출 완료 가능한 상태가 아닙니다"
            );
        }
    }

    private void updateInbox(
            OrderIntentId intentId,
            TossOrderSubmissionOutcome outcome,
            Instant completedAt
    ) {
        boolean unknown = outcome.status()
                == TossOrderSubmissionOutcome.Status
                .UNKNOWN_REQUIRES_RECONCILIATION;
        jdbc.update(
                """
                UPDATE order_executor.intent_inbox
                SET status = ?, processed_at = ?
                WHERE order_intent_id = ?
                """,
                unknown ? "PROCESSING" : "COMPLETED",
                unknown ? null : atUtc(completedAt),
                intentId.value()
        );
    }

    private void requireSameCompletedOutcome(
            AttemptForCompletion attempt,
            TossOrderSubmissionOutcome outcome
    ) {
        if (!outcome.status().name().equals(attempt.outcome())) {
            throw new IntentInboxConflictException(
                    "제출 시도에 다른 완료 결과가 이미 기록되었습니다"
            );
        }
        String expectedBrokerOrderId = outcome.status()
                == TossOrderSubmissionOutcome.Status.ACCEPTED
                ? outcome.brokerOrderId()
                : null;
        if (!Objects.equals(expectedBrokerOrderId, attempt.brokerOrderId())
                || !Objects.equals(outcome.errorCode(), attempt.errorCode())
                || !Objects.equals(
                        nullableHttpStatus(outcome.httpStatus()),
                        attempt.httpStatus()
                )) {
            throw new IntentInboxConflictException(
                    "제출 시도 완료 결과의 세부 내용이 일치하지 않습니다"
            );
        }
    }

    private static Integer nullableHttpStatus(int status) {
        return status == 0 ? null : status;
    }

    private static OffsetDateTime atUtc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record ExistingAttempt(UUID attemptId, String requestSha256) {
    }

    private record AttemptForCompletion(
            UUID brokerOrderRecordId,
            OrderIntentId orderIntentId,
            String outcome,
            Integer httpStatus,
            String errorCode,
            String clientOrderId,
            String brokerOrderId
    ) {
    }
}
