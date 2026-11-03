package com.deepseekharness.app.util;

import java.util.List;

/** Physical display policy. Virtual displays retain their separate application-window policy. */
public final class MainScreenWindowPolicy {
  private MainScreenWindowPolicy() {}

  public enum Kind {
    APPLICATION,
    SYSTEM,
    INPUT_METHOD,
    UNSUPPORTED
  }

  public record Window(
      int display,
      int id,
      int layer,
      Kind kind,
      boolean focused,
      boolean active,
      ScreenWindowRegion.Bounds bounds,
      ScreenWindowRegion.Exclusion ownedExclusion) {
    public Window(
        int display,
        int id,
        int layer,
        Kind kind,
        boolean focused,
        boolean active,
        ScreenWindowRegion.Bounds bounds) {
      this(display, id, layer, kind, focused, active, bounds, null);
    }
  }

  public record Selection(int index, String diagnostic) {}

  /** IME is an occluder of the focused editor, never an excuse to choose a background app. */
  public static int select(List<Window> windows) {
    return selection(windows).index;
  }

  public static Selection selection(List<Window> windows) {
    int selected = -1, layer = Integer.MIN_VALUE;
    boolean ambiguous = false;
    for (int i = 0; i < windows.size(); i++) {
      Window w = windows.get(i);
      if (w.display != 0
          || w.id < 0
          || !w.focused && !w.active
          || w.kind == Kind.INPUT_METHOD
          || w.ownedExclusion != null) continue;
      if (selected < 0 || w.layer > layer) {
        selected = i;
        layer = w.layer;
        ambiguous = false;
      } else if (w.layer == layer) ambiguous = true;
    }
    if (ambiguous) return new Selection(-1, "AMBIGUOUS_INTERACTIVE_WINDOWS");
    if (selected < 0) return new Selection(-1, "NO_FOCUSED_OR_ACTIVE_WINDOW");
    Window w = windows.get(selected);
    if (w.kind != Kind.APPLICATION && w.kind != Kind.SYSTEM)
      return new Selection(-1, "UNSUPPORTED_INTERACTIVE_WINDOW");
    return new Selection(selected, "READABLE");
  }

  /**
   * #210：未登记（或登记后窗口重建、windowId 漂移）的自有<b>非焦点</b>悬浮窗，
   * 按包名身份整窗排除，不计为目标遮挡。包名在 Android 内是精确身份，不存在冒名；
   * 排除区内的坐标轨迹仍被 {@code ScreenWindowRegion} 拒绝，完全盖满时依旧
   * 无可点面积 —— 行为与登记路径一致，只是不再要求每个悬浮窗都维护登记。
   * 可聚焦的自有 Activity 窗口不适用：它们可能是观察目标本身。
   */
  public static ScreenWindowRegion.Exclusion ownFallbackExclusion(
      boolean ownPackage,
      boolean focused,
      int windowId,
      ScreenWindowRegion.Bounds measured,
      ScreenWindowRegion.Exclusion registered) {
    if (registered != null
        || !ownPackage
        || focused
        || measured == null
        || !measured.valid()) return registered;
    return new ScreenWindowRegion.Exclusion(windowId, 0, measured);
  }

  /** Clip only non-interactive system chrome / IME covering a complete edge. Interior covers refuse coordinates. */
  public static ScreenWindowRegion region(int selected, List<Window> windows) {
    Window target = windows.get(selected);
    var original = target.bounds;
    if (original == null || !original.valid()) return null;
    var content = original;
    boolean obscured = false;
    var exclusions = new java.util.ArrayList<ScreenWindowRegion.Exclusion>();
    var occluders = new java.util.ArrayList<ScreenWindowRegion.Occluder>();
    for (Window other : windows) {
      if (other.display != 0 || other.id == target.id || other.layer <= target.layer) continue;
      var cover = other.bounds;
      if (cover == null || !cover.valid()) {
        obscured = true;
        occluders.add(new ScreenWindowRegion.Occluder(other.id, other.kind.name(), cover));
        continue;
      }
      // #212：1×1 的系统字形/角标窗（HyperOS 的 HyperDuo glyph、笔记角标等）
      // 物理上不可见，不得触发遮挡判定，否则整台设备的坐标手势全部不可用。
      // 阈值收紧到 1×1：既有契约锁定「内部小覆盖窗即使 2×2 也必须拒绝坐标」
      // （MainScreenObservationTest），不能为字形窗放宽到 4px²。
      // 跳过时不进排除表 —— 它们不构成任何可用面积的变化。
      if (cover.right() - cover.left() <= 1 && cover.bottom() - cover.top() <= 1) continue;
      if (!content.intersects(cover)) continue;
      if (other.ownedExclusion != null && cover.equals(other.ownedExclusion.bounds())) {
        exclusions.add(other.ownedExclusion);
        continue;
      }
      boolean edgeChrome =
          other.kind == Kind.INPUT_METHOD
              || other.kind == Kind.SYSTEM && !other.focused && !other.active;
      if (edgeChrome
          && cover.left() <= content.left()
          && cover.right() >= content.right()
          && cover.top() <= content.top()
          && cover.bottom() < content.bottom())
        content =
            new ScreenWindowRegion.Bounds(
                content.left(), cover.bottom(), content.right(), content.bottom());
      else if (edgeChrome
          && cover.left() <= content.left()
          && cover.right() >= content.right()
          && cover.bottom() >= content.bottom()
          && cover.top() > content.top())
        content =
            new ScreenWindowRegion.Bounds(
                content.left(), content.top(), content.right(), cover.top());
      else if (edgeChrome
          && cover.top() <= content.top()
          && cover.bottom() >= content.bottom()
          && cover.left() <= content.left()
          && cover.right() < content.right())
        content =
            new ScreenWindowRegion.Bounds(
                cover.right(), content.top(), content.right(), content.bottom());
      else if (edgeChrome
          && cover.top() <= content.top()
          && cover.bottom() >= content.bottom()
          && cover.right() >= content.right()
          && cover.left() > content.left())
        content =
            new ScreenWindowRegion.Bounds(
                content.left(), content.top(), cover.left(), content.bottom());
      else {
        obscured = true;
        occluders.add(new ScreenWindowRegion.Occluder(other.id, other.kind.name(), cover));
      }
    }
    return new ScreenWindowRegion(original, content, obscured, exclusions, occluders);
  }
}
