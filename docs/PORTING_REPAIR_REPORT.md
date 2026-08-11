# R0DUMP crDroid 16 / Redmi 9A 完整移植修复报告

日期：2026-08-12

> 本页同时保留早期完整 ROM 闭环记录。2026-08-12 追加的 Manager 启动交接修复
> 已完成受限内存模块构建、真机安装和 App Cloner 回归；随后生成了只改
> `system_ext` 的整包修复 OTA。修复 OTA 已通过整包签名、ZIP 和分区数据校验，
> 但尚未再次 sideload；当前手机仍使用已验证的 `/data/app` 临时覆盖。

## 当前进度（2026-08-12）

- **手机：已开机并可用**，`sys.boot_completed=1`，不是 recovery 卡死状态。
- **本次 Manager 修复：已验证**。App Cloner 可进入 `MainActivity`，7 个 DEX
  已导出，`reconstruction_failures=0`；结束状态为 `stopped/class_walk_timeout`，
  这是达到 class-walk 时间上限后的正常收口，不是“仍在运行”。
- **系统分区固化：已生成包，尚未刷入**。修复包只替换 `system_ext.new.dat.br`，
  保持原 `system_ext.transfer.list`、其它分区、动态分区布局和 OTA 元数据不变。
- **剩余工作：**选择是否把修复 OTA sideload 到手机；刷入后再做一次开机和
  Manager/App Cloner 快速复核。当前没有后台编译任务。
- **耗时原因：**完整 Soong 图有约 2,848 个脏任务，主机曾发生全局 OOM；本次
  改用已完成的基包和单分区块 OTA，避免再次整包编译。

## 追加修复记录（2026-08-11 晚间）

上一版完整 ROM 的默认掩码把 `DEFINE_CLASS`（`0x100`）与安全路径一起打开。
ART 在 class-loading 主线程内同步写 DEX/method 文件，Redmi 9A 上会表现为选中的
应用黑屏或长时间无响应；进程被系统杀掉后，旧 `status.json` 又会永久停在
`phase=dumping`。这与“昨天第一个能进系统的包”不同：那个包的导出路径尚未完整
生效，所以能进入应用，但 dump/repair 结果不完整。

本次修复：

- 默认策略改为 `0x87`，`DEFINE_CLASS` 仅高级手动选项；旧 `0x187` 首次启动自动迁移。
- method JSONL 文件一次运行只打开一次，每 128 条记录刷盘，避免 eMMC 上逐条
  open/close。
- class-walk 使用单线程、10 秒默认延迟和最多 300 秒硬截止；异常、超时和进程
  退出都会收口为终态并记录原因。
- Manager 能识别死进程留下的活动状态，显示为 `stopped/process_exit`，并关闭全局开关。
- 状态中的异步字段不再硬编码；未请求时为 `disabled`，请求但没有安全队列时才为
  `synchronous_fallback`。
- 安装前复核发现一次历史分区导出经过 ADB 伪终端，二进制 `0A` 被转换为
  `0D 0A`。6 个镜像已通过可逆流式变换恢复到标准分区大小；恢复文件重新执行
  正向变换后与原始污染文件逐字节一致，boot/recovery 可拆包，AVB 结构可解析。
- 新增 `scripts/backup-partitions.sh`，强制使用 `adb exec-out`，并用设备块大小
  对每个镜像做精确字节数校验；模拟测试覆盖 LF/CRLF 原样传输、额外字节拒绝和
  失败后 `.partial` 清理。

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
| 基线 OTA | 已成功构建，`unzip -t` 通过，VINTF `COMPATIBLE` |
| Manager 修复 OTA | 已生成，整包签名、`unzip -t` 和 `system_ext` 逐字节校验通过；尚未刷入 |
| 真机启动 | 通过，`sys.boot_completed=1` |
| 真机 DUMP | 计算器基线闭环通过；App Cloner 回归导出 7 个 DEX/DEX 041 容器 |
| 自动终止 | 计算器为 `complete`；App Cloner 为 `stopped/class_walk_timeout`，均已关闭运行时 |
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
`0x87`：Class walk、Application create、Activity create 和 InMemoryDex；DefineClass
仅作为高级选项单独验证。

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
- 未请求异步时状态明确报告 `disabled`；显式请求异步时才报告
  `synchronous_fallback`。ART 指针仍在回调内复制，方法记录文件由持久 fd 批量刷盘。

