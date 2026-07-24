from __future__ import annotations

import json
import sys
import unittest
from datetime import datetime
from datetime import timedelta
from datetime import timezone
from decimal import Decimal
from pathlib import Path
from uuid import uuid4

PROJECT_ROOT = Path(__file__).resolve().parents[2]
PYTHON_CONTRACTS = PROJECT_ROOT / "libs" / "python-contracts" / "src"
sys.path.insert(0, str(PYTHON_CONTRACTS))

from solo_contracts import EventEnvelope  # noqa: E402
from solo_contracts import Money  # noqa: E402
from solo_contracts import ObservedTime  # noqa: E402
from solo_contracts import TradingMode  # noqa: E402


class MoneyTest(unittest.TestCase):
    def test_문자열에서_정확한_십진값을_만든다(self) -> None:
        result = Money.exact("0.10", "KRW") + Money.exact("0.20", "KRW")

        self.assertEqual(Decimal("0.30"), result.amount)

    def test_binary_float를_거절한다(self) -> None:
        with self.assertRaises(TypeError):
            Money(0.1, "KRW")  # type: ignore[arg-type]

    def test_반올림_scale을_명시한다(self) -> None:
        result = Money.rounded("10.125", "USD", 2)

        self.assertEqual(Decimal("10.12"), result.amount)

    def test_다른_통화의_계산을_거절한다(self) -> None:
        with self.assertRaises(ValueError):
            _ = Money.exact("1000", "KRW") + Money.exact("1", "USD")


class TimeTest(unittest.TestCase):
    def test_원본_offset과_utc를_구분한다(self) -> None:
        source = datetime.fromisoformat("2026-07-24T09:00:00+09:00")
        observed = ObservedTime(
            source_timestamp=source,
            ingested_at=datetime(2026, 7, 24, 0, 0, 1, tzinfo=timezone.utc),
        )

        self.assertEqual(timedelta(hours=9), observed.source_timestamp.utcoffset())
        self.assertEqual(
            datetime(2026, 7, 24, 0, 0, tzinfo=timezone.utc),
            observed.source_timestamp_utc,
        )

    def test_naive_datetime을_거절한다(self) -> None:
        with self.assertRaises(ValueError):
            ObservedTime(
                source_timestamp=datetime(2026, 7, 24, 0, 0),
                ingested_at=datetime(2026, 7, 24, 0, 0, tzinfo=timezone.utc),
            )


class EventEnvelopeTest(unittest.TestCase):
    def test_필수_식별자와_utc_시각을_검증한다(self) -> None:
        now = datetime(2026, 7, 24, 0, 0, tzinfo=timezone.utc)

        envelope = EventEnvelope(
            event_id=uuid4(),
            event_type="synthetic.test.v1",
            schema_version=1,
            aggregate_id="synthetic-aggregate",
            correlation_id="synthetic-correlation",
            causation_id=None,
            trace_id="synthetic-trace",
            source="unit-test",
            source_timestamp=now,
            ingested_at=now,
            produced_at=now,
            pipeline_run_id=None,
            replay=False,
            checksum="synthetic-checksum",
            payload={"synthetic": True},
        )

        self.assertFalse(envelope.replay)

    def test_utc가_아닌_저장시각을_거절한다(self) -> None:
        utc_now = datetime(2026, 7, 24, 0, 0, tzinfo=timezone.utc)
        non_utc = datetime.fromisoformat("2026-07-24T09:00:00+09:00")

        with self.assertRaises(ValueError):
            EventEnvelope(
                event_id=uuid4(),
                event_type="synthetic.test.v1",
                schema_version=1,
                aggregate_id="synthetic-aggregate",
                correlation_id="synthetic-correlation",
                causation_id=None,
                trace_id="synthetic-trace",
                source="unit-test",
                source_timestamp=non_utc,
                ingested_at=non_utc,
                produced_at=utc_now,
                pipeline_run_id=None,
                replay=False,
                checksum="synthetic-checksum",
                payload={"synthetic": True},
            )


class SafetyContractTest(unittest.TestCase):
    def test_mock_only만_허용한다(self) -> None:
        self.assertIs(
            TradingMode.MOCK_ONLY,
            TradingMode.require_mock_only("mock-only"),
        )
        with self.assertRaises(RuntimeError):
            TradingMode.require_mock_only("live-auto")

    def test_json_contract도_외부_broker를_금지한다(self) -> None:
        contract_path = (
            PROJECT_ROOT
            / "contracts"
            / "internal-api"
            / "execution-mode.schema.json"
        )
        contract = json.loads(contract_path.read_text(encoding="utf-8"))

        self.assertEqual(
            "mock-only",
            contract["properties"]["mode"]["const"],
        )
        self.assertIs(
            False,
            contract["properties"]["externalBrokerEnabled"]["const"],
        )


if __name__ == "__main__":
    unittest.main()
