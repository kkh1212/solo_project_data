# Architecture Decision Records

ADR은 중요한 구조·안전·기술 선택과 그 결과를 기록한다.

## 상태

- `Proposed`: 검토 중
- `Accepted`: 현재 기준선
- `Superseded`: 새 ADR로 대체
- `Rejected`: 채택하지 않음

## 목록

| ADR | 제목 | 상태 |
|---|---|---|
| [0001](0001-system-boundaries.md) | 소수 독립 서비스와 Order Executor 격리 | Accepted |
| [0002](0002-delivery-semantics.md) | At-Least-Once와 애플리케이션 멱등성 | Accepted |
| [0003](0003-live-safety-boundary.md) | 실거래 다중 Gate와 독립 Kill Switch | Accepted |
| [0004](0004-account-reservation-and-submit-path.md) | 계좌 단위 예약과 단일 주문 제출 경로 | Accepted |
| [0005](0005-build-and-runtime-baseline.md) | 1단계 빌드와 런타임 기준선 | Accepted |
| [0006](0006-us-toss-execution-boundary.md) | 미국주식 외부 정책과 Toss 실행 경계 | Accepted |

시장·전략·Schema 형식·공급자·정책 수치처럼 미결정된 사항은 [DECISIONS_PENDING.md](../DECISIONS_PENDING.md)에 두고 결정 시 ADR을 추가한다.
