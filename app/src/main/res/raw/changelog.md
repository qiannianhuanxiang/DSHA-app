# DSHA 0.2.1-rc.3-bxroot.16-dsh0.2.1-rc.3 / build218

- 修复屏幕操作 `begin` 永久失败（`ASSISTANT_EXPANDED` 死锁）：展开助手面板时不再暂停屏幕操作。该面板窗口本身不可聚焦、也不会抢输入法焦点，所以这道围栏是多余的；它反而造成死锁——展开后 `expanded` 为真，读屏因 `ASSISTANT_LOCAL_RESUME_REQUIRED` 无法进行，窗口被移除后点不到收起，暂停永远无法释放。
- 同时移除了悬浮窗几何发布时的第二处同源暂停（`ASSISTANT_PANEL_EXPANDED`），以及窗口销毁时残留的展开标志。
- 编辑器与语音仍各自独立暂停屏幕操作（`ASSISTANT_IME`、`ASSISTANT_VOICE`），这两条路径不受影响。
- 从旧版本升级上来不会卡住：`collapse()` 仍会释放遗留的展开类暂停。
- 其余与 build214 相同：bxroot 运行时、Termux 路径兼容、启动与解压提速、原生调用命令。
- 本轮未在真机验证。若仍返回 `SCREEN_CONTROL_PAUSED`，`adb logcat -s DSHA` 会打出 `checkBegin refused: paused reason=...`。
