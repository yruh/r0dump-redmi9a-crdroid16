# 源码状态与发布边界

## 基线

- crDroid manifest：`16.0`，根提交 `a311bc97e4784ee24e8f97e377d06bdbff85b4bb`
- AOSP tag 基线：`android-16.0.0_r4`
- crDroid 设备树：`crdroidandroid/android_device_xiaomi_blossom` `16.0`
- R0DUMP 上游：<https://github.com/tiwe0/r0dump>，参考提交 `d0c30cf`

`manifests/manifest-snapshot.xml` 是已验证工作树的 `repo manifest -r` 输出。
`base-revisions.tsv` 和 `patches/series.tsv` 是应用脚本使用的最小基线集。

## 补丁分组

1. `001`-`005`：R0DUMP ART/libcore/framework/ROM 属性和 Settings 显示。
2. `006`-`009`：AArch64 链接、OTA 签名、Soong 内存/编码和 sepolicy 路径。
3. `010`-`017`：MTK sepolicy、blossom 设备树、内核头、HAL、媒体、蓝牙和启动器。
4. `018`-`021`：vendor 分区需要的 AOSP 库可见性与中文路径修复。
5. `022`：只包含厂商仓的文本生成文件，不包含任何 blob。

## Overlay

- `overlay/art/runtime/r0dump_runtime.cc`：ART 中新增的有界导出运行时。
- `overlay/packages/apps/R0DUMPManager`：Manager 完整 Java/Compose/资源源码。

## 明确排除

- Xiaomi/MediaTek proprietary `.so`、固件和修补后的 blob。
- OTA、APK、DEX、镜像、patched boot、签名密钥和设备备份。
- ADB 序列号、本地用户路径和 GitHub 凭据。
- `packages/modules/common/build/allowed_deps.txt` 的纯排序差异；其集合没有变化。
- `prebuilts/build-tools` 中的主机 `date`/`tar` 替换不属于设备运行时，
  只作为 `patches/optional/900-host-gnu-date-tar.patch` 保留，不在默认 series 中。

## 专有 blob 的可复现处理

`device/xiaomi/blossom/proprietary-files.txt` 已在上游设备树列出需要的 Thermal
HAL 和 NVRAM/sysenv 文件。补丁后的 `extract-files.sh` 使用 `patchelf`：

- Thermal service/impl：`libutils.so` -> `libutils-v32.so`
- Thermal service/impl：`libhidlbase.so` -> `libhidlbase-v32.so`
- `libnvram.so`/`libsysenv.so`：增加 `libshim_base.so`

因此 Git 里保留算法和提取列表，实际 blob 由构建者从自己的可用源生成。

