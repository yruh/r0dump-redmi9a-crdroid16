#!/usr/bin/env bash
# Verify required local proprietary files and their source-time compatibility fixups.
set -euo pipefail

ANDROID_ROOT="${1:-${ANDROID_ROOT:-}}"
if [[ -z "$ANDROID_ROOT" || ! -d "$ANDROID_ROOT/vendor/xiaomi/blossom" ]]; then
  printf 'usage: %s /path/to/crdroid-16\n' "$0" >&2
  exit 2
fi
ANDROID_ROOT="$(cd -- "$ANDROID_ROOT" && pwd)"
VENDOR="$ANDROID_ROOT/vendor/xiaomi/blossom/proprietary/vendor"

command -v readelf >/dev/null || {
  printf 'blob check: readelf is required\n' >&2
  exit 1
}

check_needed() {
  local file="$1"
  shift
  [[ -s "$file" ]] || {
    printf 'blob check: missing %s\n' "$file" >&2
    return 1
  }
  local dynamic
  dynamic="$(readelf -d "$file")"
  local dependency
  for dependency in "$@"; do
    if ! rg -Fq "[$dependency]" <<<"$dynamic"; then
      printf 'blob check: %s does not need %s\n' "$file" "$dependency" >&2
      return 1
    fi
  done
  printf 'blob check: OK %s\n' "${file#"$ANDROID_ROOT/"}"
}

check_needed "$VENDOR/bin/hw/android.hardware.thermal@2.0-service.mtk" \
  libhidlbase-v32.so libutils-v32.so
check_needed "$VENDOR/lib/hw/android.hardware.thermal@2.0-impl.so" \
  libhidlbase-v32.so libutils-v32.so
check_needed "$VENDOR/lib64/hw/android.hardware.thermal@2.0-impl.so" \
  libhidlbase-v32.so libutils-v32.so
check_needed "$VENDOR/lib/libnvram.so" libshim_base.so
check_needed "$VENDOR/lib64/libnvram.so" libshim_base.so
check_needed "$VENDOR/lib/libsysenv.so" libshim_base.so

[[ -s "$VENDOR/etc/init/android.hardware.thermal@2.0-service.mtk.rc" ]] || {
  printf 'blob check: missing Thermal HAL init rc\n' >&2
  exit 1
}
printf 'blob check: PASS\n'

