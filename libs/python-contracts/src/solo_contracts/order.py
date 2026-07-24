"""외부 정책 주문 제안의 Transport 독립 의미 계약."""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from enum import Enum

from .identifiers import ExternalOrderProposalId
from .instrument import Instrument
from .instrument import Market
from .money import Money
from .values import Price
from .values import Quantity

_IDENTIFIER = re.compile(r"^[A-Za-z][A-Za-z0-9._\-]{0,63}$")
_VERSION = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._\-]{0,63}$")
_SHA256 = re.compile(r"^[a-f0-9]{64}$")
_ACCOUNT_ALIAS = re.compile(r"^[a-z][a-z0-9_\-]{2,31}$")
_ONE_DOLLAR = Decimal("1")


class OrderSide(Enum):
    BUY = "BUY"
    SELL = "SELL"


class OrderType(Enum):
    LIMIT = "LIMIT"
    MARKET = "MARKET"


class TimeInForce(Enum):
    DAY = "DAY"
    CLS = "CLS"


@dataclass(frozen=True, slots=True)
class PolicyReference:
    policy_id: str
    policy_version: str
    evidence_sha256: str

    def __post_init__(self) -> None:
        _require_pattern(self.policy_id, _IDENTIFIER, "policy_id")
        _require_pattern(self.policy_version, _VERSION, "policy_version")
        _require_pattern(self.evidence_sha256, _SHA256, "evidence_sha256")


@dataclass(frozen=True, slots=True)
class UsEquityOrderSpec:
    side: OrderSide
    order_type: OrderType
    time_in_force: TimeInForce | None
    quantity: Quantity | None
    order_amount: Money | None
    limit_price: Price | None

    def __post_init__(self) -> None:
        if not isinstance(self.side, OrderSide):
            raise TypeError("side는 OrderSide여야 합니다")
        if not isinstance(self.order_type, OrderType):
            raise TypeError("order_type은 OrderType이어야 합니다")
        if self.time_in_force is not None and not isinstance(
            self.time_in_force,
            TimeInForce,
        ):
            raise TypeError("time_in_force는 TimeInForce여야 합니다")
        if self.quantity is not None and not isinstance(self.quantity, Quantity):
            raise TypeError("quantity는 Quantity여야 합니다")
        if self.order_amount is not None and not isinstance(
            self.order_amount,
            Money,
        ):
            raise TypeError("order_amount는 Money여야 합니다")
        if self.limit_price is not None and not isinstance(
            self.limit_price,
            Price,
        ):
            raise TypeError("limit_price는 Price여야 합니다")
        self._validate_shape()

    @classmethod
    def quantity_based(
        cls,
        side: OrderSide,
        order_type: OrderType,
        time_in_force: TimeInForce,
        quantity: Quantity,
        limit_price: Price | None,
    ) -> UsEquityOrderSpec:
        return cls(
            side=side,
            order_type=order_type,
            time_in_force=time_in_force,
            quantity=quantity,
            order_amount=None,
            limit_price=limit_price,
        )

    @classmethod
    def amount_based_market(
        cls,
        side: OrderSide,
        order_amount: Money,
    ) -> UsEquityOrderSpec:
        return cls(
            side=side,
            order_type=OrderType.MARKET,
            time_in_force=None,
            quantity=None,
            order_amount=order_amount,
            limit_price=None,
        )

    def _validate_shape(self) -> None:
        if (self.quantity is None) == (self.order_amount is None):
            raise ValueError("quantity와 order_amount 중 정확히 하나만 필요합니다")
        if self.order_amount is not None:
            _require_positive_usd(self.order_amount, "order_amount")
            if (
                self.order_type is not OrderType.MARKET
                or self.time_in_force is not None
                or self.limit_price is not None
            ):
                raise ValueError("금액 주문은 미국주식 MARKET 주문만 허용합니다")
            return

        if self.time_in_force is None:
            raise ValueError("수량 주문에는 time_in_force가 필요합니다")
        assert self.quantity is not None
        _validate_quantity(self.side, self.order_type, self.quantity)
        if self.order_type is OrderType.LIMIT:
            if self.limit_price is None:
                raise ValueError("LIMIT 주문에는 가격이 필요합니다")
            _validate_limit_price(self.limit_price)
        elif (
            self.limit_price is not None
            or self.time_in_force is not TimeInForce.DAY
        ):
            raise ValueError("수량 기반 MARKET 주문은 가격 없이 DAY만 허용합니다")


