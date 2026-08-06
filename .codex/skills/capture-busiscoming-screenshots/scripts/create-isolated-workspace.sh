#!/usr/bin/env bash
# 為一次截圖任務建立包含目前未提交內容的系統暫存隔離副本。

set -euo pipefail

usage() {
  echo "用法：$0 --source REPO --scene-slug SLUG" >&2
}

source_repo=""
scene_slug=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      source_repo=$2
      shift 2
      ;;
    --scene-slug)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      scene_slug=$2
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: 未知參數：$1" >&2
      usage
      exit 2
      ;;
  esac
done

[[ -n "$source_repo" && -n "$scene_slug" ]] || { usage; exit 2; }
if [[ ! "$scene_slug" =~ ^[a-z0-9]+(-[a-z0-9]+)*$ ]]; then
  echo "ERROR: scene slug 只可使用小寫英數及單一連字號分隔" >&2
  exit 2
fi
command -v git >/dev/null 2>&1 || { echo "ERROR: 找不到 git" >&2; exit 2; }
command -v rsync >/dev/null 2>&1 || { echo "ERROR: 找不到 rsync" >&2; exit 2; }

if [[ ! -d "$source_repo" ]]; then
  echo "ERROR: source 不存在或不是目錄：$source_repo" >&2
  exit 2
fi
source_repo=$(cd "$source_repo" && pwd -P)
git_root=$(git -C "$source_repo" rev-parse --show-toplevel 2>/dev/null || true)
if [[ -z "$git_root" ]]; then
  echo "ERROR: source 不是 Git 倉庫：$source_repo" >&2
  exit 2
fi
git_root=$(cd "$git_root" && pwd -P)
if [[ "$source_repo" != "$git_root" ]]; then
  echo "ERROR: source 必須是 Git 倉庫根目錄：$git_root" >&2
  exit 2
fi

temp_base=${TMPDIR:-/tmp}
temp_base=${temp_base%/}
mkdir -p "$temp_base"
task_root=$(mktemp -d "$temp_base/busiscoming-screenshots-$scene_slug.XXXXXX")

cleanup_failed_creation() {
  exit_code=$?
  if [[ $exit_code -ne 0 && -n "${task_root:-}" ]]; then
    case "$task_root" in
      "$temp_base"/busiscoming-screenshots-"$scene_slug".*)
        rm -rf -- "$task_root"
        ;;
    esac
  fi
  exit "$exit_code"
}
trap cleanup_failed_creation EXIT

workspace="$task_root/workspace"
output="$task_root/output"
mkdir -p "$workspace" "$output"

rsync -a \
  --exclude '.git' \
  --exclude '.gradle/' \
  --exclude '.idea/' \
  --exclude 'build/' \
  --exclude '.DS_Store' \
  "$source_repo/" "$workspace/"

trap - EXIT
printf 'TASK_ROOT=%s\n' "$task_root"
printf 'WORKSPACE=%s\n' "$workspace"
printf 'OUTPUT=%s\n' "$output"
printf 'MANIFEST=%s\n' "$task_root/manifest.md"
