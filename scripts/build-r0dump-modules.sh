#!/usr/bin/env bash
# Build the R0DUMP Android targets with a direct cgroup memory limit.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="${ANDROID_ROOT:-$(cd -- "$SCRIPT_DIR/../../crdroid-16" 2>/dev/null && pwd || true)}"
LUNCH_TARGET="${LUNCH_TARGET:-lineage_blossom-trunk_staging-userdebug}"
BUILD_JOBS="${BUILD_JOBS:-4}"
BUILD_HIGHMEM_JOBS="${BUILD_HIGHMEM_JOBS:-2}"
BUILD_DISABLE_LTO="${BUILD_DISABLE_LTO:-true}"
BUILD_DEXER_HEAP_SIZE="${BUILD_DEXER_HEAP_SIZE:-2048M}"
BUILD_MODE="${BUILD_MODE:-auto}"
BUILD_SOONG_ONLY="${BUILD_SOONG_ONLY:-1}"
BUILD_SOONG_GOMEMLIMIT="${BUILD_SOONG_GOMEMLIMIT:-10GiB}"
BUILD_SOONG_GOGC="${BUILD_SOONG_GOGC:-50}"
BUILD_MEMORY_HIGH="${BUILD_MEMORY_HIGH:-20G}"
BUILD_MEMORY_MAX="${BUILD_MEMORY_MAX:-20G}"
BUILD_SWAP_MAX="${BUILD_SWAP_MAX:-0G}"
BUILD_MODULES="${BUILD_MODULES:-R0DUMPManager framework-minus-apex libart}"
BUILD_NINJA_GRAPH="${BUILD_NINJA_GRAPH:-out/soong/build.lineage_blossom.ninja}"
BUILD_NINJA_WRAPPER="${BUILD_NINJA_WRAPPER:-$SCRIPT_DIR/r0dump-soong-only.ninja}"
BUILD_NINJA_TARGETS="${BUILD_NINJA_TARGETS:-\
out/soong/.intermediates/packages/apps/R0DUMPManager/R0DUMPManager/android_common/R0DUMPManager.apk \
out/soong/.intermediates/frameworks/base/framework-minus-apex/android_common/combined/framework.jar \
out/soong/.intermediates/libcore/core-libart/android_common_apex31/combined/core-libart.jar \
out/soong/.intermediates/art/runtime/libart/android_arm64_armv8-a_cortex-a53_shared_apex31/libart.so \
out/soong/.intermediates/art/runtime/libart/android_arm_armv8-a_cortex-a53_shared_apex31/libart.so}"

[[ -d "$ANDROID_ROOT" ]] || {
  printf 'build: ERROR: Android tree not found: %s\n' "$ANDROID_ROOT" >&2
  exit 1
}
[[ "$BUILD_JOBS" =~ ^[1-9][0-9]*$ ]] || {
  printf 'build: ERROR: BUILD_JOBS must be a positive integer\n' >&2
  exit 1
}
[[ "$BUILD_HIGHMEM_JOBS" =~ ^[1-9][0-9]*$ ]] || {
  printf 'build: ERROR: BUILD_HIGHMEM_JOBS must be a positive integer\n' >&2
  exit 1
}
[[ "$BUILD_SOONG_ONLY" == 0 || "$BUILD_SOONG_ONLY" == 1 ]] || {
  printf 'build: ERROR: BUILD_SOONG_ONLY must be 0 or 1\n' >&2
  exit 1
}
[[ "$BUILD_DISABLE_LTO" == true || "$BUILD_DISABLE_LTO" == false ]] || {
  printf 'build: ERROR: BUILD_DISABLE_LTO must be true or false\n' >&2
  exit 1
}
[[ "$BUILD_MODE" == auto || "$BUILD_MODE" == cached || "$BUILD_MODE" == soong ]] || {
  printf 'build: ERROR: BUILD_MODE must be auto, cached, or soong\n' >&2
  exit 1
}
[[ "$BUILD_SOONG_GOGC" =~ ^[1-9][0-9]*$ ]] || {
  printf 'build: ERROR: BUILD_SOONG_GOGC must be a positive integer\n' >&2
  exit 1
}

cd -- "$ANDROID_ROOT"
ALLOW_ACTIVE_BUILD=0 MAX_BUILD_JOBS="$BUILD_JOBS" \
  "$SCRIPT_DIR/check-build-resources.sh"

