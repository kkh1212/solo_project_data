"""binary float를 허용하지 않는 통화 금액 타입."""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from decimal import ROUND_HALF_EVEN
from typing import Self


@dataclass(frozen=True, slots=True)
class Money:
    amount: Decimal
    currency: str

    def __post_init__(self) -> None:
        if not isinstance(self.amount, Decimal):
            raise TypeError("amount는 Decimal이어야 합니다")
        if (
            len(self.currency) != 3
            or not self.currency.isascii()
            or not self.currency.isalpha()
            or not self.currency.isupper()
        ):
            raise ValueError("currency는 대문자 ISO 4217 형식이어야 합니다")

    @classmethod
    def exact(cls, amount: str, currency: str) -> Self:
        return cls(Decimal(amount), currency)

    @classmethod
    def rounded(
        cls,
        amount: str,
        currency: str,
        scale: int,
        rounding: str = ROUND_HALF_EVEN,
    ) -> Self:
        if scale < 0:
            raise ValueError("scale은 0 이상이어야 합니다")
        quantum = Decimal(1).scaleb(-scale)
        return cls(Decimal(amount).quantize(quantum, rounding=rounding), currency)

    def __add__(self, other: object) -> Self:
        if not isinstance(other, Money):
            return NotImplemented
        self._require_same_currency(other)
        return type(self)(self.amount + other.amount, self.currency)

    def __sub__(self, other: object) -> Self:
        if not isinstance(other, Money):
            return NotImplemented
        self._require_same_currency(other)
        return type(self)(self.amount - other.amount, self.currency)

    def _require_same_currency(self, other: Money) -> None:
        if self.currency != other.currency:
            raise ValueError("통화가 다른 Money는 계산할 수 없습니다")
