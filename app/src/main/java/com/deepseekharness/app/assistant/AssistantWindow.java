package com.deepseekharness.app.assistant;

import com.deepseekharness.app.util.UiText;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.deepseekharness.app.control.OwnedScreenOverlay;
import com.deepseekharness.app.util.ScreenWindowRegion;

/** Measured owned geometry is excluded only while collapsed. Expanding fences first. */
final class AssistantWindow implements AutoCloseable {
  private final Context context;
  private final AssistantSessionController controller;
  private final WindowManager manager;
  private LinearLayout root, panel, actions;
  private TextView status, messages;
  private Button input;
  private Button sendButton, resumeButton;
  private Button notificationSettingsButton;
  private WindowManager.LayoutParams params;
  private volatile boolean added;
  private boolean renderedExpanded;
  private long draftRevision = -1;
  private boolean editorVisible;
  private boolean hiddenForScreen;
  private boolean stopControlsRequired;
  private final Runnable changed = this::render;

  boolean visible() {
    return added;
  }

  boolean taskUiAvailable() {
    return added || hiddenForScreen;
  }

  /** Remove the actual input window before observing pixels; polling never recreates it. */
  boolean hideForScreen() {
    if (controller.snapshot().expanded() || editorVisible)
      throw new IllegalStateException("ASSISTANT_LOCAL_RESUME_REQUIRED");
    if (hiddenForScreen) return false;
    if (!added) throw new IllegalStateException("ASSISTANT_UI_UNAVAILABLE");
    manager.removeViewImmediate(root);
    added = false;
    hiddenForScreen = true;
    stopControlsRequired = false;
    OwnedScreenOverlay.clear("assistant");
    return true;
  }

  /** Losing the stop notification restores explicit native controls while all input stays paused. */
  void restoreStopControls() {
    stopControlsRequired = true;
    if (!added && hiddenForScreen && !editorVisible && Settings.canDrawOverlays(context)) {
      try {
        manager.addView(root, params);
        added = true;
        hiddenForScreen = false;
      } catch (RuntimeException unavailable) {
        return;
      }
    }
    render();
  }

  AssistantWindow(Context context, AssistantSessionController controller) {
    this.context = context;
    this.controller = controller;
    manager = context.getSystemService(WindowManager.class);
  }

