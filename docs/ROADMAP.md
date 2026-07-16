# 개발 로드맵

## 원칙

- 단계는 기간이 아니라 안전 Gate로 이동한다.
- 다음 단계 기술을 포트폴리오 목적으로 선행 도입하지 않는다.
- 실제 API·계좌·주문·운영 활성화는 각 단계에서 별도 사용자 승인을 받는다.
- 새 기술은 문제 측정 → 후보 조사 → PoC → 비교 → ADR → 도입 → 사후 측정 순서로 검토한다.

## 현재 상태

`CONFIRMED` 현재는 **0단계 설계 기준선 문서화 완료, 애플리케이션 구현 전**이다.

- 문서와 ADR만 존재
- Git 저장소·빌드·CI·애플리케이션·인프라 구성 없음
- 실제 Credential·API 호출·주문 없음
- 기본 거래 모드는 개념적으로 실주문 불가
- 시장·전략·정책 수치·공급자 등은 `TBD`

## 단계

| 단계 | 범위 | 주요 산출물 | 종료 Gate |
|---|---|---|---|
| 0. 설계 | 요구·경계·위협·TBD·ADR | 현재 `docs/` | 구조 기준선 승인 |
| 1. 저장소 기반 | Git, 문서 검증, Java/Python 골격, CI, Mock-only 설정 | 빌드·테스트 명령, 의존성 Lock | 실주문 경로·Secret 없음, CI 통과 |
| 2. 계약·도메인 | Money·Time·Event, Instrument, 주문 상태, Schema | 공통 계약·상태 머신 Test | Decimal·시간·호환성 Test 통과 |
| 3. 데이터 기반 | Kafka, PostgreSQL, Object Storage, 합성 Collector | Raw→Silver, Inbox/Outbox 기반 | Replay·중복 제거·DQ 통과 |
| 4. 특징·전략 | 결정론적 전략 1개, 비용 모델, 백테스트 | Feature·Signal·Backtest Report | Look-ahead·재현성·비용 검사 |
| 5. 정책·주문 Mock | Policy/Risk, Candidate/Intent, Mock Executor, Kill | 주문 상태·Audit·예외 | 중복·UNKNOWN·Kill Race 통과 |
| 6. Mart·Semantic | dbt Mart, Metric Registry, 정적 보고서 | Gold·Lineage·대사 Test | 보고서 수치 대사 통과 |
| 7. 뉴스·AI Harness | 공급자 결정 후 뉴스, Tool Allowlist, 평가 | Recommendation·Agent Scorecard | Injection·근거·비용 기준 통과 |
| 8. 관측·복구 | OTel, Prometheus, Grafana, 장애 주입 | Dashboard·SLO 기준선·Runbook | 복구·알림·백업 Restore 통과 |
| 9. Read-only Shadow | 별도 승인 후 공식 시세, 이후 계좌 조회 | 실데이터 품질·정합성 Report | 주문 권한 없이 관찰 기준 충족 |
| 10. Paper/Mock | 실제 시세 기반 가상 주문 | Paper 성과·안전 Report | 정책 위반·중복 0건 |
| 11. Approval-required | 사용자 승인된 제한 주문 | 승인·예외 UI·Audit | 운영 절차 검증 |
| 12. Limited Auto | 소액·Allowlist·저빈도 자동화 | Limited Auto Report | 안전 지표·운영 기간 충족 |
| 13. Live Auto | 보수적 자동거래 | 운영 SLO·Incident Review | 지속적 Gate 유지 |

## 다음 구현 단계: 1단계 저장소 기반

설계 문서 저장 이후 가장 작은 구현 단계는 **실거래 기능이 없는 개발 골격**이다.

제안 범위는 다음과 같다.

1. 실제 Git 저장소 초기화 여부 확인
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
