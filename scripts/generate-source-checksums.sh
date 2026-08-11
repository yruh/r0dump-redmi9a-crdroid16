#!/usr/bin/env bash
# Regenerate deterministic checksums for all published source payload files.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
OUTPUT="$ROOT/verification/SOURCE_SHA256SUMS"
TEMP="$(mktemp)"
trap 'rm -f -- "$TEMP"' EXIT

cd -- "$ROOT"
find manifests overlay patches scripts \
  -type f ! -path '*/SOURCE_SHA256SUMS' -print0 \
  | sort -z \
  | xargs -0 sha256sum > "$TEMP"
install -m 0644 "$TEMP" "$OUTPUT"
printf 'checksums: wrote %s\n' "$OUTPUT"