@dataclass(frozen=True, slots=True)
class ExternalOrderProposal:
    proposal_id: ExternalOrderProposalId
    producer_id: str
    policy: PolicyReference
    generated_at: datetime
    expires_at: datetime
    account_alias: str
    instrument: Instrument
    order: UsEquityOrderSpec

    def __post_init__(self) -> None:
        if not isinstance(self.proposal_id, ExternalOrderProposalId):
            raise TypeError("proposal_id는 ExternalOrderProposalId여야 합니다")
        _require_pattern(self.producer_id, _IDENTIFIER, "producer_id")
        if not isinstance(self.policy, PolicyReference):
            raise TypeError("policy는 PolicyReference여야 합니다")
        _require_aware(self.generated_at, "generated_at")
        _require_aware(self.expires_at, "expires_at")
        _require_pattern(self.account_alias, _ACCOUNT_ALIAS, "account_alias")
        if not isinstance(self.instrument, Instrument):
            raise TypeError("instrument는 Instrument여야 합니다")
        if self.instrument.market is not Market.US_EQUITIES:
            raise ValueError("외부 주문 제안은 미국주식만 허용합니다")
        if not isinstance(self.order, UsEquityOrderSpec):
            raise TypeError("order는 UsEquityOrderSpec이어야 합니다")
        if self.expires_at <= self.generated_at:
            raise ValueError("expires_at은 generated_at보다 뒤여야 합니다")

    @property
    def client_order_id(self) -> str:
        return str(self.proposal_id)

    def require_usable_at(self, now: datetime) -> None:
        _require_aware(now, "now")
        if now < self.generated_at or now >= self.expires_at:
            raise ValueError("외부 주문 제안이 아직 유효하지 않거나 만료되었습니다")


def _require_pattern(value: str, pattern: re.Pattern[str], name: str) -> None:
    if not isinstance(value, str):
        raise TypeError(f"{name}은 문자열이어야 합니다")
    if not pattern.fullmatch(value):
        raise ValueError(f"{name} 형식이 올바르지 않습니다")


def _require_aware(value: datetime, name: str) -> None:
    if not isinstance(value, datetime):
        raise TypeError(f"{name}은 datetime이어야 합니다")
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{name}은 offset이 있는 시각이어야 합니다")


def _validate_quantity(
    side: OrderSide,
    order_type: OrderType,
    quantity: Quantity,
) -> None:
    scale = _normalized_scale(quantity.value)
    fractional_allowed = (
        side is OrderSide.SELL and order_type is OrderType.MARKET
    )
    if not fractional_allowed and scale > 0:
        raise ValueError("소수점 수량은 미국주식 MARKET SELL에만 허용됩니다")
    if fractional_allowed and scale > 6:
        raise ValueError("소수점 수량은 6자리까지 허용됩니다")


def _validate_limit_price(price: Price) -> None:
    _require_usd(price.value, "limit_price")
    maximum_scale = 4 if price.value.amount < _ONE_DOLLAR else 2
    if _normalized_scale(price.value.amount) > maximum_scale:
        raise ValueError("미국주식 지정가 소수 자릿수가 공식 범위를 초과합니다")


def _require_positive_usd(money: Money, name: str) -> None:
    _require_usd(money, name)
    if money.amount <= 0:
        raise ValueError(f"{name}은 0보다 커야 합니다")


def _require_usd(money: Money, name: str) -> None:
    if money.currency != "USD":
        raise ValueError(f"{name} 통화는 USD여야 합니다")


def _normalized_scale(value: Decimal) -> int:
    exponent = value.normalize().as_tuple().exponent
    if not isinstance(exponent, int):
        raise ValueError("Decimal은 유한한 값이어야 합니다")
    return max(0, -exponent)
