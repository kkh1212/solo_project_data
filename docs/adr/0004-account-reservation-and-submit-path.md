# ADR-0004: 계좌 단위 예약과 단일 주문 제출 경로

- 상태: Accepted
- 결정일: 2026-07-19

## 배경

같은 계좌에서 여러 주문 후보가 동시에 평가되면 각각 동일한 현금, 매도 가능 수량과 종목·섹터 노출 여유를 사용해 개별 검사를 통과할 수 있다. 주문 직전 조회만으로는 Risk 승인 시점의 경쟁 조건을 제거할 수 없다.

기존 Topic 초안에는 `order.intent.v1`과 `order.submit-requested.v1`가 모두 Executor 입력으로 정의되어 있었고 Dispatcher의 책임이 정해지지 않았다. 두 제출 경로는 중복 발행과 소유권 혼동을 만들 수 있다.

## 결정

- Risk 승인 시 Decision, 계좌 단위 `risk_reservation`, 불변 Order Intent와 Transactional Outbox를 하나의 PostgreSQL 트랜잭션으로 생성한다.
- 활성 예약은 현금·매도수량·종목·섹터 노출과 일일 신규 투자 한도 계산에 포함한다.
- 같은 계좌의 승인 트랜잭션은 계좌 상태 행 잠금 또는 동등한 Version 검사로 직렬화한다.
- 주문 제출 경로는 `Trading Core Outbox → order.intent.v1 → Executor Inbox` 하나만 사용한다.
- `order.intent.v1`의 Kafka Key는 `account_id`이며 Producer는 Trading Core, Consumer는 Executor로 제한한다.
- Kafka Partition은 순서를 보조하지만 예약과 한도 판정의 Source of Truth는 PostgreSQL이다.
- 명확한 거절·취소·만료는 예약을 해제하고 부분 체결은 일부를 소비한다. `UNKNOWN`은 Reconciliation 전 예약을 유지한다.
- Executor 재검증 실패는 Intent를 `REJECTED_BY_EXECUTOR`로 종결하고 Broker Order를 생성하지 않는다.

## 결과

### 장점

- 동시에 승인되는 주문이 계좌 자금·수량·노출 한도를 중복 사용하지 않는다.
- 실제 주문으로 이어지는 명령의 Producer와 Consumer가 하나로 명확해진다.
- `UNKNOWN`, 부분 체결, 취소와 만료에서 예약된 위험을 감사하고 복구할 수 있다.

### 비용

- 계좌 단위 잠금·Version 충돌과 Reservation 상태 머신을 구현해야 한다.
- 장시간 유지되는 `UNKNOWN` 예약과 고아 예약을 탐지·운영하는 절차가 필요하다.
- 계좌별 직렬화로 처리량이 제한될 수 있으나 현재 개인 계좌·저빈도 목표에는 적합하다.

## 검토한 대안

- 주문 직전 Broker 조회만 사용: 동시에 진행되는 Intent의 Risk 승인 경쟁 조건을 막지 못해 제외한다.
- Kafka의 계좌 Partition만 사용: DB 트랜잭션·재처리·직접 API 경로를 포괄하지 못해 보조 수단으로만 사용한다.
- Intent와 Submit Requested Topic을 모두 유지: 이중 제출 경로와 불명확한 Dispatcher 소유권 때문에 제외한다.

## 재검토 조건

- 다계좌·다중 Executor에서 계좌별 직렬화가 측정된 처리량 SLO를 반복 위반함
- Broker가 자금·수량 Reservation 또는 원자적 Basket 주문을 공식 지원함
- 승인형·Limited Auto 단계에서 Reservation 수명주기와 운영 절차를 강화해야 함
