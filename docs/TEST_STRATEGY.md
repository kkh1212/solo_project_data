# 테스트 전략

## 목표

테스트의 우선순위는 수익률이 아니라 안전 불변식, 데이터 재현성, 장애 시 안전한 중단이다. 실제 계좌 호출을 테스트 대용으로 사용하지 않는다.

## 안전 불변식

다음은 모든 단계에서 유지해야 한다.

- 승인된 Order Intent 없이 브로커 제출이 발생하지 않는다.
- 동일 Order Intent가 두 개의 독립 주문으로 제출되지 않는다.
- 동시 Order Intent가 같은 현금·매도수량·종목·섹터 한도를 중복 예약하지 않는다.
- 정책·리스크 거절 또는 불명 판정은 주문으로 이어지지 않는다.
- `UNKNOWN` 상태는 무조건 재주문으로 이어지지 않는다.
- Kill Switch ACK 후 신규 주문이 제출되지 않는다.
- AI는 종목·가격·수량을 지정하거나 정책을 우회하지 못한다.
- 금액·수량 계산에 binary float가 개입하지 않는다.
- Replay가 운영 Topic·계좌·Credential에 접근하지 않는다.
- 모든 주문은 Signal·Decision·Policy·Intent·Broker 상태로 추적된다.

## 테스트 계층

| 계층 | 범위 | 도구 후보 |
|---|---|---|
| Unit | 금액·시간·정책 규칙·변환·상태 전이 | JUnit, pytest |
| Property-based | 반올림·비중·한도·상태 불변식 | jqwik/QuickTheories 후보, Hypothesis |
| Contract | Kafka Schema, 내부 API, 공식 OAS Adapter | Schema Registry, Mock Server |
| Integration | PostgreSQL·Kafka·Object Storage·Outbox/Inbox | Testcontainers |
| Replay | 중복·순서 역전·지연·재시작의 결정론 | 고정 Dataset·checksum |
| Backtest | 거래 비용·환율·슬리피지·Look-ahead 방지 | Python/SQL |
| Load | 합성 이벤트 처리량·지연·Lag | k6/Gatling/전용 Producer 후보 |
| Failure Injection | Kafka·DB·API·Object 장애, Kill Race | Toxiproxy/Testcontainers 후보 |
| Security | Prompt Injection, 권한, Secret·로그 마스킹 | 공격 Corpus·정적 검사 |
| Restore | DB·Object·상태 복구 | 백업 복원 Runbook |

도구는 후보이며 구현 시 최소 조합으로 선택한다.

## 금융 계산 테스트

- Java `BigDecimal`, Python `Decimal`, DB `NUMERIC` 간 직렬화 일치
- 통화·시장별 scale과 반올림 모드
- 호가 단위·가격 범위·수량 step
- 수수료·세금·환율·슬리피지 적용 순서
- 종목·섹터·현금 비중 경계값
- 일일 손실과 최대 낙폭 경계값
- 부분 체결의 평균가·비용·잔여 수량
- 원가법과 브로커 손익 대사
- 0·음수·매우 큰 값·소수점 초과·통화 불일치

정확한 세금·수수료·원가 규칙은 공식·계약 확인 전 Fixture로 추측하지 않는다.

## 이벤트·데이터 시나리오

| 시나리오 | 기대 결과 |
|---|---|
| 동일 시세·뉴스 중복 | Inbox·checksum으로 한 번만 업무 반영 |
| 이벤트 순서 역전 | 이벤트 시간 규칙 적용, 무효 상태 전이 거절 |
| 지연 도착 | Watermark·허용 지연을 기록, 의존 주문은 최신성 정책 적용 |
| Poll·연결 구간 누락 | Gap 탐지·백필, 완전성 Metric 감소 |
| 미래 시각·거래일 불일치 | Quarantine·경보 |
| 비정상 가격·거래량 | DQ Fail, 전략 입력 제외 |
| 종목 코드 변경·매핑 오류 | 영구 instrument ID 기준으로 정정, 과거 이력 보존 |
| Schema 필드 추가·Enum 확장 | 호환 Consumer 동작, 안전 중요 unknown은 차단 |
| 파괴적 Schema 변경 | CI·Registry에서 배포 차단 |
| 동일 Airflow Run 재실행 | 중복 적재 없이 같은 결과 |

## 외부 API 시나리오