## 4. 已修复问题

### 4.1 一直显示“正在 DUMP”

原因有两层：

1. ART 后到的热点回调可能把终态重新覆盖为 `dumping`。
2. Manager 自动化启动时，`onResume()` 与后台线程生成 `run_id` 存在竞态，新 ID
   会被旧值 `pending` 覆盖，导致 Manager 不认为终态属于当前批次。

修复后：

- 阶段明确为 `configured`、`waiting_delay`、`class_walk`、`complete`、
  `stopped`、`stopped_by_limit` 和 `class_walk_failed`；失败状态会主动停止运行。
- 进入终态后，后到回调不得使阶段回退。
- 新批次 ID 作为不可变参数提交，不再依赖可被 `onResume()` 覆盖的共享字段。
- 真机 UI 流程中，20 秒上限到达后写入 `complete`，Manager 自动扫描并把
  `r0dump.dump.enabled` 从 1 清为 0。

### 4.1.1 App Cloner 启动交接回归

选择 `com.applisto.appcloner` 时，Manager 原先在发出第一个启动请求后立即
`finish()`；该应用会延迟把控制权交给 `MainActivity`，于是 Android 的后台启动
限制可能让第二次启动被拒绝。修复包括：

- 启动前预创建目标 App 的外部输出根目录；
- 自动化启动完成后保留 20 秒 hand-off grace，再退出 Manager；
- 目录创建失败写入明确日志，不把失败伪装成 dump 成功。

2026-08-12 真机结果：App Cloner 正常显示 `MainActivity`，没有出现
`Background activity launch blocked`；批次 `1786478797209-70f476c2` 写出 7 个
DEX、2 条 method records，`reconstruction_failures=0`。`status.json` 最终为
`phase=stopped`、`stop_reason=class_walk_timeout`、`runtime_enabled=false`。
622 个非标准 DEX method 被按设计跳过，属于样本内容统计，不是重建失败。

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
- 构建前清空并检查专用 `out/r0dump-tmp` 的文件数、空间和 inode，同时检查
  内存和 swap；OTA 临时文件不再写入 16 GiB `/tmp`。
- 完整 OTA 参数为普通并行 4、highmem pool 2，`MemoryHigh=20G`、
  `MemoryMax=24G`、`MemorySwapMax=0G`。普通 dex/R8/D8 单进程堆为 `2048M`，
  `Launcher3QuickStep` 单独为 `3072M`；没有使用全局 `-j2`。

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

### 6.1 计算器基线闭环

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

### 6.2 App Cloner 回归

目标：`com.applisto.appcloner`，默认 `0x87` 策略、`main_only`，全局和高风险
策略关闭。批次目录为：

`1786478797209-70f476c2/com.applisto.appcloner`

- `MainActivity` 已显示，启动交接没有被系统拒绝；
- `dex_files_written=7`，`method_records_written=2`；
- `reconstruction_failures=0`，`fixed_dex_files_written=0`（本次只做 DUMP 回归）；
- `nonstandard_dex_methods_skipped=622`，说明样本包含非标准方法形态，当前实现
  会记录并跳过，不能把它误读成 CompactDex 全量转换已完成；
- 最终 `phase=stopped`、`stop_reason=class_walk_timeout`、`runtime_enabled=false`。

回归结束后已关闭 `r0dump.dump.enabled` 和 `r0dump.dump.global_runtime_enabled`，
并 force-stop 目标 App。

## 7. 最终构建与产物

源码根目录：`<android-source>/crdroid-16`

最终 `bacon` 构建成功退出。普通任务保持 `-j4`，D8/R8 highmem pool 深度为 2；
完整编译和 OTA 封装均在 cgroup 内运行，观测峰值 19.2 GiB，`oom=0`、
`oom_kill=0`。OTA 封装使用工作盘专用临时目录，未占满 `/tmp`。

