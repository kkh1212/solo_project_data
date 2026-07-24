# 개발 로드맵

## 원칙

- 단계는 기간이 아니라 안전 Gate로 이동한다.
- 다음 단계 기술을 포트폴리오 목적으로 선행 도입하지 않는다.
- 실제 API·계좌·주문·운영 활성화는 각 단계에서 별도 사용자 승인을 받는다.
- 새 기술은 문제 측정 → 후보 조사 → PoC → 비교 → ADR → 도입 → 사후 측정 순서로 검토한다.

## 현재 상태

`CONFIRMED` 현재는 **1단계 저장소 기반 Gate 통과, 2단계 안전 도메인 계약 진행 중**이다.

- Java 25·Spring Boot 4.1.0·Maven 3.9.x·Python 3.12 기준선과 CI 구성
- Java/Python `Money`, UTC 시간, 언어 내부 Event Envelope 최소 계약
- Trading Core와 격리된 Order Executor 골격, `mock-only` 시작 Gate
- 미국주식 시장, `America/New_York`, `USD` Instrument 최소 계약
- 최종 Broker는 Toss Securities Open API, 수익 정책은 외부 시스템 소유
- 기본 Bean은 외부 네트워크 능력이 없는 `MockBrokerGateway`
- 기본 구성에 연결되지 않은 OAuth·계좌 목록·주문 생성·조회·취소 Toss Adapter
- 문서 링크·Secret·Mock-only 계약 검증과 Java/Python 단위 테스트
- 빌드 기준 계약과 동적 Maven 의존성 버전 차단
- `Price`·`Quantity`·`Ratio`, 거래 시각, 타입이 있는 업무 UUID
- 언어 중립 상태 전이 CSV와 Java/Python Candidate·Intent·Reservation·Broker Order 상태 머신
- `UNKNOWN` Broker Order의 Reconciliation 강제와 Reservation 해제·만료 차단
- 실제 Credential·Toss 서버 호출·실계좌 조회·주문 없음
- 기본 거래 모드는 코드와 JSON 계약 모두 실주문 불가
- 주문 종류·한도·시간외 거래·Event Schema 등은 `TBD`
- PostgreSQL 18.4 서비스별 Schema·Flyway 마이그레이션·Spring JDBC
  Repository와 CI 통합 테스트
- Kafka·Object Storage·Airflow·dbt 인프라는 아직 없음

