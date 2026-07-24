from __future__ import annotations

import csv
import sys
import unittest
from datetime import date
from datetime import datetime
from datetime import timezone
from decimal import Decimal
from pathlib import Path
from zoneinfo import ZoneInfo

PROJECT_ROOT = Path(__file__).resolve().parents[2]
PYTHON_CONTRACTS = PROJECT_ROOT / "libs" / "python-contracts" / "src"
sys.path.insert(0, str(PYTHON_CONTRACTS))

from solo_contracts import BrokerOrderStatus  # noqa: E402
from solo_contracts import BrokerStateEvidence  # noqa: E402
from solo_contracts import BrokerSubmissionCertainty  # noqa: E402
from solo_contracts import InvalidStateTransitionError  # noqa: E402
from solo_contracts import Instrument  # noqa: E402
from solo_contracts import Market  # noqa: E402
from solo_contracts import ObservedTime  # noqa: E402
from solo_contracts import OrderCandidateId  # noqa: E402
from solo_contracts import OrderCandidateStatus  # noqa: E402
from solo_contracts import OrderIntentId  # noqa: E402
from solo_contracts import OrderIntentStatus  # noqa: E402
from solo_contracts import Price  # noqa: E402
from solo_contracts import Quantity  # noqa: E402
from solo_contracts import Ratio  # noqa: E402
from solo_contracts import RiskReservationStatus  # noqa: E402
from solo_contracts import TradingTime  # noqa: E402
from solo_contracts.states import BROKER_ORDER_TRANSITIONS  # noqa: E402
from solo_contracts.states import ORDER_CANDIDATE_TRANSITIONS  # noqa: E402
from solo_contracts.states import ORDER_INTENT_TRANSITIONS  # noqa: E402
from solo_contracts.states import RISK_RESERVATION_TRANSITIONS  # noqa: E402


class DecimalValueTypesTest(unittest.TestCase):
    def test_가격과수량은0보다커야한다(self) -> None:
        self.assertEqual(Decimal("100.25"), Price.exact("100.25", "USD").value.amount)
        with self.assertRaises(ValueError):
            Price.exact("0", "USD")
        with self.assertRaises(ValueError):
            Quantity.exact("-1")

    def test_수량반올림을명시한다(self) -> None:
        self.assertEqual(Decimal("1.2"), Quantity.rounded("1.25", 1).value)
        with self.assertRaises(TypeError):
            Quantity(1.25)  # type: ignore[arg-type]

    def test_비율경계를검증한다(self) -> None:
        self.assertEqual(Decimal("0"), Ratio.exact("0").value)
        self.assertEqual(Decimal("1"), Ratio.exact("1").value)
        with self.assertRaises(ValueError):
            Ratio.exact("1.0001")
        with self.assertRaises(ValueError):
            Ratio.rounded("-0.04", 1)
        with self.assertRaises(ValueError):
            Ratio.rounded("1.04", 1)


class IdentifierAndTradingTimeTest(unittest.TestCase):
    def test_미국주식instrument는시장시간대와통화를고정한다(self) -> None:
        instrument = Instrument.us_equity("brk.b")

        self.assertEqual("BRK.B", instrument.symbol)
        self.assertEqual("America/New_York", instrument.market.exchange_zone)
        self.assertEqual("USD", instrument.market.settlement_currency)
        self.assertIs(Market.US_EQUITIES, instrument.market)
        with self.assertRaises(ValueError):
            Instrument.us_equity("AAPL/USD")

    def test_같은uuid라도업무식별자타입은다르다(self) -> None:
        raw_id = "00000000-0000-0000-0000-000000000001"

        self.assertNotEqual(
            OrderCandidateId.parse(raw_id),
            OrderIntentId.parse(raw_id),
        )
        with self.assertRaises(TypeError):
            OrderCandidateId(raw_id)  # type: ignore[arg-type]

    def test_거래일과원본시각을별도로보존한다(self) -> None:
        trading_time = TradingTime(
            observed_time=ObservedTime(
                source_timestamp=datetime.fromisoformat(
                    "2026-07-24T09:00:00+09:00"
                ),
                ingested_at=datetime(2026, 7, 24, 0, 0, 1, tzinfo=timezone.utc),
            ),
            exchange_zone=ZoneInfo("Asia/Seoul"),
            business_date=date(2026, 7, 23),
        )

        self.assertEqual(date(2026, 7, 23), trading_time.business_date)
        self.assertEqual("Asia/Seoul", trading_time.exchange_zone.key)


