package io.github.soloprojectdata.executor.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.soloprojectdata.domain.id.OrderIntentId;
import io.github.soloprojectdata.executor.toss.TossOrderSubmissionOutcome;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ExecutionJournalRepositoryPostgresTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private ExecutionJournalRepository repository;

    @BeforeEach
    void migratePostgres() throws Exception {
        String url = System.getenv("TEST_POSTGRES_URL");
        String user = System.getenv("TEST_POSTGRES_USER");
        String password = System.getenv("TEST_POSTGRES_PASSWORD");
        Assumptions.assumeTrue(
                url != null && user != null && password != null,
                "CI PostgreSQL 환경에서만 실행"
        );
        DriverManagerDataSource configured = new DriverManagerDataSource(
                url,
                user,
                password
        );
        dataSource = configured;
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()
        ) {
            statement.execute("DROP SCHEMA IF EXISTS order_executor CASCADE");
        }
        Flyway.configure()
                .dataSource(dataSource)
                .schemas("order_executor")
                .defaultSchema("order_executor")
                .locations("classpath:db/migration/order-executor")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        repository = new ExecutionJournalRepository(dataSource);
        repository.upsertAccountMapping(
                "brokerage-main",
                1,
                true,
                NOW
        );
    }

    @Test
    void 같은IntentReplay는기존BrokerOrder를반환한다() {
        ClaimIntentCommand command = claimCommand("101");

        IntentInboxClaim claimed = repository.claimIntent(command);
        IntentInboxClaim duplicate = repository.claimIntent(command);

        assertEquals(IntentInboxClaim.Status.CLAIMED, claimed.status());
        assertEquals(
                IntentInboxClaim.Status.DUPLICATE_SAME_INTENT,
                duplicate.status()
        );
        assertEquals(claimed.brokerOrderRecordId(), duplicate.brokerOrderRecordId());
        assertEquals(1, count("intent_inbox"));
        assertEquals(1, count("broker_order"));
    }

    @Test
    void 같은Inbox식별자의다른Payload는차단한다() {
        ClaimIntentCommand command = claimCommand("101");
        repository.claimIntent(command);
        ClaimIntentCommand changed = new ClaimIntentCommand(
                command.eventId(),
                command.orderIntentId(),
                command.brokerOrderRecordId(),
                command.accountAlias(),
                command.clientOrderId(),
                "b".repeat(64),
                command.receivedAt()
        );

        assertThrows(
                IntentInboxConflictException.class,
                () -> repository.claimIntent(changed)
        );
    }

    @Test
    void 제출시도는한번만시작하고Accepted결과를원자기록한다() {
        ClaimIntentCommand claim = claimCommand("101");
        repository.claimIntent(claim);
        UUID attemptId = UUID.fromString(uuid("201"));

        assertEquals(
                ExecutionJournalRepository.BeginSubmissionResult.STARTED,
                repository.beginSubmission(
                        claim.brokerOrderRecordId(),
                        attemptId,
                        "c".repeat(64),
                        NOW.plusSeconds(1)
                )
        );
        assertEquals(
                ExecutionJournalRepository.BeginSubmissionResult
                        .DUPLICATE_ALREADY_STARTED,
                repository.beginSubmission(
                        claim.brokerOrderRecordId(),
                        attemptId,
                        "c".repeat(64),
                        NOW.plusSeconds(1)
                )
        );
        TossOrderSubmissionOutcome accepted = new TossOrderSubmissionOutcome(
                TossOrderSubmissionOutcome.Status.ACCEPTED,
                "broker-order-1",
                claim.clientOrderId(),
                null,
                200
        );
        repository.completeSubmission(
                new SubmissionCompletionCommand(
                        attemptId,
                        accepted,
                        Optional.empty(),
                        NOW.plusSeconds(2)
                )
        );
        repository.completeSubmission(
                new SubmissionCompletionCommand(
                        attemptId,
                        accepted,
                        Optional.empty(),
                        NOW.plusSeconds(2)
                )
        );

        assertEquals(
                "PENDING",
                brokerValue(claim.brokerOrderRecordId(), "status")
        );
        assertEquals(
                "SUBMITTED",
                brokerValue(claim.brokerOrderRecordId(), "submission_certainty")
        );
        assertEquals(1, count("order_attempt_journal"));
        assertEquals(0, count("reconciliation_case"));
    }

    @Test
    void 다른두번째제출시도는Db와Repository가차단한다() {
        ClaimIntentCommand claim = claimCommand("101");
        repository.claimIntent(claim);
        repository.beginSubmission(
                claim.brokerOrderRecordId(),
                UUID.fromString(uuid("201")),
                "c".repeat(64),
                NOW.plusSeconds(1)
        );

        assertThrows(
                IntentInboxConflictException.class,
                () -> repository.beginSubmission(
                        claim.brokerOrderRecordId(),
                        UUID.fromString(uuid("202")),
                        "d".repeat(64),
                        NOW.plusSeconds(2)
                )
        );
        assertEquals(1, count("order_attempt_journal"));
    }

    @Test
    void 모호한제출은Unknown과ReconciliationCase를같이기록한다() {
        ClaimIntentCommand claim = claimCommand("101");
        repository.claimIntent(claim);
        UUID attemptId = UUID.fromString(uuid("201"));
        repository.beginSubmission(
                claim.brokerOrderRecordId(),
                attemptId,
                "c".repeat(64),
                NOW.plusSeconds(1)
        );
        TossOrderSubmissionOutcome unknown = new TossOrderSubmissionOutcome(
                TossOrderSubmissionOutcome.Status
                        .UNKNOWN_REQUIRES_RECONCILIATION,
                null,
                claim.clientOrderId(),
                "transport-error",
                0
        );

        repository.completeSubmission(
                new SubmissionCompletionCommand(
                        attemptId,
                        unknown,
                        Optional.of(UUID.fromString(uuid("301"))),
                        NOW.plusSeconds(2)
                )
        );

        assertEquals(
                "UNKNOWN",
                brokerValue(claim.brokerOrderRecordId(), "status")
        );
        assertEquals(
                "UNKNOWN",
                brokerValue(claim.brokerOrderRecordId(), "submission_certainty")
        );
        assertEquals(1, count("reconciliation_case"));
        assertEquals(
                "OPEN",
                jdbc.queryForObject(
                        "SELECT status FROM order_executor.reconciliation_case",
                        String.class
                )
        );
    }

    @Test
    void 다른ClientOrderId의제출결과는원장을변경하지않는다() {
        ClaimIntentCommand claim = claimCommand("101");
        repository.claimIntent(claim);
        UUID attemptId = UUID.fromString(uuid("201"));
        repository.beginSubmission(
                claim.brokerOrderRecordId(),
                attemptId,
                "c".repeat(64),
                NOW.plusSeconds(1)
        );
        TossOrderSubmissionOutcome mismatched = new TossOrderSubmissionOutcome(
                TossOrderSubmissionOutcome.Status.ACCEPTED,
                "broker-order-1",
                uuid("999"),
                null,
                200
        );

        assertThrows(
                IntentInboxConflictException.class,
                () -> repository.completeSubmission(
                        new SubmissionCompletionCommand(
                                attemptId,
                                mismatched,
                                Optional.empty(),
                                NOW.plusSeconds(2)
                        )
                )
        );

        assertEquals(
                "SUBMITTING",
                brokerValue(claim.brokerOrderRecordId(), "status")
        );
        assertEquals(
                "STARTED",
                jdbc.queryForObject(
                        "SELECT outcome FROM order_executor.order_attempt_journal",
                        String.class
                )
        );
    }

    @Test
    void 제출시도Identity는DbTrigger가변경을차단한다() {
        ClaimIntentCommand claim = claimCommand("101");
        repository.claimIntent(claim);
        UUID attemptId = UUID.fromString(uuid("201"));
        repository.beginSubmission(
                claim.brokerOrderRecordId(),
                attemptId,
                "c".repeat(64),
                NOW.plusSeconds(1)
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        """
                        UPDATE order_executor.order_attempt_journal
                        SET request_sha256 = ?
                        WHERE attempt_id = ?
                        """,
                        "d".repeat(64),
                        attemptId
                )
        );
    }

    private ClaimIntentCommand claimCommand(String id) {
        return new ClaimIntentCommand(
                UUID.fromString(uuid(Integer.toString(Integer.parseInt(id) + 1))),
                OrderIntentId.parse(uuid(id)),
                UUID.fromString(uuid(Integer.toString(Integer.parseInt(id) + 2))),
                "brokerage-main",
                uuid(id),
                "a".repeat(64),
                NOW
        );
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_executor." + table,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private String brokerValue(UUID brokerOrderRecordId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column
                        + " FROM order_executor.broker_order "
                        + "WHERE broker_order_record_id = ?",
                String.class,
                brokerOrderRecordId
        );
    }

    private static String uuid(String suffix) {
        return "00000000-0000-0000-0000-" + String.format("%012d", Integer.parseInt(suffix));
    }
}
