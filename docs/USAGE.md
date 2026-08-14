# R0DUMP 使用说明

## 默认方式

1. 在 Manager 中明确选择一个 App。
2. 进程范围保持 `main_only`。
3. 使用默认策略组合 `0x87`。
4. 保持全局、force backfill、raw dexdata mirror 和高频 JIT/instrumentation 关闭。
5. 打开 `stop_after_complete`，然后点击开始。

`0x87` 包含 Class walk、Application create、Activity create 和 InMemoryDex。
`DEFINE_CLASS`（位 `0x100`）是性能敏感的高级选项，默认关闭；它只建议在确认
样本需要时单独开启。

## 全局与未选应用

未选应用不会自动变成全局。全局 runtime 有独立明确开关，默认为关闭。
全局模式会让多个 App 进程进入 ART hook，资源开销和输出量都更高。

## 为什么有多个文件夹

唯一输出结构是：

```text
/sdcard/Download/R0DUMP/<package>/<run_id>/<process>/
```

ART 不接受自定义输出根，也不使用备用目录。Download 无法创建或写入时，
当前运行会停止并记录 `download_output_unavailable`，不会把文件写到
`Android/data`、应用私有目录或 `/data/local/tmp`。

多个目录通常来自：

- 多次开始产生的不同 `run_id`。
- App 自身有远程 service/webview 等多进程。
- 历史测试输出没有清理。
- DEX 容器、method records、`status.json` 和修复暂存是不同用途的文件。

它不能单独证明全局开关被打开。

## 状态判定

不要用“DEX 文件已出现”判定整个任务已完成。以 `status.json` 的 `phase`
为准：

- `configured`：配置已进入目标进程。
- `waiting_delay`：等待设定的延迟。
- `class_walk`：主动遍历进行中。
- `complete`：完成并已收敛。
- `stopped`：已停止；结合 `stop_reason` 区分手动停止、超时或进程退出。
- `stopped_by_limit`：达到记录或时间上限。
- `class_walk_failed`：遍历失败，当前运行已停止；查看 `stop_reason` 和 logcat。

修复后的 Manager 不再把已进入终态的批次回退成 `dumping`，也不再因
`onResume()` 竞态把新 `run_id` 覆盖为 `pending`。

## DEX 041 与 CompactDex

Android 16 正常应用路径为标准 DEX 或 DEX 041 共享容器。Manager 能保留
DEX 041 的 entry 边界，应用 method records 后重算 SHA-1 和 Adler32。
App Cloner 最终回归中 `nonstandard_dex_methods_skipped=804`；该计数表示 ART 遇到了
不适合安全回填的非标准方法形态，不等于 7 个输出文件都是 CompactDex。
实际修复产物中 5/5 DEX 均通过 `dexdump`。完整 CompactDex 转换仍未实现，
但它不阻塞当前 Android 16 / App Cloner 闭环。

## 资源上限

默认启动后延迟为 10 秒；默认上限为 50,000 条记录和 300 秒。修复扫描同时限制目录深度、文件数、
总字节、单文件大小和符号链接。先用默认值跑通，再根据具体样本增加范围。

方法记录文件在一次运行中保持打开并按批次刷盘，减少 Redmi 9A eMMC 上反复
open/close 带来的长时间卡顿。异步开关当前显示为 `disabled`；显式请求异步时，
状态会标为 `synchronous_fallback`，不会伪装成真正的后台队列。
