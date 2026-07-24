"""원본 시각과 UTC 수집 시각을 분리하는 타입."""

from dataclasses import dataclass
from datetime import date
from datetime import datetime
from datetime import timezone
from zoneinfo import ZoneInfo


def require_aware(value: datetime, field_name: str) -> None:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field_name}은 timezone-aware datetime이어야 합니다")


def require_utc(value: datetime, field_name: str) -> None:
    require_aware(value, field_name)
    if value.utcoffset() != timezone.utc.utcoffset(value):
        raise ValueError(f"{field_name}은 UTC여야 합니다")


@dataclass(frozen=True, slots=True)
class ObservedTime:
    source_timestamp: datetime
    ingested_at: datetime

    def __post_init__(self) -> None:
        require_aware(self.source_timestamp, "source_timestamp")
        require_utc(self.ingested_at, "ingested_at")

    @property
    def source_timestamp_utc(self) -> datetime:
        return self.source_timestamp.astimezone(timezone.utc)


@dataclass(frozen=True, slots=True)
class TradingTime:
    observed_time: ObservedTime
    exchange_zone: ZoneInfo
    business_date: date

    def __post_init__(self) -> None:
        if not isinstance(self.observed_time, ObservedTime):
            raise TypeError("observed_time은 ObservedTime이어야 합니다")
        if not isinstance(self.exchange_zone, ZoneInfo):
            raise TypeError("exchange_zone은 ZoneInfo여야 합니다")
        if type(self.business_date) is not date:
            raise TypeError("business_date는 date여야 합니다")