class StateMachineSafetyTest(unittest.TestCase):
    def test_candidate와intent의단계건너뛰기를거절한다(self) -> None:
        with self.assertRaises(InvalidStateTransitionError):
            OrderCandidateStatus.CREATED.transition_to(
                OrderCandidateStatus.APPROVED
            )
        with self.assertRaises(InvalidStateTransitionError):
            OrderIntentStatus.READY.transition_to(
                OrderIntentStatus.ACCEPTED_BY_EXECUTOR
            )

    def test_unknown이면reservation을해제하거나만료할수없다(self) -> None:
        for target in (
            RiskReservationStatus.RELEASED,
            RiskReservationStatus.EXPIRED,
        ):
            with self.assertRaises(InvalidStateTransitionError):
                RiskReservationStatus.ACTIVE.transition_to(
                    target,
                    BrokerSubmissionCertainty.UNKNOWN,
                )

        self.assertIs(
            RiskReservationStatus.RELEASED,
            RiskReservationStatus.ACTIVE.transition_to(
                RiskReservationStatus.RELEASED,
                BrokerSubmissionCertainty.NOT_SUBMITTED,
            ),
        )
        with self.assertRaises(TypeError):
            RiskReservationStatus.ACTIVE.transition_to(
                RiskReservationStatus.RELEASED,
                "NOT_SUBMITTED",  # type: ignore[arg-type]
            )

    def test_unknownBrokerOrder는reconciliation을거쳐야한다(self) -> None:
        with self.assertRaises(InvalidStateTransitionError):
            BrokerOrderStatus.UNKNOWN.transition_to(
                BrokerOrderStatus.PENDING,
                BrokerStateEvidence.BROKER_QUERY,
            )

        required = BrokerOrderStatus.UNKNOWN.transition_to(
            BrokerOrderStatus.RECONCILIATION_REQUIRED,
            BrokerStateEvidence.BROKER_QUERY,
        )
        with self.assertRaises(InvalidStateTransitionError):
            required.transition_to(
                BrokerOrderStatus.FILLED,
                BrokerStateEvidence.BROKER_QUERY,
            )
        self.assertIs(
            BrokerOrderStatus.FILLED,
            required.transition_to(
                BrokerOrderStatus.FILLED,
                BrokerStateEvidence.RECONCILIATION,
            ),
        )

    def test_언어중립계약과Python전이가일치한다(self) -> None:
        contract_path = (
            PROJECT_ROOT
            / "contracts"
            / "domain"
            / "state-transitions.csv"
        )
        with contract_path.open(encoding="utf-8", newline="") as contract_file:
            contract_rows = list(csv.DictReader(contract_file))
        implementations = {
            "orderCandidate": ORDER_CANDIDATE_TRANSITIONS,
            "orderIntent": ORDER_INTENT_TRANSITIONS,
            "riskReservation": RISK_RESERVATION_TRANSITIONS,
            "brokerOrder": BROKER_ORDER_TRANSITIONS,
        }

        for machine_name, transitions in implementations.items():
            actual = {
                (source.name, target.name)
                for source, targets in transitions.items()
                for target in targets
            }
            expected = {
                (row["from"], row["to"])
                for row in contract_rows
                if row["machine"] == machine_name
            }
            self.assertEqual(expected, actual, machine_name)

        guarded_reservation_rows = [
            row
            for row in contract_rows
            if row["machine"] == "riskReservation"
            and row["to"] in {"RELEASED", "EXPIRED"}
        ]
        self.assertTrue(guarded_reservation_rows)
        self.assertTrue(
            all(
                row["forbidden_submission_certainty"] == "UNKNOWN"
                for row in guarded_reservation_rows
            )
        )

        reconciliation_rows = [
            row
            for row in contract_rows
            if row["machine"] == "brokerOrder"
            and row["from"] == "RECONCILIATION_REQUIRED"
        ]
        self.assertTrue(reconciliation_rows)
        self.assertTrue(
            all(
                row["required_evidence"] == "RECONCILIATION"
                for row in reconciliation_rows
            )
        )


if __name__ == "__main__":
    unittest.main()
