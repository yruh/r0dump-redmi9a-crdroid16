# R0DUMP crDroid 16 / Redmi 9A 完整移植修复报告

日期：2026-08-11

## 1. 结论

R0DUMP 已经移植到 Redmi 9A (`M2006C3LI`) 的 crDroid 12.11 / Android 16
源码树，并完成了编译、分区刷入、开机、DUMP、状态收敛、DEX 修复和 ZIP
校验的真机闭环。当前不是“只能编译”或“尚未实机测试”状态。

| 项目 | 结果 |
| --- | --- |
| crDroid 设备支持 | 官方已支持 Redmi 9A，统一代号 `blossom` |
| 构建目标 | `lineage_blossom-trunk_staging-userdebug`，Android 16 / SDK 36 |
| R0DUMP 集成 | ART + libcore + framework + Manager，ARM32/ARM64 |
| 策略接入 | 32/32 个策略位已接入构建和运行时 |
| 最终 OTA | 已成功构建，`unzip -t` 通过，VINTF `COMPATIBLE` |
| 真机启动 | 通过，`sys.boot_completed=1` |
| 真机 DUMP | 通过，计算器导出 3 个 DEX/DEX 041 容器和 1 份 method records |
| 自动终止 | 通过，`phase=complete`，UI 流程自动把总开关清为 0 |
| 修复 ZIP | 通过，3/3 DEX 修复，ZIP 和 Android 16 `dexdump` 均通过 |
| MTK Thermal HAL | 已修复，HAL 2.0 连接且 PID 稳定 |
| MTK IMS | 已修复类可见性和绑定，PID 稳定 |

