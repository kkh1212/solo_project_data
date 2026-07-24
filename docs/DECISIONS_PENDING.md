# 미결정 사항

## 사용 방법

이 문서의 항목은 설계 저장 요청으로 자동 확정되지 않았다. 결정을 내릴 때 다음을 기록하고 필요한 경우 ADR을 만든다.

```text
decision
status
decided_at
decided_by
rationale
safety_impact
implementation_impact
operations_impact
review_date
```

정책 수치는 투자 권유가 아니라 Replay·Paper에서 검증할 보수적 초기 가설이다.

## 완료된 구현 기반 결정

| 결정 | 상태 | 결정일 | 근거 |
|---|---|---|---|
| Java 25, Spring Boot 4.1.0, Maven 3.9.x, Python 3.12 기준선 | `CONFIRMED` | 2026-07-24 | [ADR-0005](adr/0005-build-and-runtime-baseline.md) |
| 1단계 실행 모드는 `mock-only`만 허용하고 외부 Broker 기능을 포함하지 않음 | `CONFIRMED` | 2026-07-24 | [ADR-0003](adr/0003-live-safety-boundary.md), [ADR-0005](adr/0005-build-and-runtime-baseline.md) |
| 수익 목적의 전략·정책 수치는 후속 구현으로 두고 Decimal·상태 전이·멱등성·Reservation 등 안전 기반을 우선 | `CONFIRMED` | 2026-07-24 | 사용자 작업 순서 결정, [ROADMAP](ROADMAP.md) |
| 거래 시장은 미국주식, 기본 시장 시간대는 `America/New_York`, 주문 통화는 `USD` | `CONFIRMED` | 2026-07-24 | 사용자 결정, [ADR-0006](adr/0006-us-toss-execution-boundary.md) |
| 최종 Broker는 Toss Securities Open API이며 실제 투자까지가 목표 | `CONFIRMED` | 2026-07-24 | 사용자 결정, [ADR-0006](adr/0006-us-toss-execution-boundary.md) |
| 수익 전략·수익 목적 정책은 외부 환경이 소유하고 이 저장소는 안전한 주문 실행·복구를 소유 | `CONFIRMED` | 2026-07-24 | 사용자 결정, [ADR-0006](adr/0006-us-toss-execution-boundary.md) |
| Toss Adapter 구현은 허용하되 기본 애플리케이션 연결·자격증명·실계좌 호출·주문은 별도 Gate로 분리 | `CONFIRMED` | 2026-07-24 | 사용자 의존성 승인, [ADR-0003](adr/0003-live-safety-boundary.md), [ADR-0006](adr/0006-us-toss-execution-boundary.md) |

이 결정은 수익 전략의 내용·정책 수치·주문 종류·시간외 거래·이벤트 직렬화
형식, 실제 자격증명 등록이나 특정 주문을 확정하지 않는다.

현재 2단계는 미국주식 Instrument 최소 계약과 기본 구성에 연결되지 않은 Toss
Adapter까지 구현한다. Kafka wire Schema와 실제 주문 활성화는 아직 구현하지
않는다.

## P0: 구현 기반과 데이터 계약 전에 결정

| 항목 | 권장 기본안 | 대안·장단점 | 안전·복잡도 | 결정 시점 |
|---|---|---|---|---|
| 거래 시장 | 미국주식 | 환율·야간 운영·DST·미국 세션 복잡도 수용 | 실행·데이터 계약에 미국 시장 시간 필요 | 결정 완료: ADR-0006 |
| 초기 종목 수 | 유동성 높은 10~20개 | 5개는 표본 부족, 50개 이상은 Rate Limit·뉴스 부담 | 적을수록 운영 단순 | Collector 계약 전 |
| Allowlist | 대형·고유동성 보통주, 경고·정지 제외 | ETF 포함 시 자산·섹터 모델 확장 | 엄격한 목록 권장 | Policy 모델 전 |
| 전략 | 외부 정책 시스템이 소유, 구체 전략은 `TBD` | 이 저장소에 중복 구현하면 소유권·버전 충돌 | 실행 계층은 정책 근거·버전만 검증 | 외부 주문 의도 계약 전 |
| 계산 주기 | 1분 특징, 5분 신호 | 일봉은 안전·단순, 초 단위는 REST 부담 | 낮은 빈도 권장 | Event Schema 전 |
| 데이터 단위 | 공식 1분봉·일봉 우선 | 최근 체결 REST는 완전 Tick 아님 | 과장 방지 | Collector 설계 전 |
| Event Schema | Avro+Registry PoC | Protobuf는 생성 타입 강점, Decimal 별도 모델 | 둘 다 중간 | 계약 구현 전 |
| 구조 기준선 | Java Core+격리 Executor+Python Intelligence | 전면 모놀리스·마이크로서비스 | 현재 권장안은 승인 기준선 | 결정 완료: ADR-0001 |

## P1: 뉴스·전략·Mock 정책 구현 전에 결정

