#!/usr/bin/env python3
"""1단계 저장소 정책, 링크, 비밀정보와 Mock-only 계약을 검증한다."""

from __future__ import annotations

import json
import re
import sys
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
    "apps/trading-core/pom.xml",
    "apps/order-executor/pom.xml",
    "contracts/events/README.md",
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

    executor_main = PROJECT_ROOT / "apps" / "order-executor" / "src" / "main"
    for path in executor_main.rglob("*"):
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        if "http://" in text or "https://" in text:
            relative_path = path.relative_to(PROJECT_ROOT)
            errors.append(f"Order Executor 외부 Endpoint 의심 내용: {relative_path}")


def main() -> int:
    errors: list[str] = []
    files = repository_files()

    verify_required_paths(errors)
    verify_secret_policy(files, errors)
    verify_markdown_links(files, errors)
    verify_mock_only_contract(errors)

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(
        "저장소 검증 통과: 필수 구조, 문서 링크, Secret 정책, Mock-only 계약"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