# systemd-run keeps all child processes under the same hard ceiling.  Never
# silently fall back to an unbounded Android build on this host.
if ! command -v systemd-run >/dev/null 2>&1 \
    || ! systemctl --user show-environment >/dev/null 2>&1; then
  printf 'build: ERROR: no active user systemd session; refusing an unbounded build.\n' >&2
  printf 'build: start a user session or set up an equivalent cgroup before retrying.\n' >&2
  exit 1
fi

cgroup=(
  systemd-run --user --scope --collect
  -p MemoryAccounting=yes
  -p MemoryHigh="$BUILD_MEMORY_HIGH"
  -p MemoryMax="$BUILD_MEMORY_MAX"
  -p MemorySwapMax="$BUILD_SWAP_MAX"
)

# Source edits do not require a new Soong graph when Android.bp is unchanged.
# Reusing the existing graph avoids the multi-gigabyte Soong/Kati parse and is
# the normal path after the first successful product configuration.
if [[ "$BUILD_MODE" != soong && -f "$BUILD_NINJA_GRAPH" ]]; then
  [[ -f "$BUILD_NINJA_WRAPPER" ]] || {
    printf 'build: ERROR: cached Ninja wrapper not found: %s\n' "$BUILD_NINJA_WRAPPER" >&2
    exit 1
  }
  read -r -a ninja_targets <<<"$BUILD_NINJA_TARGETS"
  ((${#ninja_targets[@]} > 0)) || {
    printf 'build: ERROR: BUILD_NINJA_TARGETS is empty\n' >&2
    exit 1
  }
  printf 'build: mode=cached jobs=%s highmem_jobs=1 targets=%s\n' \
    "$BUILD_JOBS" "${#ninja_targets[@]}"
  exec "${cgroup[@]}" bash -lc '
    lunch_target=$1
    ninja_file=$2
    jobs=$3
    disable_lto=$4
    dexer_heap=$5
    shift 5
    unset USE_CCACHE CCACHE_EXEC
    export OUT_DIR="${OUT_DIR:-out}"
    export DISABLE_LTO="$disable_lto"
    export R0DUMP_DEXER_HEAP_SIZE="$dexer_heap"
    export LANG=C.UTF-8 LC_ALL=C.UTF-8 PYTHONUTF8=1 PYTHONIOENCODING=UTF-8
    source build/envsetup.sh
    lunch "$lunch_target" >/dev/null
    exec prebuilts/build-tools/linux-x86/bin/ninja -d stats \
      -f "$ninja_file" -j"$jobs" "$@"
  ' build-r0dump "$LUNCH_TARGET" "$BUILD_NINJA_WRAPPER" "$BUILD_JOBS" \
    "$BUILD_DISABLE_LTO" "$BUILD_DEXER_HEAP_SIZE" \
    "${ninja_targets[@]}"
fi

if [[ "$BUILD_MODE" == cached ]]; then
  printf 'build: ERROR: cached Soong graph not found: %s\n' "$BUILD_NINJA_GRAPH" >&2
  exit 1
fi

printf 'build: mode=soong jobs=%s highmem_jobs=%s modules=%s\n' \
  "$BUILD_JOBS" "$BUILD_HIGHMEM_JOBS" "$BUILD_MODULES"
exec "${cgroup[@]}" bash -lc '
  unset USE_CCACHE CCACHE_EXEC
  export DISABLE_LTO="$8"
  export R0DUMP_DEXER_HEAP_SIZE="$9"
  export LANG=C.UTF-8 LC_ALL=C.UTF-8 PYTHONUTF8=1 PYTHONIOENCODING=UTF-8
  source build/envsetup.sh
  lunch "$1" >/dev/null
  NINJA_HIGHMEM_NUM_JOBS="$4" SOONG_ONLY="$5" \
    GOMEMLIMIT="$6" GOGC="$7" m $2 -j"$3"
' build-r0dump "$LUNCH_TARGET" "$BUILD_MODULES" "$BUILD_JOBS" \
  "$BUILD_HIGHMEM_JOBS" "$BUILD_SOONG_ONLY" "$BUILD_SOONG_GOMEMLIMIT" "$BUILD_SOONG_GOGC" \
  "$BUILD_DISABLE_LTO" "$BUILD_DEXER_HEAP_SIZE"
