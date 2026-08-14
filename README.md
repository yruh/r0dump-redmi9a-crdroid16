# R0DUMP for Redmi 9A (blossom)

R0DUMP 在 Redmi 9A (`M2006C3LI` / `dandelion`) 的 crDroid 12.11 / Android 16
上的可复现移植补丁集。crDroid 使用统一设备代号 `blossom`，所以构建
目标是 `lineage_blossom-trunk_staging-userdebug`；`lineage_` 只是继承的产品命名，
不代表 ROM 变成 LineageOS。

> English summary: reproducible Android 16 source patches and overlays for the
> R0DUMP runtime on crDroid 12 / Redmi 9A (`blossom`). No proprietary blobs,
> signing keys, device backups, rooted boot images, or build outputs are stored
> in this repository.

## 已验证状态

| 项目 | 结果 |
| --- | --- |
| 设备 | Redmi 9A `M2006C3LI`，运行时代号 `dandelion` |
| ROM | crDroid 12.11 / Android 16 / SDK 36 |
| 构建 | `lineage_blossom-trunk_staging-userdebug` 完整 `bacon` 成功 |
| 架构 | ARM32 + ARM64 ART 均已构建和打包 |
| R0DUMP | 32/32 个策略位已接入源码与构建图 |
| 真机 | 启动、Manager、DUMP、自动结束、扫描、Repair、ZIP 闭环通过 |
| DEX | App Cloner 导出 7 个 DEX，5/5 个修复结果通过 Android 16 `dexdump` |
| MTK | Thermal HAL 2.0 稳定，IMS 类加载与 Binder 绑定稳定 |

### 最新回归修复

R0DUMP 现在只使用
`/sdcard/Download/R0DUMP/<package>/<run-id>/<process>`：ART 忽略外部输出根
配置，Download 不可写时立即停止，不回退到 `Android/data`、应用私有目录或
`/data/local/tmp`。framework 只对当前选中的目标进程提供 pass-through 存储挂载，
SELinux 保持 Enforcing。

2026-08-15 在 Redmi 9A 上用 App Cloner 完成最终回归：批次
`1786743880030-fc0b6400` 只在 Download 写入 7 个 DEX，
`reconstruction_failures=0`，以 `stopped/max_seconds` 正常收口。Manager 扫描、
修复和 ZIP 验证均通过，5/5 个修复 DEX 可被 Android 16 `dexdump` 解析。
最终 OTA 已在同一台手机上无清数据 sideload，系统分区和当前活动 Manager
均为修复版。

crDroid 官方设备页：<https://crdroid.net/blossom/12>

## 仓库内容

```text
manifests/                 crDroid 设备 manifest 和已验证源码快照
overlay/art/               新增 ART runtime 源码
overlay/packages/apps/     R0DUMP Manager 完整源码
patches/                   按 Android 子仓分割的 22 个补丁
scripts/                   应用、校验、blob 检查和受限内存构建脚本
docs/                      构建、安装、架构和完整修复报告
verification/              实机结果与发布文件校验和
```

## 快速开始

### 1. 同步源码

```bash
mkdir crdroid-16
cd crdroid-16
repo init -u https://github.com/crdroidandroid/android.git -b 16.0 --git-lfs
mkdir -p .repo/local_manifests
cp ../r0dump-redmi9a-crdroid16/manifests/blossom.xml .repo/local_manifests/
repo sync -c -j8 --force-sync --no-clone-bundle --no-tags
```

`manifests/manifest-snapshot.xml` 记录了验证时的完整 revision 快照；
`manifests/base-revisions.tsv` 只列出受补丁影响的子仓。

### 2. 应用补丁和 overlay

```bash
cd ../r0dump-redmi9a-crdroid16
scripts/check-patches.sh ../crdroid-16
scripts/apply-patches.sh ../crdroid-16
```

基线 revision 不一致时脚本会停止，避免静默生成错误 ROM。更新到新的
crDroid 基线时，应逐个重定基并重新验证，而不是盲目跳过检查。

### 3. 提取厂商 blob

本仓库不提供 Xiaomi/MediaTek 专有二进制。在应用补丁后，从本人设备
或可用的原厂固件源提取：

```bash
cd ../crdroid-16
./device/xiaomi/blossom/extract-files.sh /path/to/extracted-stock-rom
../r0dump-redmi9a-crdroid16/scripts/verify-blobs.sh .
```

Thermal HAL、`libnvram` 和 `libsysenv` 的 Android 16 兼容依赖在提取阶段由
`extract-files.sh` 修补，不需要把修改后的 `.so` 放入 Git。

### 4. 构建

```bash
ANDROID_ROOT="$PWD" ../r0dump-redmi9a-crdroid16/scripts/build-full-ota.sh
```

默认使用普通并行 `-j4`、highmem pool `2`、`MemoryHigh=20G`、
`MemoryMax=24G`、`MemorySwapMax=0G`。普通 dex/R8/D8 堆限制为 `2048M`，
`Launcher3QuickStep` 单独使用 `3072M`；OTA 临时文件写入工作盘上的
`out/r0dump-tmp`，不会占满小容量 `/tmp`。
详细说明见 [docs/BUILD.md](docs/BUILD.md)。如果只改 Manager，优先使用缓存模块
构建或 system_ext-only 封装，不要强制让整个 Soong 图重新变脏。

## 重要区分

- 上游 R0DUMP 发布包是 OnePlus 9 (`lemonade`) 专用，不可刷入 Redmi 9A。
- 这里开源的是源码补丁和可复现配置，不是原厂 blob 镜像仓库。
- Android 16 正常应用路径使用标准 DEX/DEX 041，本次真机测试不需要
  CompactDex 转换器。
- 本地验证包使用 testkey/AVB test key；对外发布应使用自己的签名密钥。
- 刷入新 OTA 会覆盖 `boot.img`，root 需基于同一次构建的 boot 重新修补。

## 文档

- [构建与内存管理](docs/BUILD.md)
- [安装与回滚](docs/INSTALL.md)
- [功能与默认配置](docs/USAGE.md)
- [完整移植修复报告](docs/PORTING_REPAIR_REPORT.md)
- [源码基线与排除内容](docs/SOURCE_STATE.md)

## License

Apache License 2.0。上游文件保留各自原有声明，详见 [LICENSE](LICENSE)
和 [NOTICE](NOTICE)。
