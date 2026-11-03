package com.deepseekharness.app.ui;

import static org.junit.Assert.*;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import com.deepseekharness.app.R;
import com.deepseekharness.app.core.ConfigStore;
import com.deepseekharness.app.core.TerminalSessionOwner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** 实际Activity视图/Back/重建；fake只替代终端输入面，不运行真实guest。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = TerminalNavigationTest.App.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class TerminalActivityLifecycleTest {
  public static class Host extends TerminalActivity {
    @Override
    protected Fragment createPane(boolean pty, boolean skipInitial) {
      var pane = new FakePane();
      Bundle args = new Bundle();
      args.putBoolean("pty", pty);
      args.putBoolean("terminal_skip_initial_create", skipInitial);
      pane.setArguments(args);
      return pane;
    }
  }

  public static class FakePane extends Fragment implements TerminalPane {
    static int creates, closes, input, focuses;
    boolean selection;

    public FakePane() {
      super(android.R.layout.simple_list_item_1);
    }

    public void newSession() {
      creates++;
    }

    public boolean selectSession(long id) {
      return true;
    }

    public void closeSession(long id) {
      closes++;
    }

    public void changeFont(int value) {}

    public void showKeyboard() {}

    public void paste() {
      input++;
    }

    public void restoreInputFocus() {
      focuses++;
    }

    public boolean selectionBack() {
      boolean before = selection;
      selection = false;
      return before;
    }

    public boolean isPty() {
      return getArguments().getBoolean("pty");
    }
  }

  @Before
  public void prepare() {
    new ConfigStore(RuntimeEnvironment.getApplication()).setWelcomed(true);
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("deepseekharness", 0)
        .edit()
        .putBoolean("terminal_workbench", false)
        .apply();
    FakePane.creates = FakePane.closes = FakePane.input = FakePane.focuses = 0;
  }

  @Test
  public void minimalHostKeepsTerminalAndPageBackDoesNotCloseOrWrite() {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    assertEquals(
        View.GONE, host.get().findViewById(R.id.terminal_workspace_toolbar).getVisibility());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    assertEquals(View.VISIBLE, host.get().findViewById(R.id.terminal_menu_ball).getVisibility());
    host.get().getOnBackPressedDispatcher().onBackPressed();
    assertTrue(host.get().isFinishing());
    host.pause().stop().destroy();
    assertEquals(0, FakePane.closes);
    assertEquals(0, FakePane.input);
    assertEquals(0, FakePane.creates);
  }

  @Test
  public void floatingBallOpensSessionsDrawerAndBackClosesItFirst() {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    measure(host.get(), host.get().findViewById(R.id.terminal_workspace), 400, 900);
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    host.get().findViewById(R.id.terminal_menu_ball).performClick();
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_scrim).getVisibility());
    assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        host.get().findViewById(R.id.terminal_content).getImportantForAccessibility());
    assertEquals(
        ViewGroup.FOCUS_BLOCK_DESCENDANTS,
        ((ViewGroup) host.get().findViewById(R.id.terminal_content)).getDescendantFocusability());
    assertFalse(host.get().isFinishing());
    host.get().findViewById(R.id.terminal_sessions_close).performClick();
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
        host.get().findViewById(R.id.terminal_content).getImportantForAccessibility());
    assertEquals(
        ViewGroup.FOCUS_BEFORE_DESCENDANTS,
        ((ViewGroup) host.get().findViewById(R.id.terminal_content)).getDescendantFocusability());
    host.get().findViewById(R.id.terminal_menu_ball).performClick();
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    host.get().getOnBackPressedDispatcher().onBackPressed();
    assertFalse(host.get().isFinishing());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    host.get().getOnBackPressedDispatcher().onBackPressed();
    assertTrue(host.get().isFinishing());
    host.pause().stop().destroy();
    assertEquals(0, FakePane.closes);
    assertEquals(0, FakePane.input);
    assertEquals(0, FakePane.creates);
  }

  @Test
  public void workbenchMenuOpensDrawerBelowExpandedBreakpoint() {
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("deepseekharness", 0)
        .edit()
        .putBoolean("terminal_workbench", true)
        .apply();
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    var workspace = host.get().findViewById(R.id.terminal_workspace);
    float density = host.get().getResources().getDisplayMetrics().density;
    int width = Math.round(400 * density), height = Math.round(1200 * density);
    workspace.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    workspace.layout(0, 0, width, height);
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_workspace_toolbar).getVisibility());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_menu_ball).getVisibility());
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_workspace_menu).getVisibility());
    host.get().findViewById(R.id.terminal_workspace_menu).performClick();
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    host.pause().stop().destroy();
  }

  @Test
  public void expandedWorkbenchUsesInlineSidebarAndNoDrawer() {
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("deepseekharness", 0)
        .edit()
        .putBoolean("terminal_workbench", true)
        .apply();
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    var workspace = host.get().findViewById(R.id.terminal_workspace);
    float density = host.get().getResources().getDisplayMetrics().density;
    int width = Math.round(900 * density), height = Math.round(1200 * density);
    workspace.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    workspace.layout(0, 0, width, height);
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_menu_ball).getVisibility());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_workspace_menu).getVisibility());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    host.pause().stop().destroy();
  }

  @Test
  public void openDrawerStateSurvivesSameProcessRecreateWithoutCreatingSessions() {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    host.get().findViewById(R.id.terminal_menu_ball).performClick();
    Bundle saved = new Bundle();
    host.pause().saveInstanceState(saved).stop().destroy();
    var next = Robolectric.buildActivity(Host.class).create(saved).start().resume();
    assertEquals(
        View.VISIBLE, next.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    assertEquals(0, FakePane.creates);
    assertEquals(0, FakePane.closes);
    assertEquals(0, FakePane.input);
    next.pause().stop().destroy();
  }

  @Test
  public void selectionBackAndImeBackEachConsumeOnlyOneLayer() throws Exception {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    var pane =
        (FakePane) host.get().getSupportFragmentManager().findFragmentById(R.id.terminal_content);
    pane.selection = true;
    host.get().getOnBackPressedDispatcher().onBackPressed();
    assertFalse(host.get().isFinishing());
    assertFalse(pane.selection);
    var ime = TerminalActivity.class.getDeclaredField("imeVisible");
    ime.setAccessible(true);
    ime.setBoolean(host.get(), true);
    host.get().getOnBackPressedDispatcher().onBackPressed();
    assertFalse(host.get().isFinishing());
    assertEquals(0, FakePane.input);
    ime.setBoolean(host.get(), false);
    host.pause().stop().destroy();
  }

  @Test
  public void sameProcessRecreateKeepsOwnerAndDoesNotCreateCommands() {
    var owner = TerminalSessionOwner.shared();
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    Bundle saved = new Bundle();
    host.pause().saveInstanceState(saved).stop().destroy();
    var next = Robolectric.buildActivity(Host.class).create(saved).start().resume();
    assertSame(owner, TerminalSessionOwner.shared());
    assertEquals(0, FakePane.creates);
    assertEquals(0, FakePane.closes);
    assertEquals(0, FakePane.input);
    next.pause().stop().destroy();
  }

  @Test
  public void minimalTabletWideOpensInlineSidebarAndKeepsTerminalAccessible() {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    var workspace = host.get().findViewById(R.id.terminal_workspace);
    measure(host.get(), workspace, 900, 700);
    host.get().findViewById(R.id.terminal_menu_ball).performClick();
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_close).getVisibility());
    assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
        host.get().findViewById(R.id.terminal_content).getImportantForAccessibility());
    assertFalse(host.get().isFinishing());
    host.get().getOnBackPressedDispatcher().onBackPressed();
    assertFalse(host.get().isFinishing());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    host.get().findViewById(R.id.terminal_menu_ball).performClick();
    host.get().findViewById(R.id.terminal_sessions_close).performClick();
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    host.pause().stop().destroy();
  }

  @Test
  public void minimalRtlInlineHidesBallInsteadOfOverlappingPanel() {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    var workspace = host.get().findViewById(R.id.terminal_workspace);
    workspace.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    measure(host.get(), workspace, 900, 700);
    host.get().findViewById(R.id.terminal_menu_ball).performClick();
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_menu_ball).getVisibility());
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_close).getVisibility());
    host.get().findViewById(R.id.terminal_sessions_close).performClick();
    assertEquals(View.VISIBLE, host.get().findViewById(R.id.terminal_menu_ball).getVisibility());
    host.pause().stop().destroy();
  }

  @Test
  public void minimalTabletPortraitUsesModalDrawer() {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    var workspace = host.get().findViewById(R.id.terminal_workspace);
    measure(host.get(), workspace, 640, 900);
    host.get().findViewById(R.id.terminal_menu_ball).performClick();
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        host.get().findViewById(R.id.terminal_content).getImportantForAccessibility());
    assertEquals(
        View.GONE, host.get().findViewById(R.id.terminal_sessions_close_all).getVisibility());
    host.get().findViewById(R.id.terminal_sessions_close).performClick();
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    host.pause().stop().destroy();
  }

  @Test
  public void restoredSidebarSwitchesBetweenDrawerAndInlineWithoutCreatingSessions() {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    var workspace = host.get().findViewById(R.id.terminal_workspace);
    measure(host.get(), workspace, 640, 900);
    host.get().findViewById(R.id.terminal_menu_ball).performClick();
    Bundle saved = new Bundle();
    host.pause().saveInstanceState(saved).stop().destroy();
    var next = Robolectric.buildActivity(Host.class).create(saved).start().resume();
    var nextWorkspace = next.get().findViewById(R.id.terminal_workspace);
    measure(next.get(), nextWorkspace, 900, 700);
    assertEquals(
        View.VISIBLE, next.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
    assertEquals(View.GONE, next.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
        next.get().findViewById(R.id.terminal_content).getImportantForAccessibility());
    measure(next.get(), nextWorkspace, 640, 900);
    assertEquals(
        View.VISIBLE, next.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        next.get().findViewById(R.id.terminal_content).getImportantForAccessibility());
    assertEquals(0, FakePane.creates);
    assertEquals(0, FakePane.closes);
    assertEquals(0, FakePane.input);
    next.pause().stop().destroy();
  }

  @Test
  public void workbenchExpandedAddsSettingsAndHelpToTheTopbar() {
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("deepseekharness", 0)
        .edit()
        .putBoolean("terminal_workbench", true)
        .apply();
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    var workspace = host.get().findViewById(R.id.terminal_workspace);
    measure(host.get(), workspace, 900, 700);
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_workspace_settings).getVisibility());
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_workspace_help).getVisibility());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_workspace_menu).getVisibility());
    measure(host.get(), workspace, 400, 700);
    assertEquals(
        View.GONE, host.get().findViewById(R.id.terminal_workspace_settings).getVisibility());
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_workspace_help).getVisibility());
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_workspace_menu).getVisibility());
    host.pause().stop().destroy();
  }

  @Test
  public void shortWindowsHideSecondaryChromeAndUseDenseFooterActions() {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    var workspace = host.get().findViewById(R.id.terminal_workspace);
    measure(host.get(), workspace, 400, 470);
    assertEquals(
        View.GONE, host.get().findViewById(R.id.terminal_sessions_subtitle).getVisibility());
    assertEquals(
        View.GONE, host.get().findViewById(R.id.terminal_sessions_secondary).getVisibility());
    assertEquals(
        View.VISIBLE,
        host.get().findViewById(R.id.terminal_sessions_actions_dense).getVisibility());
    assertEquals(
        View.GONE, host.get().findViewById(R.id.terminal_sessions_actions).getVisibility());
    measure(host.get(), workspace, 400, 700);
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_subtitle).getVisibility());
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_secondary).getVisibility());
    assertEquals(
        View.GONE, host.get().findViewById(R.id.terminal_sessions_actions_dense).getVisibility());
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_actions).getVisibility());
    host.pause().stop().destroy();
  }

  @Test
  public void focusActionClosesSidebarAndRestoresInputOnFirstClick() {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    measure(host.get(), host.get().findViewById(R.id.terminal_workspace), 400, 900);
    host.get().findViewById(R.id.terminal_menu_ball).performClick();
    View focus = host.get().findViewById(R.id.terminal_sessions_action_focus);
    assertNotNull(focus);
    assertFalse(focus.isFocusableInTouchMode());
    focus.performClick();
    assertEquals(View.GONE, host.get().findViewById(R.id.terminal_sessions_host).getVisibility());
    org.robolectric.shadows.ShadowLooper.idleMainLooper();
    assertEquals(1, FakePane.focuses);
    host.pause().stop().destroy();
  }

  @Test
  public void backgroundSimpleStateRefreshesSidebarWhilePtyPaneIsVisible() throws Exception {
    var owner = TerminalSessionOwner.shared();
    var opened = new java.util.concurrent.CountDownLatch(1);
    var finish = new java.util.concurrent.CountDownLatch(1);
    var tab = startFakeSimple(owner, opened, finish);
    assertTrue(opened.await(2, java.util.concurrent.TimeUnit.SECONDS));
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("deepseekharness", 0)
        .edit()
        .putBoolean(PtyTerminalFragment.KEY_PTY, true)
        .apply();
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    var failed = new java.util.concurrent.CountDownLatch(1);
    Runnable observeFailure =
        () -> {
          if (tab.value.session.state()
              == com.deepseekharness.app.util.TerminalSession.State.FAILED) failed.countDown();
        };
    owner.addSimpleStateObserver(observeFailure);
    try {
      Fragment pane =
          host.get().getSupportFragmentManager().findFragmentById(R.id.terminal_content);
      assertTrue(((FakePane) pane).isPty());
      measure(host.get(), host.get().findViewById(R.id.terminal_workspace), 400, 900);
      host.get().findViewById(R.id.terminal_menu_ball).performClick();
      View sessions = host.get().findViewById(R.id.terminal_sessions_panel);
      assertTrue(
          containsText(sessions, com.deepseekharness.app.util.UiText.choose("启动中", "Starting")));
      finish.countDown();
      assertTrue(failed.await(2, java.util.concurrent.TimeUnit.SECONDS));
      org.robolectric.shadows.ShadowLooper.idleMainLooper();
      assertTrue(
          containsText(sessions, com.deepseekharness.app.util.UiText.choose("失败", "Failed")));
    } finally {
      finish.countDown();
      owner.removeSimpleStateObserver(observeFailure);
      host.pause().stop().destroy();
      owner.closeSimple(tab.id, 2000);
    }
  }

  @Test
  public void endAllWaitsForCreationAndRechecksTheConfirmedSessionList() throws Exception {
    var owner = TerminalSessionOwner.shared();
    var opened = new java.util.concurrent.CountDownLatch(1);
    var tab = startFakeSimple(owner, opened, new java.util.concurrent.CountDownLatch(0));
    assertTrue(opened.await(2, java.util.concurrent.TimeUnit.SECONDS));
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    com.deepseekharness.app.core.TerminalRuntimeProtection.Ticket pending = null;
    com.deepseekharness.app.util.TerminalTabs.Tab<TerminalSessionOwner.SimpleTab> later = null;
    try {
      measure(host.get(), host.get().findViewById(R.id.terminal_workspace), 400, 900);
      host.get().findViewById(R.id.terminal_menu_ball).performClick();
      View button = host.get().findViewById(R.id.terminal_sessions_close_all);
      assertTrue(button.isEnabled());

      pending = prepareUnstartedPty(owner);
      org.robolectric.shadows.ShadowLooper.idleMainLooper();
      assertFalse(button.isEnabled());
      assertTrue(
          containsText(
              host.get().findViewById(R.id.terminal_sessions_panel),
              com.deepseekharness.app.util.UiText.choose(
                  "终端会话正在创建，请稍后再结束全部。",
                  "A terminal session is being created. Wait before ending all sessions.")));
      enterUnpublishedPtyCreation(owner, pending);
      org.robolectric.shadows.ShadowLooper.idleMainLooper();
      assertEquals(1, owner.protectionSnapshot().creating);
      assertFalse(button.isEnabled());
      finishUnpublishedPtyCreation(owner, pending);
      pending = null;
      org.robolectric.shadows.ShadowLooper.idleMainLooper();
      assertTrue(button.isEnabled());

      button.performClick();
      var dialog =
          (androidx.appcompat.app.AlertDialog)
              org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertTrue(dialog.isShowing());
      // A request accepted while the confirmation was open must not be left out.
      pending = prepareUnstartedPty(owner);
      enterUnpublishedPtyCreation(owner, pending);
      org.robolectric.shadows.ShadowLooper.idleMainLooper();
      dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
      org.robolectric.shadows.ShadowLooper.idleMainLooper();
      assertNotNull(owner.simpleTabs().find(tab.id));
      assertFalse(tab.isClosing());
      assertEquals(1, owner.protectionSnapshot().creating);
      finishUnpublishedPtyCreation(owner, pending);
      pending = null;
      org.robolectric.shadows.ShadowLooper.idleMainLooper();

      button.performClick();
      dialog =
          (androidx.appcompat.app.AlertDialog)
              org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertTrue(dialog.isShowing());
      var laterOpened = new java.util.concurrent.CountDownLatch(1);
      later = startFakeSimple(owner, laterOpened, new java.util.concurrent.CountDownLatch(0));
      assertTrue(laterOpened.await(2, java.util.concurrent.TimeUnit.SECONDS));
      dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
      org.robolectric.shadows.ShadowLooper.idleMainLooper();
      assertNotNull(owner.simpleTabs().find(tab.id));
      assertNotNull(owner.simpleTabs().find(later.id));
      assertFalse(tab.isClosing());
      assertFalse(later.isClosing());
      assertEquals(
          com.deepseekharness.app.util.UiText.choose(
              "会话列表已改变，请重新确认结束全部。",
              "The session list changed. Confirm again before ending all sessions."),
          org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    } finally {
      if (pending != null && !pending.cancel()) finishUnpublishedPtyCreation(owner, pending);
      host.pause().stop().destroy();
      owner.closeSimple(tab.id, 2000);
      if (later != null) owner.closeSimple(later.id, 2000);
    }
  }

  private static com.deepseekharness.app.core.TerminalRuntimeProtection.Ticket prepareUnstartedPty(
      TerminalSessionOwner owner) {
    return owner
        .protection()
        .prepare(
            owner.epoch(),
            com.deepseekharness.app.core.TerminalRuntimeProtection.Kind.PTY,
            new com.deepseekharness.app.core.TerminalRuntimeProtection.Admission() {
              @Override
              public void onReady(
                  com.deepseekharness.app.core.TerminalRuntimeProtection.Ticket ticket) {}

              @Override
              public void onRejected(
                  com.deepseekharness.app.core.TerminalRuntimeProtection.Ticket ticket,
                  String reason) {}
            });
  }

  private static void enterUnpublishedPtyCreation(
      TerminalSessionOwner owner,
      com.deepseekharness.app.core.TerminalRuntimeProtection.Ticket ticket)
      throws Exception {
    assertTrue(owner.protection().promoted(ticket.id(), new Object(), () -> true));
    owner
        .protection()
        .beginCreate(
            ticket, owner.epoch(), com.deepseekharness.app.core.TerminalRuntimeProtection.Kind.PTY);
  }

  private static void finishUnpublishedPtyCreation(
      TerminalSessionOwner owner,
      com.deepseekharness.app.core.TerminalRuntimeProtection.Ticket ticket)
      throws Exception {
    var finish =
        com.deepseekharness.app.core.TerminalRuntimeProtection.class.getDeclaredMethod(
            "finishCreate", com.deepseekharness.app.core.TerminalRuntimeProtection.Ticket.class);
    finish.setAccessible(true);
    finish.invoke(owner.protection(), ticket);
  }

  private static com.deepseekharness.app.util.TerminalTabs.Tab<TerminalSessionOwner.SimpleTab>
      startFakeSimple(
          TerminalSessionOwner owner,
          java.util.concurrent.CountDownLatch opened,
          java.util.concurrent.CountDownLatch finish)
          throws Exception {
    var protection = owner.protection();
    var ticket =
        protection.prepare(
            owner.epoch(),
            com.deepseekharness.app.core.TerminalRuntimeProtection.Kind.SIMPLE,
            new com.deepseekharness.app.core.TerminalRuntimeProtection.Admission() {
              @Override
              public void onReady(
                  com.deepseekharness.app.core.TerminalRuntimeProtection.Ticket t) {}

              @Override
              public void onRejected(
                  com.deepseekharness.app.core.TerminalRuntimeProtection.Ticket t, String reason) {}
            });
    assertTrue(protection.promoted(ticket.id(), new Object(), () -> true));
    return owner.createSimple(
        ticket,
        new com.deepseekharness.app.util.TerminalSession.Backend() {
          @Override
          public Process open() throws Exception {
            opened.countDown();
            if (!finish.await(5, java.util.concurrent.TimeUnit.SECONDS))
              throw new java.io.IOException("fixture timed out");
            throw new java.io.IOException("fixture did not start a process");
          }

          @Override
          public void terminate(Process process, long group) {}
        });
  }

  private static boolean containsText(View root, String expected) {
    if (root instanceof android.widget.TextView
        && expected.contentEquals(((android.widget.TextView) root).getText())) return true;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++)
        if (containsText(group.getChildAt(i), expected)) return true;
    }
    return false;
  }

  @Test
  public void sessionMenuControlsClickOnFirstTouchWithoutFocusDetour() {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    measure(host.get(), host.get().findViewById(R.id.terminal_workspace), 400, 900);
    host.get().findViewById(R.id.terminal_menu_ball).performClick();
    View create = host.get().findViewById(R.id.terminal_sessions_create);
    assertNotNull(create);
    // 触摸模式下首击只聚焦、不点击：新建会话与结束会话的控件都不能带这个焦点。
    assertFalse(create.isFocusableInTouchMode());
    assertFalse(host.get().findViewById(R.id.terminal_sessions_close).isFocusableInTouchMode());
    host.pause().stop().destroy();
  }

  @Test
  public void staleProcessBundleDoesNotRecoverOldIdentityOrAutocreate() {
    Bundle saved = new Bundle();
    saved.putString("terminal_owner", "old-process");
    var host = Robolectric.buildActivity(Host.class).create(saved).start().resume();
    Fragment pane = host.get().getSupportFragmentManager().findFragmentById(R.id.terminal_content);
    assertTrue(pane.getArguments().getBoolean("terminal_skip_initial_create"));
    assertEquals(
        View.VISIBLE, host.get().findViewById(R.id.terminal_workspace_status).getVisibility());
    assertEquals(0, FakePane.creates);
    assertEquals(0, FakePane.input);
    host.pause().stop().destroy();
  }

  @Test
  public void veryShortSidebarScrollsSessionsAndFooterTogetherAndRestoresFocus() {
    // The close button deliberately does not focus in touch mode so the first tap clicks it.
    // Exercise focus restoration using the keyboard/DPAD mode in which it can own focus.
    var instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
    instrumentation.setInTouchMode(false);
    org.robolectric.android.controller.ActivityController<Host> host = null;
    try {
      // Attach a real window: an unattached Robolectric view always reports the resource-default
      // touch mode and has no ViewRoot to carry keyboard focus.
      host = Robolectric.buildActivity(Host.class).create().start().resume().visible();
      var workspace = host.get().findViewById(R.id.terminal_workspace);
      measure(host.get(), workspace, 320, 900);
      host.get().openSessions();
      settleLayout(host.get(), workspace, 320, 900);
      android.widget.ScrollView scroll = host.get().findViewById(R.id.terminal_sessions_scroll);
      View create = host.get().findViewById(R.id.terminal_sessions_create);
      assertFalse(isDescendant(scroll, create));
      View close = host.get().findViewById(R.id.terminal_sessions_close);
      // Robolectric's Instrumentation updates its window-manager state but does not deliver the
      // corresponding change to this attached ViewRoot. Apply Android's local transition too.
      Object viewRoot =
          org.robolectric.util.ReflectionHelpers.callInstanceMethod(close, "getViewRootImpl");
      assertNotNull(viewRoot);
      org.robolectric.util.ReflectionHelpers.callInstanceMethod(
          viewRoot,
          "ensureTouchModeLocally",
          org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class, false));
      assertFalse(close.isInTouchMode());
      assertTrue(close.isFocusable());
      assertFalse(close.isFocusableInTouchMode());
      assertTrue(close.requestFocus());
      assertTrue(close.hasFocus());
      settleLayout(host.get(), workspace, 320, 180);
      assertTrue(isDescendant(scroll, create));
      assertTrue(
          isDescendant(scroll, host.get().findViewById(R.id.terminal_sessions_actions_dense)));
      assertTrue(scroll.getHeight() >= Math.round(179 * density(host.get())));
      assertTrue(close.hasFocus());
      assertTrue(scroll.canScrollVertically(1));
      assertTrue(
          containsText(scroll, com.deepseekharness.app.util.UiText.choose("暂无会话", "No sessions")));
      scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
      assertTrue(scroll.getScrollY() > 0);
      View focus = host.get().findViewById(R.id.terminal_sessions_action_focus_dense);
      focus.performClick();
      assertEquals(1, FakePane.focuses);
      assertEquals(
          View.GONE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
      assertEquals(0, FakePane.creates + FakePane.closes + FakePane.input);
    } finally {
      try {
        if (host != null) host.pause().stop().destroy();
      } finally {
        instrumentation.setInTouchMode(true);
      }
    }
  }

  @Test
  public void largeFontsUseOneColumnAndKeepFooterInsideTheShortViewportScroll() {
    RuntimeEnvironment.setFontScale(2f);
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    try {
      var workspace = host.get().findViewById(R.id.terminal_workspace);
      measure(host.get(), workspace, 320, 600);
      host.get().openSessions();
      settleLayout(host.get(), workspace, 320, 600);
      android.widget.ScrollView scroll = host.get().findViewById(R.id.terminal_sessions_scroll);
      ViewGroup actions = host.get().findViewById(R.id.terminal_sessions_actions_dense);
      assertTrue(isDescendant(scroll, actions));
      assertEquals(6, actions.getChildCount());
      for (int i = 0; i < actions.getChildCount(); i++) {
        ViewGroup row = (ViewGroup) actions.getChildAt(i);
        assertEquals(1, row.getChildCount());
        android.widget.TextView action = (android.widget.TextView) row.getChildAt(0);
        assertTrue(action.getHeight() >= Math.round(48 * density(host.get())));
        assertTrue(action.getMaxLines() >= 2);
        assertNotNull(action.getContentDescription());
      }
      assertTrue(scroll.getHeight() > 0);
      assertTrue(scroll.canScrollVertically(1));
    } finally {
      host.pause().stop().destroy();
      RuntimeEnvironment.setFontScale(1f);
    }
  }

  @Test
  public void imePaddingAndRtlKeepTheSidebarReachableAndReturnToPinnedLayout() {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    try {
      var workspace = host.get().findViewById(R.id.terminal_workspace);
      workspace.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      measure(host.get(), workspace, 400, 900);
      host.get().openSessions();
      settleLayout(host.get(), workspace, 400, 900);
      android.widget.ScrollView scroll = host.get().findViewById(R.id.terminal_sessions_scroll);
      View create = host.get().findViewById(R.id.terminal_sessions_create);
      assertFalse(isDescendant(scroll, create));
      workspace.setPadding(0, 0, 0, Math.round(720 * density(host.get())));
      settleLayout(host.get(), workspace, 400, 900);
      assertTrue(isDescendant(scroll, create));
      View sidebar = host.get().findViewById(R.id.terminal_sessions_panel);
      assertEquals(View.LAYOUT_DIRECTION_RTL, sidebar.getLayoutDirection());
      assertEquals(workspace.getWidth(), sidebar.getRight());
      assertTrue(scroll.getHeight() > 0);
      assertTrue(scroll.getHeight() <= Math.round(180 * density(host.get())));
      workspace.setPadding(0, 0, 0, 0);
      settleLayout(host.get(), workspace, 400, 900);
      assertFalse(isDescendant(scroll, create));
      host.get().findViewById(R.id.terminal_sessions_close).performClick();
      assertTrue(host.get().findViewById(R.id.terminal_menu_ball).hasFocus());
      assertEquals(0, FakePane.creates + FakePane.closes + FakePane.input);
    } finally {
      host.pause().stop().destroy();
    }
  }

  @Test
  public void packageMenuBridgesOpenSessionsAndDisplaySettings() {
    var host = Robolectric.buildActivity(Host.class).create().start().resume();
    try {
      host.get().openSessions();
      assertEquals(
          View.VISIBLE, host.get().findViewById(R.id.terminal_sessions_panel).getVisibility());
      host.get().showDisplaySettings();
      var dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertNotNull(dialog);
      assertTrue(dialog.isShowing());
      assertEquals(0, FakePane.creates + FakePane.closes + FakePane.input);
    } finally {
      host.pause().stop().destroy();
    }
  }

  private static float density(android.app.Activity activity) {
    return activity.getResources().getDisplayMetrics().density;
  }

  private static boolean isDescendant(ViewGroup ancestor, View child) {
    for (android.view.ViewParent parent = child.getParent();
        parent != null;
        parent = parent.getParent()) {
      if (parent == ancestor) return true;
    }
    return false;
  }

  private static void settleLayout(
      android.app.Activity activity, View workspace, int widthDp, int heightDp) {
    // 第一遍窗口回调更新抽屉/内联形态，后两遍验证实际子树测量结果已稳定。
    for (int pass = 0; pass < 3; pass++) {
      workspace.forceLayout();
      measure(activity, workspace, widthDp, heightDp);
    }
  }

  private static void measure(
      android.app.Activity activity, View workspace, int widthDp, int heightDp) {
    float density = activity.getResources().getDisplayMetrics().density;
    int width = Math.round(widthDp * density), height = Math.round(heightDp * density);
    workspace.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    workspace.layout(0, 0, width, height);
  }
}
