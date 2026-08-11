# 安装与回滚

## 设备范围

本次完整真机验证对象为 Redmi 9A `M2006C3LI` / `dandelion`，统一构建
代号 `blossom`。OTA assertion 中还包含同设备树的其他变体，但本报告不把
它们等同于已完成真机验证。

## 刷入前

1. 确认 BL 已解锁。
2. 备份 `boot`、`recovery`、`dtbo`、`vbmeta`、`vbmeta_system` 和
   `vbmeta_vendor`。
3. 保留当前 ROM 对应的 recovery 和完整 OTA。
4. 校验新 ZIP 的 SHA-256 与 `unzip -t`。
5. 确认包是本机构建的 `blossom` 产物，不是上游 OnePlus 9 发布包。

## 安全导出分区

在系统已开机且 `su` 可用时执行：

```bash
ADB_SERIAL=SERIAL scripts/backup-partitions.sh device-backup
```

脚本使用无伪终端的 `adb exec-out` 导出分区，并把每个文件的实际字节数与
设备块大小比较，最后生成 `SHA256SUMS`。任何截断、额外输出或终端换行转换
都会使脚本立即失败并删除 `.partial` 文件。

不要使用 `adb shell -t 'su -c cat /dev/block/...' > boot.img` 导出二进制。
伪终端会把镜像中的 `0A` 扩展成 `0D 0A`，生成看似有正确 magic、实际不可
直接刷入的污染文件。

## Recovery sideload

在 crDroid/AOSP recovery 中选择 **Apply update -> Apply from ADB**，然后：

```bash
adb sideload out/target/product/blossom/crDroidAndroid-16.0-YYYYMMDD-blossom-v12.11.zip
```

首次跨 ROM 安装通常需要格式化 data；同一源码树的后续更新不应无理由
重复清数据。加密、签名或分区基线变化时，以 recovery 给出的具体错误为准。

## 首次开机验证

```bash
adb wait-for-device
adb shell getprop sys.boot_completed
adb shell getprop ro.crdroid.build.version
adb shell getprop ro.crdroid.device
adb shell getprop ro.product.device
```

期望值：`1`、`12.11`、`blossom`、`dandelion`。再检查：

```bash
adb shell pidof android.hardware.thermal@2.0-service.mtk
adb shell pidof com.mediatek.ims
adb logcat -b crash -d
```

## Root

必须从当前这一次构建的 `boot.img` 生成 patched boot。旧 ROM 的 patched boot
与新 system/vendor 组合没有兼容保证。安装 OTA 后 boot 会被覆盖，所以 root
处理应放在 OTA 之后。

## 回滚

回滚时应使用同一版本成套备份的 boot/dtbo/vbmeta 组合，不要混用不同
构建的 AVB 元数据。不完整的 `.partial` recovery 文件不属于可回滚产物。
