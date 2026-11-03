# DSHA 0.2.1-rc.3-bxroot.12-dsh0.2.1-rc.3 / build214

- Termux 路径兼容（bxroot 引擎）：专为 Termux 写的脚本和配置里写死的路径会自动对到 Ubuntu 上，`/data/data/com.termux/files/usr` → `/usr`（其下 `tmp`/`etc`/`var` → `/tmp`/`/etc`/`/var`），`/data/data/com.termux/files/home` → `/root`，`/data/user/0/...` 写法同样生效；shebang `#!/data/data/com.termux/files/usr/bin/bash` 因此可用。不设 `PREFIX`/`TERMUX_VERSION`，`pkg`、Termux 的 bionic 程序与 Termux:X11 等配套 App 仍不支持；可设 `BXROOT_NO_TERMUX_COMPAT=1` 关闭。
- 启动再提速：没有旧版待审阅标记时不再启动「迁移旧审阅标记」的容器进程（约 0.5 秒）；DSH 启用 Node 模块编译缓存（`/root/.cache/dsha-node-compile`，源文件变化自动失效，可设 `NODE_DISABLE_COMPILE_CACHE=1` 关闭），减少每次启动导入约 180 个包的解析编译时间。
- 首次解压提速：全新环境解压不再逐个文件强制落盘（约 8 万次 fsync），改为收尾时整盘同步一次；仅在 Linux 5.8 及以上内核启用，写入错误仍会被发现，中途断电时候选环境作废重解，不影响已发布环境。
- 启动提速：终端/Web 的运行准备结果跨 App 重启缓存；APK、环境与受管文件均未变化时，冷启动后首次打开终端不再重新逐字节比对 103 项随包文件和补丁链，任一受管文件被改动即自动重新准备。
- 新增原生能力命令：Ubuntu 里可直接用 Termux:API 风格的 `termux-clipboard-get/set`、`termux-notification`、`termux-toast`、`termux-vibrate`、`termux-torch`、`termux-open-url`、`termux-share`、`termux-location`、`termux-sensor`、`termux-battery-status`、`termux-dialog`；`dsha-am start/broadcast` 以 DSHA 自身身份启动界面或发广播（不需要 root，按屏幕确认策略询问）；`dsha-su` 在设置 → 设备能力授权中开启“不受限制的设备 shell”后，经已授权的 root 或 Shizuku 执行任意命令（默认关闭，分区/SELinux/挂载/刷机仍禁止，ADB 通道仍按白名单）。
- - 新增类 Termux 的原生调用（bxroot 引擎）：Ubuntu 里可直接运行 Android 原生命令（`getprop`、`toybox`、`pm` 等，guest 内同名程序优先，也可显式用 `bx-host <命令>`）；Android 原生 shell 可经 `$BXROOT_ENTER` 回到同一个 Ubuntu 会话。身份仍是本 App 的 UID，不提权；能用哪些系统命令由 Android 权限决定。
- - 修复 HyperOS 等系统上 1×1 的系统字形/角标窗（HyperDuo glyph、笔记角标等）触发 TARGET_WINDOW_OCCLUDED、导致坐标点击与滑动完全不可用的问题：面积极小的窗口不再参与遮挡判定（#212）。
- - 结束应用被「应用清单格式无法确认」拦截时，错误信息附带首条意外行（脱敏、截断）并区分用户/系统清单，便于定位 ROM 的 pm 输出格式差异（#210 附带）。
- - 保留 dsh-web-mobile 3.2.2、DSH rc.3、本机和局域网访问及十种语言；手机验收继续暂停。
