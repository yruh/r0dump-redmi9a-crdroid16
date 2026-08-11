#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT

mock_adb="$work/adb"
cat >"$mock_adb" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == -s ]]; then
  shift 2
fi

command="${1:-}"
shift || true
case "$command" in
  get-state)
    printf 'device\n'
    ;;
  shell)
    joined="$*"
    if [[ "$joined" == 'su -c id' ]]; then
      printf 'uid=0(root) gid=0(root)\n'
    elif [[ "$joined" == *by-name/*readlink* ]]; then
      [[ "$joined" =~ by-name/([a-z_]+) ]]
      printf '/dev/block/mock_%s\n' "${BASH_REMATCH[1]}"
    elif [[ "$joined" == *'blockdev --getsize64'* ]]; then
      printf '6\n'
    else
      printf 'unexpected mock shell command: %s\n' "$joined" >&2
      exit 2
    fi
    ;;
  exec-out)
    joined="$*"
    if [[ "$joined" == getprop ]]; then
      printf '[ro.product.device]: [fixture]\n'
    elif [[ "$joined" == *"cat '/dev/block/mock_"* ]]; then
      printf '\x00\x0a\x0d\x0a\xffA'
      [[ "${MOCK_EXTRA_BYTE:-0}" == 0 ]] || printf 'X'
    else
      printf 'unexpected mock exec-out command: %s\n' "$joined" >&2
      exit 2
    fi
    ;;
  *)
    printf 'unexpected mock ADB command: %s\n' "$command" >&2
    exit 2
    ;;
esac
MOCK
chmod +x "$mock_adb"

output="$work/good"
ADB_BIN="$mock_adb" ADB_SERIAL=SERIAL "$ROOT/scripts/backup-partitions.sh" "$output"

printf '\x00\x0a\x0d\x0a\xffA' >"$work/expected.img"
for partition in boot recovery dtbo vbmeta vbmeta_system vbmeta_vendor; do
  cmp "$work/expected.img" "$output/$partition.img"
done
(cd "$output" && sha256sum -c SHA256SUMS)

if MOCK_EXTRA_BYTE=1 ADB_BIN="$mock_adb" \
    "$ROOT/scripts/backup-partitions.sh" "$work/bad" >/dev/null 2>&1; then
  printf 'test: oversized ADB output was accepted\n' >&2
  exit 1
fi

if find "$work/bad" -name '*.partial' -print -quit | grep -q .; then
  printf 'test: failed export left a partial image\n' >&2
  exit 1
fi

printf 'backup partition tests: PASS\n'
