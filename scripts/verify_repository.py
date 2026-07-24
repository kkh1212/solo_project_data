#!/usr/bin/env python3
"""저장소 정책, 링크, 비밀정보와 기본 Fail-Closed 계약을 검증한다."""

from __future__ import annotations

import csv
import json
import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path
from urllib.parse import unquote

PROJECT_ROOT = Path(__file__).resolve().parents[1]

EXCLUDED_PARTS = {
    ".git",
    ".idea",
    ".mypy_cache",
    ".pytest_cache",
    ".ruff_cache",
    ".venv",
    "__pycache__",
    "target",
    "venv",
}

REQUIRED_PATHS = (
    ".github/workflows/ci.yml",
    "apps/trading-core/src/main/resources/db/migration/trading-core/V1__safe_order_storage.sql",
    "apps/order-executor/src/main/resources/db/migration/order-executor/V1__execution_journal.sql",
    "apps/trading-core/pom.xml",
    "apps/order-executor/pom.xml",
    "contracts/broker-adapter/toss-oas-baseline.json",
    "contracts/build/runtime-baseline.json",
    "contracts/domain/state-transitions.csv",
    "contracts/events/README.md",
    "contracts/internal-api/external-order-proposal.schema.json",
    "contracts/internal-api/execution-mode.schema.json",
    "fixtures/synthetic/README.md",
    "libs/java-domain/pom.xml",
    "libs/python-contracts/src/solo_contracts/__init__.py",
    "platform/README.md",
    "tests/contract/README.md",
)

FORBIDDEN_SECRET_SUFFIXES = {".key", ".p12", ".pem", ".pfx"}
MARKDOWN_LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
SECRET_ASSIGNMENT = re.compile(
    r"(?i)(?:api[_-]?key|access[_-]?token|client[_-]?secret|password)"
    r"\s*[:=]\s*[\"']?[A-Za-z0-9+/=_-]{20,}"
)
PRIVATE_KEY_BLOCK = re.compile(
    "-----BEGIN " + r"(?:RSA |EC |OPENSSH )?PRIVATE KEY-----"
)
MAVEN_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}


def repository_files() -> list[Path]:
    return sorted(
        path
        for path in PROJECT_ROOT.rglob("*")
        if path.is_file() and not EXCLUDED_PARTS.intersection(path.parts)
    )


def verify_required_paths(errors: list[str]) -> None:
    for relative_path in REQUIRED_PATHS:
        if not (PROJECT_ROOT / relative_path).exists():
            errors.append(f"필수 경로 누락: {relative_path}")


def verify_secret_policy(files: list[Path], errors: list[str]) -> None:
    for path in files:
        relative_path = path.relative_to(PROJECT_ROOT)
        if path.name == ".env" or (
            path.name.startswith(".env.") and path.name != ".env.example"
        ):
            errors.append(f"커밋 금지 환경 파일: {relative_path}")
        if path.suffix.lower() in FORBIDDEN_SECRET_SUFFIXES:
            errors.append(f"커밋 금지 비밀 파일 형식: {relative_path}")

        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if PRIVATE_KEY_BLOCK.search(text):
            errors.append(f"Private Key 의심 내용: {relative_path}")
        if SECRET_ASSIGNMENT.search(text):
            errors.append(f"하드코딩된 Secret 의심 내용: {relative_path}")


def verify_markdown_links(files: list[Path], errors: list[str]) -> None:
    for path in files:
        if path.suffix.lower() != ".md":
            continue
        text = path.read_text(encoding="utf-8")
        for raw_target in MARKDOWN_LINK.findall(text):
            target = raw_target.strip().strip("<>")
            if (
                not target
                or target.startswith(("#", "http://", "https://", "mailto:"))
            ):
                continue
            relative_target = unquote(target.split("#", maxsplit=1)[0])
            resolved = (path.parent / relative_target).resolve()
            if not resolved.exists():
                source = path.relative_to(PROJECT_ROOT)
                errors.append(f"깨진 문서 링크: {source} -> {target}")


