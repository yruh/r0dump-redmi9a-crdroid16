# Verified build and device results

Date: 2026-08-15

## Device and ROM

- Device: Redmi 9A `M2006C3LI`, runtime codename `dandelion`
- Product: `lineage_blossom-trunk_staging-userdebug`
- ROM: crDroid `12.11`, Android 16 / SDK 36
- Boot: `sys.boot_completed=1`, `bootmode=normal`
- SELinux: `Enforcing`
- Data: preserved through the final OTA sideload
- Runtime switches after verification: `r0dump.dump.enabled=0`,
  `r0dump.dump.global_runtime_enabled=0`

## Download-only contract

The only output root is `/sdcard/Download/R0DUMP`. ART ignores an externally
configured output root and stops with `download_output_unavailable` if the
selected Download hierarchy cannot be created. There is no fallback to
`Android/data`, app-private storage, or `/data/local/tmp`.

`StorageManagerService` gives external pass-through storage only to the active
R0DUMP target process. Platform SELinux permits traversal of the pass-through
mount root and read-only resolution of its `primary` symlink. The final App
Cloner run produced no pass-through denial while SELinux remained enforcing.

## Final OTA

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `crDroidAndroid-16.0-20260815-blossom-v12.11-download-only-final-v2.zip` | 1,407,936,702 | `ff634968fb043ee240d1ad42202c694809f2212c1c041280fec83262f360bf6a` |
| Built `system.img` | 1,487,642,624 | `fc168fee4716ffd183882bfa012979ec244897e581e18a4b06bee291c78aa126` |
| OTA-padded system raw data | 1,487,716,352 | `28b653d6170cbe4baf1a52e6b82d9871fd2a45e4974869495255a20b7af05605` |
| Built `system_ext.img` | 1,076,809,728 | `a751c4a1572ae85fe9aec11563d4f3c742a5095e78fdcb8f08fa3e4d07187d1a` |
| OTA-padded system_ext raw data | 1,076,817,920 | `0633eca263d1fd37b943d0d6c13ace596b496aeee66570cdb237e251a27d6421` |
| `R0DUMPManager.apk` | 50,696,560 | `dedc8220b63b9b8dc77c0ae5841853f5347386cfacf5127d29e3eafed4a87b77` |

Checks performed:

- `unzip -tq`: pass
- whole-package signature with the local test certificate: verified
- Brotli decompression: byte-identical to the padded partition inputs
- `sdat2img` system/system_ext reconstruction: byte-identical
- `e2fsck -fn` on both reconstructed ext4 images: pass
- in-image ART, services, SELinux policy, and Manager hashes: match staged files
- boot, recovery, vendor, product, odm, dtbo, all vbmeta images, dynamic
  partition operations, and all transfer lists: unchanged from the verified v3
- no-wipe `adb sideload`: exit code 0, `Total xfer: 0.98x`
- post-boot system Manager and active `/data/app` Manager: same final APK hash

Key installed hashes:

| Installed component | SHA-256 |
| --- | --- |
| `/system/framework/services.jar` | `11e4d602cdcedef40b7615eb077a2741381fe257ded514add8b21cc92d4da64c` |
| `/system/apex/com.android.art.capex` | `826c4e0a022c69c2e65ebebc7e4de95bc2311f4b1e52614c402b1d8600c8d114` |
| `/system/etc/selinux/plat_sepolicy.cil` | `22c04445ed63e8d726472ab2edbfbe8fc99493ae7866df681f8be11b79b51daa` |
| system and active Manager APK | `dedc8220b63b9b8dc77c0ae5841853f5347386cfacf5127d29e3eafed4a87b77` |

## App Cloner closure

- Target: `com.applisto.appcloner`
- Run: `1786743880030-fc0b6400`
- Directory: `/sdcard/Download/R0DUMP/com.applisto.appcloner/<run>/`
- Config: `main_only`, strategy `0x87`, global/raw/force-backfill disabled
- Output: 7 DEX files, one methods JSONL, one in-memory raw buffer, status JSON
- Status: `phase=stopped`, `stop_reason=max_seconds`,
  `runtime_enabled=false`, `reconstruction_failures=0`
- Manager scan: `dex=7`, `records=2`
- Repair: 5 repaired DEX files
- Repair ZIP: 6,770,008 bytes,
  `449dc5698faac70449371ef2d3e8bd1642d518fa589ab50f4455e64317941a0b`
- Device and host ZIP hashes: identical
- `unzip -tq`: pass
- Android 16 `dexdump`: 5/5 pass
- New private-output runs: 0; the four `Android/data` runs predate this fix

The installed final Manager was then used to scan and repair the same run again.
It again reported `dex=7`, `records=2`, and produced 5 repaired DEX files with
5/5 `dexdump` passing. The new ZIP is 6,770,009 bytes with SHA-256
`b6b611fa22327dbb89da196763cfc8f390be9cb6af4fca36ca898ad587ac5b1f`.
All five DEX content hashes match the first repair exactly. The whole ZIP hash
changes because `repair_manifest.json.generated_at` and ZIP entry timestamps
record the generation time.

## Build controls

- Cached full Ninja graph reused; no unnecessary full ROM rebuild
- Normal parallelism: `-j4`
- `MemoryHigh=18G`, `MemoryMax=20G`, `MemorySwapMax=0G`
- Observed incremental build peak: about 16.2 GiB
- Build temp resource precheck: pass
- Patch replay: 22/22 pass

## Remaining limits

- The OTA uses Android test keys and is for this unlocked development device.
- The declared security patch is `2025-11-05`.
- 32-bit app and real SIM/VoLTE calls were not part of the final closure.
- Global, multi-process, and high-risk strategies were not exhaustively tested.
- Full CompactDex conversion is not implemented. The current Android 16
  standard DEX/DEX 041 path is working; App Cloner's 5 repaired outputs all
  pass `dexdump` despite skipped nonstandard method shapes.