  private int dp(int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  void show() {
    if (!Settings.canDrawOverlays(context)) return;
    hiddenForScreen = false;
    root = new LinearLayout(context);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(10), dp(6), dp(10), dp(6));
    GradientDrawable background = new GradientDrawable();
    background.setColor(0xF51A2432);
    background.setCornerRadius(dp(16));
    root.setBackground(background);
    status = new TextView(context);
    status.setTextColor(Color.WHITE);
    status.setTextSize(13);
    status.setMaxLines(2);
    status.setEllipsize(TextUtils.TruncateAt.END);
    status.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(status, new LinearLayout.LayoutParams(-1, dp(50)));
    status.setOnClickListener(
        v -> {
          if (controller.snapshot().expanded()) controller.collapse();
          else controller.expand();
        });
    panel = new LinearLayout(context);
    panel.setOrientation(LinearLayout.VERTICAL);
    root.addView(panel);
    ScrollView scroll = new ScrollView(context);
    messages = new TextView(context);
    messages.setTextColor(Color.WHITE);
    messages.setTextSize(14);
    scroll.addView(messages);
    panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(190)));
    // A service overlay is not a reliable IME editor on every ROM. Use a normal
    // Activity for explicit text editing instead of claiming focus from flags alone.
    input = new Button(context);
    input.setTextColor(Color.WHITE);
    input.setMaxLines(2);
    input.setEllipsize(TextUtils.TruncateAt.END);
    input.setTextSize(14);
    panel.addView(input);
    input.setOnClickListener(v -> openEditor());
    LinearLayout row = new LinearLayout(context);
    panel.addView(row);
    sendButton =
        button(
            row,
            UiText.choose("发送", "Send"),
            () -> {
              controller.send(controller.snapshot().draft());
            });
    button(
        row,
        UiText.choose("语音", "Voice"),
        () -> {
          controller.pause("ASSISTANT_VOICE");
          context.startActivity(
              new Intent(context, AssistantVoiceActivity.class)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
    button(row, UiText.choose("收起", "Collapse"), controller::collapse);
    actions = new LinearLayout(context);
    root.addView(actions);
    button(actions, UiText.choose("暂停", "Pause"), () -> controller.pause("USER_PAUSED"));
    resumeButton = button(actions, UiText.choose("继续", "Continue"), controller::resume);
    button(actions, UiText.choose("停止", "Stop"), controller::stop);
    button(actions, UiText.choose("会话", "Chat"), controller::returnConversation);
    notificationSettingsButton = new Button(context);
    notificationSettingsButton.setText(
        UiText.choose("打开系统通知设置", "Open Android notification settings"));
    notificationSettingsButton.setOnClickListener(
        v -> context.startActivity(AssistantService.notificationSettingsIntent(context)));
    root.addView(notificationSettingsButton);
    params = new WindowManager.LayoutParams();
    params.type =
        Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
    params.format = PixelFormat.TRANSLUCENT;
    params.flags =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    params.gravity = Gravity.BOTTOM | Gravity.END;
    params.y = dp(24);
    params.x = dp(12);
    params.width =
        Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(24), dp(210));
    params.height = -2;
    params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
    root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> publish());
    try {
      manager.addView(root, params);
      added = true;
      controller.addListener(changed);
      render();
    } catch (RuntimeException unavailable) {
      controller.pause("OVERLAY_UNAVAILABLE");
      OwnedScreenOverlay.clear("assistant");
    }
  }

  private Button button(LinearLayout row, String text, Runnable action) {
    Button button = new Button(context);
    button.setText(text);
    button.setTextSize(12);
    button.setMinWidth(0);
    button.setPadding(dp(4), 0, dp(4), 0);
    row.addView(button, new LinearLayout.LayoutParams(0, dp(44), 1));
    button.setOnClickListener(v -> action.run());
    return button;
  }

  private void render() {
    if (!added) return;
    var state = controller.snapshot();
    String target = UiText.choose("本机 DSH", "Local DSH");
    String progress = state.text().replaceAll("\\s+", " ").strip();
    if (progress.length() > 90) progress = progress.substring(progress.length() - 90);
    status.setText(
        target
            + " · "
            + label(state.phase())
            + "  ▾"
            + (state.expanded() || progress.isEmpty() ? "" : "\n" + progress));
    status.setContentDescription(
        String.format(
            UiText.choose("%s，会话 %s，%s。点按展开聊天", "%s, conversation %s, %s. Tap to expand chat"),
            target,
            state.binding().sessionId(),
            label(state.phase())));
    messages.setText(state.text() + "\n\n" + detail(state));
    sendButton.setEnabled(state.terminalKnown() && !state.draft().isBlank());
    resumeButton.setEnabled(
        state.terminalKnown()
            && state.paused()
            && state.phase() != AssistantSessionState.Phase.STOPPED);
    panel.setVisibility(state.expanded() ? View.VISIBLE : View.GONE);
    actions.setVisibility(state.expanded() || stopControlsRequired ? View.VISIBLE : View.GONE);
    notificationSettingsButton.setVisibility(stopControlsRequired ? View.VISIBLE : View.GONE);
    if (draftRevision != state.draftRevision()) {
      draftRevision = state.draftRevision();
      input.setText(
          state.draft().isBlank()
              ? UiText.choose("点此输入文字", "Tap to type a message")
              : String.format(UiText.choose("编辑草稿：%s", "Edit draft: %s"), state.draft()));
    }
    if (renderedExpanded != state.expanded() || stopControlsRequired) {
      renderedExpanded = state.expanded();
      params.width =
          state.expanded() || stopControlsRequired
              ? Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(24), dp(440))
              : Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(24), dp(210));
      // This window has buttons and a transcript, never an IME target.
      params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
      try {
        manager.updateViewLayout(root, params);
      } catch (RuntimeException failure) {
        controller.pause("OVERLAY_REMOVED");
      }
    }
    publish();
  }

  static Intent editorIntent(Context context) {
    return new Intent(context, AssistantActivity.class)
        .putExtra("bound", true)
        .putExtra("focus_input", true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
  }

  private void openEditor() {
    controller.beginEditing();
    controller.collapse();
    setEditorVisible(true);
    context.startActivity(editorIntent(context));
  }

  void setEditorVisible(boolean visible) {
    editorVisible = visible;
    if (!added) return;
    root.clearFocus();
    params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    manager.updateViewLayout(root, params);
    root.setVisibility(visible ? View.GONE : View.VISIBLE);
    publish();
  }

  static String label(AssistantSessionState.Phase phase) {
    return switch (phase) {
      case CONNECTING -> UiText.choose("连接中", "Connecting");
      case IDLE -> UiText.choose("就绪", "Ready");
      case SENDING -> UiText.choose("发送中", "Sending");
      case RUNNING -> UiText.choose("执行中", "Running");
      case PAUSING -> UiText.choose("正在暂停", "Pausing");
      case PAUSED -> UiText.choose("已暂停", "Paused");
      case STOPPING -> UiText.choose("正在停止", "Stopping");
      case STOPPED -> UiText.choose("已停止", "Stopped");
      case UNKNOWN -> UiText.choose("状态待核对", "Status unknown");
      case DISCONNECTED -> UiText.choose("连接已断开", "Disconnected");
    };
  }

  static String detail(AssistantSessionController.Snapshot state) {
    return switch (state.detail()) {
      case "ASSISTANT_EXPANDED", "ASSISTANT_IME", "ASSISTANT_VOICE" ->
          UiText.choose(
              "聊天已展开，手机操作已暂停。核对停止状态后可发送新消息；收起后点继续恢复任务。",
              "Phone actions are paused while chatting. Once cancellation is confirmed, send a message or collapse and continue the task.");
      case "ASSISTANT_STOP_NOTIFICATION_REQUIRED" ->
          AssistantService.taskGuidance("ASSISTANT_STOP_NOTIFICATION_REQUIRED");
      case "USER_PAUSED" ->
          UiText.choose(
              "任务已暂停。确认停止后，点继续会先重新观察屏幕。",
              "Task paused. Continue after cancellation is confirmed to observe the screen again.");
      case "OBSERVE_REQUIRED" ->
          UiText.choose(
              "下一次手机操作前会重新观察屏幕。",
              "The screen must be observed again before the next phone action.");
      case "RETURN_CONVERSATION" ->
          UiText.choose(
              "已暂停手机操作，正在打开完整会话。", "Phone actions paused; opening the full conversation.");
      case "CONNECTION_CHANGED" ->
          UiText.choose(
              "连接已改变。返回悬浮助手重新选择会话。",
              "The connection changed. Choose a conversation again in the assistant.");
      case "" ->
          state.phase() == AssistantSessionState.Phase.STOPPED
              ? UiText.choose(
                  "当前任务已停止。输入新消息可在此会话开始新任务。",
                  "Task stopped. Send a new message to begin another task in this conversation.")
              : "";
      default ->
          state.detail().matches("[A-Z][A-Z0-9_]+")
              ? UiText.choose(
                  "手机操作已暂停；确认会话状态后继续。",
                  "Phone actions paused; check the conversation before continuing.")
              : state.detail();
    };
  }

  private void publish() {
    if (editorVisible) {
      OwnedScreenOverlay.clear("assistant");
      return;
    }
    publish(root, "assistant", controller.snapshot().expanded());
  }

  static void publish(View view, String owner, boolean expanded) {
    if (view == null || !view.isAttachedToWindow() || view.getVisibility() != View.VISIBLE) {
      OwnedScreenOverlay.clear(owner);
      return;
    }
    int[] position = new int[2];
    view.getLocationOnScreen(position);
    var node = view.createAccessibilityNodeInfo();
    try {
      OwnedScreenOverlay.publish(
          owner,
          node.getWindowId(),
          new ScreenWindowRegion.Bounds(
              position[0],
              position[1],
              position[0] + view.getWidth(),
              position[1] + view.getHeight()),
          expanded);
    } finally {
      node.recycle();
    }
  }

  @Override
  public void close() {
    controller.removeListener(changed);
    OwnedScreenOverlay.clear("assistant");
    if (added)
      try {
        manager.removeViewImmediate(root);
      } catch (RuntimeException ignored) {
      }
    added = false;
    hiddenForScreen = false;
  }
}
