# Verified build and device results

Date: 2026-08-11

## Full OTA

- Product: `lineage_blossom-trunk_staging-userdebug`
- Result: `172/172`, exit code 0, 5m59s
- VINTF: `COMPATIBLE`
- ZIP test: all entries passed `unzip -t`
- OTA: `crDroidAndroid-16.0-20260811-blossom-v12.11.zip`
- Size: `1,407,907,513` bytes
- SHA-256: `850ee5547840835ff3179f2538233f7de4a60be30cf11516ec55fd9aaed9d766`

The OTA binary is not stored in this source repository.

## Key build artifacts

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `boot.img` | 67,108,864 | `5f8659835b313f95af1b35b356776fa304a3d12a92ee614be2f7f8fb0b63d8bd` |
| `recovery.img` | 67,108,864 | `8be92ff85129a153c5aa1d93f2661248f76fbd1c6dd0cfff415f556692eea3f1` |
| `system.img` | 1,487,642,624 | `e85e1e7f8d9283d3523a8dd45d4f6a37173f3095c234b365fcece7a5b68c6472` |
| `system_ext.img` | 1,076,813,824 | `3a588e6a5dacadf2bac5dcf71e3fa5b8e9004264d28493ac8a8171568fb4c935` |
| `vendor.img` | 577,495,040 | `6f1a68e587a09c1fe3c32942ca95a4bfeccd60ed4c28e238b27c1ebe93609cbf` |
| `R0DUMPManager.apk` | 50,700,656 | `82bfb443bad5696928fdbd5700940b6819838135f91b26cb4180201f995cbd18` |

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

