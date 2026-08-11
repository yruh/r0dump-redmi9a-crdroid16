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
| highmem pool | 2 |
| `MemoryHigh` | 20G |
| `MemoryMax` | 24G |
| `MemorySwapMax` | 0G |
| dex/R8/D8 heap | 2048M |
| Launcher3QuickStep R8 heap | 3072M |
| Soong `GOMEMLIMIT` | 6GiB |
| Soong `GOGC` | 40 |

`build-full-ota.sh` 默认使用 `out/r0dump-tmp`，构建前清空该专用目录并检查
文件数、磁盘、inode、`MemAvailable` 和 swap，要求至少 16 GiB 临时空间。
这样 OTA 的 8 GiB 级目标文件不会写满小容量 `/tmp`。`systemd-run
--user --scope` 保证整个构建进程树都在同一个 cgroup 内存上限下。

如果临时文件系统是 Btrfs，`df -i` 可能返回 `-` 而不是 inode 百分比；资源
检查脚本会显示 `n/a inodes used`，继续执行文件数、空间、内存和 swap 检查，
不会把文件系统类型误判成构建错误。

普通任务仍使用 `-j4`。D8/R8 进入深度为 2 的 highmem pool；只有实测需要
更大堆的 `Launcher3QuickStep` 使用 3072M，其余 dexer 使用 2048M。

## 完整 OTA

```bash
ANDROID_ROOT=/path/to/crdroid-16 scripts/build-full-ota.sh
```

根据主机内存可调整：

```bash
BUILD_JOBS=6 \
BUILD_MEMORY_HIGH=20G \
BUILD_MEMORY_MAX=24G \
BUILD_DEXER_HEAP_SIZE=2048M \
BUILD_LARGE_R8_HEAP_SIZE=3072M \
ANDROID_ROOT=/path/to/crdroid-16 \
scripts/build-full-ota.sh
```

不应把 `BUILD_MEMORY_MAX` 设置得接近物理内存总量，系统、ADB 和页缓存仍
需要空间。

主机全局内存压力可能在 Soong 启动阶段触发 OOM，即使构建 cgroup 本身没有
`oom_kill`。遇到这种情况先使用缓存模块构建；只改 Manager 时不需要让整个
Soong 图重新编译。

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
