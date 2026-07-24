# ADR-0008: PostgreSQL 주문 원장과 서비스별 Schema

- 상태: Accepted
- 결정일: 2026-07-24

## 배경

외부 제안 UUID와 Toss `clientOrderId`만으로는 Broker의 10분 멱등성 기간 이후
Replay와 프로세스 재시작을 안전하게 처리할 수 없다. Risk 승인·Reservation·
Order Intent·Outbox가 따로 저장되면 일부만 반영된 상태에서 주문이 제출될 수
있다. Executor도 네트워크 호출 전에 제출 시도를 영속화하지 않으면 Timeout 후
재시작 시 중복 호출 가능성을 판단할 근거가 없다.

Trading Core와 Order Executor가 같은 업무 테이블을 함께 수정하면 Credential과
계좌 식별 경계도 약해진다.

## 결정

- PostgreSQL 18.4, Spring JDBC, Flyway와 공식 PostgreSQL JDBC Driver를
  저장 기반으로 사용한다.
- 서비스별 쓰기 소유권을 다음 Schema로 분리한다.
  - `trading_core`: 계좌 별칭 범위, 외부 Proposal Inbox, Risk Decision,
    Reservation, Order Intent, Transactional Outbox
  - `order_executor`: Toss 계좌 별칭 매핑, Intent Inbox, Broker Order,
    Order Attempt Journal, Reconciliation Case
- Trading Core는 실제 계좌번호나 Toss `accountSeq`를 저장하지 않는다.
  `accountSeq`는 Order Executor Schema에만 저장한다.
- 승인된 외부 Proposal은 Risk Decision·Reservation·Order Intent·Outbox와
  하나의 DB 트랜잭션으로 기록한다.
- 같은 Proposal/Intent의 동일 내용 Replay는 기존 결과를 반환한다. 같은
  식별자에 다른 SHA-256 내용이 오면 충돌로 차단한다.
- Order Executor는 외부 네트워크 호출 전에 `SUBMIT` Attempt를 기록한다.
  Broker Order별 `SUBMIT` Attempt는 부분 Unique Index로 한 번만 허용한다.
- 명확한 수락·미제출 거절·모호한 결과를 구분한다. 모호한 결과는 Broker Order
  `UNKNOWN`, 제출 확실성 `UNKNOWN`, 열린 Reconciliation Case를 같은
  트랜잭션으로 기록한다.
- 금액·가격·수량은 PostgreSQL `NUMERIC`, 시각은 `TIMESTAMPTZ`를 사용한다.
- 외부 Proposal, Risk Decision, Intent 주문 속성, Outbox Payload와 Attempt
  식별 필드는 DB Trigger로 불변성을 보강한다.
- 마이그레이션은 각 애플리케이션이 별도 Flyway Location과 이력을 소유한다.
- 현재 기본 애플리케이션에서는 JDBC·Flyway 자동 구성을 제외한다. 후속
  저장 기반 Runtime Profile과 다중 안전 Gate 없이는 Repository나 외부 DB
  연결이 활성화되지 않는다.
- 통합 테스트는 GitHub Actions의 합성 PostgreSQL 18.4 서비스에서만 실행하며
  실제 계좌 데이터는 사용하지 않는다.

## 결과

### 장점

- Broker 멱등성 기간과 프로세스 메모리에 의존하지 않는 장기 중복 방지가
  가능하다.
- 부분 저장과 제출 시도 유실을 트랜잭션·제약조건으로 차단한다.
- `UNKNOWN` 복구 대상을 누락하지 않고 감사할 수 있다.
- 계좌 Broker 식별자가 Trading Core와 외부 정책 시스템으로 전파되지 않는다.

### 비용

- 두 Schema, Flyway 이력과 서비스별 Repository 운영이 필요하다.
- 실제 DB 연결·백업·복구·권한 분리와 Migration 배포 절차가 추가된다.
- PostgreSQL 장애 시 신규 주문은 Fail-Closed로 중단해야 한다.

## 제외와 미결정

- 운영 PostgreSQL 호스트·HA·백업·RPO/RTO
- 운영 DB Role·Secret Manager와 TLS 설정
- Kafka Outbox Publisher와 Executor Consumer
- Reconciliation Case 해결 Workflow
- Risk 계산과 Reservation 금액 산출 로직

## 재검토 조건

- 다계좌·다중 Executor의 측정된 처리량이 계좌 단위 직렬화로 반복 저하됨
- PostgreSQL 장애 복구 또는 운영 배포 요구가 현재 단일 인스턴스 범위를 초과함
- Broker가 더 강한 영구 멱등성·원자적 Reservation 기능을 공식 제공함
