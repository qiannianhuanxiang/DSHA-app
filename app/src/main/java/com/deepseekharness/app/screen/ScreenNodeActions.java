package com.deepseekharness.app.screen;

import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;

/** Only advertise actions reported by this current Android node. */
final class ScreenNodeActions {
  private static final String[] NAMES = {
    "click", "long_click", "scroll_forward", "scroll_backward", "focus", "set_text"
  };

  private ScreenNodeActions() {}

  static int action(String name) {
    return switch (name) {
      case "click" -> AccessibilityNodeInfo.ACTION_CLICK;
      case "long_click" -> AccessibilityNodeInfo.ACTION_LONG_CLICK;
      case "scroll_forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
      case "scroll_backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
      case "focus" -> AccessibilityNodeInfo.ACTION_FOCUS;
      case "set_text" -> AccessibilityNodeInfo.ACTION_SET_TEXT;
      default -> -1;
    };
  }

  static boolean scroll(int action) {
    return action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
  }

  static boolean supported(AccessibilityNodeInfo node, int action) {
    for (var available : node.getActionList()) if (available.getId() == action) return true;
    return false;
  }

  static JSONArray names(AccessibilityNodeInfo node) {
    var result = new JSONArray();
    for (String name : NAMES) if (supported(node, action(name))) result.put(name);
    return result;
  }
}
