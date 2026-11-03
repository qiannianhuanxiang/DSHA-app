# DSHA 0.2.1-rc.3-bxroot.9-dsh0.2.1-rc.3 / build211

- 新增原生能力命令：Ubuntu 中可直接使用 Termux:API 風格的 `termux-clipboard-get/set`、`termux-notification`、`termux-toast`、`termux-vibrate`、`termux-torch`、`termux-open-url`、`termux-share`、`termux-location`、`termux-sensor`、`termux-battery-status`、`termux-dialog`；`dsha-am start/broadcast` 以 DSHA 自身身分啟動畫面或發送廣播（不需要 root，依螢幕確認策略詢問）；`dsha-su` 在 設定 → 裝置能力授權 開啟「不受限制的裝置 shell」後，經已授權的 root 或 Shizuku 執行任意命令（預設關閉，分割區/SELinux/掛載/刷機仍禁止，ADB 通道仍依白名單）。
- - 新增類 Termux 的原生呼叫（bxroot 引擎）：Ubuntu 中可直接執行 Android 原生指令（`getprop`、`toybox`、`pm` 等，guest 內同名程式優先，亦可明確使用 `bx-host <指令>`）；Android 原生 shell 可經 `$BXROOT_ENTER` 回到同一個 Ubuntu 工作階段。身分仍為本 App 的 UID，不提權；可用哪些系統指令由 Android 權限決定。
- - 修復 HyperOS 等系統上 1×1 的系統字形/角標窗（HyperDuo glyph、筆記角標等）觸發 TARGET_WINDOW_OCCLUDED、導致座標點擊與滑動完全不可用的問題：面積極小的窗戶不再參與遮擋判定（#212）。
- - 結束應用被「應用清單格式無法確認」攔截時，錯誤訊息附帶首條意外行（去敏、截斷）並區分使用者/系統清單，便於定位 ROM 的 pm 輸出格式差異（#210 附帶）。
- - 保留 dsh-web-mobile 3.2.2、DSH rc.3、本機與區域網路存取及十種語言；手機驗收繼續暫停。
