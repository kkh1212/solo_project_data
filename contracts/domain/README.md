# 안전 도메인 계약

현재 상태는 `2단계 진행 중`이다. 이 계약은 수익 전략이나 정책 수치를 정의하지 않는다.

## 포함 범위

- `Money`, `Price`, `Quantity`, `Ratio`의 정확한 십진 표현
- 원본 offset, UTC 수집 시각, 거래소 시간대와 거래일의 분리
- Candidate, Decision, Reservation, Intent의 타입이 있는 UUID
- 미국주식 Instrument와 외부 주문 제안의 타입이 있는 UUID
- 외부 정책 버전·근거 hash·만료·계좌 별칭과 미국주식 주문 의미
- Candidate, Intent, Reservation, Broker Order 상태 전이
- `UNKNOWN` Broker Order의 직접 복구 금지
- 제출 여부가 `UNKNOWN`인 Reservation의 해제·만료 금지

`state-transitions.csv`는 Java/Python 구현이 따르는 언어 중립 상태 전이 기준이다. 상태 이름과 전이는 버전이 있는 계약으로 변경한다. 이 CSV는 Kafka Event Schema가 아니며 Avro/Protobuf `TBD`를 확정하지 않는다.

## 제외·TBD

- 정확한 거래소·시장 캘린더·세션과 종목 메타데이터
- 주문 정책 허용 범위와 전략별 수량·금액 제한
- 진입·청산 전략과 손절·익절·비중 등 수익·리스크 수치
- Avro/Protobuf wire format
- 외부 정책 Transport·서명·상호 인증

위 항목은 `docs/DECISIONS_PENDING.md`에서 결정되기 전까지 합성 값으로도 추측하지 않는다.
