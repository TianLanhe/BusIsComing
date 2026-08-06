#!/usr/bin/env python3
"""隔離工作區及原倉庫狀態腳本的回歸測試。"""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parents[1]
CREATE_WORKSPACE = SCRIPT_DIR / "create-isolated-workspace.sh"
SNAPSHOT_REPOSITORY = SCRIPT_DIR / "snapshot-repository-state.sh"
VERIFY_REPOSITORY = SCRIPT_DIR / "verify-repository-state.sh"


class WorkspaceScriptTestCase(unittest.TestCase):
    def setUp(self) -> None:
        self.sandbox = Path(tempfile.mkdtemp(prefix="screenshot-skill-tests-"))
        self.repo = self.sandbox / "source"
        self.repo.mkdir()
        self.run_command("git", "init", "--quiet", cwd=self.repo)
        self.run_command("git", "config", "user.name", "Screenshot Skill Test", cwd=self.repo)
        self.run_command(
            "git", "config", "user.email", "screenshot-skill@example.invalid", cwd=self.repo
        )
        (self.repo / ".gitignore").write_text(
            ".gradle/\n**/build/\n", encoding="utf-8"
        )
        (self.repo / "tracked.txt").write_text("committed\n", encoding="utf-8")
        self.run_command("git", "add", ".gitignore", "tracked.txt", cwd=self.repo)
        self.run_command("git", "commit", "--quiet", "-m", "fixture", cwd=self.repo)

    def tearDown(self) -> None:
        shutil.rmtree(self.sandbox, ignore_errors=True)

    @staticmethod
    def run_command(
        *args: str | Path, cwd: Path | None = None
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [str(arg) for arg in args],
            cwd=cwd,
            check=False,
            capture_output=True,
            text=True,
        )

    def create_snapshot(self) -> Path:
        snapshot = self.sandbox / "repository.snapshot"
        result = self.run_command(
            SNAPSHOT_REPOSITORY,
            "--repo",
            self.repo,
            "--output",
            snapshot,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(snapshot.is_file())
        return snapshot

    def assert_verification_detects_change(self, snapshot: Path) -> None:
        result = self.run_command(
            VERIFY_REPOSITORY,
            "--repo",
            self.repo,
            "--snapshot",
            snapshot,
        )
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertIn("狀態不一致", result.stderr)

    def test_isolated_workspace_includes_dirty_and_untracked_source(self) -> None:
        (self.repo / "tracked.txt").write_text("dirty working tree\n", encoding="utf-8")
        (self.repo / "untracked.txt").write_text("synthetic fixture\n", encoding="utf-8")
        (self.repo / ".gradle").mkdir()
        (self.repo / ".gradle" / "cache.bin").write_bytes(b"cache")
        (self.repo / "app" / "build").mkdir(parents=True)
        (self.repo / "app" / "build" / "artifact.apk").write_bytes(b"apk")

        result = self.run_command(
            CREATE_WORKSPACE,
            "--source",
            self.repo,
            "--scene-slug",
            "future-feature",
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        values = dict(
            line.split("=", 1) for line in result.stdout.splitlines() if "=" in line
        )
        task_root = Path(values["TASK_ROOT"])
        workspace = Path(values["WORKSPACE"])
        self.addCleanup(shutil.rmtree, task_root, True)

        self.assertTrue(task_root.name.startswith("busiscoming-screenshots-future-feature."))
        self.assertEqual(workspace.parent, task_root)
        self.assertEqual((workspace / "tracked.txt").read_text(), "dirty working tree\n")
        self.assertEqual((workspace / "untracked.txt").read_text(), "synthetic fixture\n")
        self.assertFalse((workspace / ".git").exists())
        self.assertFalse((workspace / ".gradle").exists())
        self.assertFalse((workspace / "app" / "build").exists())
        self.assertEqual(Path(values["OUTPUT"]), task_root / "output")
        self.assertEqual(Path(values["MANIFEST"]), task_root / "manifest.md")

    def test_isolated_workspace_rejects_invalid_slug(self) -> None:
        result = self.run_command(
            CREATE_WORKSPACE,
            "--source",
            self.repo,
            "--scene-slug",
            "Invalid/Slug",
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("slug", result.stderr)

    def test_isolated_git_worktree_never_copies_git_pointer(self) -> None:
        worktree = self.sandbox / "linked-worktree"
        result = self.run_command(
            "git",
            "worktree",
            "add",
            "--quiet",
            "--detach",
            worktree,
            cwd=self.repo,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue((worktree / ".git").is_file())
        (worktree / "tracked.txt").write_text("worktree dirty\n", encoding="utf-8")

        result = self.run_command(
            CREATE_WORKSPACE,
            "--source",
            worktree,
            "--scene-slug",
            "linked-worktree",
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        values = dict(
            line.split("=", 1) for line in result.stdout.splitlines() if "=" in line
        )
        task_root = Path(values["TASK_ROOT"])
        workspace = Path(values["WORKSPACE"])
        self.addCleanup(shutil.rmtree, task_root, True)
        self.assertFalse((workspace / ".git").exists())
        self.assertEqual((workspace / "tracked.txt").read_text(), "worktree dirty\n")

    def test_snapshot_is_stable_and_verifies_unchanged_repository(self) -> None:
        (self.repo / "tracked.txt").write_text("dirty\n", encoding="utf-8")
        (self.repo / "untracked.txt").write_text("untracked\n", encoding="utf-8")
        first = self.create_snapshot()
        second = self.sandbox / "repository-second.snapshot"
        result = self.run_command(
            SNAPSHOT_REPOSITORY,
            "--repo",
            self.repo,
            "--output",
            second,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(first.read_text(), second.read_text())

        result = self.run_command(
            VERIFY_REPOSITORY,
            "--repo",
            self.repo,
            "--snapshot",
            first,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("未改變", result.stdout)

    def test_verifier_detects_unstaged_tracked_change(self) -> None:
        snapshot = self.create_snapshot()
        (self.repo / "tracked.txt").write_text("SECRET_PAYLOAD\n", encoding="utf-8")
        self.assert_verification_detects_change(snapshot)

    def test_verifier_detects_staged_change(self) -> None:
        snapshot = self.create_snapshot()
        (self.repo / "tracked.txt").write_text("staged\n", encoding="utf-8")
        self.run_command("git", "add", "tracked.txt", cwd=self.repo)
        self.assert_verification_detects_change(snapshot)

    def test_verifier_detects_tracked_deletion(self) -> None:
        snapshot = self.create_snapshot()
        (self.repo / "tracked.txt").unlink()
        self.assert_verification_detects_change(snapshot)

    def test_verifier_detects_untracked_change(self) -> None:
        snapshot = self.create_snapshot()
        (self.repo / "new-data.json").write_text('{"name":"合成地點"}\n', encoding="utf-8")
        self.assert_verification_detects_change(snapshot)


if __name__ == "__main__":
    unittest.main()