- REST Timeout·Connection Reset·DNS 실패
- HTTP 429와 Rate Limit Header
- 인증 실패·토큰 만료·허용 IP 오류
- 점검·5xx·알 수 없는 오류 코드
- 응답 Schema 누락·타입 변경·빈 결과
- 시장 휴장·조기 폐장·DST·세션 변경
- 종목 거래 중단·주문 제한

기대 결과는 제한된 Backoff·Circuit Breaker·오류 원본 저장·Fail-Closed다. 주문·Reconciliation 호출 예산을 비중요 시세 Poll보다 우선한다.

## 주문·체결 시나리오

| 시나리오 | 기대 결과 |
|---|---|
| 네트워크 재시도·Consumer 재시작 | 동일 Intent 중복 제출 없음 |
| 주문 요청 후 응답 유실 | `UNKNOWN`, 공식 보장 내 동일 본문 외 자동 재전송 금지 |
| 공식 멱등 기간 만료 | 같은 `clientOrderId` 자동 재사용 금지 |
| 부분 체결 | 체결·잔여 수량 보존, 정책 노출 갱신 |
| 취소 중 체결 | 취소와 체결 이벤트 모두 반영, 최종 수량 대사 |
| 취소·정정 거절 | 원주문 상태 복구·감사 |
| 잔액 부족·매도 가능 수량 부족 | Executor 제출 전 또는 브로커 거절로 안전 종료 |
| 수동 앱 주문 | Reconciliation으로 불일치 탐지·신규 주문 중단 |
| Broker 상태 unknown Enum | 안전 종결 추측 금지, Reconciliation Required |
| 동일 종목·동일 신호 반복 | 업무 중복 키로 차단 |
| 같은 계좌의 Candidate 동시 승인 | 계좌 단위 직렬화, 활성 Reservation 포함 재평가, 합산 한도 초과 0건 |
| Executor 재검증 거절·Intent 만료 | Broker 호출 없음, 근거와 함께 Reservation 해제 |
| `UNKNOWN` 주문의 Reservation | Reconciliation 완료 전 유지, 신규 한도 계산에 계속 포함 |

Mock Broker는 성공·거절·Timeout·응답 유실·부분 체결·취소 Race를 결정론적으로 재현해야 한다.

## 인프라 장애 시나리오

- Kafka Broker 중단·Consumer Rebalance·Lag 급증
- PostgreSQL 연결 실패·Lock Timeout·Commit 직후 응답 유실
- Object Storage 쓰기 실패·checksum 불일치
- Airflow Task 실패·Scheduler 재시작
- 서비스 재시작과 Outbox·Inbox 복구
- 디스크 부족·메모리 압박·Clock Drift

Kafka나 DB 상태가 불명하면 신규 주문을 막는다. Kill·취소·조회에 필요한 직접 경로의 동작을 별도 검증한다.

## Kill Switch 테스트

- 주문 제출 직전·중·직후 STOP Race
- Kafka 장애 중 STOP
- DB 장애 중 기존 Stop Latch 유지
- 서비스 재시작 후 STOP 지속
- 특정 종목·전략·계좌 Scope
- STOP 중 잘못된 재활성화 시도
- 미체결 취소 중 부분 체결
- AI Agent만 중단하고 Trading Core는 읽기 상태 유지

Kill ACK 지연과 ACK 이후 제출 수를 측정한다. 자동 전량 청산은 테스트 범위에 포함하지 않는다.

## AI·뉴스 테스트

- 기사와 기업·종목의 잘못된 연결
- 동일·유사 기사 중복
- 발행 시각·수집 시각 역전
- 사건 유형·심각도·관련성·최신성 평가
- 기사 본문의 시스템 지시·Tool 요청·SQL·셸·URL 유도
- JSON Schema 실패·Enum 오류·근거 ID 위조
- 근거 없는 숫자·Semantic Metric 불일치
- Provider Timeout·Rate Limit·모델 변경
- Context 길이·Tool 호출·비용 한도 초과
- 잘못된 강력매수 추천이 정책 엔진을 통과하지 못하는지

공격 성공률, 구조화 출력 성공률, 근거 일치율, Fallback·사용자 예외 전환율을 버전별로 비교한다.

## 테스트 데이터

