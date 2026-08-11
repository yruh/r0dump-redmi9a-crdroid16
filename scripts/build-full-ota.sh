#!/usr/bin/env bash
# Build the full blossom OTA under a cgroup memory ceiling.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="${ANDROID_ROOT:-${1:-}}"
LUNCH_TARGET="${LUNCH_TARGET:-lineage_blossom-trunk_staging-userdebug}"
BUILD_JOBS="${BUILD_JOBS:-4}"
BUILD_HIGHMEM_JOBS="${BUILD_HIGHMEM_JOBS:-1}"
BUILD_MEMORY_HIGH="${BUILD_MEMORY_HIGH:-20G}"
BUILD_MEMORY_MAX="${BUILD_MEMORY_MAX:-24G}"
BUILD_SWAP_MAX="${BUILD_SWAP_MAX:-4G}"
BUILD_SOONG_GOMEMLIMIT="${BUILD_SOONG_GOMEMLIMIT:-10GiB}"
BUILD_SOONG_GOGC="${BUILD_SOONG_GOGC:-50}"
BUILD_DISABLE_LTO="${BUILD_DISABLE_LTO:-true}"

if [[ -z "$ANDROID_ROOT" || ! -d "$ANDROID_ROOT/build" ]]; then
  printf 'usage: ANDROID_ROOT=/path/to/crdroid-16 %s\n' "$0" >&2
  exit 2
fi
ANDROID_ROOT="$(cd -- "$ANDROID_ROOT" && pwd)"

[[ "$BUILD_JOBS" =~ ^[1-9][0-9]*$ ]] || {
  printf 'build: BUILD_JOBS must be a positive integer\n' >&2
  exit 1
}
[[ "$BUILD_HIGHMEM_JOBS" =~ ^[1-9][0-9]*$ ]] || {
  printf 'build: BUILD_HIGHMEM_JOBS must be a positive integer\n' >&2
  exit 1
}

TMP_PATH="${TMP_PATH:-/tmp}" MAX_BUILD_JOBS="$BUILD_JOBS" \
  "$SCRIPT_DIR/check-build-resources.sh"

if ! command -v systemd-run >/dev/null 2>&1 \
    || ! systemctl --user show-environment >/dev/null 2>&1; then
  printf 'build: an active user systemd session is required for the memory ceiling\n' >&2
  exit 1
fi

printf 'build: target=%s jobs=%s highmem=%s memory=%s/%s swap=%s\n' \
  "$LUNCH_TARGET" "$BUILD_JOBS" "$BUILD_HIGHMEM_JOBS" \
  "$BUILD_MEMORY_HIGH" "$BUILD_MEMORY_MAX" "$BUILD_SWAP_MAX"

exec systemd-run --user --scope --collect \
  -p MemoryAccounting=yes \
  -p MemoryHigh="$BUILD_MEMORY_HIGH" \
  -p MemoryMax="$BUILD_MEMORY_MAX" \
  -p MemorySwapMax="$BUILD_SWAP_MAX" \
  bash -lc '
    root=$1
    target=$2
    jobs=$3
    highmem=$4
    gomemlimit=$5
    gogc=$6
    disable_lto=$7
    cd -- "$root"
    export USE_CCACHE=0
    export DISABLE_LTO="$disable_lto"
    export GOMEMLIMIT="$gomemlimit" GOGC="$gogc"
    export LANG=C.UTF-8 LC_ALL=C.UTF-8 PYTHONUTF8=1 PYTHONIOENCODING=UTF-8
    source build/envsetup.sh
    lunch "$target" >/dev/null
    NINJA_HIGHMEM_NUM_JOBS="$highmem" m bacon -j"$jobs"
  ' build-full "$ANDROID_ROOT" "$LUNCH_TARGET" "$BUILD_JOBS" \
    "$BUILD_HIGHMEM_JOBS" "$BUILD_SOONG_GOMEMLIMIT" "$BUILD_SOONG_GOGC" \
    "$BUILD_DISABLE_LTO"

