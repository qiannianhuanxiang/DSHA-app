# DSHA 0.2.1-rc.3-bxroot.10-dsh0.2.1-rc.3 / build212

- 初回展開を高速化：新しい環境の展開でファイルごとの強制書き込み（約 8 万回の fsync）をやめ、最後に一度だけ同期します。Linux 5.8 以降のカーネルでのみ有効で、書き込みエラーは引き続き検出され、途中で電源が切れた場合は候補を破棄して再展開し、公開済みの環境には影響しません。
- 起動を高速化：ターミナル/Web の実行準備結果をアプリ再起動後も保持します。APK・環境・管理ファイルが変わっていなければ、コールドスタート後の最初のターミナルで 103 個の同梱ファイルのバイト比較やパッチチェーンを再実行しません。管理ファイルが一つでも変わると自動で再準備します。
- ネイティブ機能コマンドを追加：Ubuntu で Termux:API 風の `termux-clipboard-get/set`、`termux-notification`、`termux-toast`、`termux-vibrate`、`termux-torch`、`termux-open-url`、`termux-share`、`termux-location`、`termux-sensor`、`termux-battery-status`、`termux-dialog` をそのまま使えます。`dsha-am start/broadcast` は DSHA 自身として画面起動やブロードキャストを行います（root 不要、画面確認ポリシーに従って確認）。`dsha-su` は 設定 → デバイス権限 で「制限なしのデバイスシェル」を有効にすると、許可済みの root または Shizuku で任意のコマンドを実行します（既定はオフ。パーティション/SELinux/マウント/フラッシュは引き続き禁止、ADB は許可リストのまま）。
- - Termux のようなネイティブ呼び出しを追加（bxroot エンジン）：Ubuntu から Android ネイティブコマンド（`getprop`、`toybox`、`pm` など）を直接実行可能（同名の guest プログラムが優先、`bx-host <コマンド>` で明示指定も可）。Android ネイティブ shell からは `$BXROOT_ENTER` で同じ Ubuntu セッションに戻れます。UID は本アプリのままで権限昇格はなく、使えるシステムコマンドは Android の権限で決まります。
- - HyperOS 等で 1×1 のシステムグリフ/バッジ窓（HyperDuo glyph、ノート角標など）が TARGET_WINDOW_OCCLUDED を引き起こし、座標タップとスワイプが完全に使えなくなる問題を修正しました。無視できるほど小さな窓は遮蔽判定に参加しません（#212）。
- - 「アプリ一覧の形式を確認できません」でアプリ停止が阻まれた際、エラーに最初の想定外行（マスキング・切り詰め済み）を添え、ユーザー/システム一覧を区別して表示します。ROM 固有の pm 出力差異の特定に役立ちます（#210 関連）。
- - dsh-web-mobile 3.2.2、DSH rc.3、ローカル・LAN 接続と10言語を維持します。実機検証は引き続き一時停止中です。
