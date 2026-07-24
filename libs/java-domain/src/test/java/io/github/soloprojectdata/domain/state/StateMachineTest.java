package io.github.soloprojectdata.domain.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StateMachineTest {

    @Test
    void candidate는평가를거쳐야승인된다() {
        assertEquals(
                OrderCandidateStatus.EVALUATING,
                OrderCandidateStatus.CREATED.transitionTo(OrderCandidateStatus.EVALUATING)
        );
        assertThrows(
                InvalidStateTransitionException.class,
                () -> OrderCandidateStatus.CREATED.transitionTo(
                        OrderCandidateStatus.APPROVED
                )
        );
    }

    @Test
    void intent는Ready에서Executor승인으로건너뛸수없다() {
        assertThrows(
                InvalidStateTransitionException.class,
                () -> OrderIntentStatus.READY.transitionTo(
                        OrderIntentStatus.ACCEPTED_BY_EXECUTOR
                )
        );
        assertEquals(
                OrderIntentStatus.DISPATCHED,
                OrderIntentStatus.READY.transitionTo(OrderIntentStatus.DISPATCHED)
        );
    }

    @Test
    void 제출여부가Unknown이면Reservation을해제하거나만료할수없다() {
        assertThrows(
                InvalidStateTransitionException.class,
                () -> RiskReservationStatus.ACTIVE.transitionTo(
                        RiskReservationStatus.RELEASED,
                        BrokerSubmissionCertainty.UNKNOWN
                )
        );
        assertThrows(
                InvalidStateTransitionException.class,
                () -> RiskReservationStatus.ACTIVE.transitionTo(
                        RiskReservationStatus.EXPIRED,
                        BrokerSubmissionCertainty.UNKNOWN
                )
        );
        assertEquals(
                RiskReservationStatus.RELEASED,
                RiskReservationStatus.ACTIVE.transitionTo(
                        RiskReservationStatus.RELEASED,
                        BrokerSubmissionCertainty.NOT_SUBMITTED
                )
        );
    }

    @Test
    void unknownBrokerOrder는직접정상상태로추측할수없다() {
        assertThrows(
                InvalidStateTransitionException.class,
                () -> BrokerOrderStatus.UNKNOWN.transitionTo(
                        BrokerOrderStatus.PENDING,
                        BrokerStateEvidence.BROKER_QUERY
                )
        );
        assertEquals(
                BrokerOrderStatus.RECONCILIATION_REQUIRED,
                BrokerOrderStatus.UNKNOWN.transitionTo(
                        BrokerOrderStatus.RECONCILIATION_REQUIRED,
                        BrokerStateEvidence.BROKER_QUERY
                )
        );
    }

    @Test
    void reconciliationRequired는Reconciliation근거로만복구한다() {
        assertThrows(
                InvalidStateTransitionException.class,
                () -> BrokerOrderStatus.RECONCILIATION_REQUIRED.transitionTo(
                        BrokerOrderStatus.FILLED,
                        BrokerStateEvidence.BROKER_QUERY
                )
        );
        assertEquals(
                BrokerOrderStatus.FILLED,
                BrokerOrderStatus.RECONCILIATION_REQUIRED.transitionTo(
                        BrokerOrderStatus.FILLED,
                        BrokerStateEvidence.RECONCILIATION
                )
        );
    }

    @Test
    void 종결상태에서추가전이를거절한다() {
        assertThrows(
                InvalidStateTransitionException.class,
                () -> BrokerOrderStatus.FILLED.transitionTo(
                        BrokerOrderStatus.PENDING,
                        BrokerStateEvidence.BROKER_QUERY
                )
        );
    }
}