| 产物 | 大小（字节） | SHA-256 |
| --- | ---: | --- |
| `crDroidAndroid-16.0-20260811-blossom-v12.11.zip` | 1,407,740,513 | `25010572c73afd87ae85d8ee1aec0d4bb718aa239b0c34f6405239978cd9157f` |
| `boot.img` | 67,108,864 | `5f8659835b313f95af1b35b356776fa304a3d12a92ee614be2f7f8fb0b63d8bd` |
| `recovery.img` | 67,108,864 | `efa079b132b6def5b9b239f3cc1629b602e4ff3fb23deef6e63a328db5adaa3f` |
| `system.img` | 1,487,642,624 | `fe012916c0fc92a69d34be932b05183b74a0ae488bb19916e06c4a085bd348d5` |
| `system_ext.img` | 1,076,817,920 | `e52aab2f94a06e1d5392ef3a59b2f4174bcc374f1c77fa84bfffe54797828ad0` |
| `product.img` | 730,292,224 | `5e785cb71968c6cbc40d138f206e5e79d9f0d53f6639020f0ea7451ab911b0db` |
| `odm.img` | 1,593,344 | `aee4b4832386fc190d7fe2053c7f90abb5e4a322bdb4457e62c1d94ff23cf3b5` |
| `vendor.img` | 577,495,040 | `1397ca93c24432140d969c68aed4c1513fbe7e1d88204a0259f04d3e1f895148` |
| `vbmeta.img` | 65,536 | `42a80d539c771fbdbd34f21eb8ac7cbdb9c0b2423aa8550b28a529530c70fc1f` |
| `vbmeta_system.img` | 65,536 | `23ec72aac5791b81dff6c63c2f373931dd8c91ae4fde748056086f122e17cc95` |
| `vbmeta_vendor.img` | 65,536 | `d3bb899b5dc0b7e23f48039e95a00e49f0adf9cf315e3d5fdcdcce70dd45f01f` |
| `R0DUMPManager.apk` | 50,700,656 | `eb5181f1c141892894da66ffccf669f886d4a8c3674a7fa823f23d3043acbf48` |

### 7.1 本次 Manager 修复产物

以下产物来自已验证的基线 OTA：只在原始 `system_ext` raw image 中替换
`priv-app/R0DUMPManager/R0DUMPManager.apk`，再重新压缩 `system_ext.new.dat.br`
并对整包做 whole-file 签名。没有重新生成其它分区。

| 产物 | 大小（字节） | SHA-256 |
| --- | ---: | --- |
| `R0DUMPManager.apk`（修复后） | 50,704,752 | `d0b6c4eecca66fff06983bfa9a40492d1aae493bb7c6029897ecfdf16fe170f5` |
| `system_ext` raw（修复后） | 1,076,817,920 | `88af07d512613e0ae275787934b1bcb528a8d3b3caa2d21a8ee210eba2ef611a` |
| `crDroidAndroid-16.0-20260812-blossom-v12.11-manager-fix.zip` | 1,407,734,167 | `eafe15270863cc61abb55f2c6b20188cd9aee501344830dc20e6646992846eef` |

修复包签名由 `check_ota_package_signature` 验证通过，包内分区数据解压后与
`system_ext` raw SHA-256 相同。该 ZIP 位于源码仓库之外，未提交二进制或密钥。

关键中间产物：

| 产物 | 大小（字节） | SHA-256 |
| --- | ---: | --- |
| `framework.jar` | 43,632,410 | `8351a2dfe8f86f2ee07db0b1a4019ab06d266ab8653045513586447341cb7160` |
| `core-libart.jar` | 599,149 | `37ed70aa58f46a39644f5fdab7e1f1ed7ffe683880d3f6b3df3e6b9f3f5f99a7` |
| ARM64 `libart.so` | 16,477,624 | `598168455500ff0d0e2abc5a9b9f7f231fc203441b7b26667be4856e8857db5f` |
| ARM32 `libart.so` | 10,478,152 | `bd69f7d20d6bbd1eda625c9b0828b2a6577d684b457842f393cb636c739e67a1` |

## 8. 已执行验证

- 基线完整系统构建和基线 OTA 构建成功；本次 Manager 修复 APK 在受限 cgroup
  中构建成功。
- 基线 OTA 与 Manager 修复 OTA 均通过 `unzip -t`；修复 OTA 另通过 whole-file
  签名验证。
