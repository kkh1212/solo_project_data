# ADR-0001: 소수 독립 서비스와 Order Executor 격리

- 상태: Accepted
- 결정일: 2026-07-16

## 배경

프로젝트는 Java 거래 핵심, Python AI·데이터 처리, Kafka·Airflow 파이프라인을 포함한다. 개인 또는 작은 팀이 4 vCPU·7.8 GiB 개발 서버에서 작업하므로 전면 마이크로서비스는 운영 복잡도가 과도하다. 반면 단일 프로세스는 Broker Credential과 AI 경계를 충분히 격리하기 어렵다.

## 결정

다음 소수의 배포 경계를 사용한다.

- Java Trading Core는 정책·리스크·Order Candidate/Intent를 소유하는 모듈러 구조다.
- Java Order Executor는 별도 서비스이며 Broker Credential을 보유하는 유일한 구성요소다.
- Python Intelligence는 뉴스·AI Agent Harness·평가를 담당하고 주문 Credential을 갖지 않는다.
- Collector와 Batch Platform은 실패·실행 형태에 맞게 분리한다.
- 내부 모듈은 필요성이 측정되기 전까지 독립 마이크로서비스로 분해하지 않는다.

## 결과

### 장점

- 주문 권한과 AI를 강하게 격리한다.
- Java와 Python의 강점을 책임에 맞게 사용한다.
- 전면 마이크로서비스보다 배포·관측·장애 지점이 적다.
- 향후 처리량이 확인된 Worker만 독립 확장할 수 있다.

### 비용

- 하나 이상의 빌드·배포 파이프라인이 필요하다.
- 서비스 간 계약과 Trace를 관리해야 한다.
- 개발 서버에서 모든 프로필을 동시에 실행하기 어렵다.

## 재검토 조건

- 특정 모듈의 독립 확장·배포 필요가 지표로 확인됨
- Credential 경계를 더 분리해야 함
- 서비스 수가 운영 인력과 자원 한계를 초과함
