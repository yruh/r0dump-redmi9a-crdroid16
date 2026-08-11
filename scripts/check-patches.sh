#!/usr/bin/env bash
# Validate every patch against its recorded Git base without changing a worktree.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
RELEASE_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
ANDROID_ROOT="${1:-${ANDROID_ROOT:-}}"
SERIES="$RELEASE_ROOT/patches/series.tsv"

if [[ -z "$ANDROID_ROOT" || ! -d "$ANDROID_ROOT/.repo" ]]; then
  printf 'usage: %s /path/to/crdroid-16\n' "$0" >&2
  exit 2
fi
ANDROID_ROOT="$(cd -- "$ANDROID_ROOT" && pwd)"

checked=0
while IFS=$'\t' read -r order project base patch_name; do
  [[ -z "$order" || "$order" == \#* ]] && continue
  repo_dir="$ANDROID_ROOT/$project"
  patch_file="$RELEASE_ROOT/patches/$patch_name"
  [[ -d "$repo_dir/.git" || -f "$repo_dir/.git" ]] || {
    printf 'patch check: missing Git project: %s\n' "$project" >&2
    exit 1
  }
  [[ -s "$patch_file" ]] || {
    printf 'patch check: missing or empty patch: %s\n' "$patch_name" >&2
    exit 1
  }
  git -C "$repo_dir" cat-file -e "$base^{commit}" 2>/dev/null || {
    printf 'patch check: base object is unavailable: %s %s\n' "$project" "$base" >&2
    exit 1
  }

  temp_index="$(mktemp)"
  rm -f -- "$temp_index"
  if ! GIT_INDEX_FILE="$temp_index" git -C "$repo_dir" read-tree "$base" \
      || ! GIT_INDEX_FILE="$temp_index" git -C "$repo_dir" apply \
          --cached --check --whitespace=error-all "$patch_file"; then
    rm -f -- "$temp_index"
    printf 'patch check: does not apply to recorded base: %s\n' "$patch_name" >&2
    exit 1
  fi
  rm -f -- "$temp_index"
  printf 'patch check: OK %s %s\n' "$order" "$project"
  checked=$((checked + 1))
done < "$SERIES"

[[ "$checked" -eq 22 ]] || {
  printf 'patch check: expected 22 entries, checked %s\n' "$checked" >&2
  exit 1
}
printf 'patch check: PASS (%s patches)\n' "$checked"

