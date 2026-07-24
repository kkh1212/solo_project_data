# ADR-0007: 외부 주문 제안 의미 계약

- 상태: Accepted
- 결정일: 2026-07-24

## 배경

[ADR-0006](0006-us-toss-execution-boundary.md)은 수익 정책을 외부 시스템이
소유하고 이 저장소가 안전한 실행을 소유하도록 경계를 정했다. 외부 객체를 바로
`Order Intent`라고 부르면 Trading Core의 안전 검증·Reservation·감사 이전에도
실행 승인이 끝난 것처럼 보일 수 있다.

Toss의 `clientOrderId` 멱등성 보장 기간은 10분이므로 외부 시스템의 메시지
중복과 장기 Replay를 Broker 기능에만 맡길 수도 없다.

## 결정

- 외부 정책 객체 이름은 `External Order Proposal`로 정한다.
- 내부 `Order Intent`는 Trading Core가 외부 제안의 인증·Schema·만료·중복,
  실행 안전 정책과 Reservation을 검증한 뒤 별도로 생성한다.
- 의미 계약 버전 1은 다음을 필수로 보존한다.
  - UUID `proposalId`
  - `producerId`
  - 외부 `policyId`, `policyVersion`, 근거 데이터 SHA-256
  - offset이 있는 `generatedAt`, `expiresAt`
  - 계좌번호가 아닌 `accountAlias`
  - 미국주식 Instrument
  - 매수·매도, 주문 유형, 수량 또는 금액, 지정가와 USD
- 사용 시각 조건은 `generatedAt <= now < expiresAt`이다.
- `proposalId`의 표준 UUID 문자열을 Toss `clientOrderId` 입력으로 사용한다.
  길이는 36자이며 공식 허용 형식에 맞는다.
- 미국주식 주문 형태·Decimal 자릿수는 Java/Python 의미 계약과 Toss Adapter
  경계에서 모두 검증한다.
- JSON Schema는 의미 계약의 언어 중립 검증 Projection으로 사용한다. 이
  결정으로 HTTP·Kafka·Avro·Protobuf Transport를 확정하지 않는다.
- 서명·상호 인증·Replay 방어의 Transport 방식은 `TBD`다. 인증 정보는 주문
  Payload가 아니라 검증된 전송·수신 Context에서 다룬다.
- 장기 중복 방지 Source of Truth는 후속 PostgreSQL Inbox·Journal이며 Broker의
  10분 보장은 보조 수단이다.

## 결과

### 장점

- 외부 정책 제안과 내부 실행 승인을 명확히 구분한다.
- 계좌번호와 Broker Credential이 외부 정책 시스템으로 전파되지 않는다.
- Java·Python·JSON Schema와 Toss 요청이 같은 제안 ID와 주문 의미를 사용한다.
- 만료·형태 오류를 Broker 호출 전에 Fail-Closed로 차단한다.

### 비용

- 외부 정책 시스템은 계약 버전과 근거 hash를 제공해야 한다.
- Transport 결정 후 인증 Context와 Schema 변환 계층이 추가된다.
- DB 멱등성·Inbox·Journal이 없으면 아직 실제 주문에 사용할 수 없다.

## 제외와 미결정

- 수익 정책 내용과 수치
- 외부 정책 입력 Transport
- 서명 알고리즘, 인증서·Key 수명주기, 상호 인증 방식
- 계좌 별칭의 운영 저장·회전 방식

## 재검토 조건

- 외부 정책 시스템이 제안 UUID·정책 버전·근거 hash를 제공할 수 없음
- 다계좌·다중 Broker 도입으로 계좌 별칭 또는 멱등성 범위가 변경됨
- 공식 Toss `clientOrderId` 계약이 변경됨
