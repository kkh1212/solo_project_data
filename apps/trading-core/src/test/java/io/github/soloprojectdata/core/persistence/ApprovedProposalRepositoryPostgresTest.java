package io.github.soloprojectdata.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.soloprojectdata.domain.Instrument;
import io.github.soloprojectdata.domain.Price;
import io.github.soloprojectdata.domain.Quantity;
import io.github.soloprojectdata.domain.id.ExternalOrderProposalId;
import io.github.soloprojectdata.domain.id.OrderIntentId;
import io.github.soloprojectdata.domain.id.RiskDecisionId;
import io.github.soloprojectdata.domain.id.RiskReservationId;
import io.github.soloprojectdata.domain.order.ExternalOrderProposal;
import io.github.soloprojectdata.domain.order.OrderSide;
import io.github.soloprojectdata.domain.order.OrderType;
import io.github.soloprojectdata.domain.order.PolicyReference;
import io.github.soloprojectdata.domain.order.TimeInForce;
import io.github.soloprojectdata.domain.order.UsEquityOrderSpec;
import java.math.BigDecimal;
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

class ApprovedProposalRepositoryPostgresTest {

    private static final Instant GENERATED_AT = Instant.parse(
            "2026-07-24T00:00:00Z"
    );
    private static final Instant ACCEPTED_AT = GENERATED_AT.plusSeconds(1);

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private ApprovedProposalRepository repository;

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
            statement.execute("DROP SCHEMA IF EXISTS trading_core CASCADE");
        }
        Flyway.configure()
                .dataSource(dataSource)
                .schemas("trading_core")
                .defaultSchema("trading_core")
                .locations("classpath:db/migration/trading-core")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        repository = new ApprovedProposalRepository(dataSource);
        repository.upsertAccountScope("brokerage-main", true, GENERATED_AT);
    }

    @Test
    void 승인결과와Intent와Outbox를한Transaction으로저장한다() {
        ApprovedProposalCommand command = command(proposal("101", "AAPL"), "201");

        PersistedOrderIntent created = repository.persist(command);
        PersistedOrderIntent duplicate = repository.persist(command);

        assertEquals(PersistedOrderIntent.Status.CREATED, created.status());
        assertEquals(
                PersistedOrderIntent.Status.DUPLICATE_SAME_PROPOSAL,
                duplicate.status()
        );
        assertEquals(created.orderIntentId(), duplicate.orderIntentId());
        assertEquals(1, count("external_proposal_inbox"));
        assertEquals(1, count("risk_decision"));
        assertEquals(1, count("risk_reservation"));
        assertEquals(1, count("order_intent"));
        assertEquals(1, count("transactional_outbox"));
        assertEquals(
                "order.intent.v1",
                jdbc.queryForObject(
                        "SELECT event_type FROM trading_core.transactional_outbox",
                        String.class
                )
        );
    }

    @Test
    void 같은ProposalId의다른내용은Replay충돌로차단한다() {
        repository.persist(command(proposal("101", "AAPL"), "201"));

        ApprovedProposalCommand changed = command(
                proposal("101", "MSFT"),
                "301"
        );

        assertThrows(
                ProposalReplayConflictException.class,
                () -> repository.persist(changed)
        );
        assertEquals(1, count("external_proposal_inbox"));
    }

    @Test
    void 마지막OutboxInsert가실패하면앞선전체Insert를Rollback한다() {
        ApprovedProposalCommand first = command(proposal("101", "AAPL"), "201");
        repository.persist(first);
        ExternalOrderProposal secondProposal = proposal("102", "MSFT");
        ApprovedProposalCommand conflictingOutbox = new ApprovedProposalCommand(
                secondProposal,
                RiskDecisionId.parse(uuid("401")),
                RiskReservationId.parse(uuid("402")),
                OrderIntentId.parse(uuid("403")),
                first.outboxEventId(),
                "safety-v1",
                Optional.of(new BigDecimal("500")),
                Optional.empty(),
                ACCEPTED_AT
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.persist(conflictingOutbox)
        );

        assertEquals(1, count("external_proposal_inbox"));
        assertEquals(1, count("risk_decision"));
        assertEquals(1, count("risk_reservation"));
        assertEquals(1, count("order_intent"));
        assertEquals(1, count("transactional_outbox"));
    }

    @Test
    void 비활성계좌별칭은원자기록전에차단한다() {
        repository.upsertAccountScope("brokerage-main", false, ACCEPTED_AT);

        assertThrows(
                IllegalStateException.class,
                () -> repository.persist(
                        command(proposal("101", "AAPL"), "201")
                )
        );
        assertEquals(0, count("external_proposal_inbox"));
    }

    @Test
    void 불변Intent주문속성은DbTrigger가변경을차단한다() {
        repository.persist(command(proposal("101", "AAPL"), "201"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        """
                        UPDATE trading_core.order_intent
                        SET symbol = 'MSFT'
                        WHERE proposal_id = ?::uuid
                        """,
                        uuid("101")
                )
        );
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM trading_core." + table,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private static ApprovedProposalCommand command(
            ExternalOrderProposal proposal,
            String idBase
    ) {
        int base = Integer.parseInt(idBase);
        return new ApprovedProposalCommand(
                proposal,
                RiskDecisionId.parse(uuid(Integer.toString(base))),
                RiskReservationId.parse(uuid(Integer.toString(base + 1))),
                OrderIntentId.parse(uuid(Integer.toString(base + 2))),
                UUID.fromString(uuid(Integer.toString(base + 3))),
                "safety-v1",
                Optional.of(new BigDecimal("500")),
                Optional.empty(),
                ACCEPTED_AT
        );
    }

    private static ExternalOrderProposal proposal(String id, String symbol) {
        return new ExternalOrderProposal(
                ExternalOrderProposalId.parse(uuid(id)),
                "externalPolicy",
                new PolicyReference(
                        "profitPolicy",
                        "v1",
                        "a".repeat(64)
                ),
                GENERATED_AT,
                GENERATED_AT.plusSeconds(60),
                "brokerage-main",
                Instrument.usEquity(symbol),
                UsEquityOrderSpec.quantityBased(
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        TimeInForce.DAY,
                        Quantity.exact("1"),
                        Price.exact("185.50", "USD")
                )
        );
    }

    private static String uuid(String suffix) {
        return "00000000-0000-0000-0000-" + String.format("%012d", Integer.parseInt(suffix));
    }
}
