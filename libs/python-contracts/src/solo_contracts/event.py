"""직렬화 형식과 분리된 공통 이벤트 Envelope."""

from dataclasses import dataclass
from datetime import datetime
from typing import Generic
from typing import TypeVar
from uuid import UUID

from .time import require_aware
from .time import require_utc

PayloadT = TypeVar("PayloadT")


@dataclass(frozen=True, slots=True)
class EventEnvelope(Generic[PayloadT]):
    event_id: UUID
    event_type: str
    schema_version: int
    aggregate_id: str
    correlation_id: str
    causation_id: str | None
    trace_id: str
    source: str
    source_timestamp: datetime
    ingested_at: datetime
    produced_at: datetime
    pipeline_run_id: str | None
    replay: bool
    checksum: str
    payload: PayloadT

    def __post_init__(self) -> None:
        for field_name in (
            "event_type",
            "aggregate_id",
            "correlation_id",
            "trace_id",
            "source",
            "checksum",
        ):
            value = getattr(self, field_name)
            if not isinstance(value, str) or not value.strip():
                raise ValueError(f"{field_name}은 비어 있을 수 없습니다")
        if self.schema_version < 1:
            raise ValueError("schema_version은 1 이상이어야 합니다")
        require_aware(self.source_timestamp, "source_timestamp")
        require_utc(self.ingested_at, "ingested_at")
        require_utc(self.produced_at, "produced_at")
        if self.payload is None:
            raise ValueError("payload는 None일 수 없습니다")
