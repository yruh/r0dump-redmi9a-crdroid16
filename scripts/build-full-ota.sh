#!/usr/bin/env bash
# Build the full blossom OTA under a cgroup memory ceiling.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="${ANDROID_ROOT:-${1:-}}"
LUNCH_TARGET="${LUNCH_TARGET:-lineage_blossom-trunk_staging-userdebug}"
BUILD_JOBS="${BUILD_JOBS:-4}"
BUILD_HIGHMEM_JOBS="${BUILD_HIGHMEM_JOBS:-2}"
BUILD_MEMORY_HIGH="${BUILD_MEMORY_HIGH:-20G}"
BUILD_MEMORY_MAX="${BUILD_MEMORY_MAX:-24G}"
BUILD_SWAP_MAX="${BUILD_SWAP_MAX:-0G}"
BUILD_DEXER_HEAP_SIZE="${BUILD_DEXER_HEAP_SIZE:-2048M}"
BUILD_LARGE_R8_MODULES="${BUILD_LARGE_R8_MODULES:-Launcher3QuickStep}"
BUILD_LARGE_R8_HEAP_SIZE="${BUILD_LARGE_R8_HEAP_SIZE:-3072M}"
BUILD_SOONG_GOMEMLIMIT="${BUILD_SOONG_GOMEMLIMIT:-6GiB}"
BUILD_SOONG_GOGC="${BUILD_SOONG_GOGC:-40}"
BUILD_DISABLE_LTO="${BUILD_DISABLE_LTO:-true}"
BUILD_TMPDIR="${BUILD_TMPDIR:-}"

if [[ -z "$ANDROID_ROOT" || ! -d "$ANDROID_ROOT/build" ]]; then
  printf 'usage: ANDROID_ROOT=/path/to/crdroid-16 %s\n' "$0" >&2
  exit 2
fi
ANDROID_ROOT="$(cd -- "$ANDROID_ROOT" && pwd)"

cleanup_default_tmp=0
if [[ -z "$BUILD_TMPDIR" ]]; then
  BUILD_TMPDIR="$ANDROID_ROOT/out/r0dump-tmp"
  cleanup_default_tmp=1
fi
mkdir -p -- "$BUILD_TMPDIR"
BUILD_TMPDIR="$(cd -- "$BUILD_TMPDIR" && pwd)"
if [[ "$cleanup_default_tmp" == 1 ]]; then
  find "$BUILD_TMPDIR" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
fi

[[ "$BUILD_JOBS" =~ ^[1-9][0-9]*$ ]] || {
  printf 'build: BUILD_JOBS must be a positive integer\n' >&2
  exit 1
}
[[ "$BUILD_HIGHMEM_JOBS" =~ ^[1-9][0-9]*$ ]] || {
  printf 'build: BUILD_HIGHMEM_JOBS must be a positive integer\n' >&2
  exit 1
}
[[ "$BUILD_DEXER_HEAP_SIZE" =~ ^[1-9][0-9]*[MmGg]$ ]] || {
  printf 'build: BUILD_DEXER_HEAP_SIZE must look like 2048M or 3G\n' >&2
  exit 1
}
[[ "$BUILD_LARGE_R8_HEAP_SIZE" =~ ^[1-9][0-9]*[MmGg]$ ]] || {
  printf 'build: BUILD_LARGE_R8_HEAP_SIZE must look like 3072M or 3G\n' >&2
  exit 1
}

TMP_PATH="$BUILD_TMPDIR" MIN_TMP_FREE_MIB="${MIN_TMP_FREE_MIB:-16384}" \
  MAX_BUILD_JOBS="$BUILD_JOBS" \
  "$SCRIPT_DIR/check-build-resources.sh"

if ! command -v systemd-run >/dev/null 2>&1 \
    || ! systemctl --user show-environment >/dev/null 2>&1; then
  printf 'build: an active user systemd session is required for the memory ceiling\n' >&2
  exit 1
fi

printf 'build: target=%s jobs=%s highmem=%s memory=%s/%s swap=%s tmp=%s\n' \
  "$LUNCH_TARGET" "$BUILD_JOBS" "$BUILD_HIGHMEM_JOBS" \
  "$BUILD_MEMORY_HIGH" "$BUILD_MEMORY_MAX" "$BUILD_SWAP_MAX" "$BUILD_TMPDIR"
printf 'build: dexer_heap=%s large_r8=%s:%s\n' \
  "$BUILD_DEXER_HEAP_SIZE" "$BUILD_LARGE_R8_MODULES" "$BUILD_LARGE_R8_HEAP_SIZE"

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
    dexer_heap=$8
    tmpdir=$9
    cleanup_tmp=${10}
    large_r8_modules=${11}
    large_r8_heap=${12}
    cd -- "$root"
    if [[ "$cleanup_tmp" == 1 ]]; then
      trap '\''find "$tmpdir" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +'\'' EXIT
    fi
    unset USE_CCACHE CCACHE_EXEC
    export DISABLE_LTO="$disable_lto"
    export GOMEMLIMIT="$gomemlimit" GOGC="$gogc"
    export R0DUMP_DEXER_HEAP_SIZE="$dexer_heap"
    export R0DUMP_LARGE_R8_MODULES="$large_r8_modules"
    export R0DUMP_LARGE_R8_HEAP_SIZE="$large_r8_heap"
    export TMPDIR="$tmpdir" TMP="$tmpdir" TEMP="$tmpdir"
    export LANG=C.UTF-8 LC_ALL=C.UTF-8 PYTHONUTF8=1 PYTHONIOENCODING=UTF-8
    source build/envsetup.sh
    lunch "$target" >/dev/null
    NINJA_HIGHMEM_NUM_JOBS="$highmem" m bacon -j"$jobs"
  ' build-full "$ANDROID_ROOT" "$LUNCH_TARGET" "$BUILD_JOBS" \
    "$BUILD_HIGHMEM_JOBS" "$BUILD_SOONG_GOMEMLIMIT" "$BUILD_SOONG_GOGC" \
    "$BUILD_DISABLE_LTO" "$BUILD_DEXER_HEAP_SIZE" "$BUILD_TMPDIR" \
    "$cleanup_default_tmp" "$BUILD_LARGE_R8_MODULES" "$BUILD_LARGE_R8_HEAP_SIZE"
