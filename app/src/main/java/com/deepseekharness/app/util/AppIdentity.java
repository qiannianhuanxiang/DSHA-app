package com.deepseekharness.app.util;

/** 独立 Bxroot 二改包身份；不与正式版共享桥端口或 Android 动作命名空间。 */
public final class AppIdentity {
  private AppIdentity() {}

  public static String applicationId() {
    return "dsh.android.bxroot";
  }

  public static int bridgePort() {
    return 3191;
  }

  public static String bridgeBaseUrl() {
    return "http://127.0.0.1:" + bridgePort();
  }

  public static String originRestoreAction() {
    return applicationId() + ".ORIGIN_RESTORE_QUIESCE";
  }

  public static String displayName() {
    return "DSHA Bxroot";
  }

  public static String publicDirectoryName() {
    return "DSHA Bxroot";
  }

  public static String defaultUpdateFeed() {
    return "";
  }

  public static boolean isExperimental() {
    return true;
  }
}