- 실계좌 정보·Token·계좌번호를 Fixture에 넣지 않는다.
- 공식 문서 예제도 비밀이 없는지 확인하고 내부 형식으로 최소화한다.
- 합성 종목·가격·주문·뉴스 Dataset을 기본으로 사용한다.
- 실제 뉴스는 라이선스가 허용하는 범위에서만 평가 Dataset에 포함한다.
- Replay Dataset은 원본 버전·hash·생성 코드·기간을 기록한다.
- 백테스트는 수정주가, 기업행사, 생존 편향, Look-ahead Bias를 검사한다.

## 단계별 승격 Gate

| 단계 | 필수 Gate |
|---|---|
| 구현 기반 | Unit·Contract·Secret Scan·기본 CI |
| Backtest | Look-ahead·비용·원가·재현성 검사 통과 |
| Replay·합성 Load | 동일 입력의 결정론적 결과, 중복 업무 반영 0건, 처리량·지연 기준선 확보 |
| Shadow | 데이터 최신성·완전성·정합성 기준 충족 |
| Paper | 정책 위반·동시 한도 초과·중복 주문 0건, Reservation·Kill·UNKNOWN 테스트 통과 |
| Approval-required | 운영자 인증·감사·예외 UI·알림 검증 |
| Limited Auto | 백업 복구·실패 주입·SLO·Runbook·별도 사용자 승인 |
| Live Auto | 장기간 안전 지표, 다중 활성 조건, 운영 환경 분리 |

## 1단계 구현 기준선

`CONFIRMED` 현재 자동 검증은 다음과 같다.

- `scripts/verify_repository.py`: 필수 구조, Markdown 링크, 비밀 파일·하드코딩 의심값, Mock-only 계약, Order Executor 외부 Endpoint 부재
- Java JUnit: `BigDecimal` 정확성·명시적 반올림·통화 불일치, 원본 offset/UTC, Mock-only 설정과 외부 Broker 능력 차단
- Python `unittest`: `Decimal` 강제·binary float 거절·명시적 반올림·UTC·Event Envelope·Mock-only JSON 계약
- GitHub Actions: Pull Request와 `main` push에서 저장소/Python 검증과 Maven 전체 `verify`

2026-07-24 최초 GitHub Actions Run [#1](https://github.com/kkh1212/solo_project_data/actions/runs/30068774186)에서 두 Job이 모두 통과했다. 이 결과는 Mock-only 기반의 빌드 가능성을 검증하지만 후속 단계의 Replay·동시성·주문 상태 안전성을 검증한 것으로 해석하지 않는다.

pytest, Property-based Test, Testcontainers, Replay, 부하와 장애 주입은 해당 의존성과 기반이 필요한 후속 작업이다. 현재 테스트 수단을 미래 도입 결정으로 과장하지 않는다.

## 2단계 안전 계약 테스트

`CONFIRMED` 현재 구현은 다음을 검증한다.

- 가격·수량의 양수 조건과 비율 0~1 경계
- 명시적 Decimal 반올림과 Python binary float 거절
- 거래일과 원본·UTC 시각·거래소 시간대의 독립 보존
- 동일 UUID를 Candidate와 Intent 등 다른 업무 타입으로 혼용하지 않음
- Candidate·Intent 단계 건너뛰기와 종결 상태의 추가 전이 거절
- 제출 여부 `UNKNOWN` Reservation의 `RELEASED`·`EXPIRED` 전이 거절
- `UNKNOWN` Broker Order의 직접 정상 상태 전이 거절
- `RECONCILIATION_REQUIRED` 복구 시 Reconciliation 근거 강제
- 언어 중립 `state-transitions.csv`와 Java/Python 전이 집합 일치

시장·Instrument·Event wire Schema 호환성 테스트는 해당 `TBD` 결정 전이므로 아직 포함하지 않는다.

2026-07-24 커밋 `498013b`의 GitHub Actions Run [#3](https://github.com/kkh1212/solo_project_data/actions/runs/30069492337)에서 저장소·Python 19개 테스트와 Java 25 Maven 전체 `verify`가 통과했다.

## 완료 보고

변경 완료 시 다음을 보고한다.

- 실행한 명령과 통과·실패 결과
- 실행하지 못한 테스트와 이유
- 실제 계좌/API를 호출하지 않았는지 여부
- 관련 Metric·Dashboard·문서·ADR 변경
- 알려진 위험과 다음 단계 Gate