2026-07-24 로컬 검증 환경에는 JDK·Maven이 없어 Python과 저장소 검증만 직접 실행할 수 있다. Java와 PostgreSQL 통합 검증은 GitHub Actions에서 수행하며 로컬 시스템 패키지는 사용자 승인 없이 설치하지 않는다. 최초 CI Run [#1](https://github.com/kkh1212/solo_project_data/actions/runs/30068774186), 2단계 안전 계약 CI Run [#3](https://github.com/kkh1212/solo_project_data/actions/runs/30069492337), Toss Adapter CI Run [#5](https://github.com/kkh1212/solo_project_data/actions/runs/30071040816), 외부 Proposal 계약 CI Run [#7](https://github.com/kkh1212/solo_project_data/actions/runs/30071682041)에서 저장소/Python Job과 Java 25 Maven `verify` Job이 모두 통과했다.

## 단계

| 단계 | 범위 | 주요 산출물 | 종료 Gate |
|---|---|---|---|
| 0. 설계 | 요구·경계·위협·TBD·ADR | 현재 `docs/` | 구조 기준선 승인 |
| 1. 저장소 기반 | 저장소 규칙, 문서 검증, Java/Python 골격, CI, Mock-only 설정 | 빌드·테스트 명령, BOM·버전 기준, Python Lock | 실주문 경로·Secret 없음, CI 통과 |
| 2. 계약·도메인 | Money·Time·Event, Instrument, 주문·Reservation 상태, Schema | 공통 계약·상태 머신 Test | Decimal·시간·호환성 Test 통과 |
| 3. 데이터 기반 | Kafka, PostgreSQL, Object Storage, 합성 Collector | Raw→Silver, Inbox/Outbox 기반 | Replay·중복 제거·DQ 통과 |
| 4. 특징·전략 | 결정론적 전략 1개, 비용 모델, 백테스트 | Feature·Signal·Backtest Report | Look-ahead·재현성·비용 검사 |
| 5. 정책·주문 Mock | Policy/Risk, Candidate/Intent/Reservation, Mock Executor, Kill | 주문 상태·Audit·예외 | 동시 한도·중복·UNKNOWN·Kill Race 통과 |
| 6. Mart·Semantic | dbt Mart, Metric Registry, 정적 보고서 | Gold·Lineage·대사 Test | 보고서 수치 대사 통과 |
| 7. 뉴스·AI Harness | 공급자 결정 후 뉴스, Tool Allowlist, 평가 | Recommendation·Agent Scorecard | Injection·근거·비용 기준 통과 |
| 8. 관측·복구 | OTel, Prometheus, Grafana, 장애 주입 | Dashboard·SLO 기준선·Runbook | 복구·알림·백업 Restore 통과 |
| 9. Read-only Shadow | 별도 승인 후 공식 시세, 이후 계좌 조회 | 실데이터 품질·정합성 Report | 주문 권한 없이 관찰 기준 충족 |
| 10. Paper/Mock | 실제 시세 기반 가상 주문 | Paper 성과·안전 Report | 정책 위반·중복 0건 |
| 11. Approval-required | 사용자 승인된 제한 주문 | 승인·예외 UI·Audit | 운영 절차 검증 |
| 12. Limited Auto | 소액·Allowlist·저빈도 자동화 | Limited Auto Report | 안전 지표·운영 기간 충족 |
| 13. Live Auto | 보수적 자동거래 | 운영 SLO·Incident Review | 지속적 Gate 유지 |

## 완료된 1단계: 저장소 기반

설계 문서 저장 이후 가장 작은 구현 단계는 **실거래 기능이 없는 개발 골격**이다.

제안 범위는 다음과 같다.

1. 현재 Git 저장소의 브랜치·검증·문서 갱신 규칙 확정
2. `apps/`, `contracts/`, `platform/`, `tests/`, `fixtures/synthetic/` 최소 디렉터리 생성
3. Java와 Python 버전·빌드 도구 ADR 작성
4. Java·Python의 빈 애플리케이션이 아니라 공통 `Money`, `Timestamp`, Event Envelope 계약부터 시작
5. Mock Broker 인터페이스와 실주문 불가 Mode 계약 정의
6. Unit·문서 링크·Secret Scan 중심의 최소 CI
7. `README.md`와 `AGENTS.md`에 실제 빌드·테스트 명령 추가

이 단계에서는 다음을 하지 않는다.

- Toss Credential 생성·저장·호출
- 계좌 조회·실주문 API
- 실제 뉴스 공급자 호출
- `live-auto` 설정
- Airflow·Kafka·Kubernetes 전체 스택 선행 구축

## 1단계 작업 기록과 다음 작업

### 2026-07-24 완료

- 권장 디렉터리의 최소 골격과 Maven 멀티 모듈 생성
- 빌드·런타임 기준 [ADR-0005](adr/0005-build-and-runtime-baseline.md) 채택
- Decimal·UTC·Event Envelope의 Java/Python 최소 타입과 테스트 작성
- Trading Core·Order Executor의 `mock-only` Fail-Closed 시작 Gate 작성
- 외부 Endpoint·Credential·실주문 메서드가 없는 Mock Broker 경계 작성
- GitHub Actions, 문서 링크·Secret·계약 검증, 실제 검증 명령 추가
- 커밋 `383b78e`의 GitHub Actions Run #1에서 두 CI Job 통과
- `runtime-baseline.json`과 검증기로 Java·Maven·Spring Boot·Python 기준 고정
- 실주문 경로·Secret 부재와 CI 통과를 확인해 1단계 종료 Gate 충족

Maven Wrapper는 로컬 JDK·Maven 설치 수요와 승인 전에는 추가하지 않는다. 현재는 Spring Boot BOM, Maven Enforcer, Python 무의존 Lock과 CI를 재현성 기준으로 사용하며 전체 Transitive Dependency Lock을 주장하지 않는다.

## 진행 중인 2단계: 안전 계약·도메인

### 2026-07-24 구현

- `Money`, `Price`, `Quantity`, `Ratio` Decimal 값과 명시적 반올림
- 원본 offset·UTC 수집 시각·거래소 시간대·거래일 분리
- Candidate·Decision·Reservation·Intent 타입 UUID
- Candidate·Intent·Reservation·Broker Order 상태 전이 CSV와 양 언어 구현
- 제출 여부 `UNKNOWN` Reservation의 해제·만료 금지
- `UNKNOWN` Broker Order의 직접 정상화 금지와 Reconciliation 근거 강제
- 커밋 `498013b`의 GitHub Actions Run #3에서 Java/Python·Contract Test 통과
- 미국주식 Instrument, `America/New_York`, `USD` 계약
- [ADR-0006](adr/0006-us-toss-execution-boundary.md)으로 외부 수익 정책과
  이 저장소의 실행 책임 분리
- 2026-07-24 공식 OAS 1.2.4 기준 계약과 SHA-256 기록
- 기본 구성에 연결되지 않은 JDK HttpClient·Jackson 3 기반 Toss Adapter
- OAuth Token, 계좌 Sequence, 미국주식 주문 생성·조회·취소 구현
- 주문별 만료 승인과 네트워크·계좌·Kill·정책·데이터·Reconciliation 다중 Gate
- `clientOrderId` 필수화, 공식 주소·Loopback 제한, 자동 재시도 금지
- 409·429·5xx·Timeout·알 수 없는 상태를 `UNKNOWN`으로 처리하는 Mock HTTP Test
- 커밋 `5e70fb6`의 GitHub Actions Run #5에서 저장소/Python과 Java 25
  Maven 전체 검증 통과
- [ADR-0007](adr/0007-external-order-proposal-contract.md)과 JSON
  Schema로 외부 정책 제안과 내부 Order Intent 분리
- Java/Python `ExternalOrderProposal`, 정책 버전·근거 SHA-256, 계좌 별칭,
  생성·만료 시각과 미국주식 주문 형태 계약
- 제안 UUID에서 Toss `clientOrderId`로 결정론적 변환
- 미래·만료·계좌번호 형태·비정상 Decimal·주문 형태 오류의 Broker 호출 전 차단
- 커밋 `9f5462b`의 GitHub Actions Run #7에서 저장소/Python과 Java 25
  Maven 전체 검증 통과
- 사용자 승인에 따라 Spring JDBC·Flyway·PostgreSQL JDBC 의존성과 GitHub
  Actions PostgreSQL 18.4 합성 서비스를 도입
- [ADR-0008](adr/0008-postgresql-order-journal.md)에 서비스별 Schema와 주문
  원장 결정 기록
- `trading_core`에 계좌 별칭, Proposal Inbox, Risk Decision, Reservation,
  Order Intent, Transactional Outbox 마이그레이션과 원자적 Repository 구현
- 같은 Proposal ID·같은 내용 Replay는 기존 Intent를 반환하고, 다른 내용은
  SHA-256 충돌로 차단
- `order_executor`에 Toss 계좌 별칭 매핑, Intent Inbox, Broker Order,
  단일 제출 Attempt와 Reconciliation Case 마이그레이션·Repository 구현
- 네트워크 호출 전 `SUBMIT` Attempt 기록, Broker Order별 단일 제출 제약,
  모호한 결과의 `UNKNOWN`·열린 Reconciliation Case 원자적 기록
- 기본 애플리케이션의 JDBC·Flyway 자동 구성을 명시적으로 제외해 DB 설정
  하나만으로 저장·실행 경로가 활성화되지 않도록 유지
- 실제 계좌번호는 저장하지 않고 Toss `accountSeq`는 Executor Schema에만
  제한하며, 실제 Credential·Toss 호출·실주문은 계속 없음

### 다음 작업

1. 외부 정책 시스템의 서명/상호 인증·Transport 계약 결정
2. Transactional Outbox Publisher와 Executor Intent Consumer의 Port·상태
   전이·멱등 처리 구현
3. Reconciliation Case 해결과 Broker Order·Reservation 수명주기 반영
4. 미국 시장 캘린더·DST·세션·종목 메타데이터 계약과 공식 조회 Adapter 구현
5. Toss 주문 정정의 승인·멱등성·모호한 응답 복구 계약 확정 후 구현
6. Avro/Protobuf 작은 PoC와 ADR 후 Event wire Schema 구현
7. 운영 DB Role·TLS·백업, Secret Manager·등록 IP·읽기 전용 실계좌 검증
   절차는 각각 별도 승인 후 수행

수익 전략·수익 목적 정책은 외부 환경의 책임으로 두며 이 단계에서 중복
구현하지 않는다. 실제 주문은 저장 기반 멱등성·Reconciliation과 운영 Gate가
준비된 뒤 개별 주문 승인을 통해 시작한다. `live-auto`는 계속 후속 단계다.

## 기술 도입 시점

| 기술 | 시점 |
|---|---|
| Git·CI·Java·Python·JUnit·pytest | 1단계 |
| PostgreSQL·Kafka·Schema Registry·Object Storage | 2~3단계 |
| Testcontainers·Contract·Replay | 2~3단계 |
| dbt·Airflow | 3~6단계, 실시간 기반 이후 |
| OpenTelemetry SDK | 1~2단계 |
| Prometheus·Grafana | 3~8단계 |
| LLM API | 평가 Harness가 준비된 7단계 |
| Alertmanager | Shadow 이전 |
| Redis·Kafka Streams | 측정 후 `LATER` |
| Kubernetes·Spark·DW·GraphDB | 운영·데이터 지표가 필요성을 증명한 후 |

## 포트폴리오 산출물

- ADR과 설계 변경 이력
- 이벤트 Schema와 호환성 Report
- 주문 상태 머신과 멱등성 Test
- UNKNOWN·부분 체결·취소 Race·Kill 시연
- Raw→Silver→Gold Lineage
- Replay·합성 부하·장애 주입 Report
- 정책 차단·계좌 정합성 Audit
- 거래·파이프라인·AI Dashboard
- AI 평가 Dataset과 모델·Prompt Scorecard
- 자동화 전후 생산성 비교
- 기술 도입 전후 Metric 비교

실제 데이터와 합성 데이터 결과를 명확히 구분한다.
