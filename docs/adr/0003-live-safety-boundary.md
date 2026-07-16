# ADR-0003: 실거래 다중 Gate와 독립 Kill Switch

- 상태: Accepted
- 결정일: 2026-07-16

## 배경

최종 목표는 정상 거래의 자동화지만 단일 설정 실수, Kafka 장애, AI 오류, 계좌 불일치가 실주문으로 이어져서는 안 된다. Kill Switch가 Kafka나 AI에만 의존하면 핵심 장애 중 중단이 불가능할 수 있다.

## 결정

- 모든 비운영 환경의 기본값은 실주문 불가다.
- Order Executor는 시작 시 `DISARMED`다.
- `live-auto`는 운영 환경·Credential·계좌 Allowlist·사용자 활성화·Kill·정책·데이터 최신성·Reconciliation·Audit·배포 버전 Gate를 모두 통과해야 한다.
- Executor는 제출 직전에 Gate를 재검증한다.
- Kill Switch는 인증된 직접 제어 경로, 영속 상태, Executor 로컬 Stop Latch를 사용한다.
- STOP은 재시작 후 유지하며 자동 해제하지 않는다.
- Kafka는 Kill 상태 전파·감사에 사용할 수 있지만 유일한 제어 경로가 아니다.
- Kill의 기본 동작은 신규 주문 중단이며 자동 전량 청산은 별도 범위다.

## 결과

### 장점

- 구성 실수와 핵심 메시징 장애 중에도 신규 주문을 차단한다.
- 재활성화가 의식적인 운영 절차가 된다.
- AI·Dashboard가 주문 권한을 직접 갖지 않는다.

### 비용

- Control Plane, 상태 저장, 직접 Executor 경로가 추가된다.
- Kill Race·재시작·부분 체결 테스트가 필요하다.
- Limited Auto 전 운영자 인증·알림·Runbook을 구현해야 한다.

## 재검토 조건

실거래 환경이나 Broker 권한 모델이 바뀌면 Gate를 강화할 수 있으나 단일 활성화 값과 Kafka 전용 Kill로 축소하지 않는다.
