# 프로젝트 컨텍스트

## 문서 상태

- 설계 기준일: 2026-07-16
- 상태: 1단계 기반 완료, 2단계 안전 계약·Broker Adapter 진행 중
- 원칙: 기존 `TBD`는 설계 저장 요청으로 자동 확정되지 않는다.

상태 표기는 다음과 같다.

| 상태 | 의미 |
|---|---|
| `CONFIRMED` | 사용자 요구 또는 환경 점검으로 확정 |
| `RECOMMENDED` | 승인된 설계 기준선 또는 권장 기본안 |
| `TBD` | 사용자 결정·공식 확인·검증 필요 |
| `LATER` | 지표와 필요성 확인 후 도입 |
| `OUT OF SCOPE` | 현재 범위에서 제외 |

## 프로젝트 정의

가칭은 **실시간 주식 데이터 처리와 위험 관리 기능을 결합한 AI 기반 주식 거래 자동화 시스템**이다.

이 프로젝트는 단순 자동매매 봇이 아니다. 다음 네 영역을 하나의 감사 가능한 시스템으로 결합한다.

1. 실시간 및 배치 금융 데이터 플랫폼
2. 외부 수익 정책과 내부 실행 안전·리스크 통제
3. 멱등하고 복구 가능한 주문·체결 시스템
4. 근거 기반 AI Agent Harness와 운영 업무 자동화

정상적인 정책 범위 내 거래는 최종적으로 자동화한다. 데이터가 불완전하거나 정책으로 판단할 수 없거나 계좌·주문 상태가 불일치하면 신규 주문을 중단하고 사용자 예외로 전환한다.

## 프로젝트 목표

`CONFIRMED` 목표는 다음과 같다.

- 미국주식을 Toss Securities Open API로 실제 거래하는 실행 시스템을 만든다.
- 외부 정책 시스템의 버전 있는 주문 의도를 안전하게 검증·실행한다.
- Kafka 기반 실시간 파이프라인과 Airflow 기반 배치 파이프라인의 역할을 올바르게 분리한다.
- Java와 Python의 책임을 금융 안전 경계에 맞게 분리한다.
- Raw/Bronze, Normalized/Silver, Mart/Gold 계층과 Lineage를 구현한다.
- 데이터 계약, 품질, 최신성, 완전성, 중복률을 측정한다.
- 정책 위반, 중복 주문, 모호한 주문 응답을 안전하게 처리한다.
- 실제 계좌와 내부 상태를 주기적으로 Reconciliation한다.
- AI를 뉴스 분석, 근거 생성, 보고서, 장애 설명, 예외 요약에 사용한다.
- AI와 Dashboard가 같은 Semantic Layer의 검증된 수치를 사용하게 한다.
- Replay, 합성 데이터, 부하·장애 테스트로 처리량과 복구 능력을 증명한다.
- AI 도입 전후의 보고서 작성·장애 분석·반복 조회 시간을 비교한다.

## 성공 기준

실거래 수익률보다 다음 엔지니어링 성과를 우선한다.

- 동일 Intent의 브로커 중복 주문 0건
- 동시 Intent가 현금·수량·노출 한도를 초과 승인한 건수 0건
- 정책 위반 주문 제출 0건
- 모든 주문과 정책·전략·근거 버전의 추적 가능성
- 모호한 응답의 무조건 재주문 0건
- Kill Switch ACK 후 신규 주문 0건
- 계좌·주문 불일치의 탐지와 복구 시간
- 데이터 처리 p50/p95/p99, Consumer Lag, 최신성, 완전성, 중복률
- 백필·Replay 성공률과 결정론적 재현성
- AI 구조화 출력, 분류 정확도, 근거 일치율, 비용과 지연
- 자동 보고서와 장애 분석의 시간 절감

실제 데이터가 작으면 대용량 처리라고 주장하지 않는다. 실제 처리량과 합성 부하 결과를 구분해서 보고한다.

## 확정된 안전 원칙

