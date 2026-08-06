#!/usr/bin/env python3
"""驗證功能截圖場景契約與完成清單。"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


REQUIRED_SECTIONS = (
    "原始需求",
    "場景契約",
    "截圖清單",
    "合成資料",
    "設備環境",
    "資料注入",
    "驗收條件",
    "清理",
    "限制與失敗",
)
REQUIRED_FIELDS = (
    "場景 slug",
    "任務狀態",
    "語言",
    "主題",
    "Font scale",
    "方向",
    "截圖模式",
    "原倉庫清理",
    "模擬器清理",
)
FIELD_PATTERN = re.compile(r"^- ([^：\n]+)：\s*`([^`]+)`\s*$", re.MULTILINE)
PLACEHOLDER_PATTERN = re.compile(r"\b(?:TODO|TBD)\b|待補|待定|\[\s\]", re.IGNORECASE)
SLUG_PATTERN = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="驗證 BusIsComing 功能截圖 manifest.md。"
    )
    parser.add_argument("manifest", type=Path, help="manifest.md 路徑")
    parser.add_argument(
        "--phase",
        choices=("contract", "complete"),
        required=True,
        help="contract 允許 pending 清理；complete 要求完整清理",
    )
    parser.add_argument(
        "--expect",
        action="append",
        default=[],
        metavar="PNG",
        help="預期在截圖清單出現的 PNG 檔名；可重複",
    )
    return parser.parse_args()


def section_bodies(text: str) -> dict[str, str]:
    matches = list(re.finditer(r"^## ([^\n]+)\s*$", text, re.MULTILINE))
    bodies: dict[str, str] = {}
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        bodies[match.group(1)] = text[match.end() : end].strip()
    return bodies


def validate(text: str, *, phase: str, expected: list[str]) -> list[str]:
    errors: list[str] = []
    if not text.startswith("# 截圖任務清單\n"):
        errors.append("首行必須是 '# 截圖任務清單'")

    bodies = section_bodies(text)
    for section in REQUIRED_SECTIONS:
        if section not in bodies:
            errors.append(f"缺少必要 section：{section}")
        elif not bodies[section]:
            errors.append(f"section 不得為空：{section}")

    fields = dict(FIELD_PATTERN.findall(text))
    for field in REQUIRED_FIELDS:
        if field not in fields:
            errors.append(f"缺少必要欄位：{field}")

    slug = fields.get("場景 slug", "")
    if slug and not SLUG_PATTERN.fullmatch(slug):
        errors.append("場景 slug 只可使用小寫英數及單一連字號分隔")

    status = fields.get("任務狀態")
    allowed_statuses = {"planned"} if phase == "contract" else {"passed", "failed"}
    if status is not None and status not in allowed_statuses:
        errors.append(
            f"{phase} 階段任務狀態必須是：{', '.join(sorted(allowed_statuses))}"
        )

    cleanup_allowed = {"pending", "passed"} if phase == "contract" else {"passed"}
    for field in ("原倉庫清理", "模擬器清理"):
        value = fields.get(field)
        if value is not None and value not in cleanup_allowed:
            errors.append(
                f"{phase} 階段{field}必須是：{', '.join(sorted(cleanup_allowed))}"
            )

    if PLACEHOLDER_PATTERN.search(text):
        errors.append("manifest 仍包含 placeholder")

    listed_pngs = set(re.findall(r"`([^`/]+\.png)`", bodies.get("截圖清單", "")))
    if not listed_pngs:
        errors.append("截圖清單至少要列出一個 PNG 檔名")
    for filename in expected:
        if Path(filename).name != filename or not filename.endswith(".png"):
            errors.append(f"--expect 必須是單一 PNG 檔名：{filename}")
        elif filename not in listed_pngs:
            errors.append(f"截圖清單缺少預期檔名：{filename}")

    return errors


def main() -> int:
    args = parse_args()
    if not args.manifest.is_file():
        print(f"ERROR: manifest 不存在：{args.manifest}", file=sys.stderr)
        return 2
    try:
        text = args.manifest.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        print("ERROR: manifest 必須使用 UTF-8", file=sys.stderr)
        return 2

    errors = validate(text, phase=args.phase, expected=args.expect)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(f"OK: {args.phase} manifest 已通過驗證：{args.manifest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
