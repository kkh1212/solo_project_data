"""Candidate, Intent, Reservation과 Broker Order의 안전 상태 전이."""

from __future__ import annotations

from enum import Enum
from typing import TypeVar

StatusT = TypeVar("StatusT", bound=Enum)


class InvalidStateTransitionError(RuntimeError):
    pass


def _transition(
    current: StatusT,
    target: StatusT,
    transitions: dict[StatusT, frozenset[StatusT]],
) -> StatusT:
    if target not in transitions.get(current, frozenset()):
        raise InvalidStateTransitionError(
            f"허용되지 않은 상태 전이: {current.name} -> {target.name}"
        )
    return target


class OrderCandidateStatus(str, Enum):
    CREATED = "CREATED"
    EVALUATING = "EVALUATING"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"
    BLOCKED_UNCERTAIN = "BLOCKED_UNCERTAIN"
    MANUAL_REVIEW_REQUIRED = "MANUAL_REVIEW_REQUIRED"
    EXPIRED = "EXPIRED"

    def transition_to(self, target: OrderCandidateStatus) -> OrderCandidateStatus:
        return _transition(self, target, ORDER_CANDIDATE_TRANSITIONS)


ORDER_CANDIDATE_TRANSITIONS = {
    OrderCandidateStatus.CREATED: frozenset({OrderCandidateStatus.EVALUATING}),
    OrderCandidateStatus.EVALUATING: frozenset(
        {
            OrderCandidateStatus.APPROVED,
            OrderCandidateStatus.REJECTED,
            OrderCandidateStatus.BLOCKED_UNCERTAIN,
            OrderCandidateStatus.MANUAL_REVIEW_REQUIRED,
            OrderCandidateStatus.EXPIRED,
        }
    ),
}


class OrderIntentStatus(str, Enum):
    READY = "READY"
    DISPATCHED = "DISPATCHED"
    ACCEPTED_BY_EXECUTOR = "ACCEPTED_BY_EXECUTOR"
    REJECTED_BY_EXECUTOR = "REJECTED_BY_EXECUTOR"
    EXPIRED = "EXPIRED"
    CANCELED = "CANCELED"

    def transition_to(self, target: OrderIntentStatus) -> OrderIntentStatus:
        return _transition(self, target, ORDER_INTENT_TRANSITIONS)


ORDER_INTENT_TRANSITIONS = {
    OrderIntentStatus.READY: frozenset(
        {
            OrderIntentStatus.DISPATCHED,
            OrderIntentStatus.EXPIRED,
            OrderIntentStatus.CANCELED,
        }
    ),
    OrderIntentStatus.DISPATCHED: frozenset(
        {
            OrderIntentStatus.ACCEPTED_BY_EXECUTOR,
            OrderIntentStatus.REJECTED_BY_EXECUTOR,
        }
    ),
}


class BrokerSubmissionCertainty(str, Enum):
    NOT_SUBMITTED = "NOT_SUBMITTED"
    TERMINAL_CONFIRMED = "TERMINAL_CONFIRMED"
    UNKNOWN = "UNKNOWN"


class RiskReservationStatus(str, Enum):
    ACTIVE = "ACTIVE"
    PARTIALLY_CONSUMED = "PARTIALLY_CONSUMED"
    CONSUMED = "CONSUMED"
    RELEASED = "RELEASED"
    EXPIRED = "EXPIRED"

    def transition_to(
        self,
        target: RiskReservationStatus,
        submission_certainty: BrokerSubmissionCertainty,
    ) -> RiskReservationStatus:
        if not isinstance(submission_certainty, BrokerSubmissionCertainty):
            raise TypeError(
                "submission_certainty는 BrokerSubmissionCertainty여야 합니다"
            )
        result = _transition(self, target, RISK_RESERVATION_TRANSITIONS)
        if (
            target in {RiskReservationStatus.RELEASED, RiskReservationStatus.EXPIRED}
            and submission_certainty is BrokerSubmissionCertainty.UNKNOWN
        ):
            raise InvalidStateTransitionError(
                "Broker 제출 여부가 UNKNOWN인 Reservation은 해제·만료할 수 없습니다"
            )
        return result