def verify_mock_only_contract(errors: list[str]) -> None:
    contract_path = (
        PROJECT_ROOT
        / "contracts"
        / "internal-api"
        / "execution-mode.schema.json"
    )
    try:
        contract = json.loads(contract_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        errors.append(f"실행 모드 계약을 읽을 수 없음: {error}")
        return

    properties = contract.get("properties", {})
    if properties.get("mode", {}).get("const") != "mock-only":
        errors.append("실행 모드 계약은 mock-only 상수여야 함")
    if properties.get("externalBrokerEnabled", {}).get("const") is not False:
        errors.append("실행 모드 계약은 외부 Broker를 비활성화해야 함")

    application_config = (
        PROJECT_ROOT
        / "apps"
        / "order-executor"
        / "src"
        / "main"
        / "resources"
        / "application.yml"
    ).read_text(encoding="utf-8")
    if "mode: mock-only" not in application_config:
        errors.append("Order Executor 기본 설정은 mock-only여야 함")

    application_source = (
        PROJECT_ROOT
        / "apps"
        / "order-executor"
        / "src"
        / "main"
        / "java"
        / "io"
        / "github"
        / "soloprojectdata"
        / "executor"
        / "OrderExecutorApplication.java"
    ).read_text(encoding="utf-8")
    if "return new MockBrokerGateway()" not in application_source:
        errors.append("Order Executor 기본 Bean은 MockBrokerGateway여야 함")
    if "new TossBrokerGateway" in application_source:
        errors.append("Order Executor 기본 구성에 TossBrokerGateway를 연결할 수 없음")


def verify_toss_contract(errors: list[str]) -> None:
    baseline_path = (
        PROJECT_ROOT
        / "contracts"
        / "broker-adapter"
        / "toss-oas-baseline.json"
    )
    try:
        baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        errors.append(f"Toss OAS 기준 계약을 읽을 수 없음: {error}")
        return

    expected = {
        "oasVersion": "1.2.4",
        "baseUri": "https://openapi.tossinvest.com",
        "accountHeader": "X-Tossinvest-Account",
    }
    for field, expected_value in expected.items():
        if baseline.get(field) != expected_value:
            errors.append(
                f"Toss 기준 계약 불일치: {field}={baseline.get(field)}, "
                f"expected={expected_value}"
            )
    client_order_id = baseline.get("clientOrderId", {})
    if client_order_id.get("requiredByInternalContract") is not True:
        errors.append("Toss clientOrderId는 내부 계약에서 필수여야 함")
    if client_order_id.get("brokerIdempotencyWindowMinutes") != 10:
        errors.append("Toss Broker 멱등성 기준은 확인된 10분이어야 함")

    source_path = (
        PROJECT_ROOT
        / "apps"
        / "order-executor"
        / "src"
        / "main"
        / "java"
        / "io"
        / "github"
        / "soloprojectdata"
        / "executor"
        / "toss"
        / "TossApiContract.java"
    )
    source = source_path.read_text(encoding="utf-8")
    for expected_literal in expected.values():
        if expected_literal not in source:
            errors.append(f"Toss Java 계약 상수 누락: {expected_literal}")


def verify_external_order_proposal_contract(errors: list[str]) -> None:
    contract_path = (
        PROJECT_ROOT
        / "contracts"
        / "internal-api"
        / "external-order-proposal.schema.json"
    )
    try:
        contract = json.loads(contract_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        errors.append(f"외부 주문 제안 계약을 읽을 수 없음: {error}")
        return

    required = set(contract.get("required", []))
    expected_required = {
        "schemaVersion",
        "proposalId",
        "producerId",
        "policy",
        "generatedAt",
        "expiresAt",
        "accountAlias",
        "instrument",
        "order",
    }
    if required != expected_required:
        errors.append(
            "외부 주문 제안 필수 필드 불일치: "
            f"actual={sorted(required)}, expected={sorted(expected_required)}"
        )
    properties = contract.get("properties", {})
    if properties.get("schemaVersion", {}).get("const") != 1:
        errors.append("외부 주문 제안 schemaVersion은 1이어야 함")

    definitions = contract.get("$defs", {})
    instrument = definitions.get("usEquityInstrument", {})
    market = instrument.get("properties", {}).get("market", {})
    if market.get("const") != "US_EQUITIES":
        errors.append("외부 주문 제안 시장은 US_EQUITIES여야 함")
    account_pattern = properties.get("accountAlias", {}).get("pattern", "")
    if not account_pattern.startswith("^[a-z]"):
        errors.append("외부 주문 제안은 계좌번호 대신 명시적 별칭을 요구해야 함")
    order_variants = properties.get("order", {}).get("oneOf", [])
    if len(order_variants) != 3:
        errors.append("외부 주문 제안은 세 가지 주문 형태를 구분해야 함")


def verify_postgres_contract(errors: list[str]) -> None:
    migrations = {
        "trading-core": (
            PROJECT_ROOT
            / "apps"
            / "trading-core"
            / "src"
            / "main"
            / "resources"
            / "db"
            / "migration"
            / "trading-core"
            / "V1__safe_order_storage.sql"
        ),
        "order-executor": (
            PROJECT_ROOT
            / "apps"
            / "order-executor"
            / "src"
            / "main"
            / "resources"
            / "db"
            / "migration"
            / "order-executor"
            / "V1__execution_journal.sql"
        ),
    }
    required_fragments = {
        "trading-core": {
            "NUMERIC",
            "TIMESTAMPTZ",
            "external_proposal_inbox",
            "risk_reservation",
            "order_intent",
            "transactional_outbox",
            "content_sha256",
        },
        "order-executor": {
            "TIMESTAMPTZ",
            "intent_inbox",
            "order_attempt_journal",
            "reconciliation_case",
            "uq_order_attempt_single_submit",
            "UNKNOWN",
        },
    }
    for owner, path in migrations.items():
        text = path.read_text(encoding="utf-8")
        for fragment in required_fragments[owner]:
            if fragment not in text:
                errors.append(f"{owner} PostgreSQL 계약 누락: {fragment}")
        if re.search(r"\b(?:REAL|DOUBLE\s+PRECISION)\b", text, re.IGNORECASE):
            errors.append(f"{owner} 금융 저장에 binary float 형식 사용 금지")

    executor_sql = migrations["order-executor"].read_text(encoding="utf-8")
    if re.search(r"account_?no|account_?number", executor_sql, re.IGNORECASE):
        errors.append("Executor Schema에 실제 계좌번호 Column을 둘 수 없음")

    expected_dependencies = {
        "spring-boot-starter-jdbc",
        "spring-boot-starter-flyway",
        "flyway-database-postgresql",
        "postgresql",
    }
    for module in ("trading-core", "order-executor"):
        pom_path = PROJECT_ROOT / "apps" / module / "pom.xml"
        root = ElementTree.parse(pom_path).getroot()
        actual = {
            element.text
            for element in root.findall(
                ".//m:dependencies/m:dependency/m:artifactId",
                namespaces=MAVEN_NAMESPACE,
            )
        }
        missing = expected_dependencies - actual
        if missing:
            errors.append(
                f"{module} PostgreSQL 의존성 누락: {sorted(missing)}"
            )

        source_name = (
            "TradingCoreApplication.java"
            if module == "trading-core"
            else "OrderExecutorApplication.java"
        )
        package_name = (
            "core" if module == "trading-core" else "executor"
        )
        application_source = (
            PROJECT_ROOT
            / "apps"
            / module
            / "src"
            / "main"
            / "java"
            / "io"
            / "github"
            / "soloprojectdata"
            / package_name
            / source_name
        ).read_text(encoding="utf-8")
        for auto_configuration in (
            "org.springframework.boot.jdbc.autoconfigure."
            "DataSourceAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure."
            "FlywayAutoConfiguration",
        ):
            if auto_configuration not in application_source:
                errors.append(
                    f"{module} 기본 실행의 DB 자동 연결 차단 누락: "
                    f"{auto_configuration}"
                )


def verify_build_baseline(errors: list[str]) -> None:
    baseline_path = PROJECT_ROOT / "contracts" / "build" / "runtime-baseline.json"
    try:
        baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
        root_pom = ElementTree.parse(PROJECT_ROOT / "pom.xml").getroot()
    except (OSError, json.JSONDecodeError, ElementTree.ParseError) as error:
        errors.append(f"빌드 기준 계약을 읽을 수 없음: {error}")
        return

    java = baseline.get("java", {})
    python = baseline.get("python", {})
    expected_values = {
        ".java-version": java.get("languageVersion"),
        ".python-version": python.get("languageVersion"),
    }
    for relative_path, expected in expected_values.items():
        actual = (PROJECT_ROOT / relative_path).read_text(encoding="utf-8").strip()
        if actual != expected:
            errors.append(
                f"빌드 기준 불일치: {relative_path}={actual}, expected={expected}"
            )

    pom_parent_version = root_pom.findtext("m:parent/m:version", namespaces=MAVEN_NAMESPACE)
    spring_boot_version = java.get("framework", {}).get("springBoot")
    if pom_parent_version != spring_boot_version:
        errors.append(
            "Spring Boot 기준 불일치: "
            f"pom={pom_parent_version}, baseline={spring_boot_version}"
        )

    pom_java_version = root_pom.findtext(
        "m:properties/m:java.version",
        namespaces=MAVEN_NAMESPACE,
    )
    if pom_java_version != java.get("languageVersion"):
        errors.append(
            "Java 기준 불일치: "
            f"pom={pom_java_version}, baseline={java.get('languageVersion')}"
        )

    pom_enforcer_version = root_pom.findtext(
        "m:properties/m:maven-enforcer-plugin.version",
        namespaces=MAVEN_NAMESPACE,
    )
    baseline_enforcer_version = java.get("plugins", {}).get("mavenEnforcer")
    if pom_enforcer_version != baseline_enforcer_version:
        errors.append(
            "Maven Enforcer 기준 불일치: "
            f"pom={pom_enforcer_version}, baseline={baseline_enforcer_version}"
        )

    pom_maven_range = root_pom.findtext(
        ".//m:requireMavenVersion/m:version",
        namespaces=MAVEN_NAMESPACE,
    )
    baseline_maven_range = java.get("buildTool", {}).get("acceptedVersion")
    if pom_maven_range != baseline_maven_range:
        errors.append(
            "Maven 범위 불일치: "
            f"pom={pom_maven_range}, baseline={baseline_maven_range}"
        )

    locked_python_dependencies = [
        line.strip()
        for line in (PROJECT_ROOT / "requirements-dev.lock")
        .read_text(encoding="utf-8")
        .splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    baseline_python_dependencies = python.get("thirdPartyDependencies")
    if locked_python_dependencies != baseline_python_dependencies:
        errors.append(
            "Python 의존성 기준 불일치: "
            f"lock={locked_python_dependencies}, "
            f"baseline={baseline_python_dependencies}"
        )

    for pom_path in PROJECT_ROOT.rglob("pom.xml"):
        pom_root = ElementTree.parse(pom_path).getroot()
        for version_element in pom_root.findall(
            ".//m:dependencies/m:dependency/m:version",
            namespaces=MAVEN_NAMESPACE,
        ):
            version = (version_element.text or "").strip()
            if (
                version.upper() in {"LATEST", "RELEASE"}
                or version.startswith(("[", "("))
            ):
                relative_path = pom_path.relative_to(PROJECT_ROOT)
                errors.append(
                    f"동적 Maven 의존성 버전 금지: {relative_path} -> {version}"
                )


def verify_domain_contract(errors: list[str]) -> None:
    contract_path = PROJECT_ROOT / "contracts" / "domain" / "state-transitions.csv"
    try:
        with contract_path.open(encoding="utf-8", newline="") as contract_file:
            rows = list(csv.DictReader(contract_file))
    except OSError as error:
        errors.append(f"상태 머신 계약을 읽을 수 없음: {error}")
        return

    expected_fields = {
        "machine",
        "from",
        "to",
        "required_evidence",
        "forbidden_submission_certainty",
    }
    if not rows or set(rows[0]) != expected_fields:
        errors.append("상태 전이 계약 Header 또는 행이 올바르지 않음")
        return

    identities: set[tuple[str, str, str]] = set()
    for row in rows:
        identity = (row["machine"], row["from"], row["to"])
        if not all(identity):
            errors.append(f"상태 전이 계약 필수값 누락: {row}")
        if identity in identities:
            errors.append(f"상태 전이 계약 중복: {identity}")
        identities.add(identity)
        if (
            row["machine"] == "riskReservation"
            and row["to"] in {"RELEASED", "EXPIRED"}
            and row["forbidden_submission_certainty"] != "UNKNOWN"
        ):
            errors.append(f"Reservation UNKNOWN 해제 Guard 누락: {identity}")
        if (
            row["machine"] == "brokerOrder"
            and row["from"] == "RECONCILIATION_REQUIRED"
            and row["required_evidence"] != "RECONCILIATION"
        ):
            errors.append(f"Broker 복구 Reconciliation Guard 누락: {identity}")


def main() -> int:
    errors: list[str] = []
    files = repository_files()

    verify_required_paths(errors)
    verify_secret_policy(files, errors)
    verify_markdown_links(files, errors)
    verify_mock_only_contract(errors)
    verify_toss_contract(errors)
    verify_external_order_proposal_contract(errors)
    verify_postgres_contract(errors)
    verify_build_baseline(errors)
    verify_domain_contract(errors)

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(
        "저장소 검증 통과: 구조, 링크, Secret, 빌드 기준, "
        "기본 Mock-only, Toss 기준, 도메인·PostgreSQL 계약"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
