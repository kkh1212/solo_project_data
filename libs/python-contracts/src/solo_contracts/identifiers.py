"""주문 안전 경계의 타입이 있는 UUID."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Self
from uuid import UUID


@dataclass(frozen=True, slots=True)
class ExternalOrderProposalId:
    value: UUID

    def __post_init__(self) -> None:
        if not isinstance(self.value, UUID):
            raise TypeError("value는 UUID여야 합니다")

    @classmethod
    def parse(cls, value: str) -> Self:
        return cls(UUID(value))

    def __str__(self) -> str:
        return str(self.value)


@dataclass(frozen=True, slots=True)
class OrderCandidateId:
    value: UUID

    def __post_init__(self) -> None:
        if not isinstance(self.value, UUID):
            raise TypeError("value는 UUID여야 합니다")

    @classmethod
    def parse(cls, value: str) -> Self:
        return cls(UUID(value))


@dataclass(frozen=True, slots=True)
class RiskDecisionId:
    value: UUID

    def __post_init__(self) -> None:
        if not isinstance(self.value, UUID):
            raise TypeError("value는 UUID여야 합니다")

    @classmethod
    def parse(cls, value: str) -> Self:
        return cls(UUID(value))


@dataclass(frozen=True, slots=True)
class RiskReservationId:
    value: UUID

    def __post_init__(self) -> None:
        if not isinstance(self.value, UUID):
            raise TypeError("value는 UUID여야 합니다")

    @classmethod
    def parse(cls, value: str) -> Self:
        return cls(UUID(value))


@dataclass(frozen=True, slots=True)
class OrderIntentId:
    value: UUID

    def __post_init__(self) -> None:
        if not isinstance(self.value, UUID):
            raise TypeError("value는 UUID여야 합니다")

    @classmethod
    def parse(cls, value: str) -> Self:
        return cls(UUID(value))
