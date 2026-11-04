# DSHA 0.2.1-rc.3-bxroot.16-dsh0.2.1-rc.3 / build218

- 画面操作 `begin` の恒久的な失敗（`ASSISTANT_EXPANDED` デッドロック）を修正: アシスタントパネルを展開しても画面操作を一時停止しなくなりました。このパネルウィンドウはフォーカス不可で IME の対象にもならないため、このフェンスは不要でした。むしろデッドロックを招いていました — 展開で `expanded` が立ち、画面読み取りが `ASSISTANT_LOCAL_RESUME_REQUIRED` で失敗し、ウィンドウが外れると折りたたみもできず、一時停止が永久に解除されませんでした。
- オーバーレイのジオメトリ公開時に発生する同源の 2 つ目の一時停止（`ASSISTANT_PANEL_EXPANDED`）と、ウィンドウ破棄時に残る展開フラグも削除しました。
- エディタと音声は従来どおり独自に一時停止します（`ASSISTANT_IME`、`ASSISTANT_VOICE`）。これらは影響を受けません。
- 旧ビルドからの更新で詰まることはありません。`collapse()` は従来の展開由来の一時停止を引き続き解除します。
- それ以外は build214 と同じ: bxroot ランタイム、Termux パス互換、起動と展開の高速化、ネイティブ呼び出しコマンド。
- 今回は実機未検証です。`SCREEN_CONTROL_PAUSED` がまだ出る場合、`adb logcat -s DSHA` に `checkBegin refused: paused reason=...` が出ます。
