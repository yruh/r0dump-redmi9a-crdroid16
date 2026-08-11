# 构建说明

## 已验证基线

- crDroid 12.11 / Android 16 / SDK 36
- 产品：`lineage_blossom-trunk_staging-userdebug`
- manifest 根提交：`a311bc97e4784ee24e8f97e377d06bdbff85b4bb`
- Redmi 9A 设备树：`7fbb5673df67f3477fdd56c763b71036dea1ba3b`
- R0DUMP 上游仓库参考提交：`d0c30cf`

当前完整源码树加构建输出约 291 GiB。建议为源码、`out/`、临时文件
和 OTA 留出至少 350 GiB 可用空间。

## 预检

```bash
scripts/verify-release-tree.sh
scripts/check-patches.sh ../crdroid-16
scripts/apply-patches.sh --dry-run ../crdroid-16
scripts/verify-blobs.sh ../crdroid-16
```

`check-patches.sh` 使用临时 Git index，直接证明每个补丁能应用到记录的
base commit，不会修改 Android 工作树。

## 资源策略

构建脚本不使用全局 `-j2`。默认配置是：

| 参数 | 默认值 |
| --- | ---: |
| 普通 Ninja 并行 | 4 |
| highmem pool | 1 |
| `MemoryHigh` | 20G |
| `MemoryMax` | 24G |
| `MemorySwapMax` | 4G |
| Soong `GOMEMLIMIT` | 10GiB |
| Soong `GOGC` | 50 |

`check-build-resources.sh` 会在构建前检查 `/tmp` 文件数、磁盘、inode、
`MemAvailable` 和 swap。`systemd-run --user --scope` 保证整个构建进程树
都在同一个 cgroup 内存上限下。

## 完整 OTA

```bash
ANDROID_ROOT=/path/to/crdroid-16 scripts/build-full-ota.sh
```

根据主机内存可调整：

```bash
BUILD_JOBS=6 \
BUILD_MEMORY_HIGH=20G \
BUILD_MEMORY_MAX=24G \
ANDROID_ROOT=/path/to/crdroid-16 \
scripts/build-full-ota.sh
```

不应把 `BUILD_MEMORY_MAX` 设置得接近物理内存总量，系统、ADB 和页缓存仍
需要空间。

## 仅构建 R0DUMP 核心模块

```bash
ANDROID_ROOT=/path/to/crdroid-16 scripts/build-r0dump-modules.sh
```

该脚本构建 Manager、framework、core-libart 和 ARM32/ARM64 libart。已有 Soong
Ninja 图时会走 cached path；改动 `Android.bp` 后应使用：

```bash
BUILD_MODE=soong ANDROID_ROOT=/path/to/crdroid-16 scripts/build-r0dump-modules.sh
```

## 完整性校验

```bash
unzip -t out/target/product/blossom/crDroidAndroid-16.0-*-blossom-v12.11.zip
sha256sum out/target/product/blossom/crDroidAndroid-16.0-*-blossom-v12.11.zip
```

验证构建的参考哈希见 `verification/BUILD_RESULTS.md`。

## 可选主机兼容补丁

`patches/optional/900-host-gnu-date-tar.patch` 记录了验证主机上禁用 hermetic
Toybox `date`/`tar` 的差异。它不是 Redmi 9A 运行时补丁，默认不应用；
只有当构建日志明确证明主机 GNU 工具行为是必需时才使用。

