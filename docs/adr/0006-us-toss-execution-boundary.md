# ADR-0006: 미국주식 외부 정책과 Toss 실행 경계

- 상태: Accepted
- 결정일: 2026-07-24

## 배경

사용자는 미국주식을 Toss Securities Open API로 실제 거래하는 것을 최종 목표로
확정했다. 수익을 만드는 전략과 거래 정책은 다른 환경에서 구현 중이므로 이
저장소에서 같은 정책을 중복 구현하면 정책 소유권과 버전 추적이 불명확해진다.

외부 정책 시스템이 Broker API를 직접 호출하게 하면 Credential 격리,
Reservation, 멱등성, Kill Switch, 주문 상태 복구를 우회할 수 있다. 반대로 실제
Broker Adapter 구현 자체를 마지막 단계까지 미루면 공식 계약과 실패 의미를
검증할 수 없다.

## 결정

- 거래 시장은 미국주식으로 확정한다. 기본 시장 시간대는
  `America/New_York`, 주문 통화는 `USD`다.
- 실제 Broker 목표는 Toss Securities Open API로 확정한다.
- 수익 전략과 수익 목적의 정책 수치는 외부 정책 시스템이 소유한다.
- 외부 시스템은 Broker를 직접 호출하지 않고 버전·근거·만료 시각이 있는 불변
  주문 의도를 전달한다. 정확한 Transport와 상호 인증 방식은 `TBD`다.
- 이 저장소는 주문 의도 Schema 검증, 계좌 별칭 매핑, Reservation 확인, 실행
  Gate, 멱등성, Broker 제출, 조회·취소와 Reconciliation을 소유한다.
- 공식 OAS 기반 Adapter 코드는 2단계부터 구현할 수 있지만 기본 애플리케이션에는
  연결하지 않는다. 기본 Bean과 실행 설정은 계속 `MockBrokerGateway`,
  `mock-only`다.
- 실제 자격증명 등록, 실계좌 읽기, 개별 실주문, `live-auto` 활성화는 서로 다른
  승인과 운영 Gate로 분리한다.
- 수익 정책을 외부화해도 Allowlist, 주문 만료, 계좌·시장 상태, Kill Switch,
  중복 방지와 Reconciliation 같은 실행 안전 정책은 생략하지 않는다.

## 결과

### 장점

- 수익 정책과 Broker Credential의 소유권이 분리된다.
- 실제 API 오류와 상태 계약을 합성 Mock HTTP 테스트로 조기에 검증할 수 있다.
- 외부 정책 구현이 바뀌어도 주문 제출과 복구의 안전 계약을 유지할 수 있다.

### 비용

- 외부 정책 시스템과 주문 의도의 인증·버전 호환성 계약이 필요하다.
- 미국 시장의 DST, 야간 운영, 환전·결제와 세션 제약을 검증해야 한다.
- 실계좌 연동 전에 운영 Secret Manager, IP Allowlist와 독립 Kill 경로가
  필요하다.

## 제외와 미결정

- 이 결정은 주문 종류, 시간외 거래, 종목 Allowlist, 금액·손실 한도와 수익
  정책 수치를 확정하지 않는다.
- 공식 Sandbox/Paper 제공 여부는 현재 OAS에서 확인하지 못했으므로 `TBD`다.
- 현재 개발 서버를 `live-auto` 운영 서버로 승인하지 않는다.

## 재검토 조건

- 외부 정책 시스템이 불변 주문 의도와 버전·근거를 제공할 수 없음
- Toss 공식 API의 권한·Endpoint·상태 계약이 변경됨
- 미국 외 시장이나 다른 Broker를 추가함
