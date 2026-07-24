# 빌드 기준 계약

`runtime-baseline.json`은 1단계에서 검증할 언어·빌드·Framework 기준을 고정한다.

- Java 25와 Maven 3.9.x 범위
- Spring Boot 4.1.0 BOM
- 명시된 Maven Enforcer Plugin 버전
- Python 3.12와 제3자 의존성 없음

Maven Wrapper는 현재 개발 서버에 JDK·Maven이 없고 Bootstrap 다운로드가 필요하므로 아직 추가하지 않는다. GitHub Actions의 격리된 JDK 25 환경과 Maven Enforcer로 빌드를 검증하며, 로컬 Java 환경을 승인해 구성할 때 Wrapper와 checksum 고정을 다시 검토한다.

이 파일은 Maven의 전체 Transitive Dependency Lock을 주장하지 않는다. 직접 기준과 BOM을 고정하고 동적 의존성 버전을 금지하는 현재 단계의 계약이다.
