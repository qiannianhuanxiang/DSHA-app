package com.deepseekharness.app.util;

/** UI navigation and recents visibility never authorize stopping the runtime or deleting data. */
public final class TaskNavigationPolicy {
  public enum Recents {
    KEEP,
    SHOW,
    HIDE
  }

  public enum Back {
    POP_PAGE,
    BACKGROUND
  }

  private TaskNavigationPolicy() {}

  public static Back back(int nestedPages, boolean stateSaved) {
    return nestedPages > 0 && !stateSaved ? Back.POP_PAGE : Back.BACKGROUND;
  }

  public static Recents recents(
      int taskId, boolean mainRoot, boolean browserRoot, int selectedMainTask) {
    if (taskId < 0) return Recents.KEEP;
    if (mainRoot) {
      // Without an identified Main, do not hide the user's last route back into the app.
      if (selectedMainTask < 0) return Recents.KEEP;
      return taskId == selectedMainTask ? Recents.SHOW : Recents.HIDE;
    }
    return browserRoot ? Recents.HIDE : Recents.KEEP;
  }
}
