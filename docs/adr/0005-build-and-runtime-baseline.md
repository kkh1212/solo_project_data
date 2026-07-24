# ADR-0005: 1단계 빌드와 런타임 기준선

- 상태: Accepted
- 결정일: 2026-07-24

## 배경

1단계는 Java/Python 골격, 반복 가능한 테스트와 CI가 필요하다. 개발 서버에는 Python 3.12.3만 설치되어 있고 Java·Maven은 없다. 시스템 패키지나 Docker 권한을 사용자 승인 없이 변경해서는 안 되며, 이벤트 직렬화 형식과 실제 공급자 연동은 아직 `TBD`다.

## 결정

- Java 기준은 JDK 25로 고정한다. JDK 25는 2025-09-16 GA된 LTS 계열이며 프로젝트의 새 구현 기준으로 사용한다.
- Spring Boot 4.1.0과 Maven 3.9.x를 사용한다. Spring Boot 4.1.0은 Java 17~26과 Maven 3.6.3 이상을 지원한다.
- Maven 중앙 저장소 의존성은 Spring Boot 4.1.0 BOM과 명시된 Maven Plugin 버전으로 관리한다. Maven 3.9 계열의 권장 로컬 버전은 결정 시점 최신 GA인 3.9.16이다.
- Python은 현재 개발 환경과 일치하는 3.12를 사용한다. 1단계 공통 계약은 표준 라이브러리만 사용하며 제3자 의존성 잠금 파일은 빈 상태를 명시한다.
- CI는 GitHub-hosted 격리 러너에서 JDK 25와 Python 3.12를 준비해 동일 검증을 실행한다.
- 로컬 JDK·Maven·Python 패키지나 시스템 구성을 자동 설치하지 않는다.
- `contracts/build/runtime-baseline.json`과 저장소 검증으로 JDK·Maven 범위·Spring Boot BOM·Plugin·Python 의존성 기준의 불일치를 차단한다.
- Maven Wrapper는 Bootstrap 다운로드와 로컬 Java 실행 수요가 생길 때 checksum 고정과 함께 다시 검토한다. 현재 1단계에서는 추가하지 않는다.
- Java/Python 공통 타입은 언어 내부 불변식만 구현한다. Avro/Protobuf와 패키지 배포 방식은 후속 계약 단계의 `TBD`로 유지한다.

공식 근거:

- [OpenJDK JDK 25](https://openjdk.org/projects/jdk/25/)
- [Spring Boot 4.1.0 System Requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html)
- [Apache Maven Releases History](https://maven.apache.org/docs/history)

## 결과

### 장점

- 새 프로젝트에 LTS Java 기준을 사용하고 CI와 개발 문서의 버전을 일치시킨다.
- Spring Boot BOM으로 호환되는 의존성 집합을 고정한다.
- 로컬 시스템 변경 없이 Python 검증을 즉시 실행하고 Java 검증은 CI에서 재현할 수 있다.
- wire contract의 미결정을 보존하면서 Decimal·UTC·Mock-only 불변식을 먼저 검증한다.

### 비용

- 현재 개발 서버에서는 Java 빌드를 바로 실행할 수 없다.
- Maven 의존성을 최초로 받는 CI 또는 승인된 개발 환경에는 네트워크가 필요하다.
- Python 테스트 도구가 표준 `unittest`로 제한되며 Property-based·pytest 도입은 필요성과 승인 후 진행한다.
- Maven 전체 Transitive Dependency Lock을 제공하지 않으므로 BOM 밖 의존성이 추가되면 별도 잠금·검증 방식을 재검토해야 한다.

## 재검토 조건

- Spring Boot 4.1 계열의 지원 상태나 보안 기준 변경
- JDK 25 공급자·운영 이미지 결정
- Python 서비스가 제3자 라이브러리와 배포 패키지를 실제로 필요로 함
- Maven Wrapper 또는 별도 의존성 잠금 방식의 재현성 이점이 확인됨
