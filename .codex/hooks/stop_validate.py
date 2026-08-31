#!/usr/bin/env python3
"""Run fast, non-destructive checks before Codex finishes a turn."""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path


SECRET_PATTERNS = (
    ("private key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
    ("JWT", re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b")),
    ("OpenAI API key", re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b")),
    ("GitHub token", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{20,}\b")),
    ("AWS access key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
)

FORBIDDEN_TRACKED_PATHS = {
    ".env",
    "application-secret.yaml",
    "src/main/resources/application-secret.yaml",
    "tokens.csv",
}


def run(root: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=root,
        check=False,
        capture_output=True,
        text=True,
    )


def added_lines(root: Path) -> list[str]:
    diff = run(root, "git", "diff", "--no-ext-diff", "--unified=0", "HEAD", "--")
    lines = [line[1:] for line in diff.stdout.splitlines() if line.startswith("+") and not line.startswith("+++")]

    untracked = run(root, "git", "ls-files", "--others", "--exclude-standard", "-z")
    for relative_path in filter(None, untracked.stdout.split("\0")):
        path = root / relative_path
        try:
            if path.is_file() and path.stat().st_size <= 2_000_000:
                lines.extend(path.read_text(encoding="utf-8").splitlines())
        except (OSError, UnicodeDecodeError):
            continue
    return lines


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        print(json.dumps({"systemMessage": "Stop Hook 입력을 읽지 못해 검사를 생략했습니다."}, ensure_ascii=False))
        return 0

    if payload.get("stop_hook_active"):
        print("{}")
        return 0

    cwd = Path(payload.get("cwd") or ".").resolve()
    root_result = run(cwd, "git", "rev-parse", "--show-toplevel")
    if root_result.returncode != 0:
        print("{}")
        return 0

    root = Path(root_result.stdout.strip())
    problems: list[str] = []

    for args in (("git", "diff", "--check"), ("git", "diff", "--cached", "--check")):
        result = run(root, *args)
        if result.returncode != 0:
            detail = (result.stdout or result.stderr).strip().splitlines()
            problems.append(f"whitespace 오류: {detail[0] if detail else 'git diff --check 실패'}")

    staged = run(root, "git", "diff", "--cached", "--name-only", "--diff-filter=AM")
    for path in filter(None, staged.stdout.splitlines()):
        if path in FORBIDDEN_TRACKED_PATHS or path.startswith("mysql-data/"):
            problems.append(f"추적하면 안 되는 민감/런타임 파일: {path}")

    for line in added_lines(root):
        for label, pattern in SECRET_PATTERNS:
            if pattern.search(line):
                problems.append(f"새로 추가된 {label} 패턴")

    if problems:
        unique = list(dict.fromkeys(problems))
        reason = "완료 전 저장소 안전 검사를 해결하세요:\n- " + "\n- ".join(unique[:10])
        print(json.dumps({"decision": "block", "reason": reason}, ensure_ascii=False))
        return 0

    print("{}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
