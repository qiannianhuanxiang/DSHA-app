package com.deepseekharness.app.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.deepseekharness.app.PtySession;
import com.deepseekharness.app.R;
import com.deepseekharness.app.core.ConfigStore;
import com.deepseekharness.app.core.TerminalRuntimeProtection;
import com.deepseekharness.app.core.TerminalSessionOwner;
import com.deepseekharness.app.util.SensitiveData;
import com.deepseekharness.app.util.TerminalSession;
import com.deepseekharness.app.util.TerminalTabs;
import com.deepseekharness.app.util.TerminalWorkspacePolicy;
import com.deepseekharness.app.util.UiText;
import java.util.concurrent.atomic.AtomicInteger;

/** 同 task 的终端工作台。页面只持有视图，结束进程必须明确指定会话并核验。 */
public class TerminalActivity extends AppCompatActivity implements TerminalPane.Host {
  private static final String WORKBENCH = "terminal_workbench";
  private static final String SESSIONS_OPEN = "sessions_open";
  private final TerminalSessionOwner owner = TerminalSessionOwner.shared();
  private FrameLayout root, content, drawerHost;
  private LinearLayout column,
      toolbar,
      body,
      sessionPanel,
      sessionList,
      sessionHeader,
      sessionFooter;
  private LinearLayout footerActionsWide, footerActionsDense, sessionFlow;
  private ScrollView sessionScroll;
  private View sessionDivider;
  private boolean wholeSidebarScrolling, stackedSessionDetails;
  private int footerWideColumns = 2, footerDenseColumns = 3;
  private int chromeWidthDp = -1, chromeHeightDp = -1;
  private float chromeFontScale = Float.NaN;
  private boolean chromeLandscapeCompact;
  private View sessionScrim, inlineDivider, toolbarDivider, statusBarScrim;
  private View menuButton, settingsButton, helpButton, newSessionButton, sessionClose;
  private TextView title, status, sessionSummary, sessionSubtitle, createLabel, createHint;
  private TextView panelSecondary, panelNotice, closeAllButton;
  private TerminalFloatingMenu ball;
  private AlertDialog panel;
  private boolean workbench, imeVisible, previousProcess, changingMode, expanded, sessionsRequested;
  private boolean compactHeight,
      denseFooter,
      landscapeCompact,
      sessionsRendered,
      presentationScheduled;
  private volatile boolean closeAllRunning, closeAllDispatching;
  private final AtomicInteger closeAllPending = new AtomicInteger();
  private volatile String closeAllEpoch = "";
  private int closeAllTotal;
  private volatile int closeAllRun;
  private final java.util.Set<Long> closeAllPtyIds = new java.util.HashSet<>();
  private final java.util.Set<Long> closeAllSimpleIds = new java.util.HashSet<>();
  private String closeAllNotice = "";
  private Runnable closeAllWatchdog;
  private final Runnable protectionChanged = () -> runOnUiThread(this::onTerminalPaneChanged);
  private final Runnable simpleStateChanged =
      () ->
          runOnUiThread(
              () -> {
                if (getLifecycle()
                    .getCurrentState()
                    .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) onTerminalPaneChanged();
              });

