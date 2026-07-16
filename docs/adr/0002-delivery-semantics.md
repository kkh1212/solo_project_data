# ADR-0002: At-Least-Once와 애플리케이션 멱등성

- 상태: Accepted
- 결정일: 2026-07-16

## 배경

Kafka, PostgreSQL, 외부 Broker REST API를 하나의 분산 트랜잭션으로 묶을 수 없다. Consumer 재시작, 네트워크 Timeout, 서버 재시작과 응답 유실이 발생할 수 있으며 전체 Exactly-Once를 가정하면 중복 주문 위험을 숨기게 된다.

확인 당시 Toss 공식 `clientOrderId` 멱등성 보장 기간도 10분으로 제한된다.

## 결정

- Kafka 처리는 At-Least-Once를 기본으로 한다.
- DB 변경과 이벤트 발행 의도는 Transactional Outbox로 원자 기록한다.
- Consumer는 Inbox·처리 이력·Unique Constraint로 멱등하게 만든다.
- Order Intent에는 영구 내부 idempotency key를 둔다.
- Executor는 네트워크 호출 전에 `SUBMITTING`, 요청 hash와 시각을 기록한다.
- 공식 멱등성은 보조 방어로만 사용한다.
- 모호한 주문 응답은 `UNKNOWN`으로 전환하고 자동 Replay하지 않는다.
- Reconciliation이 내부 상태와 브로커 상태를 복구한다.

## 결과

### 장점

- 실제 외부 API의 한계를 정직하게 반영한다.
- 재시작·Replay·중복 이벤트에서도 업무 중복을 방지한다.
- 모호한 주문의 조사 근거를 남긴다.

### 비용

- Outbox·Inbox·처리 이력과 정리 정책이 필요하다.
- Consumer와 상태 전이 로직이 복잡해진다.
- 주문 DLQ를 일반 데이터처럼 자동 Replay할 수 없다.

## 재검토 조건

공식 API가 더 긴 멱등성·조회 키·체결 이벤트를 제공해도 내부 안전 계층은 제거하지 않고 단순화 가능성만 검토한다.
