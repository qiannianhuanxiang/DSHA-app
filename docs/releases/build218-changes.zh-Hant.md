# DSHA 0.2.1-rc.3-bxroot.16-dsh0.2.1-rc.3 / build218

- 修復螢幕操作 `begin` 永久失敗（`ASSISTANT_EXPANDED` 死鎖）：展開助手面板時不再暫停螢幕操作。該面板視窗本身不可聚焦、也不會搶輸入法焦點，所以這道圍欄是多餘的；它反而造成死鎖——展開後 `expanded` 為真，讀屏因 `ASSISTANT_LOCAL_RESUME_REQUIRED` 無法進行，視窗被移除後點不到收合，暫停永遠無法釋放。
- 同時移除了懸浮窗幾何發佈時的第二處同源暫停（`ASSISTANT_PANEL_EXPANDED`），以及視窗銷毀時殘留的展開旗標。
- 編輯器與語音仍各自獨立暫停螢幕操作（`ASSISTANT_IME`、`ASSISTANT_VOICE`），這兩條路徑不受影響。
- 從舊版本升級上來不會卡住：`collapse()` 仍會釋放遺留的展開類暫停。
- 其餘與 build214 相同：bxroot 執行環境、Termux 路徑相容、啟動與解壓加速、原生呼叫命令。
- 本輪未在實機驗證。若仍回傳 `SCREEN_CONTROL_PAUSED`，`adb logcat -s DSHA` 會印出 `checkBegin refused: paused reason=...`。
