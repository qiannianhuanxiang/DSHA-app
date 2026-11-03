package com.deepseekharness.app.util;

/** 展示状态只改变原生容器；不创建页面、不发消息、不重新加载草稿。 */
public final class BrowserPresentationState {
  public enum Mode {
    FULL,
    PIP,
    HIDDEN
  }

  private Mode mode = Mode.FULL;

  public Mode mode() {
    return mode;
  }

  public void show() {
    mode = Mode.FULL;
  }

  public void hide() {
    mode = Mode.HIDDEN;
  }

  public void picture(boolean enabled) {
    if (mode != Mode.HIDDEN) mode = enabled ? Mode.PIP : Mode.FULL;
  }

  public void restore(String saved) {
    // Only the system can restore an actual PiP window; retired or unknown modes reopen full
    // screen.
    mode = "HIDDEN".equals(saved) ? Mode.HIDDEN : Mode.FULL;
  }
}
