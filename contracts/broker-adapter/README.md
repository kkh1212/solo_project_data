# Broker Adapter 계약

`CONFIRMED` 최종 Broker는 Toss Securities Open API이며 거래 시장은
미국주식이다. [구현 기준선](toss-oas-baseline.json)은 2026-07-24에 공식
OAS 1.2.4를 재확인해 구현에 필요한 항목만 추출한 계약이다. 전체 OAS 사본은
저장소에 복제하지 않고 공식 Canonical URL과 SHA-256을 기록한다.

현재 구현 범위:

- OAuth2 Client Credentials Token 발급
- 계좌번호를 전파하지 않는 계좌 Sequence 조회
- 미국주식 주문 생성, 개별 주문 조회, 주문 취소
- `clientOrderId` 내부 필수화
- Decimal 문자열, 미국주식 수량·가격 자릿수 검증
- 409·429·5xx·Timeout의 `UNKNOWN` 분류와 자동 재시도 금지
- 공식 주소와 테스트 Loopback 외 Endpoint 차단
- 주문별 만료 승인과 다중 실행 Gate

`TBD`:

- 공식 Sandbox/Paper 환경 제공 여부
- 주문 정정의 내부 멱등성·승인·복구 계약
- Rate Limit Group별 운영 예산
- Credential 발급·회전과 운영 Secret Manager

테스트는 합성 자격증명과 Loopback Mock HTTP 서버만 사용한다. 기본
애플리케이션은 계속 `MockBrokerGateway`만 생성하며 Toss API로 연결되지 않는다.
