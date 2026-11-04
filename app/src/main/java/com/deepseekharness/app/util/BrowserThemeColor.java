package com.deepseekharness.app.util;

import java.util.regex.Pattern;

/** 只解析浏览器计算后的 CSS RGB/RGBA 与明确十六进制；失败保留上一有效颜色。 */
public final class BrowserThemeColor {
  private static final Pattern RGB =
      Pattern.compile(
          "rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})(?:\\s*,\\s*(0(?:\\.\\d+)?|1(?:\\.0+)?|\\.\\d+))?\\s*\\)",
          Pattern.CASE_INSENSITIVE);

  private BrowserThemeColor() {}

  public static Integer parse(String value, int background) {
    if (value == null || value.length() > 96) return null;
    String text = value.trim();
    try {
      if (text.matches("#[0-9a-fA-F]{3}")) {
        int v = Integer.parseInt(text.substring(1), 16);
        return 0xff000000 | ((v >> 8) * 17 << 16) | ((v >> 4 & 15) * 17 << 8) | ((v & 15) * 17);
      }
      if (text.matches("#[0-9a-fA-F]{6}"))
        return 0xff000000 | Integer.parseInt(text.substring(1), 16);
      var match = RGB.matcher(text);
      if (!match.matches()) return null;
      int r = Integer.parseInt(match.group(1)),
          g = Integer.parseInt(match.group(2)),
          b = Integer.parseInt(match.group(3));
      if (r > 255 || g > 255 || b > 255) return null;
      double alpha = match.group(4) == null ? 1 : Double.parseDouble(match.group(4));
      if (alpha <= 0) return null;
      r = (int) Math.round(r * alpha + (background >> 16 & 255) * (1 - alpha));
      g = (int) Math.round(g * alpha + (background >> 8 & 255) * (1 - alpha));
      b = (int) Math.round(b * alpha + (background & 255) * (1 - alpha));
      return 0xff000000 | r << 16 | g << 8 | b;
    } catch (RuntimeException invalid) {
      return null;
    }
  }
}
