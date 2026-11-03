# DSHA 0.2.1-rc.3-bxroot.12-dsh0.2.1-rc.3 / build214

- Termux 路徑相容（bxroot 引擎）：專為 Termux 撰寫的腳本與設定中寫死的路徑會自動對應到 Ubuntu，`/data/data/com.termux/files/usr` → `/usr`（其下 `tmp`/`etc`/`var` → `/tmp`/`/etc`/`/var`），`/data/data/com.termux/files/home` → `/root`，`/data/user/0/...` 寫法同樣生效；shebang `#!/data/data/com.termux/files/usr/bin/bash` 因此可用。不設定 `PREFIX`/`TERMUX_VERSION`，`pkg`、Termux 的 bionic 程式與 Termux:X11 等配套 App 仍不支援；可設 `BXROOT_NO_TERMUX_COMPAT=1` 關閉。
- 啟動再提速：沒有舊版待審閱標記時不再啟動「遷移舊審閱標記」的容器行程（約 0.5 秒）；DSH 啟用 Node 模組編譯快取（`/root/.cache/dsha-node-compile`，原始檔變化自動失效，可設 `NODE_DISABLE_COMPILE_CACHE=1` 關閉），減少每次啟動匯入約 180 個套件的解析編譯時間。
- 首次解壓提速：全新環境解壓不再逐個檔案強制寫入磁碟（約 8 萬次 fsync），改為收尾時整體同步一次；僅在 Linux 5.8 及以上核心啟用，寫入錯誤仍會被發現，中途斷電時候選環境作廢重解，不影響已發佈環境。
- 啟動提速：終端/Web 的執行準備結果跨 App 重啟快取；APK、環境與受管檔案均未變化時，冷啟動後首次開啟終端不再重新逐位元組比對 103 項隨包檔案與補丁鏈，任一受管檔案被改動即自動重新準備。
- 新增原生能力命令：Ubuntu 中可直接使用 Termux:API 風格的 `termux-clipboard-get/set`、`termux-notification`、`termux-toast`、`termux-vibrate`、`termux-torch`、`termux-open-url`、`termux-share`、`termux-location`、`termux-sensor`、`termux-battery-status`、`termux-dialog`；`dsha-am start/broadcast` 以 DSHA 自身身分啟動畫面或發送廣播（不需要 root，依螢幕確認策略詢問）；`dsha-su` 在 設定 → 裝置能力授權 開啟「不受限制的裝置 shell」後，經已授權的 root 或 Shizuku 執行任意命令（預設關閉，分割區/SELinux/掛載/刷機仍禁止，ADB 通道仍依白名單）。
- - 新增類 Termux 的原生呼叫（bxroot 引擎）：Ubuntu 中可直接執行 Android 原生指令（`getprop`、`toybox`、`pm` 等，guest 內同名程式優先，亦可明確使用 `bx-host <指令>`）；Android 原生 shell 可經 `$BXROOT_ENTER` 回到同一個 Ubuntu 工作階段。身分仍為本 App 的 UID，不提權；可用哪些系統指令由 Android 權限決定。
- - 修復 HyperOS 等系統上 1×1 的系統字形/角標窗（HyperDuo glyph、筆記角標等）觸發 TARGET_WINDOW_OCCLUDED、導致座標點擊與滑動完全不可用的問題：面積極小的窗戶不再參與遮擋判定（#212）。
- - 結束應用被「應用清單格式無法確認」攔截時，錯誤訊息附帶首條意外行（去敏、截斷）並區分使用者/系統清單，便於定位 ROM 的 pm 輸出格式差異（#210 附帶）。
- - 保留 dsh-web-mobile 3.2.2、DSH rc.3、本機與區域網路存取及十種語言；手機驗收繼續暫停。
