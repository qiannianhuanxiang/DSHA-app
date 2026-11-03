package com.deepseekharness.app.util;

/** 输入提交结果；已经提交后的失败不得被当成可以重放的前置拒绝。 */
public enum VirtualScreenInputResult {
  REJECTED,
  ACCEPTED,
  UNKNOWN;

  public static VirtualScreenInputResult after(
      VirtualScreenInputResult previous, VirtualScreenInputResult next) {
    if (previous == UNKNOWN || next == UNKNOWN) return UNKNOWN;
    if (previous == ACCEPTED && next == REJECTED) return UNKNOWN;
    return next;
  }

  public boolean accepted() {
    return this == ACCEPTED;
  }
}