| 항목 | 권장 기본안 | 대안·장단점 | 안전·복잡도 | 결정 시점 |
|---|---|---|---|---|
| 뉴스 공급자 | 저장·AI 사용권을 계약으로 확인한 공급자 | RSS·웹 수집은 법률·완전성 위험 | 법률 영향 큼 | News Collector 전 |
| 뉴스 보관 | 원문은 허용 기간만, 메타·파생값 별도 | 장기 원문은 편리하나 계약 부담 | 최소 보관 권장 | 공급자 계약 시 |
| 주문 종류 | 신규 매수 지정가 `DAY` 우선 | 시장가는 체결↑·슬리피지↑ | 지정가 보수적 | Mock Policy 전 |
| 시장가 정책 | 신규 매수 금지, 위험 매도 별도 검토 | 전면 허용은 단순하지만 급변 위험 | 제한 권장 | Paper 전 |
| 시간외 거래 | 초기 금지 | 프리·애프터는 유동성·스프레드 위험 | 금지 권장 | Paper 전 |
| 단일 주문 | 평가자산 5%와 5만원 중 작은 값 가설 | 고정액만 사용하면 자산 변화 미반영 | 보수적 가설 | Paper Policy 전 |
| 종목 비중 | 최대 10% 가설 | 5~20% 검증 | 낮을수록 안전 | Paper 전 |
| 섹터 비중 | 최대 25% 가설 | 분류 품질에 따라 더 낮춤 | 미분류 처리 필요 | Paper 전 |
| 일일 손실 | 1% 도달 시 신규 주문 중단 가설 | 0.5~2% Replay | 낮을수록 안전 | Paper 전 |
| 최대 낙폭 | 5% 도달 시 자동거래 중단 가설 | 3~10% Replay | 재활성화 절차 필요 | Paper 전 |
| 손절·익절 | 손절 3~5% 가설, 익절·추적은 비교 | 고정·ATR·추적 | Gap·비용 반영 | Strategy 평가 시 |
| 거래 횟수 | 일 3회 이하 가설 | 종목·전략별 제한 | 과매매 억제 | Paper 전 |
| 최소 현금 | 50% 가설 | 30~70% | 높을수록 안전 | Paper 전 |
| 비용 모델 | 공식 수수료·세금·환율·슬리피지 | 고정 근사는 단순하지만 편향 | 공식 확인 필요 | Backtest 전 |
| 원가·수익률 | Broker 대사 가능한 명시적 원가법·TWR | 단순 수익률은 현금흐름 왜곡 | 지표 정확성 영향 | Mart 전 |

## P2: AI·Shadow·승인형 거래 전에 결정

| 항목 | 권장 기본안 | 대안·장단점 | 안전·복잡도 | 결정 시점 |
|---|---|---|---|---|
| LLM 공급자 | 추상화 후 고정 Dataset 평가로 선택 | 단일은 단순, 다중은 복구력↑·운영↑ | 데이터 정책 중요 | AI Harness 호출 전 |
| AI 거래 영향 | 초기 0, Shadow만 | 이후 신규 매수 억제 신호부터 제한 적용 | 위험 감소 방향 우선 | 평가 기준 충족 후 |
| 데이터 보존 | Kafka 수일, Raw 90일 가설, Mart 3년 | 디스크·계약에 따라 조정 | 뉴스·감사 분리 | Storage 구성 전 |
| 운영 위치 | 현재 서버 dev, live 별도 호스트 | 단일 서버는 저비용·단일 장애점 | 분리 권장 | Shadow 전 |
| Secret 관리 | 운영 Secret Manager | 파일 기반은 단순하나 위험 | 최소 권한·회전 필요 | 공식 API 전 |
| 알림 | Push/Webhook+이메일 | SMS·전화는 비용 증가 | Critical 이중 채널 | Shadow 전 |
| UI | Grafana 관측+별도 Admin UI | SPA는 기능 강하지만 초기 부담 | Kill·승인은 전용 UI | 승인형 전 |
| 사용자 인증 | MFA·재인증·Role 분리 | 단일 비밀번호는 부족 | 운영 안전 핵심 | Admin UI 전 |
| RPO/RTO | 주문 DB·Audit 중심으로 별도 정의 | 비용과 복구 시간 Trade-off | 운영 승격 영향 | Shadow 전 |
| 승인형 절차 | 1회성 승인 capability와 만료 | 단순 버튼은 재사용 위험 | 감사·Race 고려 | 승인형 전 |

## P3: 운영 지표 확인 후 결정

| 항목 | 도입 기준 | 대안·영향 | 상태 |
|---|---|---|---|
| Redis | 다중 인스턴스 공유 상태·Rate Limit 필요 | PostgreSQL·로컬 캐시 유지 | `LATER` |
| Kafka Streams | Stateful Window·Join이 직접 Consumer보다 유리 | 일반 Consumer 유지 | `LATER` |
| Loki | 로그 검색량·보존 요구가 로컬 방식을 초과 | 다른 Log Backend | `LATER` |
| Kubernetes | 다중 호스트·확장·복구·배포 Toil이 Compose 한계 초과 | Compose 유지 | `LATER` |
| Spark | SQL·Polars 백필이 반복적으로 SLO 위반 | 단일 노드 최적화 | `LATER` |
| 별도 DW | 분석 Query가 운영 PostgreSQL SLO 방해 | Read Replica·DuckDB·ClickHouse·Cloud DW | `LATER` |
| GraphDB/RDF | 관계 탐색이 관계형 모델보다 명확한 이점 | PostgreSQL 관계 모델 | `LATER` |
| IaC | 운영 환경 반복 생성·감사 필요 | 수동 문서화는 Drift 위험 | `LATER` |

## 현재 제외

- Hadoop 초기 도입
- 현재 개발 서버의 `live-auto`
- AI 자유 SQL·셸·임의 주문
- 전체 Exactly-Once 보장 주장
- 기본 Kill Switch의 자동 전량 청산
- 비공식 뉴스 무단 수집
- 공식 확인 없는 WebSocket·Sandbox·체결 필드 구현