- target-files VINTF 最终结果为 `COMPATIBLE`。
- Boot Classpath protobuf 包含 telephony compat 和全部 7 个 MTK framework JAR。
- Thermal service 和 32/64 位 impl 的 ELF `NEEDED` 均指向 v32 兼容库。
- 手机活动 `/data/app` Manager APK 与本次修复 APK SHA-256 完全相同：
  `d0b6c4eecca66fff06983bfa9a40492d1aae493bb7c6029897ecfdf16fe170f5`。
- 系统分区内仍是基线 APK：`eb5181f1c141892894da66ffccf669f886d4a8c3674a7fa823f23d3043acbf48`；
  这正是待 sideload 的 system_ext-only 修复包要固化的内容。
- Manager 所需 `WRITE_SECURE_SETTINGS`、`READ_LOGS`、`MANAGE_EXTERNAL_STORAGE`、
  `FORCE_STOP_PACKAGES` 等权限已授予。
- IMS/Thermal 先连续观察 2 分钟，后续在 DUMP/模块构建期间 PID 仍未变化。
- crash buffer 为空，普通 logcat 中无 `NoClassDefFoundError`、
  `ClassNotFoundException`、`VerifyError`、`FATAL EXCEPTION` 或 Thermal fatal signal。
- 各已修改 Git 子仓的 `git diff --check` 通过。
- 构建脚本 `bash -n` 和 sepolicy `policy.py` Python 编译检查通过。

## 9. 当前手机状态

- 系统已开机，ADB 连接正常，`sys.boot_completed=1`。
- 当前活动 Manager 为 `/data/app` 中的同签名系统应用更新，内容与本次修复 APK
  相同；系统分区仍保留旧 APK。恢复出厂或清除该更新后会回到旧版本，直到修复
  OTA 被刷入。
- 当前运行时、IMS/Thermal 和其它分区来自已验证基线 OTA；修复 OTA 生成后没有
  再次 sideload。
- 当前设置为 `r0dump.dump.enabled=0`、`r0dump.dump.global_runtime_enabled=0`，
  无后台 R0DUMP 任务。
- 当前 `su` 不存在，即当前系统没有激活 root。
- 验证时的 patched boot 仅保存在本地私有备份，未放入公开仓库。
- 最终 OTA 包含未 root 的 `boot.img`；安装 OTA 会覆盖 root boot，需在 OTA 之后
  重新修补对应的新 boot。
- 当前 recovery 是 AOSP/crDroid recovery。本地曾有一份不完整的第三方
  recovery 下载，从未刷入，也未放入公开仓库。

## 10. 已知限制与剩余风险

1. **修复 OTA 尚未再次安装**：修复 ZIP 已通过离线完整性、whole-file 签名和
   `system_ext` 逐字节校验；手机目前靠 `/data/app` 覆盖运行。刷入修复 ZIP 后
   仍需做一次不清数据的开机复核，确认系统分区中的 Manager 版本。
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
7. **真正的异步队列仍未启用**：默认状态为 `disabled`；显式请求异步时使用
   `synchronous_fallback`，但方法记录已改为持久 fd 和批量刷盘，避免逐条 open/close。
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

- 刷机前备份仅保存在本地私有存储，未放入公开仓库。前一天的干净备份约
  161 MiB，包含 boot、recovery、dtbo、三级 vbmeta、分区哈希、`getprop` 和
  `lpdump`。
- 最后一次刷入前备份曾受 ADB 伪终端 CRLF 转换污染。原始目录已明确标为禁止
  刷入，恢复副本独立保存，并通过 6/6 SHA-256、标准分区大小、逐字节往返、
  `unpack_bootimg` 和 AVB 结构检查。
- 当前 patched boot 的内嵌 hash descriptor 与载荷不一致，这是 root 修补后的
  既有状态；顶层 vbmeta flags 为 3。回滚时应使用同一套恢复后的 boot/dtbo/vbmeta，
  不混用其他构建的 AVB 元数据。
- 最终 OTA 会覆盖 boot、dtbo 和三级 vbmeta，刷入前应保留该备份和与当前
  版本对应的 patched boot。
- 不完整的第三方 recovery 下载不参与任何回滚或刷入。
