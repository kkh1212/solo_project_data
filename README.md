# AI 기반 주식 거래 자동화 시스템

실시간·배치 데이터 파이프라인, 결정론적 정책·리스크 통제, 안전한 주문 실행, AI Agent Harness를 결합하는 포트폴리오 프로젝트다.

## 현재 상태

- 단계: 1단계 저장소 기반 Gate 통과, 2단계 안전 도메인 계약 진행 중
- 기본 거래 상태: 실주문 불가
- 확정 시장·Broker: 미국주식, Toss Securities Open API
- 저장소 상태: Java/Python Decimal·시간·Instrument·상태·외부 주문 제안 계약,
  PostgreSQL 주문 원장, Mock-only 기본 Gate, 기본 구성에 연결되지 않은 Toss
  Adapter 기반
- 운영 목표: 외부 정책 시스템의 주문 의도를 안전하게 검증·실행하고 단계별 승인 후 실거래

수익 전략과 수익 목적의 정책은 다른 환경이 소유한다. 이 저장소는 실행 안전
정책, 주문 멱등성, 상태 복구와 Broker 연동을 소유한다. 주문 종류·한도·시간외
거래, Event Schema, 뉴스·LLM 공급자 등은 아직 확정하지 않았다.
`docs/DECISIONS_PENDING.md`를 기준으로 결정한다.

현재 코드에는 공식 OAS 1.2.4 기반 OAuth·계좌 목록·미국주식 주문 생성·조회·취소
Adapter와 환경 자격증명 입력 Port가 있다. 다만 기본 실행 모드는 `mock-only`
하나뿐이고 애플리케이션은 외부 네트워크 능력이 없는 `MockBrokerGateway`만
생성한다. 실제 자격증명·계좌정보는 없으며 Toss 서버 호출과 실주문도 수행하지
않았다.

외부 정책 입력은
[External Order Proposal 의미 계약](contracts/internal-api/external-order-proposal.schema.json)을
사용한다. 외부 제안은 내부 실행 승인이 아니며 Trading Core의 인증·만료·중복,
안전 정책과 Reservation 검증을 통과해야 내부 `Order Intent`가 된다. 입력
Transport와 서명·상호 인증 방식은 아직 `TBD`다.

`CONFIRMED` PostgreSQL 저장 계층은 서비스별 쓰기 경계를 분리한다.
`trading_core` Schema는 Proposal Inbox, Risk Decision, Reservation, Intent와
Outbox를 한 트랜잭션으로 기록하고, `order_executor` Schema는 Intent Inbox,
Broker Order, 제출 시도와 Reconciliation Case를 기록한다. Trading Core에는
Toss 계좌 Sequence나 계좌번호를 저장하지 않는다. 이 Repository는 아직 기본
애플리케이션의 주문 흐름에 연결하지 않았고 JDBC·Flyway 자동 구성도 기본
애플리케이션에서는 명시적으로 제외했다. 따라서 DB가 준비되지 않아도 기본
`mock-only` 애플리케이션은 외부 DB나 실주문 능력이 없다.

## 문서 읽기 순서

1. [프로젝트 컨텍스트](docs/PROJECT_CONTEXT.md)
2. [전체 아키텍처](docs/ARCHITECTURE.md)
3. [거래·주문·정책 시스템](docs/TRADING_SYSTEM.md)
4. [보안 및 위험](docs/SECURITY_AND_RISK.md)
5. [데이터 아키텍처](docs/DATA_ARCHITECTURE.md)
6. [기술 전략](docs/TECHNOLOGY_STRATEGY.md)
7. [AI Agent Harness](docs/AI_HARNESS.md)
8. [외부 연동 제약](docs/EXTERNAL_INTEGRATIONS.md)
9. [관측가능성](docs/OBSERVABILITY.md)
10. [테스트 전략](docs/TEST_STRATEGY.md)
11. [로드맵](docs/ROADMAP.md)
12. [미결정 사항](docs/DECISIONS_PENDING.md)
13. [ADR 목록](docs/adr/README.md)

Codex와 자동화 도구의 반복 작업 규칙은 [AGENTS.md](AGENTS.md)를 따른다.

## 개발 환경

빌드 기준 결정은 [ADR-0005](docs/adr/0005-build-and-runtime-baseline.md)를 따른다.

- Python 3.12
- Java 25
- Maven 3.9.x

2026-07-24 현재 개발 서버에는 Python 3.12.3만 있고 Java와 Maven은 설치되어 있지 않다. 시스템 패키지를 자동 설치하지 않으며 Java 검증은 동일 버전의 GitHub Actions 러너에서도 수행한다.

현재 서버는 개발·테스트·Replay 용도다. `live-auto` 운영 서버로 간주하지 않는다.

## 빌드와 테스트

제3자 Python 패키지 설치 없이 다음 검증을 실행할 수 있다.

```bash
make verify
make test-python
```

Java 25와 Maven 3.9.x가 준비된 환경에서는 JUnit과 애플리케이션 패키징까지 검증한다.

```bash
mvn --batch-mode --no-transfer-progress verify
```

PostgreSQL 통합 테스트는 `TEST_POSTGRES_URL`, `TEST_POSTGRES_USER`,
`TEST_POSTGRES_PASSWORD`가 모두 있을 때만 실행한다. GitHub Actions는
계좌정보가 없는 합성 PostgreSQL 18.4 서비스를 자동 제공한다. 로컬 DB나
시스템 패키지는 이 저장소가 자동 설치·변경하지 않으며, 환경변수가 없으면
DB 통합 테스트는 명시적으로 건너뛴다.

모든 로컬 도구가 준비된 경우 전체 검증은 다음과 같다.

```bash
make test
```

`make verify`는 필수 저장소 구조, Markdown 상대 링크, 비밀 파일·하드코딩 의심값,
기본 Mock-only 설정, Toss OAS 기준 계약, PostgreSQL 마이그레이션 불변식과
Order Executor 기본 Bean을 검사한다. CI는 Pull Request와 `main` push에서
같은 검증을 반복하고 PostgreSQL 통합 테스트를 추가로 실행한다.

## 저장소 구조

```text
apps/                 Java Trading Core와 격리된 기본 Mock-only Order Executor
contracts/            빌드·상태 전이·Event·내부 API·Broker Adapter 계약
libs/java-domain/     Decimal·UTC·식별자·상태 머신·실행 모드
libs/python-contracts/ Decimal·UTC·식별자·상태 머신·실행 모드
platform/             후속 데이터 플랫폼 구성을 위한 경계
tests/                Contract·Replay·Load·장애 테스트 경계
fixtures/synthetic/   실계좌 정보가 없는 합성 데이터 전용
```

이벤트 wire format은 아직 `TBD`다. 미국주식 Instrument의 시장·시간대·통화만
확정됐으며 정확한 거래소·세션·종목 메타데이터 Schema는 후속 계약이다. 현재
Java/Python 타입과 상태 전이 CSV는 언어 내부 안전 계약이며 Avro 또는 Protobuf
확정으로 해석하지 않는다.
