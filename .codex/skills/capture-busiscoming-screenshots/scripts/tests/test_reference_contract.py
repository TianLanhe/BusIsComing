#!/usr/bin/env python3
"""確保文件中的場景契約模板可直接通過配套校驗器。"""

from __future__ import annotations

import re
import subprocess
import tempfile
import unittest
from pathlib import Path


SKILL_DIR = Path(__file__).resolve().parents[2]
REFERENCE = SKILL_DIR / "references" / "scene-contract.md"
VALIDATOR = SKILL_DIR / "scripts" / "validate-scene-manifest.py"


class SceneContractReferenceTestCase(unittest.TestCase):
    def template(self) -> str:
        text = REFERENCE.read_text(encoding="utf-8")
        match = re.search(
            r"```markdown\n(# 截圖任務清單\n.*?)\n```",
            text,
            re.DOTALL,
        )
        self.assertIsNotNone(match, "scene-contract.md 缺少 manifest markdown 模板")
        return match.group(1) + "\n"

    def validate(self, text: str, phase: str) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory(prefix="scene-contract-reference-") as directory:
            manifest = Path(directory) / "manifest.md"
            manifest.write_text(text, encoding="utf-8")
            return subprocess.run(
                [
                    str(VALIDATOR),
                    str(manifest),
                    "--phase",
                    phase,
                    "--expect",
                    "01-feature-state.png",
                ],
                check=False,
                capture_output=True,
                text=True,
            )

    def test_planned_template_is_a_valid_contract(self) -> None:
        result = self.validate(self.template(), "contract")
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_documented_status_transition_is_a_valid_complete_manifest(self) -> None:
        completed = (
            self.template()
            .replace("- 任務狀態：`planned`", "- 任務狀態：`passed`")
            .replace("- 原倉庫清理：`pending`", "- 原倉庫清理：`passed`")
            .replace("- 模擬器清理：`pending`", "- 模擬器清理：`passed`")
        )
        result = self.validate(completed, "complete")
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
