"""Mock-only 실행 모드 계약."""

from enum import Enum
from typing import Self


class TradingMode(str, Enum):
    MOCK_ONLY = "mock-only"

    @classmethod
    def require_mock_only(cls, raw_mode: str | None) -> Self:
        if raw_mode is None:
            raise RuntimeError("trading.mode가 없으므로 안전하게 시작을 중단합니다")
        try:
            mode = cls(raw_mode.strip().lower())
        except ValueError as error:
            raise RuntimeError(
                "1단계에서는 trading.mode=mock-only만 허용됩니다"
            ) from error
        if mode is not cls.MOCK_ONLY:
            raise RuntimeError("Mock-only가 아닌 실행 모드는 허용되지 않습니다")
        return mode
