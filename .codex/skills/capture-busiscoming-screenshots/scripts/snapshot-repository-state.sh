#!/usr/bin/env bash
# 對 Git 倉庫可見狀態建立不洩漏內容的 SHA-256 指紋。

set -euo pipefail

usage() {
  echo "用法：$0 --repo REPO (--output FILE | --fingerprint-only)" >&2
}

repo=""
output=""
fingerprint_only=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      repo=$2
      shift 2
      ;;
    --output)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      output=$2
      shift 2
      ;;
    --fingerprint-only)
      fingerprint_only=true
      shift
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

[[ -n "$repo" ]] || { usage; exit 2; }
if [[ "$fingerprint_only" == true && -n "$output" ]] || \
   [[ "$fingerprint_only" == false && -z "$output" ]]; then
  usage
  exit 2
fi
command -v git >/dev/null 2>&1 || { echo "ERROR: 找不到 git" >&2; exit 2; }

if [[ ! -d "$repo" ]]; then
  echo "ERROR: repo 不存在或不是目錄：$repo" >&2
  exit 2
fi
repo=$(cd "$repo" && pwd -P)
git_root=$(git -C "$repo" rev-parse --show-toplevel 2>/dev/null || true)
if [[ -z "$git_root" ]]; then
  echo "ERROR: repo 不是 Git 倉庫：$repo" >&2
  exit 2
fi
git_root=$(cd "$git_root" && pwd -P)
if [[ "$repo" != "$git_root" ]]; then
  echo "ERROR: repo 必須是 Git 倉庫根目錄：$git_root" >&2
  exit 2
fi

scratch=$(mktemp -d "${TMPDIR:-/tmp}/busiscoming-repo-state.XXXXXX")
cleanup_scratch() {
  rm -rf -- "$scratch"
}
trap cleanup_scratch EXIT
canonical="$scratch/canonical.bin"

{
  printf 'capture-busiscoming-repository-state-v1\0'
  printf 'head\0'
  git -C "$repo" rev-parse --verify HEAD 2>/dev/null || printf 'UNBORN\n'
  printf '\0symbolic-head\0'
  git -C "$repo" symbolic-ref -q HEAD 2>/dev/null || printf 'DETACHED\n'
  printf '\0status\0'
  git -C "$repo" status --porcelain=v1 -z --untracked-files=all
  printf '\0unstaged-diff\0'
  git -C "$repo" diff --binary --no-ext-diff --no-textconv
  printf '\0staged-diff\0'
  git -C "$repo" diff --cached --binary --no-ext-diff --no-textconv
  printf '\0untracked-content\0'
  while IFS= read -r -d '' path; do
    printf '%s\0' "$path"
    (cd "$repo" && git hash-object --no-filters -- "$path")
    printf '\0'
  done < <(git -C "$repo" ls-files --others --exclude-standard -z)
} > "$canonical"

if command -v shasum >/dev/null 2>&1; then
  fingerprint=$(shasum -a 256 "$canonical" | awk '{print $1}')
elif command -v sha256sum >/dev/null 2>&1; then
  fingerprint=$(sha256sum "$canonical" | awk '{print $1}')
else
  echo "ERROR: 找不到 shasum 或 sha256sum" >&2
  exit 2
fi

if [[ "$fingerprint_only" == true ]]; then
  printf '%s\n' "$fingerprint"
  exit 0
fi

output_parent=$(dirname "$output")
if [[ ! -d "$output_parent" ]]; then
  echo "ERROR: snapshot 父目錄不存在：$output_parent" >&2
  exit 2
fi
{
  printf 'format=1\n'
  printf 'repo=%s\n' "$repo"
  printf 'fingerprint=%s\n' "$fingerprint"
} > "$output"
echo "OK: 已保存原倉庫狀態指紋：$output"
