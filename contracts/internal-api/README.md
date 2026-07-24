# 내부 API 계약

1단계에는 실행 안전 모드 계약만 둔다.

- `execution-mode.schema.json`은 `mock-only`와 외부 Broker 비활성만 표현한다.
- 실제 계좌·주문 Endpoint와 인증 계약은 존재하지 않는다.
- 내부 서비스 API의 OAS 형식과 버전 정책은 2단계 계약 작업에서 결정한다.
