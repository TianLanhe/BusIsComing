#!/usr/bin/env python3
"""不依賴圖像套件地驗證功能截圖 PNG 結構與尺寸。"""

from __future__ import annotations

import argparse
import binascii
import re
import struct
import sys
from dataclasses import dataclass
from pathlib import Path


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
EXPECTATION_PATTERN = re.compile(
    r"(?P<name>[A-Za-z0-9][A-Za-z0-9._-]*\.png)(?:=(?P<width>[1-9][0-9]*)x(?P<height>[1-9][0-9]*))?"
)


class PngValidationError(ValueError):
    pass


@dataclass(frozen=True)
class Expectation:
    name: str
    width: int | None
    height: int | None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="驗證 BusIsComing 功能截圖 PNG 產物。"
    )
    parser.add_argument("output_dir", type=Path, help="只包含最終 PNG 的 output 目錄")
    parser.add_argument(
        "--expect",
        action="append",
        required=True,
        metavar="NAME[=WIDTHxHEIGHT]",
        help="預期 PNG；指定尺寸時作精確比較，可重複",
    )
    parser.add_argument("--min-width", type=int, default=320)
    parser.add_argument("--min-height", type=int, default=480)
    parser.add_argument("--min-bytes", type=int, default=4096)
    parser.add_argument(
        "--allow-extra",
        action="store_true",
        help="允許 output 目錄存在未列入 --expect 的其他 PNG",
    )
    return parser.parse_args()


def parse_expectation(raw: str) -> Expectation:
    match = EXPECTATION_PATTERN.fullmatch(raw)
    if not match:
        raise ValueError(f"無效 --expect：{raw}")
    width = int(match.group("width")) if match.group("width") else None
    height = int(match.group("height")) if match.group("height") else None
    return Expectation(match.group("name"), width, height)


def inspect_png(path: Path) -> tuple[int, int]:
    payload = path.read_bytes()
    if not payload.startswith(PNG_SIGNATURE):
        raise PngValidationError("PNG signature 不正確")

    offset = len(PNG_SIGNATURE)
    chunk_index = 0
    width: int | None = None
    height: int | None = None
    saw_idat = False
    saw_iend = False

    while offset < len(payload):
        if offset + 12 > len(payload):
            raise PngValidationError("PNG chunk 被截斷")
        length = struct.unpack(">I", payload[offset : offset + 4])[0]
        kind = payload[offset + 4 : offset + 8]
        data_start = offset + 8
        data_end = data_start + length
        crc_end = data_end + 4
        if crc_end > len(payload):
            raise PngValidationError("PNG chunk 長度超出檔案")
        data = payload[data_start:data_end]
        expected_crc = struct.unpack(">I", payload[data_end:crc_end])[0]
        actual_crc = binascii.crc32(kind)
        actual_crc = binascii.crc32(data, actual_crc) & 0xFFFFFFFF
        if expected_crc != actual_crc:
            label = kind.decode("ascii", errors="replace")
            raise PngValidationError(f"PNG {label} chunk CRC 不正確")

        if chunk_index == 0 and kind != b"IHDR":
            raise PngValidationError("PNG 第一個 chunk 必須是 IHDR")
        if kind == b"IHDR":
            if chunk_index != 0 or length != 13:
                raise PngValidationError("PNG IHDR 結構不正確")
            width, height = struct.unpack(">II", data[:8])
            if width == 0 or height == 0:
                raise PngValidationError("PNG 尺寸不得為 0")
        elif kind == b"IDAT":
            saw_idat = True
        elif kind == b"IEND":
            if length != 0:
                raise PngValidationError("PNG IEND 長度必須為 0")
            saw_iend = True
            offset = crc_end
            if offset != len(payload):
                raise PngValidationError("PNG IEND 後存在多餘資料")
            break

        offset = crc_end
        chunk_index += 1

    if width is None or height is None:
        raise PngValidationError("PNG 缺少 IHDR")
    if not saw_idat:
        raise PngValidationError("PNG 缺少 IDAT")
    if not saw_iend:
        raise PngValidationError("PNG 缺少 IEND")
    return width, height


def main() -> int:
    args = parse_args()
    if args.min_width < 1 or args.min_height < 1 or args.min_bytes < 1:
        print("ERROR: 最小尺寸及檔案大小必須大於 0", file=sys.stderr)
        return 2
    if not args.output_dir.is_dir():
        print(f"ERROR: output 目錄不存在：{args.output_dir}", file=sys.stderr)
        return 2

    try:
        expectations = [parse_expectation(raw) for raw in args.expect]
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
    if len({item.name for item in expectations}) != len(expectations):
        print("ERROR: --expect 不得重複同一檔名", file=sys.stderr)
        return 2

    errors: list[str] = []
    expected_names = {item.name for item in expectations}
    entries = list(args.output_dir.iterdir())
    actual_names = {
        path.name
        for path in entries
        if path.is_file() and not path.is_symlink() and path.suffix == ".png"
    }
    for entry in sorted(path.name for path in entries if path.name not in actual_names):
        errors.append(f"存在非 PNG 產物：{entry}")
    if not args.allow_extra:
        for filename in sorted(actual_names - expected_names):
            errors.append(f"存在未預期 PNG：{filename}")

    for expectation in expectations:
        path = args.output_dir / expectation.name
        if not path.is_file():
            errors.append(f"缺少預期 PNG：{expectation.name}")
            continue
        size = path.stat().st_size
        if size < args.min_bytes:
            errors.append(
                f"PNG 檔案太小：{expectation.name}（{size} < {args.min_bytes} bytes）"
            )
            continue
        try:
            width, height = inspect_png(path)
        except (OSError, PngValidationError) as error:
            errors.append(f"{expectation.name}：{error}")
            continue

        if expectation.width is not None and expectation.height is not None:
            if (width, height) != (expectation.width, expectation.height):
                errors.append(
                    f"PNG 尺寸不符：{expectation.name} 是 {width}x{height}，"
                    f"預期 {expectation.width}x{expectation.height}"
                )
        elif width < args.min_width or height < args.min_height:
            errors.append(
                f"PNG 尺寸過小：{expectation.name} 是 {width}x{height}，"
                f"至少要 {args.min_width}x{args.min_height}"
            )

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    for expectation in expectations:
        width, height = inspect_png(args.output_dir / expectation.name)
        print(f"OK: {expectation.name} {width}x{height}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
