#!/usr/bin/env bash
# Apply the verified patch series and copy source-only overlays into a crDroid tree.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
RELEASE_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
SERIES="$RELEASE_ROOT/patches/series.tsv"
ALLOW_BASE_MISMATCH=0
DRY_RUN=0
ANDROID_ROOT=""

usage() {
  printf 'usage: %s [--dry-run] [--allow-base-mismatch] /path/to/crdroid-16\n' "$0" >&2
}

while (($#)); do
  case "$1" in
    --dry-run) DRY_RUN=1 ;;
    --allow-base-mismatch) ALLOW_BASE_MISMATCH=1 ;;
    -h|--help) usage; exit 0 ;;
    -*) usage; exit 2 ;;
    *)
      [[ -z "$ANDROID_ROOT" ]] || { usage; exit 2; }
      ANDROID_ROOT="$1"
      ;;
  esac
  shift
done

[[ -n "$ANDROID_ROOT" && -d "$ANDROID_ROOT/.repo" ]] || { usage; exit 2; }
ANDROID_ROOT="$(cd -- "$ANDROID_ROOT" && pwd)"

declare -a projects patches states
index=0

# Preflight the whole series before changing any file.
while IFS=$'\t' read -r order project base patch_name; do
  [[ -z "$order" || "$order" == \#* ]] && continue
  repo_dir="$ANDROID_ROOT/$project"
  patch_file="$RELEASE_ROOT/patches/$patch_name"
  [[ -d "$repo_dir/.git" || -f "$repo_dir/.git" ]] || {
    printf 'apply: missing Git project: %s\n' "$project" >&2
    exit 1
  }
  current="$(git -C "$repo_dir" rev-parse HEAD)"
  if [[ "$current" != "$base" && "$ALLOW_BASE_MISMATCH" != 1 ]]; then
    printf 'apply: base mismatch for %s\n  expected: %s\n  current:  %s\n' \
      "$project" "$base" "$current" >&2
    exit 1
  fi

  if git -C "$repo_dir" apply --reverse --check "$patch_file" >/dev/null 2>&1; then
    state=present
  elif git -C "$repo_dir" apply --check --whitespace=error-all "$patch_file"; then
    state=apply
  else
    printf 'apply: patch neither applies nor matches current files: %s\n' "$patch_name" >&2
    exit 1
  fi
  projects[index]="$project"
  patches[index]="$patch_name"
  states[index]="$state"
  index=$((index + 1))
done < "$SERIES"

art_source="$RELEASE_ROOT/overlay/art/runtime/r0dump_runtime.cc"
art_target="$ANDROID_ROOT/art/runtime/r0dump_runtime.cc"
manager_source="$RELEASE_ROOT/overlay/packages/apps/R0DUMPManager"
manager_target="$ANDROID_ROOT/packages/apps/R0DUMPManager"

if [[ -e "$art_target" ]] && ! cmp -s "$art_source" "$art_target"; then
  printf 'apply: overlay collision: %s\n' "$art_target" >&2
  exit 1
fi
if [[ -e "$manager_target" ]] && ! diff -qr "$manager_source" "$manager_target" >/dev/null; then
  printf 'apply: overlay collision: %s\n' "$manager_target" >&2
  exit 1
fi

for ((i = 0; i < index; i++)); do
  if [[ "${states[i]}" == present ]]; then
    printf 'apply: already present %s\n' "${projects[i]}"
  elif [[ "$DRY_RUN" == 1 ]]; then
    printf 'apply: would apply %s\n' "${projects[i]}"
  else
    git -C "$ANDROID_ROOT/${projects[i]}" apply \
      --whitespace=error-all "$RELEASE_ROOT/patches/${patches[i]}"
    printf 'apply: applied %s\n' "${projects[i]}"
  fi
done

if [[ "$DRY_RUN" == 1 ]]; then
  [[ -e "$art_target" ]] || printf 'apply: would install %s\n' "$art_target"
  [[ -e "$manager_target" ]] || printf 'apply: would install %s\n' "$manager_target"
else
  if [[ ! -e "$art_target" ]]; then
    install -D -m 0644 "$art_source" "$art_target"
    printf 'apply: installed art/runtime/r0dump_runtime.cc\n'
  fi
  if [[ ! -e "$manager_target" ]]; then
    mkdir -p -- "$(dirname -- "$manager_target")"
    cp -a -- "$manager_source" "$manager_target"
    printf 'apply: installed packages/apps/R0DUMPManager\n'
  fi
fi

printf 'apply: PASS (%s patches, dry_run=%s)\n' "$index" "$DRY_RUN"

