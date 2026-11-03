# DSHA 0.2.1-rc.3-bxroot.9-dsh0.2.1-rc.3 / build211

- 新增原生能力命令：Ubuntu 里可直接用 Termux:API 风格的 `termux-clipboard-get/set`、`termux-notification`、`termux-toast`、`termux-vibrate`、`termux-torch`、`termux-open-url`、`termux-share`、`termux-location`、`termux-sensor`、`termux-battery-status`、`termux-dialog`；`dsha-am start/broadcast` 以 DSHA 自身身份启动界面或发广播（不需要 root，按屏幕确认策略询问）；`dsha-su` 在设置 → 设备能力授权中开启“不受限制的设备 shell”后，经已授权的 root 或 Shizuku 执行任意命令（默认关闭，分区/SELinux/挂载/刷机仍禁止，ADB 通道仍按白名单）。
- - 新增类 Termux 的原生调用（bxroot 引擎）：Ubuntu 里可直接运行 Android 原生命令（`getprop`、`toybox`、`pm` 等，guest 内同名程序优先，也可显式用 `bx-host <命令>`）；Android 原生 shell 可经 `$BXROOT_ENTER` 回到同一个 Ubuntu 会话。身份仍是本 App 的 UID，不提权；能用哪些系统命令由 Android 权限决定。
- - 修复 HyperOS 等系统上 1×1 的系统字形/角标窗（HyperDuo glyph、笔记角标等）触发 TARGET_WINDOW_OCCLUDED、导致坐标点击与滑动完全不可用的问题：面积极小的窗口不再参与遮挡判定（#212）。
- - 结束应用被「应用清单格式无法确认」拦截时，错误信息附带首条意外行（脱敏、截断）并区分用户/系统清单，便于定位 ROM 的 pm 输出格式差异（#210 附带）。
- - 保留 dsh-web-mobile 3.2.2、DSH rc.3、本机和局域网访问及十种语言；手机验收继续暂停。
