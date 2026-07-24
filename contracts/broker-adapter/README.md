# Broker Adapter 계약

현재 상태는 `CONFIRMED: MOCK-ONLY`다.

- 외부 Broker Endpoint, 요청/응답 필드, 계좌 식별자와 Credential 계약은 정의하지 않는다.
- Java Order Executor에는 외부 네트워크 능력이 없는 `MockBrokerGateway`만 존재한다.
- 공식 명세와 별도 사용자 승인을 받기 전 실제 Adapter를 추가하지 않는다.
- Mock 주문 상태와 실패 시나리오는 5단계에서 결정론적으로 구현한다.
