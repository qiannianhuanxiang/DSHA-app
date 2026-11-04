package com.deepseekharness.app.ui;

/** Controlled host input for real WebFullscreenUi; no AndroidX or window API is substituted. */
public class PictureInPictureActivity extends android.app.Activity implements WebFullscreenUi.Host {
  public boolean picture;
  private int theme = 0xffffffff;

  public static boolean showing(android.app.Activity activity) {
    return activity instanceof PictureInPictureActivity host && host.picture;
  }

  public int browserThemeColor() {
    return theme;
  }

  public void applyBrowserTheme(int color) {
    theme = color;
  }
}