- 안전이 수익률보다 우선한다.
- 불확실하면 신규 주문을 실행하지 않는다.
- AI·외부 수익 정책보다 실행 안전·리스크 엔진이 우선한다.
- Kill Switch는 AI와 Kafka에 독립적이어야 한다.
- 정확한 숫자 계산과 최종 정책 판정은 코드가 담당한다.
- Broker Credential은 Order Executor만 가진다.
- 주문 결과가 불명확하면 무조건 재주문하지 않는다.
- 전체 Exactly-Once를 가정하지 않고 At-Least-Once와 멱등성·중복 제거·Reconciliation을 사용한다.
- 모든 자동 결정은 입력 Snapshot과 버전까지 감사 가능해야 한다.
- 개발 편의를 위해 실거래 안전장치를 우회하지 않는다.

## 기본 업무 흐름

```text
시장·뉴스 데이터
→ 검증·정규화
→ 특징·뉴스 분석
→ 외부 정책 시스템의 버전 있는 주문 제안
→ Order Candidate
→ 실행 안전 정책 검사
→ 리스크 검사
→ Risk Reservation과 승인된 Order Intent
→ Order Executor
→ 주문 상태 조회
→ 계좌·주문 Reconciliation
→ Mart·Semantic Layer
→ Dashboard·보고서·Agent Tool
```

추천·외부 정책 제안과 주문은 별개의 개념이다. Order Intent는 인증된 외부
제안이 실행 안전 정책·리스크를 통과한 뒤 이 저장소가 생성하는 불변 실행
객체다.

## Fail-Closed 조건

다음 조건에서는 해당 주문 또는 전체 신규 자동 주문을 차단한다.

- 데이터 누락·오래됨·품질 실패
- 시장·세션·종목 거래 가능 상태 불명
- 계좌 잔액·포지션·미체결 주문 불일치
- 정책 충돌·정책 미로딩·미정의 예외
- 핵심 DB·Kafka·Executor 상태 비정상
- 시장 급변·거래 정지·예상하지 못한 상태
- 주문 성공 여부 불명
- 주문 실패 반복 또는 미체결 한도 초과
- AI 의존 경로에서 구조화 출력·근거·신뢰도 실패

AI 보고서 실패처럼 주문 입력과 무관한 장애는 결정론적 거래 전체를 자동으로 중단하지 않는다. Fail-Closed 범위는 의존 관계에 맞게 정의한다.

## 환경과 모드

다음 모드를 논리적으로 분리한다.

```text
dev → test → replay → backtest → shadow → paper/mock
→ approval-required → limited-auto → live-auto
```

`CONFIRMED` 초기 기본값은 실주문 불가다. `live-auto`는 운영 전용 환경·자격증명·계좌 Allowlist·Kill Switch·정책·데이터 최신성·계좌 정합성·Audit·승인된 배포 버전이 모두 확인될 때만 활성화한다.

현재 Linux 서버는 개발·테스트·Replay 용도이며 운영 자동거래 서버와 분리한다.

## 현재 구현 범위 밖

- 실제 Credential 등록·실계좌 호출·운영 배포·실거래 활성화
- 수익 보장 또는 성능 과장
- AI의 자유 SQL·셸·주문 API
- 기본 Kill Switch의 전량 자동청산
- Spark, Kubernetes, Hadoop, GraphDB의 초기 도입
- 현재 개발 서버의 고가용성 또는 운영 적합성 주장

## 관련 문서

- 시스템 경계: [ARCHITECTURE.md](ARCHITECTURE.md)
- 주문·정책·리스크: [TRADING_SYSTEM.md](TRADING_SYSTEM.md)
- 데이터: [DATA_ARCHITECTURE.md](DATA_ARCHITECTURE.md)
- 보안: [SECURITY_AND_RISK.md](SECURITY_AND_RISK.md)
- 구현 순서: [ROADMAP.md](ROADMAP.md)
- 미결정 사항: [DECISIONS_PENDING.md](DECISIONS_PENDING.md)
