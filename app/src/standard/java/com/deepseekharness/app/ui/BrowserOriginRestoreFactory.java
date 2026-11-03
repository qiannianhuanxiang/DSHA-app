package com.deepseekharness.app.ui;

import android.content.Context;

final class BrowserOriginRestoreFactory {
  static boolean supportsGecko() {
    return false;
  }

  static BrowserOriginRestoreActivity.Surface create(Context context, boolean gecko) {
    if (gecko) throw new IllegalArgumentException("RESTORE_GECKO_NOT_PACKAGED");
    return new BrowserOriginRestoreWebView(context);
  }
}
