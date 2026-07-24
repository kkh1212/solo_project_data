# 내부 API 의미 계약

## 외부 정책 주문 제안

[external-order-proposal.schema.json](external-order-proposal.schema.json)은 외부
정책 시스템이 이 저장소에 전달할 주문 **제안**의 버전 1 의미 계약이다.

- 외부 객체 이름은 `Order Intent`가 아니라 `External Order Proposal`이다.
- 내부 `Order Intent`는 Trading Core가 인증·만료·중복·실행 안전 정책과
  Reservation을 검증한 뒤 별도로 생성한다.
- 외부 제안 ID의 UUID 문자열을 Broker `clientOrderId`의 안정적인 입력으로
  사용하되, PostgreSQL 장기 멱등성 기록이 최종 Source of Truth다.
- 계좌번호 대신 `accountAlias`만 받는다.
- 미국주식, USD, 공식 Toss 주문 형태와 Decimal 문자열만 허용한다.
- `generatedAt <= now < expiresAt`일 때만 사용할 수 있다.

이 JSON Schema는 언어 중립 의미 계약의 검증 Projection이다. HTTP, Kafka,
Avro/Protobuf 같은 Transport와 서명·상호 인증 방식을 확정하지 않는다. 해당
항목은 `TBD`다.

## 실행 모드

[execution-mode.schema.json](execution-mode.schema.json)은 기본 애플리케이션이
계속 `mock-only`이며 외부 Broker를 활성화하지 않는다는 시작 계약이다.
