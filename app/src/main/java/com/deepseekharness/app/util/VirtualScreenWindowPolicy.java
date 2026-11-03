package com.deepseekharness.app.util;

/** 同一虚拟显示的根窗口排序；所有读屏与动作入口使用同一规则。 */
public final class VirtualScreenWindowPolicy {
  private VirtualScreenWindowPolicy() {}

  public static final class Window {
    public final int display, id, layer;
    public final boolean application, focused, active;

    public Window(
        int display, int id, int layer, boolean application, boolean focused, boolean active) {
      this.display = display;
      this.id = id;
      this.layer = layer;
      this.application = application;
      this.focused = focused;
      this.active = active;
    }
  }

  /** Select by window metadata before requesting any root. A root failure never elects a background app. */
  public static int select(int display, java.util.List<Window> windows) {
    int selected = -1, bestRank = -1, bestLayer = Integer.MIN_VALUE;
    boolean ambiguous = false;
    for (int i = 0; i < windows.size(); i++) {
      Window window = windows.get(i);
      if (window.display != display || window.id < 0 || !window.application) continue;
      int rank = rank(window.focused, window.active);
      if (selected < 0 || rank > bestRank || rank == bestRank && window.layer > bestLayer) {
        selected = i;
        bestRank = rank;
        bestLayer = window.layer;
        ambiguous = false;
      } else if (rank == bestRank && window.layer == bestLayer) ambiguous = true;
    }
    return ambiguous ? -1 : selected;
  }

  public static int rank(boolean focused, boolean active) {
    return focused ? 2 : active ? 1 : 0;
  }

  public static boolean approved(
      int display, String approvedPackage, int currentDisplay, String currentPackage) {
    return display > 0
        && display == currentDisplay
        && approvedPackage != null
        && !approvedPackage.isEmpty()
        && approvedPackage.equals(currentPackage);
  }
}
