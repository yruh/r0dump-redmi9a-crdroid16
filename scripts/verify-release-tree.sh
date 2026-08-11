#!/usr/bin/env bash
# Fast CI check for the small public repository; no Android checkout is required.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd -- "$ROOT"

for required in LICENSE NOTICE README.md patches/series.tsv \
    overlay/art/runtime/r0dump_runtime.cc \
    overlay/packages/apps/R0DUMPManager/Android.bp; do
  [[ -s "$required" ]] || {
    printf 'release check: missing %s\n' "$required" >&2
    exit 1
  }
done

for script in scripts/*.sh; do
  bash -n "$script"
done

forbidden="$(find . -path './.git' -prune -o -type f \
  \( -name '*.apk' -o -name '*.img' -o -name '*.so' -o -name '*.zip' \
     -o -name '*.jks' -o -name '*.keystore' -o -name '*.pk8' \) -print)"
[[ -z "$forbidden" ]] || {
  printf 'release check: forbidden binary or key files:\n%s\n' "$forbidden" >&2
  exit 1
}

large="$(find . -path './.git' -prune -o -type f -size +5M -print)"
[[ -z "$large" ]] || {
  printf 'release check: unexpectedly large files:\n%s\n' "$large" >&2
  exit 1
}

if rg -n --hidden \
    -g '!.git/**' -g '!scripts/verify-release-tree.sh' \
    '(/home/[A-Za-z0-9._-]+/|HYHISC|gh[opsu]_[A-Za-z0-9_]{20,}|folk_patched|device-backup-[0-9])' \
    .; then
  printf 'release check: local path, device identifier, token, or backup name found\n' >&2
  exit 1
fi

entries="$(awk -F '\t' '$1 !~ /^#/ && NF {count++} END {print count+0}' patches/series.tsv)"
[[ "$entries" -eq 22 ]] || {
  printf 'release check: expected 22 patch entries, found %s\n' "$entries" >&2
  exit 1
}
while IFS=$'\t' read -r order project base patch_name; do
  [[ -z "$order" || "$order" == \#* ]] && continue
  [[ "$base" =~ ^[0-9a-f]{40}$ && -s "patches/$patch_name" ]] || {
    printf 'release check: malformed series entry %s %s\n' "$order" "$project" >&2
    exit 1
  }
done < patches/series.tsv

if [[ -d .git ]]; then
  # Generated patch text can faithfully carry whitespace from its recorded
  # upstream base.  check-patches.sh validates application with Git's strict
  # whitespace mode; keep this tree check focused on hand-authored files.
  git diff --check -- . ':(exclude)patches/*.patch'
fi
if [[ -s verification/SOURCE_SHA256SUMS ]]; then
  sha256sum -c verification/SOURCE_SHA256SUMS
fi
printf 'release check: PASS\n'
