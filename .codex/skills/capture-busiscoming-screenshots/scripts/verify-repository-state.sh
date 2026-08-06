#!/usr/bin/env bash
# 驗證 Git 倉庫仍與截圖任務開始時的狀態一致。

set -euo pipefail

usage() {
  echo "用法：$0 --repo REPO --snapshot FILE" >&2
}

repo=""
snapshot=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      repo=$2
      shift 2
      ;;
    --snapshot)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      snapshot=$2
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

[[ -n "$repo" && -n "$snapshot" ]] || { usage; exit 2; }
if [[ ! -f "$snapshot" ]]; then
  echo "ERROR: snapshot 不存在：$snapshot" >&2
  exit 2
fi

format=$(sed -n 's/^format=//p' "$snapshot")
snapshot_repo=$(sed -n 's/^repo=//p' "$snapshot")
expected=$(sed -n 's/^fingerprint=//p' "$snapshot")
if [[ "$format" != "1" || -z "$snapshot_repo" || ! "$expected" =~ ^[0-9a-f]{64}$ ]]; then
  echo "ERROR: snapshot 格式無效：$snapshot" >&2
  exit 2
fi

if [[ ! -d "$repo" ]]; then
  echo "ERROR: repo 不存在或不是目錄：$repo" >&2
  exit 2
fi
repo=$(cd "$repo" && pwd -P)
if [[ "$repo" != "$snapshot_repo" ]]; then
  echo "ERROR: snapshot 屬於另一個倉庫：$snapshot_repo" >&2
  exit 2
fi

script_dir=$(cd "$(dirname "$0")" && pwd -P)
actual=$(
  "$script_dir/snapshot-repository-state.sh" \
    --repo "$repo" \
    --fingerprint-only
)
if [[ "$actual" != "$expected" ]]; then
  echo "ERROR: 原倉庫狀態不一致；不要宣稱清理完成" >&2
  git -C "$repo" status --short >&2 || true
  exit 1
fi

echo "OK: 原倉庫未改變：$repo"
