from __future__ import annotations

import json
import sys
import unittest
from datetime import datetime
from datetime import timedelta
from datetime import timezone
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
PYTHON_CONTRACTS = PROJECT_ROOT / "libs" / "python-contracts" / "src"
sys.path.insert(0, str(PYTHON_CONTRACTS))

from solo_contracts import ExternalOrderProposal  # noqa: E402
from solo_contracts import ExternalOrderProposalId  # noqa: E402
from solo_contracts import Instrument  # noqa: E402
from solo_contracts import Money  # noqa: E402
from solo_contracts import OrderSide  # noqa: E402
from solo_contracts import OrderType  # noqa: E402
from solo_contracts import PolicyReference  # noqa: E402
from solo_contracts import Price  # noqa: E402
from solo_contracts import Quantity  # noqa: E402
from solo_contracts import TimeInForce  # noqa: E402
from solo_contracts import UsEquityOrderSpec  # noqa: E402

GENERATED_AT = datetime(2026, 7, 24, tzinfo=timezone.utc)
EVIDENCE_HASH = "a" * 64


class ExternalOrderProposalTest(unittest.TestCase):
    def test_유효한제안은uuid를clientOrderId로사용한다(self) -> None:
        proposal = self._proposal()

        self.assertEqual(
            "00000000-0000-0000-0000-000000000101",
            proposal.client_order_id,
        )
        proposal.require_usable_at(GENERATED_AT + timedelta(seconds=1))

    def test_미래와만료제안은차단한다(self) -> None:
        proposal = self._proposal()

        with self.assertRaises(ValueError):
            proposal.require_usable_at(GENERATED_AT - timedelta(microseconds=1))
        with self.assertRaises(ValueError):
            proposal.require_usable_at(GENERATED_AT + timedelta(seconds=60))
        with self.assertRaises(ValueError):
            ExternalOrderProposal(
                proposal_id=proposal.proposal_id,
                producer_id=proposal.producer_id,
                policy=proposal.policy,
                generated_at=GENERATED_AT,
                expires_at=GENERATED_AT,
                account_alias=proposal.account_alias,
                instrument=proposal.instrument,
                order=proposal.order,
            )

    def test_계좌번호형태와정책근거위변조를차단한다(self) -> None:
        with self.assertRaises(ValueError):
            self._proposal(account_alias="12345678901")
        with self.assertRaises(ValueError):
            PolicyReference("profitPolicy", "v1", "A" * 64)

    def test_미국주식주문형태와decimal경계를차단한다(self) -> None:
        with self.assertRaises(ValueError):
            UsEquityOrderSpec.quantity_based(
                side=OrderSide.BUY,
                order_type=OrderType.MARKET,
                time_in_force=TimeInForce.DAY,
                quantity=Quantity.exact("0.5"),
                limit_price=None,
            )
        with self.assertRaises(ValueError):
            UsEquityOrderSpec.quantity_based(
                side=OrderSide.BUY,
                order_type=OrderType.LIMIT,
                time_in_force=TimeInForce.DAY,
                quantity=Quantity.exact("1"),
                limit_price=Price.exact("1.001", "USD"),
            )
        with self.assertRaises(ValueError):
            UsEquityOrderSpec.amount_based_market(
                OrderSide.BUY,
                Money.exact("100", "KRW"),
            )

    def test_jsonSchema의필수필드와언어계약이일치한다(self) -> None:
        schema_path = (
            PROJECT_ROOT
            / "contracts"
            / "internal-api"
            / "external-order-proposal.schema.json"
        )
        schema = json.loads(schema_path.read_text(encoding="utf-8"))

        self.assertEqual(1, schema["properties"]["schemaVersion"]["const"])
        self.assertEqual(
            "US_EQUITIES",
            schema["$defs"]["usEquityInstrument"]["properties"]["market"][
                "const"
            ],
        )
        self.assertEqual(3, len(schema["properties"]["order"]["oneOf"]))
        self.assertEqual(
            {"BUY", "SELL"},
            set(schema["$defs"]["side"]["enum"]),
        )

    def _proposal(
        self,
        account_alias: str = "brokerage-main",
    ) -> ExternalOrderProposal:
        return ExternalOrderProposal(
            proposal_id=ExternalOrderProposalId.parse(
                "00000000-0000-0000-0000-000000000101"
            ),
            producer_id="externalPolicy",
            policy=PolicyReference(
                "profitPolicy",
                "v1",
                EVIDENCE_HASH,
            ),
            generated_at=GENERATED_AT,
            expires_at=GENERATED_AT + timedelta(seconds=60),
            account_alias=account_alias,
            instrument=Instrument.us_equity("AAPL"),
            order=UsEquityOrderSpec.quantity_based(
                side=OrderSide.BUY,
                order_type=OrderType.LIMIT,
                time_in_force=TimeInForce.DAY,
                quantity=Quantity.exact("1"),
                limit_price=Price.exact("185.50", "USD"),
            ),
        )


if __name__ == "__main__":
    unittest.main()
