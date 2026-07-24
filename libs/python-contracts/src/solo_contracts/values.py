"""가격·수량·비율의 정확한 십진 값 타입."""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from decimal import ROUND_HALF_EVEN
from typing import Self

from .money import Money


@dataclass(frozen=True, slots=True)
class Price:
    value: Money

    def __post_init__(self) -> None:
        if not isinstance(self.value, Money):
            raise TypeError("value는 Money여야 합니다")
        if self.value.amount <= 0:
            raise ValueError("가격은 0보다 커야 합니다")

    @classmethod
    def exact(cls, amount: str, currency: str) -> Self:
        return cls(Money.exact(amount, currency))


@dataclass(frozen=True, slots=True)
class Quantity:
    value: Decimal

    def __post_init__(self) -> None:
        if not isinstance(self.value, Decimal):
            raise TypeError("value는 Decimal이어야 합니다")
        if self.value <= 0:
            raise ValueError("수량은 0보다 커야 합니다")

    @classmethod
    def exact(cls, value: str) -> Self:
        return cls(Decimal(value))

    @classmethod
    def rounded(
        cls,
        value: str,
        scale: int,
        rounding: str = ROUND_HALF_EVEN,
    ) -> Self:
        if scale < 0:
            raise ValueError("scale은 0 이상이어야 합니다")
        quantum = Decimal(1).scaleb(-scale)
        return cls(Decimal(value).quantize(quantum, rounding=rounding))


@dataclass(frozen=True, slots=True)
class Ratio:
    value: Decimal

    def __post_init__(self) -> None:
        if not isinstance(self.value, Decimal):
            raise TypeError("value는 Decimal이어야 합니다")
        if self.value < 0 or self.value > 1:
            raise ValueError("비율은 0 이상 1 이하여야 합니다")

    @classmethod
    def exact(cls, value: str) -> Self:
        return cls(Decimal(value))

    @classmethod
    def rounded(
        cls,
        value: str,
        scale: int,
        rounding: str = ROUND_HALF_EVEN,
    ) -> Self:
        if scale < 0:
            raise ValueError("scale은 0 이상이어야 합니다")
        unrounded = Decimal(value)
        if unrounded < 0 or unrounded > 1:
            raise ValueError("비율은 반올림 전에도 0 이상 1 이하여야 합니다")
        quantum = Decimal(1).scaleb(-scale)
        return cls(unrounded.quantize(quantum, rounding=rounding))
