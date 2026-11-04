# DSHA 0.2.1-rc.3-bxroot.8-dsh0.2.1-rc.3 / build210

- - 新增类 Termux 的原生调用（bxroot 引擎）：Ubuntu 里可直接运行 Android 原生命令（`getprop`、`toybox`、`pm` 等，guest 内同名程序优先，也可显式用 `bx-host <命令>`）；Android 原生 shell 可经 `$BXROOT_ENTER` 回到同一个 Ubuntu 会话。身份仍是本 App 的 UID，不提权；能用哪些系统命令由 Android 权限决定。
- - 修复 HyperOS 等系统上 1×1 的系统字形/角标窗（HyperDuo glyph、笔记角标等）触发 TARGET_WINDOW_OCCLUDED、导致坐标点击与滑动完全不可用的问题：面积极小的窗口不再参与遮挡判定（#212）。
- - 结束应用被「应用清单格式无法确认」拦截时，错误信息附带首条意外行（脱敏、截断）并区分用户/系统清单，便于定位 ROM 的 pm 输出格式差异（#210 附带）。
- - 保留 dsh-web-mobile 3.2.2、DSH rc.3、本机和局域网访问及十种语言；手机验收继续暂停。
