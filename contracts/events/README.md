# 이벤트 계약

공통 Envelope 필드는 `docs/DATA_ARCHITECTURE.md`의 `CONFIRMED` 원칙을 따른다.

이벤트 직렬화 형식은 `TBD`다. Avro+Schema Registry가 `RECOMMENDED`지만 Protobuf 비교 PoC와 ADR 전에는 Schema 파일을 확정하지 않는다.

1단계의 Java/Python `EventEnvelope`는 언어 내부 불변식 검증용이며 Kafka wire contract가 아니다.