  @Override
  protected void onCreate(@Nullable Bundle saved) {
    // 新进程不恢复旧会话的Fragment参数或旧异步输入身份。
    previousProcess =
        saved != null && !owner.ownerEpoch().equals(saved.getString("terminal_owner"));
    super.onCreate(previousProcess ? null : saved);
    if (!new ConfigStore(this).isWelcomed()) {
      startActivity(new Intent(this, MainActivity.class));
      finish();
      return;
    }
    workbench = getSharedPreferences("deepseekharness", MODE_PRIVATE).getBoolean(WORKBENCH, false);
    sessionsRequested = saved != null && !previousProcess && saved.getBoolean(SESSIONS_OPEN, false);
    buildWorkspace();
    renderSessions();
    if (saved != null)
      ball.restore(saved.getBoolean("ball_right", true), saved.getFloat("ball_fraction", .6f));
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new androidx.activity.OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (panel != null && panel.isShowing()) {
                  panel.dismiss();
                  return;
                }
                if (sessionsDismissible()) {
                  closeSessions();
                  return;
                }
                TerminalPane pane = pane();
                if (pane != null && pane.selectionBack()) return;
                if (imeVisible) {
                  WindowCompat.getInsetsController(getWindow(), root)
                      .hide(WindowInsetsCompat.Type.ime());
                  return;
                }
                leavePage(); // 仅关闭视图；绝不shutdown、exit、Ctrl+D或stopWeb。
              }
            });
    if (getSupportFragmentManager().findFragmentById(R.id.terminal_content) == null) {
      Fragment fragment = createPane(PtyTerminalFragment.preferred(this), previousProcess);
      getSupportFragmentManager()
          .beginTransaction()
          .replace(R.id.terminal_content, fragment)
          .commitNow();
    }
    onTerminalPaneChanged();
  }

  private void buildWorkspace() {
    root = new FrameLayout(this);
    root.setId(R.id.terminal_workspace);
    root.setBackgroundColor(getColor(R.color.terminal_surface));
    if (android.os.Build.VERSION.SDK_INT >= 29) root.setForceDarkAllowed(false);
    column = new LinearLayout(this);
    column.setOrientation(LinearLayout.VERTICAL);
    root.addView(column, new FrameLayout.LayoutParams(-1, -1));
    // 工作台浅色时状态栏背后也是面板色；极简模式仍是黑底输出。窗口 inset 决定高度。
    root.setClipToPadding(false); // inset 遮罩用负 margin 覆盖到根 padding 之外的状态栏区域。
    statusBarScrim = new View(this);
    statusBarScrim.setBackgroundColor(getColor(R.color.terminal_panel));
    statusBarScrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    statusBarScrim.setClickable(false);
    statusBarScrim.setVisibility(View.GONE);
    root.addView(statusBarScrim, new FrameLayout.LayoutParams(-1, 0, Gravity.TOP));
    toolbar = new LinearLayout(this);
    toolbar.setId(R.id.terminal_workspace_toolbar);
    toolbar.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.setBackgroundColor(getColor(R.color.terminal_panel));
    View back =
        iconButton(
            R.drawable.ic_ui_back,
            UiText.choose("返回", "Back"),
            this::leavePage,
            R.color.terminal_panel_text);
    back.setId(R.id.terminal_workspace_back);
    toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
    title = toolbarLabel("");
    toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    settingsButton =
        iconButton(
            R.drawable.ic_settings,
            UiText.choose("终端显示设置", "Terminal display settings"),
            this::showDisplaySettings,
            R.color.terminal_panel_text);
    settingsButton.setId(R.id.terminal_workspace_settings);
    toolbar.addView(settingsButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
    helpButton =
        iconButton(
            R.drawable.ic_about,
            UiText.choose("终端与会话说明", "Terminal and sessions help"),
            this::showSessionHelp,
            R.color.terminal_panel_text);
    helpButton.setId(R.id.terminal_workspace_help);
    toolbar.addView(helpButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
    menuButton =
        iconButton(
            R.drawable.ic_terminal_menu,
            UiText.choose("打开会话侧栏", "Open sessions sidebar"),
            this::toggleSessions,
            R.color.terminal_panel_text);
    menuButton.setId(R.id.terminal_workspace_menu);
    toolbar.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
    column.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
    toolbarDivider = divider(R.color.terminal_line);
    column.addView(toolbarDivider, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
    status = label("");
    status.setId(R.id.terminal_workspace_status);
    status.setTextColor(getColor(R.color.terminal_foreground));
    status.setBackgroundColor(getColor(R.color.terminal_surface));
    status.setPadding(dp(8), dp(4), dp(8), dp(4));
    column.addView(status, new LinearLayout.LayoutParams(-1, -2));
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.HORIZONTAL);
    column.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    content = new FrameLayout(this);
    content.setId(R.id.terminal_content);
    content.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    body.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
    sessionPanel = buildSessionsPanel();
    ball = new TerminalFloatingMenu(this);
    ball.setOnClickListener(v -> toggleSessions());
    drawerHost = new FrameLayout(this);
    drawerHost.setId(R.id.terminal_sessions_host);
    drawerHost.setVisibility(View.GONE);
    sessionScrim = new View(this);
    sessionScrim.setId(R.id.terminal_sessions_scrim);
    sessionScrim.setBackgroundColor(getColor(R.color.terminal_scrim));
    sessionScrim.setClickable(true);
    sessionScrim.setOnClickListener(v -> closeSessions());
    sessionScrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    drawerHost.addView(sessionScrim, new FrameLayout.LayoutParams(-1, -1));
    drawerHost.addView(
        sessionPanel,
        new FrameLayout.LayoutParams(
            dp(TerminalWorkspacePolicy.drawerMaxWidth()), -1, Gravity.START));
    root.addView(ball);
    root.addView(drawerHost, new FrameLayout.LayoutParams(-1, -1));
    setContentView(root);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    applySystemBars();
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (view, insets) -> {
          var safe =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
          imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
          root.setPadding(safe.left, safe.top, safe.right, Math.max(safe.bottom, keyboard));
          if (statusBarScrim != null) {
            FrameLayout.LayoutParams scrim =
                (FrameLayout.LayoutParams) statusBarScrim.getLayoutParams();
            scrim.height = safe.top;
            scrim.topMargin = -safe.top;
            scrim.leftMargin = -safe.left;
            scrim.rightMargin = -safe.right;
            statusBarScrim.setLayoutParams(scrim);
          }
          ball.reserveBottom(root.getPaddingBottom() + dp(100));
          ball.constrain(); // IME仅改padding时外框未变，也必须重约束菜单球。
          // IME 只改 padding 时根视图 bounds 不变，onLayoutChange 不可依赖；短窗形态必须重判。
          schedulePresentation();
          return WindowInsetsCompat.CONSUMED;
        });
    root.addOnLayoutChangeListener(
        (v, l, t, r, b, ol, ot, or, ob) -> {
          applyPresentation();
          // 球的坐标系是根窗口，底部安全区/键盘和两排按键都留出。
          ball.reserveBottom(root.getPaddingBottom() + dp(100));
          ball.constrain();
        });
    ViewCompat.requestApplyInsets(root);
    applyPresentation();
  }

  /** 侧栏内容只由宿主渲染；抽屉、平板内联与宽屏常驻复用同一份视图。 */
  private LinearLayout buildSessionsPanel() {
    LinearLayout panel = new SessionsPanelLayout();
    sessionFlow = new LinearLayout(this);
    sessionFlow.setOrientation(LinearLayout.VERTICAL);
    panel.setId(R.id.terminal_sessions_panel);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setBackgroundColor(getColor(R.color.terminal_panel));
    panel.setFocusable(true);
    panel.setVisibility(View.GONE);
    sessionHeader = new LinearLayout(this);
    sessionHeader.setGravity(Gravity.CENTER_VERTICAL);
    sessionHeader.setPaddingRelative(dp(14), dp(12), dp(6), dp(8));
    AppCompatImageView identity = new AppCompatImageView(this);
    identity.setImageResource(R.drawable.ic_terminal);
    identity.setBackgroundResource(R.drawable.bg_terminal_identity);
    identity.setImageTintList(ColorStateList.valueOf(getColor(R.color.terminal_accent)));
    identity.setPadding(dp(8), dp(8), dp(8), dp(8));
    identity.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(dp(36), dp(36));
    identityParams.setMarginEnd(dp(10));
    sessionHeader.addView(identity, identityParams);
    LinearLayout headings = new LinearLayout(this);
    headings.setOrientation(LinearLayout.VERTICAL);
    TextView heading =
        panelText(UiText.text("会话"), 16, getColor(R.color.terminal_panel_text_strong), true);
    sessionSubtitle =
        panelText(
            UiText.choose("独立会话 · 返回不结束进程", "Independent sessions · back keeps processes"),
            11,
            getColor(R.color.terminal_muted),
            false);
    sessionSubtitle.setId(R.id.terminal_sessions_subtitle);
    sessionSubtitle.setPadding(0, dp(2), 0, 0);
    headings.addView(heading);
    headings.addView(sessionSubtitle);
    sessionHeader.addView(headings, new LinearLayout.LayoutParams(0, -2, 1));
    sessionClose =
        iconButton(
            R.drawable.ic_terminal_close,
            UiText.choose("收起会话侧栏", "Close sessions sidebar"),
            this::closeSessions,
            R.color.terminal_muted);
    sessionClose.setId(R.id.terminal_sessions_close);
    sessionHeader.addView(sessionClose, new LinearLayout.LayoutParams(dp(48), dp(48)));
    panel.addView(sessionHeader, new LinearLayout.LayoutParams(-1, -2));
    newSessionButton = buildCreateButton();
    LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(-1, -2);
    createParams.setMargins(dp(12), dp(4), dp(12), dp(10));
    panel.addView(newSessionButton, createParams);
    sessionScroll = new ScrollView(this);
    sessionScroll.setId(R.id.terminal_sessions_scroll);
    sessionScroll.setFillViewport(true);
    sessionList = new LinearLayout(this);
    sessionList.setOrientation(LinearLayout.VERTICAL);
    sessionList.setPadding(dp(6), dp(4), dp(6), dp(12));
    sessionScroll.addView(sessionList, new FrameLayout.LayoutParams(-1, -2));
    panel.addView(sessionScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    sessionDivider = divider(R.color.terminal_line);
    panel.addView(sessionDivider, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
    sessionFooter = new LinearLayout(this);
    sessionFooter.setOrientation(LinearLayout.VERTICAL);
    sessionFooter.setBackgroundColor(getColor(R.color.terminal_panel_card));
    sessionFooter.setPadding(dp(8), dp(6), dp(8), dp(10));
    LinearLayout summaryRow = new LinearLayout(this);
    summaryRow.setGravity(Gravity.CENTER_VERTICAL);
    summaryRow.setPadding(dp(8), 0, dp(4), 0);
    View summaryDot = new View(this);
    summaryDot.setBackgroundResource(R.drawable.bg_terminal_dot);
    summaryDot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    summaryRow.addView(summaryDot, new LinearLayout.LayoutParams(dp(6), dp(6)));
    sessionSummary = panelText("", 12, getColor(R.color.terminal_panel_text), false);
    sessionSummary.setId(R.id.terminal_sessions_summary);
    sessionSummary.setMaxLines(1);
    sessionSummary.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(0, -2, 1);
    summaryParams.setMarginStart(dp(7));
    summaryRow.addView(sessionSummary, summaryParams);
    closeAllButton =
        footerButton(
            UiText.choose("结束全部", "End all"),
            R.drawable.ic_terminal_end_all,
            R.drawable.bg_terminal_action_danger,
            getColor(R.color.terminal_danger),
            R.color.terminal_danger,
            12,
            this::confirmCloseAll);
    closeAllButton.setId(R.id.terminal_sessions_close_all);
    closeAllButton.setContentDescription(
        UiText.choose("结束全部终端会话并核验", "End all terminal sessions and verify"));
    closeAllButton.setVisibility(View.GONE);
    summaryRow.addView(closeAllButton, new LinearLayout.LayoutParams(-2, -2));
    sessionFooter.addView(summaryRow, new LinearLayout.LayoutParams(-1, -2));
    panelNotice = panelText("", 11, getColor(R.color.terminal_warning), false);
    panelNotice.setId(R.id.terminal_sessions_notice);
    panelNotice.setPadding(dp(8), dp(2), dp(8), 0);
    panelNotice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    panelNotice.setVisibility(View.GONE);
    sessionFooter.addView(panelNotice, new LinearLayout.LayoutParams(-1, -2));
    footerActionsWide = new LinearLayout(this);
    footerActionsWide.setOrientation(LinearLayout.VERTICAL);
    footerActionsWide.setId(R.id.terminal_sessions_actions);
    addFooterGridRow(
        footerActionsWide,
        footerAction(
            R.id.terminal_sessions_action_home,
            UiText.choose("返回主界面", "Home"),
            R.drawable.ic_terminal_home,
            R.drawable.bg_terminal_action_soft,
            getColor(R.color.terminal_accent),
            R.color.terminal_accent,
            12,
            true,
            () -> {
              closeSessions();
              leavePage();
            }),
        footerAction(
            R.id.terminal_sessions_action_display,
            UiText.choose("显示设置", "Display"),
            R.drawable.ic_terminal_display,
            R.drawable.bg_btn,
            getColor(R.color.terminal_panel_text),
            R.color.terminal_accent,
            12,
            false,
            this::showDisplaySettings));
    addFooterGridRow(
        footerActionsWide,
        footerAction(
            R.id.terminal_sessions_action_keyboard,
            UiText.choose("显示键盘", "Keyboard"),
            R.drawable.ic_terminal_keyboard,
            R.drawable.bg_btn,
            getColor(R.color.terminal_panel_text),
            R.color.terminal_accent,
            12,
            false,
            () ->
                closeSessionsThen(
                    () -> {
                      if (pane() != null) pane().showKeyboard();
                    })),
        footerAction(
            R.id.terminal_sessions_action_help,
            UiText.choose("说明", "Help"),
            R.drawable.ic_terminal_help,
            R.drawable.bg_btn,
            getColor(R.color.terminal_panel_text),
            R.color.terminal_accent,
            12,
            false,
            this::showSessionHelp));
    addFooterGridRow(
        footerActionsWide,
        footerAction(
            R.id.terminal_sessions_action_paste,
            UiText.choose("粘贴文本", "Paste"),
            R.drawable.ic_ui_copy,
            R.drawable.bg_btn,
            getColor(R.color.terminal_panel_text),
            R.color.terminal_accent,
            12,
            false,
            () ->
                closeSessionsThen(
                    () -> {
                      if (pane() != null) pane().paste();
                    })),
        footerAction(
            R.id.terminal_sessions_action_focus,
            UiText.choose("回到输入", "Focus input"),
            R.drawable.ic_ui2_prompt,
            R.drawable.bg_btn,
            getColor(R.color.terminal_panel_text),
            R.color.terminal_accent,
            12,
            false,
            () ->
                closeSessionsThen(
                    () -> {
                      if (pane() != null) pane().restoreInputFocus();
                    })));
    sessionFooter.addView(footerActionsWide, new LinearLayout.LayoutParams(-1, -2));
    footerActionsDense = new LinearLayout(this);
    footerActionsDense.setOrientation(LinearLayout.VERTICAL);
    footerActionsDense.setId(R.id.terminal_sessions_actions_dense);
    addFooterGridRow(
        footerActionsDense,
        footerAction(
            R.id.terminal_sessions_action_home_dense,
            UiText.choose("返回主界面", "Home"),
            R.drawable.ic_terminal_home,
            R.drawable.bg_terminal_action_soft,
            getColor(R.color.terminal_accent),
            R.color.terminal_accent,
            11,
            true,
            () -> {
              closeSessions();
              leavePage();
            }),
        footerAction(
            R.id.terminal_sessions_action_display_dense,
            UiText.choose("显示设置", "Display"),
            R.drawable.ic_terminal_display,
            R.drawable.bg_btn,
            getColor(R.color.terminal_panel_text),
            R.color.terminal_accent,
            11,
            false,
            this::showDisplaySettings),
        footerAction(
            R.id.terminal_sessions_action_keyboard_dense,
            UiText.choose("显示键盘", "Keyboard"),
            R.drawable.ic_terminal_keyboard,
            R.drawable.bg_btn,
            getColor(R.color.terminal_panel_text),
            R.color.terminal_accent,
            11,
            false,
            () ->
                closeSessionsThen(
                    () -> {
                      if (pane() != null) pane().showKeyboard();
                    })));
    addFooterGridRow(
        footerActionsDense,
        footerAction(
            R.id.terminal_sessions_action_paste_dense,
            UiText.choose("粘贴文本", "Paste"),
            R.drawable.ic_ui_copy,
            R.drawable.bg_btn,
            getColor(R.color.terminal_panel_text),
            R.color.terminal_accent,
            11,
            false,
            () ->
                closeSessionsThen(
                    () -> {
                      if (pane() != null) pane().paste();
                    })),
        footerAction(
            R.id.terminal_sessions_action_focus_dense,
            UiText.choose("回到输入", "Focus input"),
            R.drawable.ic_ui2_prompt,
            R.drawable.bg_btn,
            getColor(R.color.terminal_panel_text),
            R.color.terminal_accent,
            11,
            false,
            () ->
                closeSessionsThen(
                    () -> {
                      if (pane() != null) pane().restoreInputFocus();
                    })),
        footerAction(
            R.id.terminal_sessions_action_help_dense,
            UiText.choose("说明", "Help"),
            R.drawable.ic_terminal_help,
            R.drawable.bg_btn,
            getColor(R.color.terminal_panel_text),
            R.color.terminal_accent,
            11,
            false,
            this::showSessionHelp));
    footerActionsDense.setVisibility(View.GONE);
    compactDenseActions();
    sessionFooter.addView(footerActionsDense, new LinearLayout.LayoutParams(-1, -2));
    panelSecondary =
        panelText(
            UiText.choose(
                "返回只关闭页面 · 结束进程单独核验", "Back closes the page only · exits are verified separately"),
            11,
            getColor(R.color.terminal_faint),
            false);
    panelSecondary.setId(R.id.terminal_sessions_secondary);
    panelSecondary.setGravity(Gravity.CENTER);
    panelSecondary.setPadding(dp(8), dp(6), dp(8), dp(2));
    sessionFooter.addView(panelSecondary, new LinearLayout.LayoutParams(-1, -2));
    panel.addView(sessionFooter, new LinearLayout.LayoutParams(-1, -2));
    return panel;
  }

  /** 测量固定区域后再决定滚动范围，避免极短窗把 weight=1 的会话列表压到零。 */
  private final class SessionsPanelLayout extends LinearLayout {
    private View focusAfterLayout;
    private int scrollBeforeLayout, listTopBeforeLayout;
    private boolean restoreScrollAfterLayout;

    SessionsPanelLayout() {
      super(TerminalActivity.this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int height = MeasureSpec.getSize(heightMeasureSpec);
      if (sessionFooter != null && width > 0 && height > 0) {
        float density = getResources().getDisplayMetrics().density;
        applyCompactChrome(Math.round(width / density), Math.round(height / density));
        int chrome =
            measureChrome(sessionHeader, width)
                + measureChrome(newSessionButton, width)
                + measureChrome(sessionDivider, width)
                + measureChrome(sessionFooter, width);
        boolean scrollAll =
            TerminalWorkspacePolicy.scrollWholeSidebar(
                height,
                chrome,
                dp(
                    TerminalWorkspacePolicy.minimumSessionHeight(
                        getResources().getConfiguration().fontScale)));
        if (wholeSidebarScrolling != scrollAll) {
          if (!restoreScrollAfterLayout) {
            focusAfterLayout = findFocus();
            scrollBeforeLayout = sessionScroll.getScrollY();
            listTopBeforeLayout = sessionList.getTop();
            restoreScrollAfterLayout = true;
          }
          setWholeSidebarScrolling(scrollAll);
        }
      }
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private int measureChrome(View child, int width) {
      if (child.getVisibility() == View.GONE) return 0;
      LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
      child.measure(
          MeasureSpec.makeMeasureSpec(
              Math.max(0, width - params.leftMargin - params.rightMargin), MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(
              Math.max(0, params.height),
              params.height >= 0 ? MeasureSpec.EXACTLY : MeasureSpec.UNSPECIFIED));
      return child.getMeasuredHeight() + params.topMargin + params.bottomMargin;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
      super.onLayout(changed, left, top, right, bottom);
      if (!restoreScrollAfterLayout) return;
      restoreScrollAfterLayout = false;
      View focused = focusAfterLayout;
      focusAfterLayout = null;
      if (focused != null) focused.requestFocus();
      // 已滚动时保持同一段会话；首次展开仍从头部开始，避免藏住关闭和新建入口。
      sessionScroll.scrollTo(
          0,
          scrollBeforeLayout > 0
              ? Math.max(0, scrollBeforeLayout + sessionList.getTop() - listTopBeforeLayout)
              : 0);
    }
  }

  /** 始终只保留一个滚动容器，短窗将头部、新建、会话与 footer 一起纳入滚动。 */
  private void setWholeSidebarScrolling(boolean scrollAll) {
    sessionScroll.removeAllViews();
    sessionFlow.removeAllViews();
    sessionPanel.removeAllViews();
    wholeSidebarScrolling = scrollAll;
    if (scrollAll) {
      sessionFlow.addView(sessionHeader);
      sessionFlow.addView(newSessionButton);
      sessionFlow.addView(sessionList, new LinearLayout.LayoutParams(-1, -2));
      sessionFlow.addView(sessionDivider);
      sessionFlow.addView(sessionFooter);
      sessionScroll.addView(sessionFlow, new FrameLayout.LayoutParams(-1, -2));
      sessionPanel.addView(sessionScroll, new LinearLayout.LayoutParams(-1, -1));
    } else {
      sessionPanel.addView(sessionHeader);
      sessionPanel.addView(newSessionButton);
      sessionScroll.addView(sessionList, new FrameLayout.LayoutParams(-1, -2));
      sessionPanel.addView(sessionScroll, new LinearLayout.LayoutParams(-1, 0, 1));
      sessionPanel.addView(sessionDivider);
      sessionPanel.addView(sessionFooter);
    }
  }

  private void addFooterGridRow(LinearLayout footer, TextView... buttons) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    for (TextView button : buttons) {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
      params.setMargins(dp(3), dp(3), dp(3), dp(3));
      row.addView(button, params);
    }
    footer.addView(row, new LinearLayout.LayoutParams(-1, -2));
  }

  private TextView footerAction(
      int id,
      String text,
      int iconRes,
      int backgroundRes,
      int textColor,
      int iconTintRes,
      float sizeSp,
      boolean bold,
      Runnable action) {
    TextView button =
        footerButton(text, iconRes, backgroundRes, textColor, iconTintRes, sizeSp, action);
    button.setId(id);
    if (bold) button.setTypeface(Typeface.DEFAULT_BOLD);
    return button;
  }

  private TextView footerButton(
      String text,
      int iconRes,
      int backgroundRes,
      int textColor,
      int iconTintRes,
      float sizeSp,
      Runnable action) {
    TextView value = panelText(text, sizeSp, textColor, false);
    value.setContentDescription(text);
    value.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    value.setMinHeight(dp(48));
    value.setMinWidth(dp(48));
    value.setPaddingRelative(dp(10), dp(6), dp(8), dp(6));
    value.setSingleLine(false);
    value.setMaxLines(2);
    value.setEllipsize(TextUtils.TruncateAt.END);
    value.setBackgroundResource(backgroundRes);
    setCompoundIcon(value, iconRes, iconTintRes, 17);
    value.setFocusable(true);
    value.setClickable(true);
    value.setOnClickListener(v -> action.run());
    return value;
  }

  private LinearLayout buildCreateButton() {
    LinearLayout row = new LinearLayout(this);
    row.setId(R.id.terminal_sessions_create);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(dp(48));
    row.setPadding(dp(12), dp(6), dp(12), dp(6));
    row.setBackgroundResource(R.drawable.bg_btn_primary);
    row.setFocusable(true);
    row.setClickable(true);
    ColorStateList primaryText = getColorStateList(R.color.button_primary_text);
    AppCompatImageView icon = new AppCompatImageView(this);
    icon.setImageResource(R.drawable.ic_terminal_new);
    icon.setImageTintList(primaryText);
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
    createLabel = panelText(UiText.choose("新建终端会话", "New terminal session"), 14, 0, true);
    createLabel.setTextColor(primaryText);
    LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
    labelParams.setMarginStart(dp(8));
    row.addView(createLabel, labelParams);
    createHint = panelText(UiText.text("PTY / 简易"), 11, 0, false);
    createHint.setTextColor(primaryText);
    createHint.setAlpha(.82f);
    createHint.setGravity(Gravity.END);
    createHint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(createHint, new LinearLayout.LayoutParams(-2, -2));
    row.setOnClickListener(v -> showCreateChooser());
    return row;
  }

  private void applySystemBars() {
    if (root == null) return;
    boolean panel = workbench;
    getWindow()
        .setStatusBarColor(
            panel ? getColor(R.color.terminal_panel) : android.graphics.Color.TRANSPARENT);
    getWindow().setNavigationBarColor(getColor(R.color.terminal_surface));
    var bars = WindowCompat.getInsetsController(getWindow(), root);
    bars.setAppearanceLightStatusBars(panel && !ThemeController.isDark(this));
    bars.setAppearanceLightNavigationBars(false);
    if (statusBarScrim != null) statusBarScrim.setVisibility(panel ? View.VISIBLE : View.GONE);
  }

  private void setCompoundIcon(TextView value, int iconRes, int tintRes, int sizeDp) {
    setCompoundIcon(value, iconRes, ColorStateList.valueOf(getColor(tintRes)), sizeDp);
  }

  private void setCompoundIcon(TextView value, int iconRes, ColorStateList tint, int sizeDp) {
    Drawable icon = getDrawable(iconRes);
    if (icon == null) return;
    icon = icon.mutate();
    icon.setTintList(tint);
    icon.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
    value.setCompoundDrawablesRelative(icon, null, null, null);
    value.setCompoundDrawablePadding(dp(7));
  }

  private AppCompatImageButton iconButton(
      int iconRes, String description, Runnable action, int tintRes) {
    AppCompatImageButton value = new AppCompatImageButton(this);
    value.setImageResource(iconRes);
    value.setImageTintList(ColorStateList.valueOf(getColor(tintRes)));
    value.setBackgroundResource(R.drawable.bg_terminal_touch);
    value.setContentDescription(description);
    value.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
    value.setPadding(dp(12), dp(12), dp(12), dp(12));
    value.setMinimumWidth(dp(48));
    value.setMinimumHeight(dp(48));
    value.setFocusable(true);
    value.setClickable(true);
    value.setOnClickListener(v -> action.run());
    return value;
  }

  private View divider(int colorRes) {
    View divider = new View(this);
    divider.setBackgroundColor(getColor(colorRes));
    return divider;
  }

  private TerminalPane pane() {
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.terminal_content);
    return fragment instanceof TerminalPane ? (TerminalPane) fragment : null;
  }

  /** 宽度决定内联/抽屉，工作台与极简只在“何时显示”上不同；旋转和分屏都必须重新判定。 */
  private void applyPresentation() {
    if (root == null || sessionPanel == null || menuButton == null || isDestroyed()) return;
    int widthDp = workspaceWidthDp();
    int heightDp = workspaceHeightDp();
    boolean canInline = TerminalWorkspacePolicy.canInline(widthDp);
    expanded = canInline && (workbench || sessionsRequested);
    landscapeCompact = TerminalWorkspacePolicy.shortLandscape(widthDp, heightDp);
    toolbar.setVisibility(workbench ? View.VISIBLE : View.GONE);
    toolbarDivider.setVisibility(workbench ? View.VISIBLE : View.GONE);
    // 极简内联时面板本身带关闭按钮；隐藏球，避免 RTL/左吸边时叠在侧栏上。
    ball.setVisibility(workbench || (expanded && !workbench) ? View.GONE : View.VISIBLE);
    menuButton.setVisibility(workbench && !expanded ? View.VISIBLE : View.GONE);
    settingsButton.setVisibility(workbench && expanded ? View.VISIBLE : View.GONE);
    helpButton.setVisibility(workbench && expanded ? View.VISIBLE : View.GONE);
    float density = getResources().getDisplayMetrics().density;
    applyCompactChrome(
        Math.round((expanded ? inlineWidthPx() : drawerWidthPx()) / density),
        expanded && body.getHeight() > 0 ? Math.round(body.getHeight() / density) : heightDp);
    if (expanded) {
      showInlinePanel();
    } else if (sessionsRequested) {
      if (drawerShowing()) updateDrawerPresentation();
      else showDrawer(false);
    } else {
      hideSessionsPanel();
    }
    ball.setSessionsExpanded(expanded || sessionsRequested);
    applySystemBars();
  }

  private void schedulePresentation() {
    if (root == null || presentationScheduled) return;
    presentationScheduled = true;
    root.post(
        () -> {
          presentationScheduled = false;
          applyPresentation();
        });
  }

  private void applyCompactChrome(int panelWidthDp, int panelHeightDp) {
    float fontScale = getResources().getConfiguration().fontScale;
    if (chromeWidthDp == panelWidthDp
        && chromeHeightDp == panelHeightDp
        && Float.compare(chromeFontScale, fontScale) == 0
        && chromeLandscapeCompact == landscapeCompact) return;
    chromeWidthDp = panelWidthDp;
    chromeHeightDp = panelHeightDp;
    chromeFontScale = fontScale;
    chromeLandscapeCompact = landscapeCompact;
    compactHeight = TerminalWorkspacePolicy.compactHeight(panelHeightDp, fontScale);
    denseFooter = TerminalWorkspacePolicy.denseFooter(panelHeightDp, fontScale);
    boolean tight = compactHeight || landscapeCompact;
    sessionSubtitle.setVisibility(tight ? View.GONE : View.VISIBLE);
    panelSecondary.setVisibility(tight ? View.GONE : View.VISIBLE);
    sessionHeader.setPaddingRelative(dp(14), dp(tight ? 8 : 12), dp(6), dp(tight ? 4 : 8));
    sessionFooter.setPadding(
        dp(landscapeCompact ? 6 : 8), dp(tight ? 4 : 6),
        dp(landscapeCompact ? 6 : 8), dp(tight ? 6 : 10));
    sessionList.setPadding(dp(6), dp(tight ? 0 : 4), dp(6), dp(tight ? 6 : 12));
    createHint.setVisibility(
        landscapeCompact || TerminalWorkspacePolicy.stackSessionDetails(panelWidthDp, fontScale)
            ? View.GONE
            : View.VISIBLE);
    boolean compactActions = denseFooter || landscapeCompact;
    footerActionsWide.setVisibility(compactActions ? View.GONE : View.VISIBLE);
    footerActionsDense.setVisibility(compactActions ? View.VISIBLE : View.GONE);
    int wideColumns = TerminalWorkspacePolicy.footerColumns(panelWidthDp, fontScale, false);
    int denseColumns = TerminalWorkspacePolicy.footerColumns(panelWidthDp, fontScale, true);
    if (wideColumns != footerWideColumns) {
      reflowFooterActions(footerActionsWide, wideColumns);
      footerWideColumns = wideColumns;
    }
    if (denseColumns != footerDenseColumns) {
      reflowFooterActions(footerActionsDense, denseColumns);
      footerDenseColumns = denseColumns;
    }
    boolean stacked = TerminalWorkspacePolicy.stackSessionDetails(panelWidthDp, fontScale);
    if (stackedSessionDetails != stacked) {
      stackedSessionDetails = stacked;
      if (sessionsRendered) renderSessions();
    }
  }

  /** 列数只改变已有按钮的排布，不替换点击回调或完整的无障碍名称。 */
  private void reflowFooterActions(LinearLayout footer, int columns) {
    View focused = footer.findFocus();
    java.util.ArrayList<TextView> buttons = new java.util.ArrayList<>();
    for (int rowIndex = 0; rowIndex < footer.getChildCount(); rowIndex++) {
      ViewGroup row = (ViewGroup) footer.getChildAt(rowIndex);
      while (row.getChildCount() > 0) {
        TextView button = (TextView) row.getChildAt(0);
        row.removeView(button);
        buttons.add(button);
      }
    }
    footer.removeAllViews();
    for (int index = 0; index < buttons.size(); index += columns) {
      addFooterGridRow(
          footer,
          buttons
              .subList(index, Math.min(buttons.size(), index + columns))
              .toArray(new TextView[0]));
    }
    if (focused != null) focused.requestFocus();
  }

  /** 短窗使用简短标签，不缩小系统字号；保留换行和至少 48dp 的触摸高度。 */
  private void compactDenseActions() {
    for (int rowIndex = 0; rowIndex < footerActionsDense.getChildCount(); rowIndex++) {
      View row = footerActionsDense.getChildAt(rowIndex);
      if (!(row instanceof ViewGroup group)) continue;
      for (int index = 0; index < group.getChildCount(); index++) {
        if (!(group.getChildAt(index) instanceof TextView button)) continue;
        button.setText(denseActionLabel(button.getId()));
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTextSize(12);
        button.setMinHeight(dp(48));
        button.setPaddingRelative(dp(8), dp(6), dp(5), dp(6));
        button.setCompoundDrawablePadding(dp(5));
      }
    }
  }

  /** 短标签配完整无障碍名称，按钮列数由实际宽度和字体决定。 */
  private CharSequence denseActionLabel(int id) {
    if (id == R.id.terminal_sessions_action_home_dense) return UiText.choose("主界面", "Home");
    if (id == R.id.terminal_sessions_action_display_dense) return UiText.choose("显示", "Display");
    if (id == R.id.terminal_sessions_action_keyboard_dense) return UiText.choose("键盘", "Keyboard");
    if (id == R.id.terminal_sessions_action_paste_dense) return UiText.choose("粘贴", "Paste");
    if (id == R.id.terminal_sessions_action_focus_dense) return UiText.choose("聚焦输入", "Focus");
    if (id == R.id.terminal_sessions_action_help_dense) return UiText.choose("说明", "Help");
    return "";
  }

  private void showInlinePanel() {
    if (sessionPanel.getParent() != body) {
      sessionPanel.animate().cancel();
      removeSessionPanel();
      body.addView(sessionPanel, 0, new LinearLayout.LayoutParams(inlineWidthPx(), -1));
      ensureInlineDivider();
      renderSessions();
    } else {
      updateInlineWidth();
      ensureInlineDivider();
    }
    sessionPanel.setVisibility(View.VISIBLE);
    sessionPanel.setElevation(0f);
    sessionPanel.setTranslationX(0f);
    sessionClose.setVisibility(workbench ? View.GONE : View.VISIBLE);
    drawerHost.setVisibility(View.GONE);
    setDrawerAccessibility(false);
    ViewCompat.setAccessibilityPaneTitle(sessionPanel, UiText.text("会话侧栏"));
  }

  private void ensureInlineDivider() {
    if (inlineDivider == null) inlineDivider = divider(R.color.terminal_line);
    if (inlineDivider.getParent() != body) {
      body.addView(
          inlineDivider,
          Math.min(1, body.getChildCount()),
          new LinearLayout.LayoutParams(Math.max(1, dp(1)), -1));
    }
    inlineDivider.setVisibility(View.VISIBLE);
  }

  private void updateInlineWidth() {
    if (sessionPanel.getParent() != body) return;
    ViewGroup.LayoutParams params = sessionPanel.getLayoutParams();
    if (params == null) return;
    int width = inlineWidthPx();
    if (params.width != width) {
      params.width = width;
      sessionPanel.setLayoutParams(params);
    }
  }

  private void showDrawer(boolean animate) {
    if (expanded) return;
    removeInlineDivider();
    if (sessionPanel.getParent() != drawerHost) {
      sessionPanel.animate().cancel();
      removeSessionPanel();
      drawerHost.addView(
          sessionPanel, new FrameLayout.LayoutParams(drawerWidthPx(), -1, Gravity.START));
      renderSessions();
    }
    drawerHost.setVisibility(View.VISIBLE);
    sessionScrim.setVisibility(View.VISIBLE);
    sessionPanel.setVisibility(View.VISIBLE);
    sessionClose.setVisibility(View.VISIBLE);
    sessionPanel.setElevation(dp(8));
    setDrawerAccessibility(true);
    ViewCompat.setAccessibilityPaneTitle(sessionPanel, UiText.text("会话侧栏"));
    ball.setSessionsExpanded(true);
    updateDrawerWidth();
    if (animate) {
      sessionPanel.setTranslationX(drawerHiddenTranslation());
      sessionScrim.setAlpha(0f);
      drawerHost.post(
          () -> {
            if (!sessionsRequested || expanded) return;
            sessionPanel.animate().translationX(0f).setDuration(180).start();
            sessionScrim.animate().alpha(1f).setDuration(180).start();
          });
    } else if (sessionPanel.getTranslationX() != 0f) {
      sessionPanel.setTranslationX(0f);
      sessionScrim.setAlpha(1f);
    }
  }

  private boolean drawerShowing() {
    return drawerHost != null
        && drawerHost.getVisibility() == View.VISIBLE
        && sessionPanel.getParent() == drawerHost
        && sessionPanel.getVisibility() == View.VISIBLE;
  }

  private void updateDrawerPresentation() {
    sessionClose.setVisibility(View.VISIBLE);
    sessionPanel.setElevation(dp(8));
    setDrawerAccessibility(true);
    updateDrawerWidth();
    ball.setSessionsExpanded(true);
  }

  /** Android 没有 iOS 的 accessibilityViewIsModal；抽屉打开时隐藏背景的可访问性。 */
  private void setDrawerAccessibility(boolean open) {
    int mode =
        open
            ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
    content.setImportantForAccessibility(mode);
    toolbar.setImportantForAccessibility(mode);
    status.setImportantForAccessibility(
        open ? View.IMPORTANT_FOR_ACCESSIBILITY_NO : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    ball.setImportantForAccessibility(
        open ? View.IMPORTANT_FOR_ACCESSIBILITY_NO : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    content.setDescendantFocusability(
        open ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_BEFORE_DESCENDANTS);
    toolbar.setDescendantFocusability(
        open ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_BEFORE_DESCENDANTS);
    ball.setFocusable(!open);
    ball.setFocusableInTouchMode(!open);
  }

  private void hideSessionsPanel() {
    if (sessionPanel == null) return;
    sessionPanel.animate().cancel();
    removeInlineDivider();
    // 保持面板挂在抽屉宿主里（GONE），这样恢复/查询视图与状态时不需要重新构造。
    if (sessionPanel.getParent() != drawerHost) {
      removeSessionPanel();
      drawerHost.addView(
          sessionPanel, new FrameLayout.LayoutParams(drawerWidthPx(), -1, Gravity.START));
    }
    if (drawerHost != null) drawerHost.setVisibility(View.GONE);
    sessionPanel.setVisibility(View.GONE);
    sessionPanel.setTranslationX(0f);
    sessionPanel.setElevation(0f);
    setDrawerAccessibility(false);
    if (sessionScrim != null) {
      sessionScrim.animate().cancel();
      sessionScrim.setAlpha(1f);
      sessionScrim.setVisibility(View.VISIBLE);
    }
    if (ball != null) ball.setSessionsExpanded(false);
  }

  private void removeInlineDivider() {
    if (inlineDivider != null && inlineDivider.getParent() instanceof ViewGroup parent)
      parent.removeView(inlineDivider);
  }

  private void removeSessionPanel() {
    if (sessionPanel.getParent() instanceof ViewGroup parent) parent.removeView(sessionPanel);
  }

  private void updateDrawerWidth() {
    if (sessionPanel.getParent() != drawerHost) return;
    ViewGroup.LayoutParams params = sessionPanel.getLayoutParams();
    if (params == null) return;
    int width = drawerWidthPx();
    if (params.width != width) {
      params.width = width;
      sessionPanel.setLayoutParams(params);
    }
  }

  private int drawerWidthPx() {
    int available =
        root == null ? 0 : root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();
    return TerminalWorkspacePolicy.drawerWidth(
        available, dp(TerminalWorkspacePolicy.drawerMaxWidth()), dp(32));
  }

  private int inlineWidthPx() {
    int widthDp = workspaceWidthDp();
    return dp(TerminalWorkspacePolicy.inlineWidth(widthDp > 0 ? widthDp : 840));
  }

  private int workspaceWidthDp() {
    if (root == null) return 0;
    int width = Math.max(0, root.getWidth() - root.getPaddingLeft() - root.getPaddingRight());
    return Math.round(width / getResources().getDisplayMetrics().density);
  }

  private int workspaceHeightDp() {
    if (root == null) return 0;
    int height = Math.max(0, root.getHeight() - root.getPaddingTop() - root.getPaddingBottom());
    return Math.round(height / getResources().getDisplayMetrics().density);
  }

  private int drawerHiddenTranslation() {
    return root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
        ? drawerWidthPx()
        : -drawerWidthPx();
  }

  private void toggleSessions() {
    if (sessionsRequested) {
      closeSessions();
      return;
    }
    if (expanded) return;
    openSessions();
  }

  void openSessions() {
    if (sessionsRequested) return;
    sessionsRequested = true;
    renderSessions();
    applyPresentation();
    if (!expanded) showDrawer(true);
    if (sessionClose.getVisibility() == View.VISIBLE) {
      sessionClose.requestFocus();
      sessionClose.performAccessibilityAction(
          AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
    }
  }

  private void closeSessions() {
    closeSessions(true);
  }

  /**
   * 关闭会话侧栏。
   *
   * <p>{@code restoreSurfaceFocus} 会在侧栏关闭后把焦点交给悬浮球/菜单；「回到输入」「显示键盘」
   * 这类动作必须传 false，否则输入框焦点会被抢走，表现为第一次点按没有回到输入。
   */
  private void closeSessions(boolean restoreSurfaceFocus) {
    if (workbench && expanded) return;
    if (!sessionsRequested && !expanded) return;
    sessionsRequested = false;
    applyPresentation();
    if (restoreSurfaceFocus) restoreSurfaceFocus();
  }

  /** 收起侧栏后立即执行输入动作；不再把焦点交还悬浮球，避免第一次点按只收起侧栏。 */
  private void closeSessionsThen(Runnable action) {
    closeSessions(false);
    if (!isFinishing() && !isDestroyed()) action.run();
  }

  private boolean sessionsDismissible() {
    return sessionsRequested && !(workbench && expanded);
  }

  private void restoreSurfaceFocus() {
    View target =
        ball.getVisibility() == View.VISIBLE
            ? ball
            : menuButton.getVisibility() == View.VISIBLE ? menuButton : null;
    if (target != null) target.requestFocus();
  }

  @Override
  public void onTerminalPaneChanged() {
    if (root == null || isDestroyed()) return;
    TerminalPane pane = pane();
    boolean pty = pane == null ? PtyTerminalFragment.preferred(this) : pane.isPty();
    var tab = pty ? owner.currentPty() : owner.currentSimple();
    title.setText(tab == null ? UiText.text("暂无终端") : sessionDisplayName(pty, tab));
    String warning =
        previousProcess
            ? UiText.choose(
                "应用进程已重建；旧会话不能恢复，请手动新建。",
                "The app process restarted. Old sessions cannot be restored; create a new session explicitly.")
            : "";
    if (tab != null && tab.isCloseUnconfirmed())
      warning =
          UiText.choose(
              "退出待核验；会话和环境保护仍保留。",
              "Exit unconfirmed; the session and runtime protection are retained.");
    if (tab != null && tab.isClosing())
      warning = UiText.choose("正在结束进程并核验退出…", "Ending the process and verifying exit…");
    if (owner.protectionSnapshot().state == TerminalRuntimeProtection.State.LOST)
      warning =
          UiText.choose(
              "后台运行保护已丢失；会话未被自动结束，请查看会话状态。",
              "Background protection was lost. Sessions were not automatically ended; inspect their status.");
    status.setText(warning);
    status.setVisibility(warning.isEmpty() ? View.GONE : View.VISIBLE);
    if (sessionPanel != null && sessionPanel.getVisibility() == View.VISIBLE) renderSessions();
  }

  private String sessionLabel(boolean pty, TerminalTabs.Tab<?> tab) {
    String name = tab.customTitle();
    return UiText.format(
        "%s · %s",
        pty ? "PTY" : UiText.choose("简易", "Simple"),
        name.isEmpty() ? UiText.format("终端 %s", tab.number) : name);
  }

  /** 侧栏与顶栏只显示单行名称；完整类型/会话号在无障碍描述里。 */
  private String sessionDisplayName(boolean pty, TerminalTabs.Tab<?> tab) {
    String name = tab.customTitle();
    if (!name.isEmpty()) return name;
    return pty
        ? UiText.format("PTY %d", tab.number)
        : UiText.format(UiText.choose("简易 %d", "Simple %d"), tab.number);
  }

  /** 状态胶囊只放短词；完整状态仍由无障碍描述与对话框说明。 */
  private String sessionShortState(boolean pty, TerminalTabs.Tab<?> tab) {
    if (tab.isClosing()) return UiText.choose("结束中", "Ending");
    if (tab.isCloseUnconfirmed()) return UiText.choose("待核验", "Unverified");
    if (pty) {
      var process = (PtySession) tab.value;
      if (process.isRunning()) return UiText.choose("运行中", "Running");
      return process.isCleanupPending()
          ? UiText.choose("待清理", "Cleanup")
          : UiText.choose("已退出", "Exited");
    }
    var simple = (TerminalSessionOwner.SimpleTab) tab.value;
    return switch (simple.session.state()) {
      case STARTING -> UiText.choose("启动中", "Starting");
      case READY -> UiText.choose("就绪", "Ready");
      case BUSY -> UiText.choose("执行中", "Busy");
      case STOPPING -> UiText.choose("停止中", "Stopping");
      case FAILED -> UiText.choose("失败", "Failed");
      default -> UiText.choose("未运行", "Idle");
    };
  }

  private String sessionState(boolean pty, TerminalTabs.Tab<?> tab) {
    if (tab.isClosing()) return UiText.choose("正在结束并核验", "Ending and verifying");
    if (tab.isCloseUnconfirmed()) return UiText.choose("退出待核验", "Exit unconfirmed");
    if (pty) {
      var process = (PtySession) tab.value;
      if (process.isRunning()) return UiText.choose("运行中", "Running");
      return process.isCleanupPending()
          ? UiText.choose("进程已退出 · 清理待核验", "Process exited · cleanup unverified")
          : UiText.choose("已核验退出 · 历史保留", "Exit verified · history retained");
    }
    var simple = (TerminalSessionOwner.SimpleTab) tab.value;
    return switch (simple.session.state()) {
      case STARTING -> UiText.choose("启动中", "Starting");
      case READY -> UiText.choose("就绪", "Ready");
      case BUSY -> UiText.choose("执行中", "Busy");
      case STOPPING -> UiText.choose("停止中", "Stopping");
      case FAILED -> UiText.choose("失败 · 请检查", "Failed · inspect session");
      default -> UiText.choose("未运行", "Not running");
    };
  }

  private boolean sessionRunning(boolean pty, TerminalTabs.Tab<?> tab) {
    if (tab.isClosing() || tab.isCloseUnconfirmed()) return false;
    if (pty) return ((PtySession) tab.value).isRunning();
    var state = ((TerminalSessionOwner.SimpleTab) tab.value).session.state();
    return state == TerminalSession.State.READY || state == TerminalSession.State.BUSY;
  }

  void showDisplaySettings() {
    dismissPanel();
    String[] choices = {
      UiText.choose("字号减小", "Smaller text"),
      UiText.choose("字号增大", "Larger text"),
      workbench
          ? UiText.choose("切换极简模式", "Use minimal mode")
          : UiText.choose("切换工作台模式", "Use workbench mode"),
      UiText.choose("重置菜单位置", "Reset menu position"),
      UiText.choose("快捷按键布局", "Extra keys layout")
    };
    panel =
        new DshaDialogBuilder(this)
            .setTitle(UiText.choose("显示设置", "Display settings"))
            .setItems(
                choices,
                (dialog, index) -> {
                  if (index < 2 && pane() != null) pane().changeFont(index == 0 ? -1 : 1);
                  if (index == 2) {
                    workbench = !workbench;
                    getSharedPreferences("deepseekharness", MODE_PRIVATE)
                        .edit()
                        .putBoolean(WORKBENCH, workbench)
                        .apply();
                    applyPresentation();
                  }
                  if (index == 3) ball.resetPosition();
                  if (index == 4)
                    ExtraKeysDialog.show(
                        TerminalActivity.this,
                        () -> {
                          var current = pane();
                          if (current != null) current.refreshExtraKeys();
                        });
                })
            .show();
  }

  private void showCreateChooser() {
    dismissPanel();
    String[] choices = {
      UiText.choose("PTY · 交互会话", "PTY · interactive session"),
      UiText.choose("简易 · 按行命令", "Simple · line commands")
    };
    panel =
        new DshaDialogBuilder(this)
            .setTitle(UiText.choose("新建终端会话", "New terminal session"))
            .setItems(
                choices,
                (dialog, index) -> {
                  dismissPanel();
                  createInMode(index == 0);
                })
            .setNegativeButton(UiText.text("取消"), null)
            .show();
  }

  private void showSessionHelp() {
    dismissPanel();
    panel =
        new DshaDialogBuilder(this)
            .setTitle(UiText.choose("会话侧栏", "Sessions sidebar"))
            .setMessage(
                UiText.choose(
                    "点击悬浮球或顶栏“菜单”打开会话侧栏。平板宽屏在侧栏打开时内联显示并保留终端，工作台宽屏常驻；窄窗口用抽屉。返回只关闭页面，结束进程必须在会话行明确确认并核验。",
                    "Tap the floating ball or the top menu to open the sessions sidebar. On wide tablets the sidebar stays inline beside the terminal when opened, and is persistent in workbench mode; narrow windows use a drawer. Back only closes the page; ending a process requires an explicit confirmed and verified action on its row."))
            .setPositiveButton(UiText.choose("知道了", "Got it"), null)
            .show();
  }

  private void renderSessions() {
    if (sessionList == null) return;
    int scrollY = sessionsRendered && sessionScroll != null ? sessionScroll.getScrollY() : -1;
    int listOffset = scrollY - sessionList.getTop();
    sessionList.removeAllViews();
    var ptyTabs = owner.ptyTabs().snapshot();
    var simpleTabs = owner.simpleTabs().snapshot();
    addGroupHeader(
        UiText.choose("PTY · 交互会话", "PTY · interactive sessions"),
        ptyTabs.size(),
        R.drawable.ic_terminal_pty);
    if (ptyTabs.isEmpty()) addGroupEmpty();
    for (var tab : ptyTabs) addSessionRow(true, tab);
    addGroupHeader(
        UiText.choose("简易 · 按行命令", "Simple · line commands"),
        simpleTabs.size(),
        R.drawable.ic_terminal_simple);
    if (simpleTabs.isEmpty()) addGroupEmpty();
    for (var tab : simpleTabs) addSessionRow(false, tab);
    int total = ptyTabs.size() + simpleTabs.size();
    if (sessionSummary != null)
      sessionSummary.setText(
          UiText.format(
              UiText.choose("%d 个会话 · PTY %d / 简易 %d", "%d sessions · PTY %d / simple %d"),
              total,
              ptyTabs.size(),
              simpleTabs.size()));
    if (closeAllButton != null) closeAllButton.setVisibility(total > 0 ? View.VISIBLE : View.GONE);
    sessionsRendered = true;
    if (scrollY > 0 && sessionScroll != null)
      sessionScroll.post(
          () -> sessionScroll.scrollTo(0, Math.max(0, listOffset + sessionList.getTop())));
    refreshCloseAllNotice();
  }

  private void addGroupHeader(String text, int count, int iconRes) {
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(8), dp(14), dp(8), dp(6));
    row.addView(
        iconView(iconRes, R.color.terminal_faint, 15),
        new LinearLayout.LayoutParams(dp(20), dp(20)));
    TextView heading = panelText(text, 11, getColor(R.color.terminal_muted), true);
    LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1);
    headingParams.setMarginStart(dp(7));
    row.addView(heading, headingParams);
    TextView number = panelText(String.valueOf(count), 10, getColor(R.color.terminal_muted), false);
    number.setGravity(Gravity.CENTER);
    number.setMinWidth(dp(22));
    number.setMinHeight(dp(20));
    number.setPadding(dp(7), 0, dp(7), 0);
    number.setBackgroundResource(R.drawable.bg_terminal_count_pill);
    row.addView(number, new LinearLayout.LayoutParams(-2, -2));
    sessionList.addView(row, new LinearLayout.LayoutParams(-1, -2));
  }

  private void addGroupEmpty() {
    TextView empty =
        panelText(
            UiText.choose("暂无会话", "No sessions"), 11, getColor(R.color.terminal_faint), false);
    empty.setBackgroundResource(R.drawable.bg_terminal_empty);
    empty.setGravity(Gravity.CENTER_VERTICAL);
    empty.setPadding(dp(10), dp(8), dp(10), dp(8));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.setMargins(dp(2), dp(2), dp(2), dp(4));
    sessionList.addView(empty, params);
  }

  private void addSessionRow(boolean pty, TerminalTabs.Tab<?> tab) {
    TerminalTabs.Tab<?> current = pty ? owner.currentPty() : owner.currentSimple();
    boolean active = current != null && current.id == tab.id;
    boolean running = sessionRunning(pty, tab);
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(dp(56));
    if (active) row.setBackgroundResource(R.drawable.bg_terminal_session);
    View accent = new View(this);
    accent.setBackgroundResource(R.drawable.bg_terminal_accent_bar);
    accent.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
    row.addView(accent, new LinearLayout.LayoutParams(Math.max(1, dp(3)), -1));
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.HORIZONTAL);
    body.setGravity(Gravity.CENTER_VERTICAL);
    body.setPadding(dp(8), dp(6), dp(4), dp(6));
    body.setFocusable(true);
    body.setClickable(true);
    body.setBackgroundResource(R.drawable.bg_terminal_touch);
    AppCompatImageView glyph =
        iconView(
            pty ? R.drawable.ic_terminal_pty : R.drawable.ic_terminal_simple,
            active ? R.color.terminal_accent : R.color.terminal_muted,
            16);
    glyph.setBackgroundResource(
        active ? R.drawable.bg_terminal_icon_block_active : R.drawable.bg_terminal_icon_block);
    glyph.setPadding(dp(7), dp(7), dp(7), dp(7));
    glyph.setVisibility(stackedSessionDetails ? View.GONE : View.VISIBLE);
    body.addView(glyph, new LinearLayout.LayoutParams(dp(30), dp(30)));
    LinearLayout details = new LinearLayout(this);
    details.setOrientation(stackedSessionDetails ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    details.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1);
    detailsParams.setMarginStart(dp(stackedSessionDetails ? 0 : 9));
    body.addView(details, detailsParams);
    TextView name =
        panelText(
            sessionDisplayName(pty, tab),
            13,
            getColor(active ? R.color.terminal_accent : R.color.terminal_panel_text),
            active);
    name.setSingleLine(true);
    name.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams nameParams =
        stackedSessionDetails
            ? new LinearLayout.LayoutParams(-1, -2)
            : new LinearLayout.LayoutParams(0, -2, 1);
    details.addView(name, nameParams);
    TextView state =
        panelText(
            sessionShortState(pty, tab),
            10,
            getColor(active || running ? R.color.terminal_accent : R.color.terminal_muted),
            false);
    state.setSingleLine(true);
    state.setGravity(Gravity.CENTER);
    state.setPadding(dp(7), dp(3), dp(7), dp(3));
    state.setBackgroundResource(
        active ? R.drawable.bg_terminal_state_pill_active : R.drawable.bg_terminal_state_pill);
    state.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-2, -2);
    stateParams.setMarginStart(dp(stackedSessionDetails ? 0 : 6));
    stateParams.topMargin = dp(stackedSessionDetails ? 3 : 0);
    stateParams.gravity = Gravity.START;
    details.addView(state, stateParams);
    String description = UiText.format("%s · %s", sessionLabel(pty, tab), sessionState(pty, tab));
    if (active)
      description = UiText.format(UiText.choose("当前会话 · %s", "Current session · %s"), description);
    body.setContentDescription(description);
    body.setOnClickListener(v -> selectSession(pty, tab.id));
    row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
    View rename =
        iconButton(
            R.drawable.ic_terminal_rename,
            UiText.format(UiText.choose("重命名 %s", "Rename %s"), sessionLabel(pty, tab)),
            () -> rename(pty, tab.id),
            R.color.terminal_muted);
    rename.setEnabled(!tab.isClosing());
    rename.setAlpha(rename.isEnabled() ? 1f : .35f);
    row.addView(rename, new LinearLayout.LayoutParams(dp(48), dp(48)));
    View close =
        iconButton(
            tab.isCloseUnconfirmed()
                ? R.drawable.ic_recovery_refresh
                : R.drawable.ic_terminal_close,
            UiText.format(
                tab.isCloseUnconfirmed()
                    ? UiText.choose("核验并结束 %s", "Verify and end %s")
                    : UiText.choose("结束 %s 的进程", "End process for %s"),
                sessionLabel(pty, tab)),
            () -> requestCloseSession(pty, tab.id),
            tab.isCloseUnconfirmed() ? R.color.terminal_warning : R.color.terminal_muted);
    close.setEnabled(!tab.isClosing());
    close.setAlpha(close.isEnabled() ? 1f : .35f);
    row.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
    rowParams.bottomMargin = dp(2);
    sessionList.addView(row, rowParams);
    if (!tab.closeError().isEmpty()) {
      TextView error =
          panelText(
              SensitiveData.redact(tab.closeError()), 11, getColor(R.color.terminal_danger), false);
      error.setPadding(dp(10), 0, dp(10), dp(6));
      sessionList.addView(error, new LinearLayout.LayoutParams(-1, -2));
    }
  }

  private AppCompatImageView iconView(int iconRes, int tintRes, int sizeDp) {
    AppCompatImageView view = new AppCompatImageView(this);
    view.setImageResource(iconRes);
    view.setImageTintList(ColorStateList.valueOf(getColor(tintRes)));
    view.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    view.setMinimumWidth(dp(sizeDp));
    view.setMinimumHeight(dp(sizeDp));
    return view;
  }

  private void selectSession(boolean pty, long id) {
    closeSessions();
    dismissPanel();
    if (pty) owner.selectPty(id);
    else owner.selectSimple(id);
    switchTerminalMode(pty);
    root.post(
        () -> {
          if (pane() != null) pane().restoreInputFocus();
        });
  }

  private void rename(boolean pty, long id) {
    var tab = pty ? owner.ptyTabs().find(id) : owner.simpleTabs().find(id);
    if (tab == null) return;
    dismissPanel();
    EditText input = new EditText(this);
    input.setSingleLine();
    input.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(80)});
    input.setText(tab.customTitle());
    panel =
        new DshaDialogBuilder(this)
            .setTitle(UiText.choose("重命名会话", "Rename session"))
            .setMessage(
                UiText.choose(
                    "留空恢复默认名称，最多 80 个字符",
                    "Leave blank to restore the default name. Maximum 80 characters."))
            .setView(input)
            .setNegativeButton(UiText.text("取消"), null)
            .setPositiveButton(
                UiText.text("保存"),
                (d, w) -> {
                  if (pty) owner.renamePty(id, input.getText().toString());
                  else owner.renameSimple(id, input.getText().toString());
                  onTerminalPaneChanged();
                })
            .show();
  }

  @Override
  public void requestCloseSession(boolean pty, long id) {
    var tab = pty ? owner.ptyTabs().find(id) : owner.simpleTabs().find(id);
    if (tab == null || tab.isClosing()) return;
    dismissPanel();
    final String epoch = owner.ownerEpoch();
    panel =
        new DshaDialogBuilder(this)
            .setTitle(UiText.choose("结束此会话的进程？", "End this session's process?"))
            .setMessage(
                UiText.format(
                    "%s\n%s",
                    sessionLabel(pty, tab),
                    UiText.choose(
                        "这不是返回页面。只结束此会话，核验退出成功后才移除；其他会话和 Web 不受影响。",
                        "This is not page navigation. Only this session is ended and removed after verified exit; other sessions and Web are unaffected.")))
            .setNegativeButton(UiText.text("取消"), null)
            .setPositiveButton(
                UiText.choose("结束并核验", "End and verify"),
                (d, w) -> {
                  if (!epoch.equals(owner.ownerEpoch())) return;
                  // 不切页、不重放输入；后台关闭结果由owner保留，宿主离开不会抹掉结果。
                  TerminalSessionOwner.CloseCallback completed =
                      (closedEpoch, kind, closedId, error) -> {
                        if (epoch.equals(closedEpoch)) runOnUiThread(this::onTerminalPaneChanged);
                      };
                  if (pty) owner.closePtyAsync(id, 5000, completed);
                  else owner.closeSimpleAsync(id, 5000, completed);
                  onTerminalPaneChanged();
                })
            .show();
  }

  /**
   * 尽力关闭：每个会话仍走 owner 的关闭与核验，不在宿主遍历裸 PID，也不把未确认伪装成已关闭。
   * 维护流程仍使用其原有的 fail-fast closeAll，不与此入口混用。
   */
  private void confirmCloseAll() {
    if (closeAllRunning || refuseCloseAllWhileCreating()) return;
    var ptyTabs = owner.ptyTabs().snapshot();
    var simpleTabs = owner.simpleTabs().snapshot();
    int total = ptyTabs.size() + simpleTabs.size();
    if (total == 0) return;
    final java.util.Set<Long> expectedPty = new java.util.HashSet<>();
    final java.util.Set<Long> expectedSimple = new java.util.HashSet<>();
    for (var tab : ptyTabs) expectedPty.add(tab.id);
    for (var tab : simpleTabs) expectedSimple.add(tab.id);
    dismissPanel();
    panel =
        new DshaDialogBuilder(this)
            .setTitle(UiText.choose("结束全部终端？", "End all terminal sessions?"))
            .setMessage(
                UiText.format(
                    UiText.choose(
                        "将结束 %1$d 个会话（PTY %2$d / 简易 %3$d）。\n这不是返回页面：每个进程分别结束并核验，未确认的会话会保留并标记，Web 和其他功能不受影响。",
                        "This ends %1$d sessions (PTY %2$d / simple %3$d). This is not page navigation: each process is ended and verified separately. Unconfirmed sessions remain marked, and Web or other features are unaffected."),
                    total,
                    ptyTabs.size(),
                    simpleTabs.size()))
            .setNegativeButton(UiText.text("取消"), null)
            .setPositiveButton(
                UiText.choose("全部结束并核验", "End all and verify"),
                (d, w) -> closeAllBestEffort(expectedPty, expectedSimple))
            .show();
  }

  private boolean terminalCreationPending() {
    var snapshot = owner.protectionSnapshot();
    return snapshot.preparing > 0 || snapshot.creating > 0;
  }

  private boolean refuseCloseAllWhileCreating() {
    if (!terminalCreationPending()) return false;
    refreshCloseAllNotice();
    return true;
  }

  private void closeAllBestEffort(
      java.util.Set<Long> expectedPty, java.util.Set<Long> expectedSimple) {
    if (closeAllRunning || refuseCloseAllWhileCreating()) return;
    var ptyTabs = owner.ptyTabs().snapshot();
    var simpleTabs = owner.simpleTabs().snapshot();
    java.util.Set<Long> actualPty = new java.util.HashSet<>();
    java.util.Set<Long> actualSimple = new java.util.HashSet<>();
    for (var tab : ptyTabs) actualPty.add(tab.id);
    for (var tab : simpleTabs) actualSimple.add(tab.id);
    if (!actualPty.equals(expectedPty) || !actualSimple.equals(expectedSimple)) {
      android.widget.Toast.makeText(
              this,
              UiText.choose(
                  "会话列表已改变，请重新确认结束全部。",
                  "The session list changed. Confirm again before ending all sessions."),
              android.widget.Toast.LENGTH_LONG)
          .show();
      return;
    }
    int total = ptyTabs.size() + simpleTabs.size();
    if (total == 0) return;
    final int run = ++closeAllRun;
    closeAllRunning = true;
    closeAllDispatching = true;
    closeAllEpoch = owner.ownerEpoch();
    closeAllTotal = total;
    closeAllPending.set(0);
    closeAllNotice = "";
    closeAllPtyIds.clear();
    for (var tab : ptyTabs) closeAllPtyIds.add(tab.id);
    closeAllSimpleIds.clear();
    for (var tab : simpleTabs) closeAllSimpleIds.add(tab.id);
    final TerminalSessionOwner.CloseCallback callback =
        (epoch, kind, id, error) -> {
          if (run != closeAllRun) return;
          if (epoch == null || !epoch.equals(closeAllEpoch)) return;
          closeAllPending.decrementAndGet();
          runOnUiThread(
              () -> {
                if (isDestroyed() || run != closeAllRun) return;
                if (closeAllRunning && !closeAllDispatching && closeAllPending.get() <= 0)
                  finishCloseAll();
                else {
                  refreshCloseAllNotice();
                  renderSessions();
                }
              });
        };
    for (var tab : ptyTabs) {
      closeAllPending.incrementAndGet();
      if (!owner.closePtyAsync(tab.id, 5000, callback)) closeAllPending.decrementAndGet();
    }
    for (var tab : simpleTabs) {
      closeAllPending.incrementAndGet();
      if (!owner.closeSimpleAsync(tab.id, 5000, callback)) closeAllPending.decrementAndGet();
    }
    closeAllDispatching = false;
    if (closeAllPending.get() <= 0) {
      finishCloseAll();
      return;
    }
    if (root != null) {
      if (closeAllWatchdog != null) root.removeCallbacks(closeAllWatchdog);
      closeAllWatchdog =
          () -> {
            if (run == closeAllRun && closeAllRunning && !isDestroyed()) finishCloseAll();
          };
      root.postDelayed(closeAllWatchdog, 5000L * Math.max(1, total) + 5000);
    }
    refreshCloseAllNotice();
    renderSessions();
    onTerminalPaneChanged();
  }

  private void finishCloseAll() {
    if (!closeAllRunning) return;
    closeAllRunning = false;
    closeAllDispatching = false;
    if (root != null && closeAllWatchdog != null) root.removeCallbacks(closeAllWatchdog);
    refreshCloseAllNotice();
    onTerminalPaneChanged();
  }

  /** 只统计本轮发起时快照的会话；新建会话不会被误算进未确认。 */
  private int[] closeAllCounts() {
    int closing = 0, unconfirmed = 0;
    for (var tab : owner.ptyTabs().snapshot()) {
      if (!closeAllPtyIds.contains(tab.id)) continue;
      if (tab.isClosing()) closing++;
      else unconfirmed++;
    }
    for (var tab : owner.simpleTabs().snapshot()) {
      if (!closeAllSimpleIds.contains(tab.id)) continue;
      if (tab.isClosing()) closing++;
      else unconfirmed++;
    }
    int present = closing + unconfirmed;
    return new int[] {Math.max(0, closeAllTotal - present), closing, unconfirmed};
  }

  private void refreshCloseAllNotice() {
    boolean creating = terminalCreationPending();
    if (!closeAllRunning && creating) {
      closeAllNotice =
          UiText.choose(
              "终端会话正在创建，请稍后再结束全部。",
              "A terminal session is being created. Wait before ending all sessions.");
    } else if (closeAllTotal <= 0) {
      closeAllNotice = "";
    } else {
      int[] counts = closeAllCounts();
      int closed = counts[0], closing = counts[1], unconfirmed = counts[2];
      if (closeAllRunning) {
        closeAllNotice =
            UiText.format(
                UiText.choose(
                    "正在结束全部：已关闭 %1$d / %2$d，正在结束 %3$d，未确认 %4$d…",
                    "Ending all: %1$d of %2$d closed, %3$d closing, %4$d unconfirmed…"),
                closed,
                closeAllTotal,
                closing,
                unconfirmed);
      } else if (closing > 0) {
        closeAllNotice =
            UiText.format(
                UiText.choose(
                    "结束全部仍在核验：已关闭 %1$d，正在结束 %2$d，未确认 %3$d。",
                    "End all still verifying: %1$d closed, %2$d closing, %3$d unconfirmed."),
                closed,
                closing,
                unconfirmed);
      } else if (unconfirmed > 0) {
        closeAllNotice =
            UiText.format(
                UiText.choose(
                    "结束全部完成：已关闭 %1$d，未确认 %2$d。未确认会话保留在列表中，可逐个核验。",
                    "End all finished: %1$d closed, %2$d unconfirmed. Unconfirmed sessions remain for individual verification."),
                closed,
                unconfirmed);
      } else {
        closeAllNotice =
            UiText.format(
                UiText.choose("结束全部完成：已关闭 %1$d。", "End all finished: %1$d closed."), closed);
      }
    }
    if (panelNotice != null) {
      panelNotice.setText(closeAllNotice);
      panelNotice.setVisibility(closeAllNotice.isEmpty() ? View.GONE : View.VISIBLE);
    }
    if (closeAllButton != null) {
      closeAllButton.setEnabled(!closeAllRunning && !creating);
      closeAllButton.setAlpha(closeAllRunning || creating ? .55f : 1f);
      closeAllButton.setText(
          closeAllRunning ? UiText.choose("正在结束…", "Ending…") : UiText.choose("结束全部", "End all"));
    }
    if (newSessionButton != null) {
      newSessionButton.setEnabled(!closeAllRunning);
      newSessionButton.setAlpha(closeAllRunning ? .55f : 1f);
    }
  }

  private void resetCloseAllReport() {
    closeAllRun++;
    closeAllTotal = 0;
    closeAllNotice = "";
    closeAllPtyIds.clear();
    closeAllSimpleIds.clear();
    if (panelNotice != null) panelNotice.setVisibility(View.GONE);
  }

  private void createInMode(boolean pty) {
    if (!allowTerminalCreation()
        || changingMode
        || getSupportFragmentManager().isStateSaved()
        || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
      return;
    if (!closeAllRunning) resetCloseAllReport();
    closeSessions();
    dismissPanel();
    previousProcess = false;
    // 禁止模式首次自动创建与菜单新建叠加：显式新建的目标Fragment跳过默认创建。
    replacePane(pty, true);
    if (pane() != null && pane().isPty() == pty) pane().newSession();
  }

  /** A close-all operation applies to its confirmed snapshot; do not start new tabs mid-close. */
  public boolean allowTerminalCreation() {
    if (!closeAllRunning) return true;
    android.widget.Toast.makeText(
            this,
            UiText.choose(
                "正在结束全部会话，请稍后再新建。", "Sessions are being ended. Wait before creating another."),
            android.widget.Toast.LENGTH_SHORT)
        .show();
    return false;
  }

  @Override
  public void switchTerminalMode(boolean pty) {
    replacePane(pty, false);
  }

  private void replacePane(boolean pty, boolean explicitCreate) {
    if (changingMode || getSupportFragmentManager().isStateSaved()) return;
    TerminalPane current = pane();
    if (current != null && current.isPty() == pty) {
      onTerminalPaneChanged();
      return;
    }
    changingMode = true;
    try {
      getSharedPreferences("deepseekharness", MODE_PRIVATE)
          .edit()
          .putBoolean(PtyTerminalFragment.KEY_PTY, pty)
          .apply();
      Fragment fragment = createPane(pty, explicitCreate || previousProcess);
      getSupportFragmentManager()
          .beginTransaction()
          .replace(R.id.terminal_content, fragment)
          .commitNow();
    } finally {
      changingMode = false;
    }
    onTerminalPaneChanged();
  }

  /** 测试可替换视图端口，但生产始终复用原生终端组件。 */
  protected Fragment createPane(boolean pty, boolean skipInitial) {
    Fragment fragment = pty ? new PtyTerminalFragment() : new TerminalFragment();
    Bundle args = new Bundle();
    args.putBoolean("terminal_skip_initial_create", skipInitial);
    fragment.setArguments(args);
    return fragment;
  }

  private void leavePage() {
    // 通知在Main已不存在时也只补同task原生主页，不创建独立最近任务卡片。
    if (isTaskRoot())
      startActivity(
          new Intent(this, MainActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    finish();
  }

  private void dismissPanel() {
    if (panel != null) panel.dismiss();
    panel = null;
  }

  @Override
  protected void onResume() {
    super.onResume();
    owner.protection().addObserver(protectionChanged);
    owner.addSimpleStateObserver(simpleStateChanged);
    onTerminalPaneChanged();
  }

  @Override
  protected void onPause() {
    owner.removeSimpleStateObserver(simpleStateChanged);
    owner.protection().removeObserver(protectionChanged);
    dismissPanel();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (root != null && closeAllWatchdog != null) root.removeCallbacks(closeAllWatchdog);
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putString("terminal_owner", owner.ownerEpoch());
    out.putBoolean(SESSIONS_OPEN, sessionsRequested);
    if (ball != null) {
      out.putBoolean("ball_right", ball.dockRight());
      out.putFloat("ball_fraction", ball.dockFraction());
    }
    super.onSaveInstanceState(out);
  }

  private TextView toolbarLabel(String text) {
    TextView value = new TextView(this);
    value.setText(text);
    value.setTextColor(getColor(R.color.terminal_panel_text));
    value.setTextSize(16);
    value.setTypeface(Typeface.DEFAULT_BOLD);
    value.setMaxLines(2);
    value.setEllipsize(TextUtils.TruncateAt.END);
    value.setPadding(dp(8), dp(4), dp(8), dp(4));
    return value;
  }

  private TextView panelText(String text, float sizeSp, int color, boolean bold) {
    TextView value = new TextView(this);
    value.setText(text);
    value.setTextSize(sizeSp);
    value.setTextColor(color);
    value.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    return value;
  }

  private TextView label(String text) {
    TextView value = new TextView(this);
    value.setText(text);
    value.setTextColor(getColor(R.color.terminal_foreground));
    value.setTextSize(14);
    value.setPadding(dp(12), dp(6), dp(12), dp(6));
    return value;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
