# 기술 전략

## 선택 원칙

- 기술은 유명세나 포트폴리오 장식이 아니라 구체적인 책임과 측정 가능한 문제를 해결해야 한다.
- 현재 4 vCPU·7.8 GiB·Swap 없음·디스크 약 70 GiB의 개발 서버 제약을 고려한다.
- 처음부터 모든 서비스를 상시 실행하지 않고 Compose Profile로 분리한다.
- 새 기술은 현재 지표 → 문제 정의 → 후보 PoC → 기존 방식 비교 → ADR → 도입 후 지표 순서로 검토한다.
- 제품 버전은 구현 단계에서 공식 지원·호환성을 확인해 고정한다. 1단계 기준은 [ADR-0005](adr/0005-build-and-runtime-baseline.md)의 Java 25, Spring Boot 4.1.0, Maven 3.9.x, Python 3.12다.

이 기준은 이벤트 Schema·외부 공급자·운영 이미지 선택 같은 기존 `TBD`를 확정하지 않는다.

## 거래·백엔드

| 후보 | 목적·필요 이유 | 도입 시점 | 장점 | 단점·대안 | 초기·자원 |
|---|---|---|---|---|---|
| Java LTS | 금융 핵심 타입·동시성·정밀 계산 | 1단계 | BigDecimal, 성숙한 운영 생태계 | Python 단일 언어보다 설정 증가 | 초기 필요, 중 |
| Spring Boot | API, DI, Kafka, 보안, 관측 통합 | 1단계 | 운영 기능·테스트 지원 | 메모리 사용, 경량 Java 대안 | 초기 필요, 중 |
| JDK HttpClient + Jackson 3 | Toss REST·JSON Adapter | 2단계 | 별도 HTTP SDK 없이 표준 전송, 타입 있는 JSON | 재시도·Rate Limit·오류 분류 직접 구현 | 사용자 의존성 승인 완료, 낮 |
| Kafka Consumer | 명시적 멱등 Consumer·실시간 Worker | 3단계 | 흐름과 실패 제어가 단순 | Stateful Join 직접 구현 | 초기 필요 |
| Kafka Streams | Window·State Store·Join | 복잡도·처리량 확인 후 | 이벤트 시간·복구 지원 | RocksDB·Topology 운영 부담 | `LATER`, 중~상 |

Java는 Trading Core, 정책·리스크, 주문 상태, Executor, Reconciliation과 안전 중요 Consumer를 담당한다.

## AI·분석·배치

| 후보 | 목적·필요 이유 | 도입 시점 | 장점 | 단점·대안 | 초기·자원 |
|---|---|---|---|---|---|
| Python | 뉴스·AI·평가·분석 생태계 | 1단계 | 빠른 실험, 풍부한 라이브러리 | 금융 핵심 동시성 경계에는 부적합 | 초기 필요, 중 |
| FastAPI | 제한된 Agent Tool·평가 API | 7단계 | Pydantic 구조화 출력 | 서비스 추가 | AI 단계, 낮~중 |
| Airflow | 백필·DQ·Mart·보고서 오케스트레이션 | 실시간 기반 이후 | 실행 이력·재시도 | 현재 서버에서 무거움 | Batch Profile, 중~상 |
| Pandas | 작은 탐색·평가 | 4단계 | 접근성·생태계 | 메모리 사용 | 선택, 중 |
| Polars | 단일 노드 Replay·Backfill | 데이터 증가 시 | 속도·메모리 효율 | 일부 생태계 차이 | 권장 후보, 중 |
| LLM API | 분류·근거·보고서 설명 | 7단계 | 업무 자동화 | 비용·지연·환각·데이터 정책 | 평가 Harness 이후 |
| Provider 추상화 | 모델 교체·비교·Fallback | 7단계 시작 | 종속성 감소 | 과도한 추상화 위험 | 얇은 인터페이스만 |

Python도 금융 수치에는 `Decimal`을 사용하며 보고서 수치를 재계산하지 않는다.

## 데이터 플랫폼

| 후보 | 목적·필요 이유 | 도입 시점 | 장점 | 단점·대안 | 초기·자원 |
|---|---|---|---|---|---|
| Kafka KRaft | 실시간 전달·Consumer 분리·Replay | 3단계 | 이벤트 기반 확장 | 단일 Broker는 HA 아님 | 초기 개발 필요, 상 |
| PostgreSQL | 주문·정책·감사·메타데이터·초기 Mart | 2~3단계 | 트랜잭션·SQL·운영 단순 | 대규모 시계열 분석 한계 | 필수, 중 |
| Redis | 다중 인스턴스 캐시·Rate Limit | 필요 측정 후 | 빠른 공유 상태 | 추가 장애점, 정합성 Source 부적합 | `LATER`, 중 |
| S3 호환 Object Storage/MinIO | Raw·Replay·백업·감사 Export | 3단계 | 불변 원본·저비용 | 같은 디스크면 DR 아님 | 초기 데이터 단계, 중 |
| Avro | Kafka 계약·Decimal logical type | 2단계 PoC | Schema 진화·Java/Python | 사람이 읽기 불편 | `RECOMMENDED` |
| Protobuf | 생성 코드·엄격한 메시지 | Avro 대안 PoC | 타입·성능 | Decimal 별도 모델 | `TBD` |
| Schema Registry | 파괴적 계약 변경 차단 | 2~3단계 | 호환성 자동화 | 서비스·메모리 추가 | 초기 계약 단계, 중 |
| SQL/dbt | Mart·Metric 버전·Test·Lineage | 6단계 | 재현 가능한 변환 | 실시간 처리 도구 아님 | Gold 단계, 낮~중 |
| 별도 DW | 운영 DB와 분석 부하 격리 | Query SLO 위반 후 | 분석 확장 | 비용·운영 증가 | `LATER` |