crDroid 的构建产品名仍是 `lineage_blossom`，因为 crDroid 继承 Lineage 的构建
规则；这不表示手机被刷成了 LineageOS。当前官方设备信息见
[crDroid Redmi 9A / blossom 设备页](https://crdroid.net/blossom/12)。

## 2. 设备与构建基线

- 市场型号：Redmi 9A / `M2006C3LI`
- 运行时设备名：`dandelion`
- 统一设备树/产品名：`blossom`
- Android：16，SDK 36，代号 Baklava
- crDroid：12.11
- 构建 ID：`BP4A.251205.006`
- 指纹：`Redmi/lineage_blossom/blossom:Baklava/BP4A.251205.006/eng.androi:userdebug/release-keys`
- 设备树基线：`crdroidandroid/android_device_xiaomi_blossom` 16.0，本地 HEAD `7fbb567`
- 内核：预编译 4.19.191
- 分区：非 A/B，动态分区，OTA 支持
  `dandelion, angelica, angelican, cattail, angelicain, blossom`

## 3. R0DUMP 功能完成度

### 3.1 32 个策略位

| 位 | 策略 | 位 | 策略 |
| ---: | --- | ---: | --- |
| 0 | Class walk | 16 | VDEX open all dex files |
| 1 | Application create | 17 | OAT DexFile open |
| 2 | Activity create | 18 | VerifyClass |
| 3 | RealInvoke | 19 | Class init before |
| 4 | LoadMethod | 20 | Class init after |
| 5 | DEX load | 21 | Interpreter Execute |
| 6 | RegisterDex | 22 | JIT MethodEntered |
| 7 | InMemoryDex | 23 | JIT Compile |
| 8 | DefineClass | 24 | Reflection Method.invoke |
| 9 | LoadClass | 25 | Instrumentation enter |
| 10 | ResolveMethod | 26 | Instrumentation exit |
| 11 | Force backfill | 27 | Java ClassLoader route |
| 12 | Force backfill before | 28 | Java DexFile route |
| 13 | Force backfill after | 29 | Native defineClass |
| 14 | OpenCommon | 30 | OAT register |
| 15 | OpenDexFilesFromOat | 31 | Image-space dex |

这里的 32/32 表示代码路径已接入、能通过 ARM32/ARM64 构建，不表示 32 种
策略都已用不同加壳样本逐一实机触发。本次真机闭环使用默认安全组合
`0x187`：Class walk、Application create、Activity create、InMemoryDex 和 DefineClass。

### 3.2 Manager

- 应用选择与明确全局开关分离；“未选应用”不会自动变成全局。
- 支持 `main_only`、`all` 和精确进程三种范围。
- 输出按 `package/run_id/process` 隔离；历史批次和多进程会生成多个目录。
- 支持 start/stop/scan/repair shell 自动化，仅信任 self/root/system/shell UID。
- 状态、logcat、扫描和修复使用后台任务，不阻塞 Compose 主线程。
- 扫描具有深度、文件数、字节数、符号链接和单文件上限。
- 修复输出使用暂存目录、结构校验、原子切换和失败回滚。

### 3.3 导出与修复

- 支持标准 DEX 和 Android 16 DEX 041 共享容器。
- DEX 041 保存容器范围和 entry 信息，修复后重算 SHA-1/Adler32。
- method record 按批次、包、进程、DEX 身份、method index、偏移、长度和 hash 去重。
- 对导出范围和内存映射先校验；不可读稀疏区间写成文件空洞。
- headerless raw dexdata 只在有可信 ART header 快照时重建。
- 异步选项当前明确报告 `synchronous_fallback`；这是为避免 ART 裸指针离开
  回调生命周期后失效，不是编译遗漏。

## 4. 已修复问题

### 4.1 一直显示“正在 DUMP”

原因有两层：

1. ART 后到的热点回调可能把终态重新覆盖为 `dumping`。
2. Manager 自动化启动时，`onResume()` 与后台线程生成 `run_id` 存在竞态，新 ID
   会被旧值 `pending` 覆盖，导致 Manager 不认为终态属于当前批次。

修复后：

- 阶段明确为 `configured`、`waiting_delay`、`class_walk`、`complete`、
  `stopped`、`stopped_by_limit` 和 `class_walk_failed`。
- 进入终态后，后到回调不得使阶段回退。
- 新批次 ID 作为不可变参数提交，不再依赖可被 `onResume()` 覆盖的共享字段。
- 真机 UI 流程中，20 秒上限到达后写入 `complete`，Manager 自动扫描并把
  `r0dump.dump.enabled` 从 1 清为 0。

### 4.2 MTK Thermal HAL 循环崩溃

原问题是 32/64 位 Thermal HAL 实现 blob 直接链接 Android 16 的 `libutils.so` 和
`libhidlbase.so`，运行后在 `libutils::Looper::pollOnce` 周期性 SIGSEGV。

修复为对 service 和两个 `android.hardware.thermal@2.0-impl.so` 统一替换依赖：

- `libutils.so` -> `libutils-v32.so`
- `libhidlbase.so` -> `libhidlbase-v32.so`

真机结果：`HAL Ready: true`、`ThermalHAL 2.0 connected: yes`，CPU/GPU/NPU/
电池/机身温度和 cooling devices 都可读；PID `946` 持续稳定。

### 4.3 MTK IMS 类缺失

实机依次暴露：

1. `com.android.internal.telephony.metrics.TelephonyMetrics` 缺失。
2. `OperatorCustomizationFactoryLoader$OperatorFactoryInfo` 缺失。
3. `com.mediatek.telephony.MtkTelephonyManagerEx` 缺失。

前两层通过 Lineage telephony compat 和 MediaTek common framework 解决。第三层来自官方
设备树的试验性变更 `3ce6254 [try]blossom: Switch to shared libs to mtk frameworks`：
它删除了 IMS blob 直接依赖的 Boot JAR，但 ImsService manifest 又没有声明这些核心
共享库。

本次恢复了历史已知工作提交 `114d3dc` 中的 Boot Classpath 设计：

- `telephony-common-stub`
- `mediatek-common`
- `mediatek-framework`
- `mediatek-ims-base`
- `mediatek-ims-common`
- `mediatek-telecom-common`
- `mediatek-telephony-base`
- `mediatek-telephony-common`

真机结果：`com.mediatek.ims` PID `2478` 持续稳定，
`MtkDynamicImsService` 为 `requested=true received=true hasBound=true`，没有再出现
`NoClassDefFoundError`。

### 4.4 构建与设备树问题

- 预编译 4.19 内核不支持 EROFS APEX，改为 ext4 APEX payload。
- Android 16 trunk 默认矩阵不再包含该设备需要的 FCM 5，显式打包
  `framework_compatibility_matrix.5.xml`。
- 解决通用 MTK USB gadget 与设备定制 service 的同名安装冲突。
- 补全 VNDK32 兼容库、NVRAM/sysenv shim 和指纹 HAL 选择。
- 修复中文工作路径下 Soong sbox、build.prop 和 sepolicy Python 编码。
- 禁用 LTO 后修复 Bionic AArch64 条件分支距离越界。
- 构建前检查 `/tmp` 文件数、空间、inode、内存和 swap。
- 最终参数为普通并行 4、highmem pool 1，`MemoryHigh=20G`、
  `MemoryMax=24G`、`MemorySwapMax=4G`；没有使用无意义的全局 `-j2`。

## 5. 问题来源划分

| 问题 | 来源 |
| --- | --- |
| 策略、输出、终态、`pending` 批次竞态 | R0DUMP 移植/Manager |
| Thermal HAL SIGSEGV | Redmi 9A 旧 MTK vendor blob 与 Android 16 兼容层 |
| IMS 三层类缺失 | 当前官方 blossom 设备树的试验性 shared-lib 配置 |
| EROFS/FCM/USB/shim | blossom 设备树向 Android 16 迁移 |
| 中文路径、内存、LTO | 本地主机构建环境 |
| 清数据后 root 消失 | `fastboot -w`/数据清除，不是 crDroid bug |
| 当前 recovery 界面简陋 | AOSP/crDroid recovery 设计，不是 R0DUMP bug |

换其他 Android 16 类原生 ROM 也可以移植 R0DUMP，但不会自动消除硬件适配工作：
ART/framework patch、device tree、vendor blob、内核、VINTF、分区和签名仍需针对新
ROM 重新合并和测试。

## 6. 真机 DUMP / Repair 证据

测试目标：系统计算器 `com.android.calculator2`，主进程模式，全局、raw mirror 和
force backfill 均关闭，测试延迟 1 秒，最长 20 秒。

- 最终 UI 批次：`1786439571351-b60424ea`
- 批次目录不再是 `pending`。
- 导出：3 个 DEX/DEX 041 容器，1 个 method JSONL，1 个 `status.json`。
- 状态：`phase=complete`、`stop_reason=max_seconds`、`runtime_enabled=false`。
- Manager 扫描：`dex=3, records=1`，总开关自动清零。
- 修复：`repaired=3`，读取 499 条，应用 472 条，去重 27 条，跳过 0 条。
- 修复 ZIP：19,275,090 字节，SHA-256
  `a9861271cf5d217af095b5e99e9f5b96867a12c939f1e00f562ba743d87f2d0d`。
- `unzip -t`：全部条目 OK。
- Android 16 host `dexdump`：3/3 修复 DEX 全部可解析。
- 验收后已删除手机上约 218 MiB 测试目录和 UI XML，不留额外垃圾。
- 最终设置已恢复为：总开关 0、全局 0、force backfill 0、raw mirror 0、
  无目标包。

## 7. 最终构建与产物

源码根目录：`<android-source>/crdroid-16`

最终 `bacon` 构建：`172/172`，退出码 0，用时 5 分 59 秒。

| 产物 | 大小（字节） | SHA-256 |
| --- | ---: | --- |
| `crDroidAndroid-16.0-20260811-blossom-v12.11.zip` | 1,407,907,513 | `850ee5547840835ff3179f2538233f7de4a60be30cf11516ec55fd9aaed9d766` |
| `boot.img` | 67,108,864 | `5f8659835b313f95af1b35b356776fa304a3d12a92ee614be2f7f8fb0b63d8bd` |
| `recovery.img` | 67,108,864 | `8be92ff85129a153c5aa1d93f2661248f76fbd1c6dd0cfff415f556692eea3f1` |
| `system.img` | 1,487,642,624 | `e85e1e7f8d9283d3523a8dd45d4f6a37173f3095c234b365fcece7a5b68c6472` |
| `system_ext.img` | 1,076,813,824 | `3a588e6a5dacadf2bac5dcf71e3fa5b8e9004264d28493ac8a8171568fb4c935` |
| `product.img` | 730,292,224 | `a8277a8993fb8c0d6e5e683f7f3f93dc8a384504f39bb56b54e721fa9778518d` |
| `odm.img` | 1,593,344 | `44e921819ff4e8586e247066e4a44f63f4d35540f1f003ae47e8d4459b9bf444` |
| `vendor.img` | 577,495,040 | `6f1a68e587a09c1fe3c32942ca95a4bfeccd60ed4c28e238b27c1ebe93609cbf` |
| `vbmeta.img` | 65,536 | `42a80d539c771fbdbd34f21eb8ac7cbdb9c0b2423aa8550b28a529530c70fc1f` |
| `vbmeta_system.img` | 65,536 | `1cfcf822558d98fc86a5bc6fd543daf2d7fe4fa2fb5cc0309514c5ad3e815d37` |
| `vbmeta_vendor.img` | 65,536 | `01b60fe041a2d8d5cd46fdd829356e2e396433661e09d5097182ac87fb59b470` |
| `R0DUMPManager.apk` | 50,700,656 | `82bfb443bad5696928fdbd5700940b6819838135f91b26cb4180201f995cbd18` |

关键中间产物：

| 产物 | 大小（字节） | SHA-256 |
| --- | ---: | --- |
| `framework.jar` | 44,907,511 | `a68d99f957428781621d415bd0aeec6cb2e519bdca040816056631d701fa969c` |
| `core-libart.jar` | 604,551 | `e17b032937defc0e06939868acbe36f020a45fd4234fb29020524f5410d43d24` |
| ARM64 `libart.so` | 16,476,744 | `974fc859698463abceaf14b805906149310956e687aa2ef5621ce722da22c9b3` |
| ARM32 `libart.so` | 10,477,608 | `c3ab4b82e52c1cd759a8cd356b4255d2bf991ab27f0564faee0242936f9052ed` |

## 8. 已执行验证

- 完整系统构建和最终 OTA 构建均成功。
- `unzip -t` 校验最终 OTA 全部条目，无错误。
- target-files VINTF 最终结果为 `COMPATIBLE`。
- Boot Classpath protobuf 包含 telephony compat 和全部 7 个 MTK framework JAR。
- Thermal service 和 32/64 位 impl 的 ELF `NEEDED` 均指向 v32 兼容库。
- 手机活动 Manager APK 与最终本地 APK SHA-256 完全相同。
- Manager 所需 `WRITE_SECURE_SETTINGS`、`READ_LOGS`、`MANAGE_EXTERNAL_STORAGE`、
  `FORCE_STOP_PACKAGES` 等权限已授予。
- IMS/Thermal 先连续观察 2 分钟，后续在 DUMP/修复/整包构建期间 PID 仍未变化。
- crash buffer 为空，普通 logcat 中无 `NoClassDefFoundError`、
  `ClassNotFoundException`、`VerifyError`、`FATAL EXCEPTION` 或 Thermal fatal signal。
- 各已修改 Git 子仓的 `git diff --check` 通过。
- 构建脚本 `bash -n` 和 sepolicy `policy.py` Python 编译检查通过。

## 9. 当前手机状态

- 系统已开机，ADB 连接正常。
- 当前活动 Manager 为 `/data/app` 中的同签名系统应用更新，内容与
  最终构建 APK 相同。
- 当前运行时与 IMS/Thermal 分区来自最终整包之前的等价增量构建；
  最终 OTA 生成后没有再整包安装一次。
- 当前 `su` 不存在，即当前系统没有激活 root。
- 验证时的 patched boot 仅保存在本地私有备份，未放入公开仓库。
- 最终 OTA 包含未 root 的 `boot.img`；安装 OTA 会覆盖 root boot，需在 OTA 之后
  重新修补对应的新 boot。
- 当前 recovery 是 AOSP/crDroid recovery。本地曾有一份不完整的第三方
  recovery 下载，从未刷入，也未放入公开仓库。

## 10. 已知限制与剩余风险

1. **最终 OTA 尚未再次整包安装**：与它等价的核心分区已刷入，且最终
   Manager APK 已在真机激活，但最终 ZIP 本身只完成了离线完整性和 VINTF 验证。
2. **使用测试密钥**：本地 userdebug OTA 使用 Android testkey/AVB test key，不是生产
   私钥发布包；顶层 vbmeta flags 为 3，适合解锁后的研发机，不是安全发行配置。
3. **安全补丁偏旧**：构建中声明的 SPL 为 `2025-11-05`，相对当前日期已滞后。
4. **VoLTE 实际通话未验证**：IMS 进程、类加载和 Binder 绑定已通过；测试时
   `subId=-1`/无有效注册网络，因此没有完成带 SIM 的电话、短信和 VoLTE 通话验证。
5. **32 位样本未做完整闭环**：ARM32 libart 已构建和打包，但本次计算器闭环
   是 64 位进程。
6. **高风险策略未逐项实机跑完**：全局模式、多进程 `all`、精确进程、raw
   mirror、force backfill、ANR 保护和所有高频 JIT/instrumentation 组合只完成构建/
   代码审计，未用专用样本逐个覆盖。
7. **同步导出回退是有意设计**：`synchronous_fallback` 保证 ART 指针生命周期，尚未
   实现“先安全拷贝、再后台持久化”的有界队列优化。
8. **CompactDex 不是当前阻塞**：Android 16 正常应用路径使用标准 DEX/DEX 041，
   本次实测 `nonstandard_dex_methods_skipped=0`。不需要为当前端口补一个旧版
   CompactDex 转换器；未来遇到非标准样本时应按具体魔数和容器再处理。
9. **设备树仍有上游警告**：预编译内核 ABI header 对齐和 PowerOffAlarm vendor
   seapp Treble 标签在构建中会告警，当前没有导致构建失败或本次实机异常。

## 11. 默认使用建议

普通 App 先使用默认预设：

- 明确选择一个目标 App。
- 进程模式保持 `main_only`。
- 全局模式关闭。
- force backfill 关闭。
- raw dexdata mirror 关闭。
- 高频执行/JIT/instrumentation 策略不要一次全开。
- `stop_after_complete` 开启。
- 默认上限为 50,000 条记录和 300 秒；如果只做快速测试，再手动降低。

开始后可以看到多个文件或目录：DEX、DEX 041 容器、method records、
`status.json`、不同 `run_id` 和不同 process 目录都是正常的。判断是否结束应以
`status.json` 的 `phase` 为准，不是以文件是否已经出现为准。

## 12. 备份与回滚

- 刷机前备份仅保存在本地私有存储，约 161 MiB，未放入公开仓库。
- 包含原 boot、recovery、dtbo、vbmeta/vbmeta_system/vbmeta_vendor、分区哈希、
  `getprop` 和 `lpdump`。
- 最终 OTA 会覆盖 boot、dtbo 和三级 vbmeta，刷入前应保留该备份和与当前
  版本对应的 patched boot。
- 不完整的第三方 recovery 下载不参与任何回滚或刷入。
