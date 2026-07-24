# 프로젝트 작업 규칙

이 파일은 저장소 전체에 적용된다. 상세 요구사항을 이 파일에 복제하지 말고 아래 문서를 먼저 읽는다.

- 프로젝트 목표와 확정 원칙: `docs/PROJECT_CONTEXT.md`
- 시스템·서비스 경계: `docs/ARCHITECTURE.md`
- 주문·정책·리스크: `docs/TRADING_SYSTEM.md`, `docs/SECURITY_AND_RISK.md`
- 데이터 계약·파이프라인·마트: `docs/DATA_ARCHITECTURE.md`
- 기술 선택·도입 시점: `docs/TECHNOLOGY_STRATEGY.md`
- AI Agent 작업: `docs/AI_HARNESS.md`
- 외부 API 작업: `docs/EXTERNAL_INTEGRATIONS.md`
- 테스트·완료 기준: `docs/TEST_STRATEGY.md`
- 단계와 미결정 사항: `docs/ROADMAP.md`, `docs/DECISIONS_PENDING.md`

## 모든 작업에서 지킬 규칙

- 문서와 사용자 대상 설명은 한국어로 작성한다.
- `CONFIRMED`, `RECOMMENDED`, `TBD`, `LATER`, `OUT OF SCOPE` 상태를 보존한다. `TBD`를 임의 확정하지 않는다.
- 현재 단계와 승인된 범위를 확인하고, 다른 단계의 기능을 선행 구현하지 않는다.
- 실제 계좌·주문 API 호출, 실주문, `live-auto` 활성화, 운영 배포는 사용자의 별도 명시적 승인 없이는 금지한다.
- 기본 실행 모드는 실주문이 불가능해야 한다. 단일 설정값이나 실수로 운영 거래가 활성화되어서는 안 된다.
- 비밀정보, 토큰, 계좌번호, 개인정보를 코드·Fixture·문서·로그·Git에 저장하지 않는다. `.env`는 커밋하지 않는다.
- Broker Credential은 Order Executor만 접근한다. AI, Dashboard, Airflow, 보고서, Trading Core에 주문 자격증명을 전달하지 않는다.
- AI는 정책·리스크 판정을 우회하거나 종목·가격·수량을 직접 지정해 주문할 수 없다. 자유 SQL·셸·임의 네트워크 도구를 제공하지 않는다.
- 불확실한 시장·데이터·계좌·주문 상태에서는 Fail-Closed로 신규 주문을 막는다. Kill Switch와 취소·조회 경로는 Kafka에만 의존하지 않는다.
- 모호한 주문 응답을 무조건 재시도하지 않는다. 내부 멱등성, Inbox/Outbox, 상태 조회, Reconciliation을 적용한다.
- 돈·가격·수량·비율 계산에 binary float를 쓰지 않는다. Java는 `BigDecimal`, Python은 `Decimal`, DB는 `NUMERIC`을 사용하고 반올림 규칙을 명시한다.
- 저장 시각은 UTC를 기준으로 하고, 원본 시각·수집 시각·거래소 시간대와 거래일을 구분한다.
- 외부 API 필드·Endpoint·뉴스 권한을 추측하지 않는다. 공식 명세와 라이선스를 확인하고 확인하지 못한 항목은 TODO/TBD로 남긴다.
- 이벤트는 버전이 있는 계약을 사용하고 재처리 가능한 Consumer는 멱등하게 만든다. 전체 Exactly-Once를 주장하지 않는다.
- 정책, 전략, 모델, 프롬프트, 이벤트 스키마와 계산 지표는 버전과 근거 데이터를 감사 가능하게 남긴다.
- 기존 사용자 변경을 보존하고, 파괴적 명령·패키지 설치·시스템 구성 변경은 사전 설명과 승인을 거친다.
- 구현 변경 시 관련 설계 문서, ADR, 데이터 계약, 테스트와 운영 지표 정의를 함께 갱신한다.

## 검증 규칙

- 저장소 구조·문서 링크·Secret·Mock-only 계약 검증: `make verify`
- Python 공통 계약 단위 테스트: `make test-python`
- Java 전체 빌드·JUnit: `mvn --batch-mode --no-transfer-progress verify`
- 로컬 도구가 모두 준비된 경우 전체 검증: `make test`
- 기준 버전은 Python 3.12, Java 25, Maven 3.9.x이며 상세 결정은 `docs/adr/0005-build-and-runtime-baseline.md`를 따른다.
- 금융 계산, 정책, 주문 상태 전이, 멱등성, Replay, Contract, 장애·Kill Switch Race 테스트를 위험도에 비례해 작성한다.
- 완료 전 변경 범위에 해당하는 가장 좁은 테스트부터 전체 관련 테스트까지 실행하고 결과를 보고한다.
- 테스트하지 못한 항목과 이유를 숨기지 않는다. 실제 계좌 호출을 테스트 대용으로 사용하지 않는다.
