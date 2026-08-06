#!/usr/bin/env python3
"""功能截圖 Skill deterministic validator 測試。"""

from __future__ import annotations

import binascii
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib
from pathlib import Path


SCRIPTS_DIR = Path(__file__).resolve().parents[1]
MANIFEST_VALIDATOR = SCRIPTS_DIR / "validate-scene-manifest.py"
OUTPUT_VALIDATOR = SCRIPTS_DIR / "validate-screenshot-output.py"


def png_chunk(kind: bytes, data: bytes) -> bytes:
    crc = binascii.crc32(kind)
    crc = binascii.crc32(data, crc) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", crc)


def write_png(path: Path, width: int = 2, height: int = 3) -> None:
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    scanline = b"\x00" + (b"\x20\x80\xc0" * width)
    payload = b"\x89PNG\r\n\x1a\n"
    payload += png_chunk(b"IHDR", header)
    payload += png_chunk(b"IDAT", zlib.compress(scanline * height))
    payload += png_chunk(b"IEND", b"")
    path.write_bytes(payload)


def manifest_text(*, status: str, cleanup: str) -> str:
    return f"""# 截圖任務清單

- 場景 slug：`update-prompt`
- 任務狀態：`{status}`
- 語言：`zh-Hant-HK`
- 主題：`light`
- Font scale：`1.0`
- 方向：`portrait`
- 截圖模式：`app-content`

## 原始需求

顯示版本更新提示。

## 場景契約

使用真實更新 Dialog 及合成版本狀態。

## 截圖清單

- `update-prompt.png`：更新提示 Dialog。

## 合成資料

版本與日期均為合成資料。

## 設備環境

API 36.1、360dp 直向。

## 資料注入

透過既有 update state store 注入。

## 驗收條件

Dialog、版本及三個操作完整可見。

## 清理

- 原倉庫清理：`{cleanup}`
- 模擬器清理：`{cleanup}`

## 限制與失敗

無。
"""


class ValidatorTestCase(unittest.TestCase):
    def run_script(self, script: Path, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(script), *args],
            check=False,
            capture_output=True,
            text=True,
        )

    def test_contract_manifest_accepts_planned_cleanup(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "manifest.md"
            manifest.write_text(manifest_text(status="planned", cleanup="pending"))

            result = self.run_script(
                MANIFEST_VALIDATOR,
                str(manifest),
                "--phase",
                "contract",
                "--expect",
                "update-prompt.png",
            )

        self.assertEqual(0, result.returncode, result.stderr)

    def test_complete_manifest_requires_all_sections(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "manifest.md"
            text = manifest_text(status="passed", cleanup="passed")
            manifest.write_text(text.replace("## 合成資料", "## 缺失資料章節"))

            result = self.run_script(
                MANIFEST_VALIDATOR,
                str(manifest),
                "--phase",
                "complete",
                "--expect",
                "update-prompt.png",
            )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("合成資料", result.stderr)

    def test_complete_manifest_rejects_placeholders(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "manifest.md"
            manifest.write_text(
                manifest_text(status="passed", cleanup="passed").replace("無。", "TODO")
            )

            result = self.run_script(
                MANIFEST_VALIDATOR,
                str(manifest),
                "--phase",
                "complete",
            )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("placeholder", result.stderr)

    def test_complete_manifest_requires_successful_cleanup(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "manifest.md"
            manifest.write_text(manifest_text(status="failed", cleanup="failed"))

            result = self.run_script(
                MANIFEST_VALIDATOR,
                str(manifest),
                "--phase",
                "complete",
            )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("清理", result.stderr)

    def test_output_validator_accepts_valid_png_and_exact_dimensions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            write_png(output / "update-prompt.png")

            result = self.run_script(
                OUTPUT_VALIDATOR,
                str(output),
                "--expect",
                "update-prompt.png=2x3",
                "--min-bytes",
                "1",
            )

        self.assertEqual(0, result.returncode, result.stderr)

    def test_output_validator_rejects_missing_png(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            result = self.run_script(
                OUTPUT_VALIDATOR,
                directory,
                "--expect",
                "missing.png",
            )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing.png", result.stderr)

    def test_output_validator_rejects_corrupt_signature(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            (output / "broken.png").write_bytes(b"not a png")

            result = self.run_script(
                OUTPUT_VALIDATOR,
                str(output),
                "--expect",
                "broken.png",
                "--min-bytes",
                "1",
            )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("PNG", result.stderr)

    def test_output_validator_rejects_crc_corruption(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            png = output / "broken.png"
            write_png(png)
            payload = bytearray(png.read_bytes())
            payload[-5] ^= 0x01
            png.write_bytes(payload)

            result = self.run_script(
                OUTPUT_VALIDATOR,
                str(output),
                "--expect",
                "broken.png",
                "--min-bytes",
                "1",
            )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("CRC", result.stderr)

    def test_output_validator_rejects_dimension_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            write_png(output / "update-prompt.png")

            result = self.run_script(
                OUTPUT_VALIDATOR,
                str(output),
                "--expect",
                "update-prompt.png=3x2",
                "--min-bytes",
                "1",
            )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("尺寸", result.stderr)

    def test_output_validator_rejects_unexpected_png_by_default(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            write_png(output / "expected.png")
            write_png(output / "unexpected.png")

            result = self.run_script(
                OUTPUT_VALIDATOR,
                str(output),
                "--expect",
                "expected.png",
                "--min-bytes",
                "1",
            )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("unexpected.png", result.stderr)

    def test_output_validator_rejects_non_png_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            write_png(output / "expected.png")
            (output / "instrumentation.log").write_text("debug only\n", encoding="utf-8")

            result = self.run_script(
                OUTPUT_VALIDATOR,
                str(output),
                "--expect",
                "expected.png=2x3",
                "--min-bytes",
                "1",
            )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("非 PNG", result.stderr)


if __name__ == "__main__":
    unittest.main()
