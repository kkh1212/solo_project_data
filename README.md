# AI 기반 주식 거래 자동화 시스템

실시간·배치 데이터 파이프라인, 결정론적 정책·리스크 통제, 안전한 주문 실행, AI Agent Harness를 결합하는 포트폴리오 프로젝트다.

## 현재 상태

- 단계: 설계 기준선 문서화 완료, 애플리케이션 구현 전
- 기본 거래 상태: 실주문 불가
- 저장소 상태: 문서만 존재하며 애플리케이션·인프라 코드는 아직 없음
- 운영 목표: 안전 검증 단계를 통과한 뒤 보수적인 `live-auto`

시장·전략·정책 수치·뉴스 공급자·LLM 공급자 등은 아직 확정하지 않았다. `docs/DECISIONS_PENDING.md`를 기준으로 결정한다.

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

## 개발 환경 점검 기준선

2026-07-16 읽기 전용 점검 결과다.

| 항목 | 상태 |
|---|---|
| OS | Ubuntu 24.04.3 LTS, UTC, x86-64 |
| 자원 | 4 vCPU, 메모리 7.8 GiB, Swap 없음, 디스크 약 70 GiB 여유 |
| Python | 3.12.3 |
| Java/Maven/Gradle | 설치되지 않음 |
| Docker | CLI와 Compose 설치, 현재 사용자는 daemon socket 접근 불가 |
| Kafka/PostgreSQL/Redis/Airflow/dbt | 실행 파일 미확인 |

현재 서버는 개발·테스트·Replay 용도다. `live-auto` 운영 서버로 간주하지 않는다.

## 빌드와 테스트

아직 구현 코드와 빌드 구성이 없으므로 실행 명령도 없다. 다음 구현 단계에서 저장소 골격과 Mock-only 실행 환경을 만들 때 실제 명령을 이 문서와 `AGENTS.md`에 추가한다.