PostgreSQL이 주문 Source of Truth다. Kafka나 Redis를 금융 상태의 최종 기준으로 삼지 않는다.

## 관측가능성

| 후보 | 목적·필요 이유 | 도입 시점 | 장점 | 단점·대안 | 초기·자원 |
|---|---|---|---|---|---|
| OpenTelemetry | 언어 간 Signal→Order Trace | 1~2단계 | 표준 계측 | Collector·Backend 운영 | SDK 초기, 중 |
| Prometheus | 수치형 SLI·Alert 입력 | 3~8단계 | Grafana 통합 | Cardinality 관리 필요 | 필요, 중 |
| Grafana | 거래·파이프라인 Dashboard | 3~8단계 | 빠른 시각화 | 업무 승인 UI 대체 불가 | 필요, 중 |
| Loki | 중앙 로그 검색 | 로그량 측정 후 | Grafana 통합 | 메모리·디스크 부담 | `LATER`, 중~상 |
| Alertmanager | Critical 경보 라우팅·중복 억제 | Shadow 이전 | 운영 알림 체계 | 채널 구성 필요 | 운영 전 필요 |

개발 서버에서는 관측 스택을 별도 Profile로 실행한다.

## 개발·배포

| 후보 | 목적·필요 이유 | 도입 시점 | 장점 | 단점·대안 | 초기·자원 |
|---|---|---|---|---|---|
| Git | 변경·ADR·Version 추적 | 0~1단계 | 필수 이력 | 설계 문서 커밋 이력 존재, CI·브랜치 규칙 미구성 | 현재 사용 중 |
| Docker | 재현 가능한 실행 환경 | 1~3단계 | 격리·Testcontainers | 현재 daemon 권한 없음 | 승인 후, 중 |
| Docker Compose | 개인 개발 서비스 조합 | 3단계 | 현재 규모에 적합 | HA·자동복구 한계 | 초기 권장 |
| CI | Unit·Contract·Secret·Schema 검사 | 1단계 | 회귀 방지 | 초기 설정 | 초기 필수 |
| CD | 환경별 안전 배포 | Shadow 이후 | 재현 배포 | 운영 승인·Rollback 필요 | `LATER` |
| Kubernetes | 다중 호스트·복구·확장 | 지표 확인 후 | 운영 자동화 | 현재 과도한 복잡도 | `LATER`, 매우 높음 |
| IaC | 운영 환경 재현·감사 | 운영 환경 결정 후 | Drift 감소 | 초기 학습·운영 비용 | `LATER` |

현재 Docker CLI와 Compose는 설치되어 있지만 사용자가 daemon socket에 접근하지 못한다. 권한이나 시스템 구성을 사용자 승인 없이 변경하지 않는다.

## 테스트

| 후보 | 목적 | 시점 | 이유·대안 |
|---|---|---|---|
| JUnit | Java Unit·상태·정책 | 1단계 | 금융 핵심 회귀 |
| pytest | Python Unit·AI 평가 | 1단계 | 데이터·Harness 회귀 |
| Testcontainers | PostgreSQL·Kafka 통합 | 2~3단계 | 실제 구성과 가까운 검증, Docker 필요 |
| Contract Test | OAS·Kafka·내부 API | 2단계부터 | 외부·언어 경계 보호 |
| Replay Test | 중복·순서·재시작·결정론 | 3단계부터 | 실시간 복구 증명 |
| Load Test | 합성 처리량·p95/p99·Lag | 3단계부터 | 실제 규모 과장 방지 |
| Failure Injection | DB·Kafka·API·Kill Race | 5단계부터 | Fail-Closed 검증 |

## 지금·나중·제외

### 구현 초기

- Git, CI
- Java LTS, Spring Boot
- Python, pytest
- 금융·시간·이벤트 계약
- PostgreSQL, Kafka, Schema Registry, Object Storage는 단계적으로
- JUnit, Contract, Testcontainers, Replay
- OpenTelemetry 계측 규약

### 지표 확인 후

- Redis
- Kafka Streams
- Loki
- Kubernetes
- Spark
- IaC
- 별도 DW
- GraphDB/RDF

### 현재 제외

- Hadoop
- 현재 개발 서버의 `live-auto`
- AI 자유 SQL·셸·주문
- 전체 Exactly-Once 주장
- 포트폴리오 전시를 위한 불필요한 기술 추가

## 고급 기술의 도입 기준

- Spark: SQL·Polars 기반 백필이 목표 처리 시간·메모리 SLO를 반복적으로 위반
- Kubernetes: 다중 호스트·수평 확장·자동복구·배포 Toil이 Compose보다 명확히 큼
- 별도 DW: 분석 Query가 운영 PostgreSQL의 거래·Reconciliation SLO를 방해
- GraphDB: 관계 탐색이 핵심 기능이 되고 관계형 Query보다 측정 가능한 이점 존재
- Redis: 다중 인스턴스 공유 상태나 Rate Limit이 실제 병목

각 도입은 ADR과 도입 전후 Metric 비교를 남긴다.
