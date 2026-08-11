# Verified build and device results

Date: 2026-08-12

## Full OTA

- Product: `lineage_blossom-trunk_staging-userdebug`
- Result: final `bacon` target completed with exit code 0; all build units were
  run under a cgroup memory ceiling, with a measured peak of 19.2 GiB and no
  `oom`/`oom_kill` events
- VINTF: `COMPATIBLE`
- ZIP test: all entries passed `unzip -t`
- OTA: `crDroidAndroid-16.0-20260811-blossom-v12.11.zip`
- Size: `1,407,740,513` bytes
- SHA-256: `25010572c73afd87ae85d8ee1aec0d4bb718aa239b0c34f6405239978cd9157f`

The OTA binary is not stored in this source repository.

## Key build artifacts

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `boot.img` | 67,108,864 | `5f8659835b313f95af1b35b356776fa304a3d12a92ee614be2f7f8fb0b63d8bd` |
| `recovery.img` | 67,108,864 | `efa079b132b6def5b9b239f3cc1629b602e4ff3fb23deef6e63a328db5adaa3f` |
| `system.img` | 1,487,642,624 | `fe012916c0fc92a69d34be932b05183b74a0ae488bb19916e06c4a085bd348d5` |
| `system_ext.img` | 1,076,813,824 | `e52aab2f94a06e1d5392ef3a59b2f4174bcc374f1c77fa84bfffe54797828ad0` |
| `vendor.img` | 577,495,040 | `1397ca93c24432140d969c68aed4c1513fbe7e1d88204a0259f04d3e1f895148` |
| `R0DUMPManager.apk` | 50,700,656 | `eb5181f1c141892894da66ffccf669f886d4a8c3674a7fa823f23d3043acbf48` |

## Device smoke

- Device: Redmi 9A `M2006C3LI`, runtime codename `dandelion`
- ROM properties: crDroid `12.11`, `ro.crdroid.device=blossom`, Android 16 / SDK 36
- Boot: `sys.boot_completed=1`
- Thermal HAL 2.0: connected, actual temperature/cooling data returned, stable process
- MediaTek IMS: class loading and Binder binding stable; a SIM/VoLTE call was not part of this run
- R0DUMP target: AOSP Calculator, `main_only`, global/force/raw mirror disabled
- Output: 3 DEX/DEX 041 containers plus method records
- Repair: 3/3 repaired, 472 records applied, 27 duplicates, 0 skipped
- Repaired DEX: 3/3 parsed by Android 16 host `dexdump`
- Manager completion: terminal `complete` state and global dump enable automatically cleared

The final ZIP is kept outside this source repository. It has passed `unzip -t` and
the SHA-256 check above; recovery sideload and post-boot verification are recorded
separately because they require the device-side recovery menu.
