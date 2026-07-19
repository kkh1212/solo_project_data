# 관측가능성

## 목표

로그 수집만이 아니라 시장 이벤트에서 정책·주문·체결 관측까지 원인과 지연을 추적하고, 안전 Gate와 기술 도입 판단에 필요한 지표를 제공한다.

## Trace 모델

```text
market_event_id
→ feature_id
→ signal_id / recommendation_id
→ order_candidate_id
→ risk_decision_id
→ order_intent_id
→ broker_order_id
→ fill_observation_id
```

모든 단계에 `correlation_id`, `causation_id`, `trace_id`, Schema·전략·정책·모델·Prompt 버전을 연결한다.

공식 개별 체결 ID가 제공되지 않으면 `fill_observation_id`까지만 보장하고 실제 브로커 Fill Trace라고 주장하지 않는다.

## 도구 도입 순서

1. 애플리케이션의 구조화 로그와 OpenTelemetry 계측 규약
2. Prometheus Metric과 Grafana Dashboard
3. OTel Collector와 분산 Trace Backend 후보
4. Shadow 이전 Alertmanager
5. 로그량·검색 필요가 확인되면 Loki 또는 대안

현재 8 GiB 개발 서버에서는 관측 스택을 별도 Compose Profile로 실행한다.

## Metric 설계 원칙

- Counter: 수신·처리·중복·실패·차단·주문·AI 호출 건수
- Histogram: 처리·주문·Reconciliation·AI·DAG 복구 지연
- Gauge: Consumer Lag, 데이터 최신성, 미체결 주문, 자산 노출, Resource 사용량
- Trace: 개별 이벤트·주문의 인과관계
- Structured Log: 오류 세부와 Audit ID
- Mart: 금융 성과, AI 정확도, 생산성

종목·주문·기사 ID를 Prometheus Label로 사용하지 않는다. 높은 Cardinality 식별자는 Trace·Log에 둔다.

## 데이터 지표

- 초·분당 수신·정규화·처리 이벤트 수
- 단계별 p50·p95·p99 처리 지연
- Kafka Consumer Lag과 Lag 증가율
- Source Timestamp 기준 데이터 최신성
- REST 예정 Poll Slot 기준 완전성
- 중복률, DQ 실패율, DLQ 발생률
- 순서 역전·지연 도착·Gap 건수
- 백필·Replay 성공률과 처리 속도

## 파이프라인·서비스 지표

- Airflow DAG·Task 성공률, 재시도율, 평균 복구 시간
- 서비스 요청 성공·실패·Timeout·Circuit 상태
- PostgreSQL Query·Lock·Connection 지연
- Object Storage 쓰기 실패·checksum 불일치
- Kafka Produce·Consume 실패와 Rebalance
- CPU·메모리·디스크·inode·네트워크
- 서비스 가용성과 배포·재시작 횟수

## 거래 시스템 지표

- 제출 시도, 브로커 접수, 거절, 부분·전체 체결률
- 주문 제출·접수·첫 체결·완료 지연
- 중복 주문 차단과 idempotency 충돌 건수
- 활성·만료·고아 Reservation, 예약 충돌·재평가·해제 지연
- 예약 현금·매도수량·종목·섹터 노출과 실제 미체결 주문의 차이
- 정책·리스크 차단 건수와 사유
- `UNKNOWN`과 Reconciliation Required 건수·해결 시간
- 계좌·포지션·미체결 불일치 건수
- Kill Switch 명령→Executor ACK 지연
- Kill ACK 이후 주문 제출 시도
- 회전율·수수료·세금·슬리피지 비중

## AI 지표

- 뉴스·종목 연결 Precision·Recall·F1
- 사건 유형·심각도·위험도 정확도
- 구조화 출력 성공률
- 근거 일치율과 근거 없는 주장 비율
- 저신뢰·Fallback·사용자 예외 전환율
- Tool 호출 성공·거절·정책 위반 시도
- Prompt Injection 탐지·공격 성공률
- 응답 p50·p95·p99, Token, 요청당 비용
- 모델·Prompt·Tool 버전별 성능