RISK_RESERVATION_TRANSITIONS = {
    RiskReservationStatus.ACTIVE: frozenset(
        {
            RiskReservationStatus.PARTIALLY_CONSUMED,
            RiskReservationStatus.CONSUMED,
            RiskReservationStatus.RELEASED,
            RiskReservationStatus.EXPIRED,
        }
    ),
    RiskReservationStatus.PARTIALLY_CONSUMED: frozenset(
        {
            RiskReservationStatus.CONSUMED,
            RiskReservationStatus.RELEASED,
            RiskReservationStatus.EXPIRED,
        }
    ),
}


class BrokerStateEvidence(str, Enum):
    DIRECT_RESPONSE = "DIRECT_RESPONSE"
    BROKER_QUERY = "BROKER_QUERY"
    RECONCILIATION = "RECONCILIATION"


class BrokerOrderStatus(str, Enum):
    SUBMITTING = "SUBMITTING"
    PENDING = "PENDING"
    PARTIALLY_FILLED = "PARTIALLY_FILLED"
    FILLED = "FILLED"
    CANCEL_PENDING = "CANCEL_PENDING"
    CANCELED = "CANCELED"
    REPLACE_PENDING = "REPLACE_PENDING"
    REPLACED = "REPLACED"
    BROKER_REJECTED = "BROKER_REJECTED"
    UNKNOWN = "UNKNOWN"
    RECONCILIATION_REQUIRED = "RECONCILIATION_REQUIRED"

    def transition_to(
        self,
        target: BrokerOrderStatus,
        evidence: BrokerStateEvidence,
    ) -> BrokerOrderStatus:
        if not isinstance(evidence, BrokerStateEvidence):
            raise TypeError("evidence는 BrokerStateEvidence여야 합니다")
        result = _transition(self, target, BROKER_ORDER_TRANSITIONS)
        if (
            self is BrokerOrderStatus.RECONCILIATION_REQUIRED
            and evidence is not BrokerStateEvidence.RECONCILIATION
        ):
            raise InvalidStateTransitionError(
                "RECONCILIATION_REQUIRED 상태는 Reconciliation 근거로만 복구할 수 있습니다"
            )
        return result


BROKER_ORDER_TRANSITIONS = {
    BrokerOrderStatus.SUBMITTING: frozenset(
        {
            BrokerOrderStatus.PENDING,
            BrokerOrderStatus.BROKER_REJECTED,
            BrokerOrderStatus.UNKNOWN,
        }
    ),
    BrokerOrderStatus.PENDING: frozenset(
        {
            BrokerOrderStatus.PARTIALLY_FILLED,
            BrokerOrderStatus.FILLED,
            BrokerOrderStatus.CANCEL_PENDING,
            BrokerOrderStatus.REPLACE_PENDING,
            BrokerOrderStatus.UNKNOWN,
        }
    ),
    BrokerOrderStatus.PARTIALLY_FILLED: frozenset(
        {
            BrokerOrderStatus.FILLED,
            BrokerOrderStatus.CANCEL_PENDING,
            BrokerOrderStatus.REPLACE_PENDING,
            BrokerOrderStatus.UNKNOWN,
        }
    ),
    BrokerOrderStatus.CANCEL_PENDING: frozenset(
        {
            BrokerOrderStatus.PARTIALLY_FILLED,
            BrokerOrderStatus.FILLED,
            BrokerOrderStatus.CANCELED,
            BrokerOrderStatus.UNKNOWN,
        }
    ),
    BrokerOrderStatus.REPLACE_PENDING: frozenset(
        {
            BrokerOrderStatus.PARTIALLY_FILLED,
            BrokerOrderStatus.FILLED,
            BrokerOrderStatus.REPLACED,
            BrokerOrderStatus.UNKNOWN,
        }
    ),
    BrokerOrderStatus.UNKNOWN: frozenset(
        {BrokerOrderStatus.RECONCILIATION_REQUIRED}
    ),
    BrokerOrderStatus.RECONCILIATION_REQUIRED: frozenset(
        {
            BrokerOrderStatus.PENDING,
            BrokerOrderStatus.PARTIALLY_FILLED,
            BrokerOrderStatus.FILLED,
            BrokerOrderStatus.CANCELED,
            BrokerOrderStatus.REPLACED,
            BrokerOrderStatus.BROKER_REJECTED,
        }
    ),
}
