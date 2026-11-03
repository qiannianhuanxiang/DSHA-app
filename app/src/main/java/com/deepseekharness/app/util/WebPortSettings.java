package com.deepseekharness.app.util;

/** The configured listening policy is separate from the actual authenticated Web port. */
public final class WebPortSettings {
  public static final String AUTO = "auto";
  public static final String FIXED = "fixed";

  private WebPortSettings() {}

  public static String mode(String stored, String legacyPort) {
    if (AUTO.equals(stored) || FIXED.equals(stored)) return stored;
    if (stored != null) return AUTO;
    try {
      int port = Integer.parseInt(legacyPort == null ? "" : legacyPort.trim());
      return port > 0 && port <= 65535 && port != 3080 && !WebPortPolicy.reserved(port)
          ? FIXED
          : AUTO;
    } catch (NumberFormatException invalid) {
      return AUTO;
    }
  }

  public static String requireMode(String value) {
    if (!AUTO.equals(value) && !FIXED.equals(value))
      throw new IllegalArgumentException("WEB_PORT_MODE");
    return value;
  }

  public static int requested(String mode, int fixedPort) {
    requireMode(mode);
    if (AUTO.equals(mode)) return 0;
    if (fixedPort < 1 || fixedPort > 65535 || WebPortPolicy.reserved(fixedPort))
      throw new IllegalArgumentException("WEB_FIXED_PORT");
    return fixedPort;
  }

  public static int fixedImport(Object value) {
    if (!(value instanceof String)) throw new IllegalArgumentException("WEB_FIXED_PORT");
    try {
      return requested(FIXED, Integer.parseInt(((String) value).trim()));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException("WEB_FIXED_PORT", invalid);
    }
  }
}