## 생산성 지표

도입 전 기준 시간을 먼저 측정한다.

- 일간·주간 보고서 작성 시간
- 장애 감지부터 원인 후보 파악까지의 시간
- 반복 계좌·성과 조회 시간
- 수동 확인 건수와 건당 처리 시간
- 자동 처리 가능한 업무 비율
- AI 설명을 운영자가 수정한 비율

## SLO 후보

아래 값은 설계 후보이며 부하·Rate Limit·전략 주기를 측정한 뒤 확정한다.

| 영역 | SLI/SLO 후보 | 실패 시 동작 |
|---|---|---|
| 정책 안전 | 정책 위반 주문 제출 0건 | 자동거래 중단·조사 |
| 중복 안전 | 동일 Intent의 중복 브로커 주문 0건 | 전체 계좌 Stop |
| 동시 한도 | 활성 Reservation을 포함한 현금·수량·노출 초과 승인 0건 | 신규 주문 중단·Reconciliation |
| Kill | Executor ACK p99 2초 이내, ACK 후 신규 제출 0건 | 직접 운영 개입 |
| Trace | 모든 주문의 Intent·Decision·Policy 연결률 100% | Reconciliation·승격 차단 |
| 모호한 응답 | `UNKNOWN` 분류 누락 0건 | 재주문 금지 |
| 데이터 최신성 | 장중 정상 데이터가 2 Poll 주기 이내 99.5% 후보 | 의존 주문 차단 |
| 실시간 처리 | ingest→feature p95 5초 이내 후보 | Lag 경보·거래 Gate |
| 계좌 정합성 | 장 시작 전 100%, 장중 정책 주기 실행 | 불일치 시 신규 주문 중단 |
| 중요 DAG | 30일 성공률 99% 후보 | Mart·보고서 발행 중단 |
| AI Schema | 구조화 출력 성공률 99% 후보 | Fallback·예외 |
| AI 근거 | 고정 평가셋 일치율 98% 후보 | 버전 승격 금지 |

안전 불변식은 오류 예산으로 상쇄하지 않는다. 정책 위반·중복 주문은 허용 가능한 작은 비율이 아니라 0건 목표다.

## Dashboard

### 거래 Dashboard

- 총자산·현금·포지션·종목·섹터 비중
- 실현·미실현손익·일별·누적 수익률
- 전략 신호·추천 등급·근거 시각
- 주문·체결 관측·미체결·취소·`UNKNOWN`
- 정책 차단·위험 상태·Reconciliation
- Kill Switch Scope·상태·마지막 변경자
- 사용자 확인 예외와 조치 기한

### 데이터·AI Dashboard

- Kafka 처리량·Consumer Lag·지연
- 최신성·완전성·중복률·DLQ
- Airflow 성공·실패·재시도·백필
- DB·Object Storage 지연·실패
- 뉴스·AI 처리량·지연·실패·비용
- 모델·Prompt 버전별 평가 성능

Grafana는 관측 도구이며 정책 승인·예외 처리·Kill Switch를 위한 업무 UI를 대체하지 않는다.

## 알림 우선순위

| 등급 | 예 | 기본 동작 |
|---|---|---|
| Critical | Kill 실패, 중복 주문 의심, 계좌 불일치, Credential 위험 | 신규 주문 즉시 중단, 이중 채널 알림 |
| High | `UNKNOWN`, 주문 실패 반복, 데이터 최신성 임계 초과 | 해당 계좌·전략 중단 |
| Warning | Consumer Lag 증가, DQ Warn, AI Fallback 증가 | 운영 검토 |
| Info | 배치 완료, 보고서 생성, 정책 초안 변경 | 기록·요약 |

알림 채널은 `TBD`지만 Critical은 Push/Webhook과 이메일 등 최소 두 경로를 권장한다.
