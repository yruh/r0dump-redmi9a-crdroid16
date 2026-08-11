# Verified build and device results

Date: 2026-08-12

## Baseline full OTA

- Product: `lineage_blossom-trunk_staging-userdebug`
- Result: baseline `bacon` target completed with exit code 0; VINTF was
  `COMPATIBLE` and all ZIP entries passed `unzip -t`
- OTA: `crDroidAndroid-16.0-20260811-blossom-v12.11.zip`
- Size: `1,407,740,513` bytes
- SHA-256: `25010572c73afd87ae85d8ee1aec0d4bb718aa239b0c34f6405239978cd9157f`

The baseline OTA binary is kept outside this source repository.

## Manager hand-off fix

The change in `MainActivity.java` was built with the cached Soong graph under a
16 GiB cgroup ceiling (`MemoryHigh=14G`, `MemoryMax=16G`, no swap). The resulting
APK is:

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `R0DUMPManager.apk` | 50,704,752 | `d0b6c4eecca66fff06983bfa9a40492d1aae493bb7c6029897ecfdf16fe170f5` |

It was installed with `adb install -r -d` and exercised on the Redmi 9A. The
system partition initially contained the previous APK
(`eb5181f1c141892894da66ffccf669f886d4a8c3674a7fa823f23d3043acbf48`), so a
factory reset would initially have removed the data-app overlay. The
system_ext-only OTA below was then used to make the fix persistent.

## System_ext-only repair OTA

The repair package starts from the verified baseline OTA. It replaces only
`system_ext.new.dat.br` with a same-size raw image containing the fixed Manager;
the original `system_ext.transfer.list` and all other OTA entries are unchanged.

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| Modified `system_ext` raw image | 1,076,817,920 | `88af07d512613e0ae275787934b1bcb528a8d3b3caa2d21a8ee210eba2ef611a` |
| `crDroidAndroid-16.0-20260812-blossom-v12.11-manager-fix.zip` | 1,407,734,167 | `eafe15270863cc61abb55f2c6b20188cd9aee501344830dc20e6646992846eef` |

Checks performed:

- `unzip -tq`: pass;
- `check_ota_package_signature` with the local test certificate: `VERIFIED`;
- Brotli decompression of `system_ext.new.dat.br` matches the modified raw image;
- `system_ext.transfer.list` hash matches the baseline list;
- no full Soong rebuild was required for this packaging step.

The repair OTA was sideloaded with `adb sideload` without formatting data. The
phone rebooted successfully and `/system_ext/priv-app/R0DUMPManager/R0DUMPManager.apk`
now hashes to `d0b6c4eecca66fff06983bfa9a40492d1aae493bb7c6029897ecfdf16fe170f5`.

## Device smoke

- Device: Redmi 9A `M2006C3LI`, runtime codename `dandelion`
- ROM: crDroid `12.11`, Android 16 / SDK 36
- `sys.boot_completed=1`
- `ro.build.display.id=R0DUMP 16`
- `ro.crdroid.device=blossom`
- `ro.product.device=dandelion`
- `ro.boot.verifiedbootstate=green`, `ro.boot.flash.locked=1`
- `su`: absent on the clean baseline boot and after the repair OTA
- Data was preserved; the existing `/data/app` update remained installed

### Calculator baseline

- Target: `com.android.calculator2`, `main_only`, default `0x87`, global/raw/
  force-backfill disabled
- Output: 3 DEX/DEX 041 containers and 1 method-record file
- Repair: 3/3 repaired, 472 records applied, 27 duplicates, 0 skipped
- Terminal state: `phase=complete`, runtime switch cleared to 0

### App Cloner regression

- Target: `com.applisto.appcloner`
- MainActivity launch: displayed successfully; no background-activity-start block
- Output: 7 DEX files, 2 method records
- `reconstruction_failures=0`
- `nonstandard_dex_methods_skipped=622`
- Terminal state: `phase=stopped`, `stop_reason=class_walk_timeout`,
  `runtime_enabled=false`
- Post-test settings: `r0dump.dump.enabled=0`,
  `r0dump.dump.global_runtime_enabled=0`

## Known limits

- The repair OTA still needs one no-wipe sideload and post-boot check to replace the
  system-partition Manager APK.
- The OTA uses Android test keys and is for the unlocked development device.
- The declared security patch is `2025-11-05`.
- 32-bit app and real SIM/VoLTE calls were not part of this smoke run.
- High-risk/global/multi-process strategies and a complete CompactDex converter were
  not validated; Android 16 standard DEX/DEX 041 paths are covered.
- The host experienced global OOM when a full Soong graph was forced dirty. Future
  builds should use the cached module path or the bounded build script; do not infer
  a source failure from that host resource event.
