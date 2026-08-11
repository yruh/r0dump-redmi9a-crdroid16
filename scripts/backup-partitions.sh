#!/usr/bin/env bash
# Export rollback-critical partitions without allocating an ADB pseudo-terminal.
set -euo pipefail

umask 077

ADB_BIN="${ADB_BIN:-adb}"
ADB_SERIAL="${ADB_SERIAL:-}"
OUTPUT_DIR="${1:-device-backup-$(date +%Y%m%d-%H%M%S)}"
PARTITIONS=(boot recovery dtbo vbmeta vbmeta_system vbmeta_vendor)

die() {
  printf 'backup: ERROR: %s\n' "$*" >&2
  exit 1
}

if [[ "$ADB_BIN" == */* ]]; then
  [[ -x "$ADB_BIN" ]] || die "ADB executable not found: $ADB_BIN"
else
  command -v "$ADB_BIN" >/dev/null 2>&1 || die "ADB executable not found: $ADB_BIN"
fi

adb_cmd=("$ADB_BIN")
if [[ -n "$ADB_SERIAL" ]]; then
  adb_cmd+=(-s "$ADB_SERIAL")
fi

state="$("${adb_cmd[@]}" get-state 2>/dev/null || true)"
[[ "$state" == device ]] || die "device state must be 'device', got '${state:-absent}'"

root_id="$("${adb_cmd[@]}" shell su -c id 2>/dev/null | tr -d '\r')"
[[ "$root_id" == uid=0* ]] || die "su did not return uid=0"

if [[ -e "$OUTPUT_DIR" ]] && find "$OUTPUT_DIR" -mindepth 1 -print -quit | grep -q .; then
  die "output directory is not empty: $OUTPUT_DIR"
fi
mkdir -p -- "$OUTPUT_DIR"

partial=""
cleanup() {
  if [[ -n "$partial" && -e "$partial" ]]; then
    rm -f -- "$partial"
  fi
}
trap cleanup EXIT INT TERM

images=()
for partition in "${PARTITIONS[@]}"; do
  resolve="p=/dev/block/by-name/$partition; "
  resolve+='[ -e "$p" ] || p=/dev/block/platform/bootdevice/by-name/'"$partition"'; '
  resolve+='readlink -f "$p"'
  block="$("${adb_cmd[@]}" shell su -c "$resolve" 2>/dev/null | tr -d '\r\n')"
  [[ "$block" == /dev/block/* ]] || die "could not resolve $partition block device"

  expected="$("${adb_cmd[@]}" shell su -c "blockdev --getsize64 '$block'" 2>/dev/null | tr -d '\r\n')"
  [[ "$expected" =~ ^[1-9][0-9]*$ ]] || die "invalid size for $partition: $expected"

  partial="$OUTPUT_DIR/.$partition.img.partial"
  printf 'backup: %-13s %s bytes from %s\n' "$partition" "$expected" "$block"

  # adb exec-out is required here.  adb shell -t corrupts arbitrary binary LF
  # bytes by applying terminal CRLF conversion.
  if ! "${adb_cmd[@]}" exec-out su -c "cat '$block'" >"$partial"; then
    die "ADB export failed for $partition"
  fi

  actual="$(stat -c %s "$partial")"
  [[ "$actual" == "$expected" ]] || die \
    "$partition byte count mismatch: expected $expected, received $actual"

  image="$OUTPUT_DIR/$partition.img"
  mv -- "$partial" "$image"
  partial=""
  images+=("$partition.img")
done

"${adb_cmd[@]}" exec-out getprop >"$OUTPUT_DIR/getprop.txt"

(
  cd -- "$OUTPUT_DIR"
  sha256sum "${images[@]}" >SHA256SUMS
)

{
  printf 'created_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'transport=adb exec-out (no PTY)\n'
  printf 'device_state=%s\n' "$state"
  printf 'partitions=%s\n' "${PARTITIONS[*]}"
} >"$OUTPUT_DIR/MANIFEST.txt"

trap - EXIT INT TERM
printf 'backup: PASS: %s\n' "$OUTPUT_DIR"
