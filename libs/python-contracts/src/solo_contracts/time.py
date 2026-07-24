"""원본 시각과 UTC 수집 시각을 분리하는 타입."""

from dataclasses import dataclass
from datetime import datetime
from datetime import timezone


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
