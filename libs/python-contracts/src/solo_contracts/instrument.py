"""거래 시장과 종목 식별 계약."""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum

_US_SYMBOL = re.compile(r"^[A-Z0-9.\-]+$")


class Market(Enum):
    """현재 확정된 거래 시장."""

    US_EQUITIES = ("America/New_York", "USD")

    @property
    def exchange_zone(self) -> str:
        return self.value[0]

    @property
    def settlement_currency(self) -> str:
        return self.value[1]


@dataclass(frozen=True)
class Instrument:
    """시장과 Broker 심볼을 함께 보존한다."""

    market: Market
    symbol: str

    def __post_init__(self) -> None:
        if not isinstance(self.market, Market):
            raise TypeError("market은 Market이어야 합니다")
        if not isinstance(self.symbol, str):
            raise TypeError("symbol은 문자열이어야 합니다")
        normalized = self.symbol.strip().upper()
        if not normalized:
            raise ValueError("종목 심볼은 비어 있을 수 없습니다")
        if self.market is Market.US_EQUITIES and not _US_SYMBOL.fullmatch(normalized):
            raise ValueError("미국주식 심볼 형식이 올바르지 않습니다")
        object.__setattr__(self, "symbol", normalized)

    @classmethod
    def us_equity(cls, symbol: str) -> Instrument:
        return cls(Market.US_EQUITIES, symbol)
