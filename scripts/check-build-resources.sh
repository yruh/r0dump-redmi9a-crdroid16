#!/usr/bin/env bash
# Check host resources before starting an Android/ART build.
set -euo pipefail

TMP_PATH="${TMP_PATH:-/tmp}"
MAX_TMP_FILES="${MAX_TMP_FILES:-120000}"
MAX_TMP_USED_PERCENT="${MAX_TMP_USED_PERCENT:-70}"
MIN_TMP_FREE_MIB="${MIN_TMP_FREE_MIB:-8192}"
MIN_MEM_AVAILABLE_MIB="${MIN_MEM_AVAILABLE_MIB:-12288}"
MIN_SWAP_FREE_MIB="${MIN_SWAP_FREE_MIB:-4096}"
# This is informational only.  Build parallelism is controlled by the cgroup
# wrapper, so the default can stay high without making the memory guard weaker.
MAX_BUILD_JOBS="${MAX_BUILD_JOBS:-8}"
ALLOW_ACTIVE_BUILD="${ALLOW_ACTIVE_BUILD:-0}"

die() {
  printf 'resource check: ERROR: %s\n' "$*" >&2
  printf 'resource check: clean obsolete worktrees/temp files, then rerun.\n' >&2
  exit 1
}

is_uint() {
  [[ "$1" =~ ^[0-9]+$ ]]
}

for value in "$MAX_TMP_FILES" "$MAX_TMP_USED_PERCENT" "$MIN_TMP_FREE_MIB" \
    "$MIN_MEM_AVAILABLE_MIB" "$MIN_SWAP_FREE_MIB" "$MAX_BUILD_JOBS"; do
  is_uint "$value" || die "threshold is not an integer: $value"
done

[[ -d "$TMP_PATH" ]] || die "temporary path does not exist: $TMP_PATH"

# Ignore permission failures from system-private directories while still counting
# every regular file visible to the current build user.
tmp_files="$({ find "$TMP_PATH" -xdev -type f 2>/dev/null || true; } | wc -l)"
tmp_stats="$(df -Pk "$TMP_PATH" | awk 'NR == 2 {gsub(/%/, "", $5); print $4, $5}')"
read -r tmp_free_kib tmp_used_percent <<<"$tmp_stats"
tmp_inode_stats="$(df -Pik "$TMP_PATH" | awk 'NR == 2 {gsub(/%/, "", $5); print $4, $5}')"
read -r tmp_free_inodes tmp_inode_used_percent <<<"$tmp_inode_stats"

mem_available_kib="$(awk '/^MemAvailable:/ {print $2; exit}' /proc/meminfo)"
swap_total_kib="$(awk '/^SwapTotal:/ {print $2; exit}' /proc/meminfo)"
swap_free_kib="$(awk '/^SwapFree:/ {print $2; exit}' /proc/meminfo)"

is_uint "$tmp_files" || die "could not count files under $TMP_PATH"
is_uint "$tmp_free_kib" || die "could not read free space for $TMP_PATH"
is_uint "$tmp_used_percent" || die "could not read space usage for $TMP_PATH"
is_uint "$tmp_free_inodes" || die "could not read inode usage for $TMP_PATH"
is_uint "$tmp_inode_used_percent" || die "could not read inode usage for $TMP_PATH"
is_uint "$mem_available_kib" || die "could not read MemAvailable"
is_uint "$swap_total_kib" || die "could not read SwapTotal"
is_uint "$swap_free_kib" || die "could not read SwapFree"

tmp_free_mib=$((tmp_free_kib / 1024))
mem_available_mib=$((mem_available_kib / 1024))
swap_free_mib=$((swap_free_kib / 1024))

printf 'resource check: tmp=%s files, %s MiB free, %s%% used, %s%% inodes used\n' \
  "$tmp_files" "$tmp_free_mib" "$tmp_used_percent" "$tmp_inode_used_percent"
printf 'resource check: MemAvailable=%s MiB, SwapFree=%s MiB, max_jobs=%s\n' \
  "$mem_available_mib" "$swap_free_mib" "$MAX_BUILD_JOBS"

(( tmp_files <= MAX_TMP_FILES )) || die \
  "too many temporary files ($tmp_files > $MAX_TMP_FILES)"
(( tmp_used_percent <= MAX_TMP_USED_PERCENT )) || die \
  "temporary filesystem is too full ($tmp_used_percent% > $MAX_TMP_USED_PERCENT%)"
(( tmp_free_mib >= MIN_TMP_FREE_MIB )) || die \
  "not enough free temporary space (${tmp_free_mib}MiB < ${MIN_TMP_FREE_MIB}MiB)"
(( tmp_inode_used_percent <= MAX_TMP_USED_PERCENT )) || die \
  "temporary filesystem has too few free inodes ($tmp_inode_used_percent% used)"
(( mem_available_mib >= MIN_MEM_AVAILABLE_MIB )) || die \
  "not enough available memory (${mem_available_mib}MiB < ${MIN_MEM_AVAILABLE_MIB}MiB)"

if (( swap_total_kib > 0 && swap_free_mib < MIN_SWAP_FREE_MIB )); then
  die "not enough free swap (${swap_free_mib}MiB < ${MIN_SWAP_FREE_MIB}MiB)"
fi

if [[ "$ALLOW_ACTIVE_BUILD" != "1" ]] && pgrep -af \
    '(^|/)(soong_ui|ninja|ckati)( |$)' >/dev/null 2>&1; then
  die "an Android build process is already running"
fi

printf 'resource check: PASS\n'
