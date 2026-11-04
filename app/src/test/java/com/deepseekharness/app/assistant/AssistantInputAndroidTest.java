package com.deepseekharness.app.assistant;

import static org.junit.Assert.*;
import android.content.*;
import android.graphics.Rect;
import android.os.Looper;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.deepseekharness.app.DshaApp;
import com.deepseekharness.app.R;
import com.deepseekharness.app.control.PhoneControlCoordinator;
import com.deepseekharness.app.core.HarnessController;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.*;

/** Actual Android Button/Activity/EditText/IME flow; no guest, model, or permission expansion. */
@RunWith(RobolectricTestRunner.class)
@Config(
    sdk = 34,
    application = AssistantInputAndroidTest.App.class,
    shadows = AssistantInputAndroidTest.NoRuntime.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class AssistantInputAndroidTest {
  public static final class App extends DshaApp {
    @Override
    public void onCreate() {}
  }

  @Implements(value = HarnessController.class, isInAndroidSdk = false)
  public static class NoRuntime {
    @Implementation
    protected static HarnessController get(Context context) {
      return Shadow.newInstanceOf(HarnessController.class);
    }

    @Implementation
    protected void addReadyWebPageListener(Runnable listener) {}

    @Implementation
    protected void removeReadyWebPageListener(Runnable listener) {}
  }

  private final AtomicInteger cancellations = new AtomicInteger();
  private AssistantSessionController session;
  private AssistantWindow window;
  private App app;

  @Before
  public void create() throws Exception {
    app = (App) RuntimeEnvironment.getApplication();
    ShadowSettings.setCanDrawOverlays(true);
    PhoneControlCoordinator.get(app).resume();
    JSONArray history = new JSONArray();
    for (int turn = 0; turn < 8; turn++) {
      StringBuilder text = new StringBuilder();
      for (int line = 0; line < 40; line++)
        text.append("History turn ").append(turn).append(" line ").append(line).append('\n');
      history.put(
          new JSONObject()
              .put("role", turn % 2 == 0 ? "user" : "assistant")
              .put("text", text.toString()));
    }
    AssistantBackend backend =
        new AssistantBackend() {
          public boolean current() {
            return true;
          }

          public JSONObject call(String method, JSONObject args) throws java.io.IOException {
            if (method.equals("sessions.cancel")) cancellations.incrementAndGet();
            try {
              return new JSONObject()
                  .put("sessionId", "full-session-input")
                  .put("hostInstanceId", "host-input")
                  .put("running", false)
                  .put("turnId", "7")
                  .put("taskId", "full-session-input")
                  .put("messages", history)
                  .put("accepted", true);
            } catch (org.json.JSONException failure) {
              throw new java.io.IOException(failure);
            }
          }

          public void returnConversation(AssistantBinding binding) {}
        };
    session =
        new AssistantSessionController(
            app,
            new AssistantBinding("host-input", "full-session-input", "input-epoch", 1),
            backend);
    drain();
  }

  private static Object field(Object owner, String name) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }

  private void drain() throws Exception {
    ((ScheduledExecutorService) field(session, "worker")).submit(() -> {}).get(5, TimeUnit.SECONDS);
    Shadow.<ShadowLooper>extract(Looper.getMainLooper()).idle();
  }

  private static Rect boundsWithin(ViewGroup viewport, View child) {
    Rect bounds = new Rect(0, 0, child.getWidth(), child.getHeight());
    viewport.offsetDescendantRectToMyCoords(child, bounds);
    return bounds;
  }

  private static void layoutViewport(ViewGroup viewport, int heightDp) {
    float density = viewport.getResources().getDisplayMetrics().density;
    int width = Math.round(360 * density);
    int height = Math.round(heightDp * density);
    viewport.forceLayout();
    viewport.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    viewport.layout(0, 0, width, height);
  }

  @After
  public void close() {
    if (window != null) window.close();
    if (session != null) session.close();
  }

  @Test
  public void expandedOverlayInputHandsOffToBoundEditorAndReleasesWindowFocus() throws Exception {
    window = new AssistantWindow(app, session);
    window.show();
    session.expand();
    drain();
    int before = cancellations.get();
    Button input = (Button) field(window, "input");
    assertTrue(input.isShown());
    assertTrue(input.performClick());
    drain();
    Intent editor = Shadow.<ShadowApplication>extract(app).getNextStartedActivity();
    assertEquals(AssistantActivity.class.getName(), editor.getComponent().getClassName());
    assertTrue(editor.getBooleanExtra("bound", false));
    assertTrue(editor.getBooleanExtra("focus_input", false));
    View root = (View) field(window, "root");
    assertEquals(View.GONE, root.getVisibility());
    WindowManager.LayoutParams flags = (WindowManager.LayoutParams) field(window, "params");
    assertNotEquals(0, flags.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    assertFalse(session.snapshot().expanded());
    assertTrue(PhoneControlCoordinator.get(app).snapshot().paused());
    assertEquals(
        "Opening the editor must not issue another background cancellation",
        before,
        cancellations.get());
    TextView status = (TextView) field(window, "status");
    assertEquals(2, status.getMaxLines());
    assertTrue(status.getText().toString().split("\n").length <= 2);
  }

  @Test
  public void activityComposerOwnsImeSharedDraftAndSurvivesPeriodicRenderThenReleases()
      throws Exception {
    Intent intent = AssistantWindow.editorIntent(app);
    var host = Robolectric.buildActivity(AssistantActivity.class, intent);
    host.get().setTheme(R.style.Theme_DeepseekHarness);
    try {
      host.setup().visible().windowFocusChanged(true);
      drain();
      AssistantActivity activity = host.get();
      EditText input = (EditText) field(activity, "input");
      assertTrue("The actual EditText must own focus", input.hasFocus());
      ViewGroup viewport = activity.findViewById(android.R.id.content);
      TextView history = (TextView) field(activity, "messages");
      ScrollView historyScroll = (ScrollView) field(activity, "pageScroll");
      Button send = (Button) field(activity, "sendButton");
      assertTrue(history.getText().toString().contains("History turn 7 line 39"));
      // Traverse the actual Android layout first at full height, then with the
      // smaller viewport delivered by adjustResize when a keyboard is visible.
      for (int height : new int[] {640, 360}) {
        layoutViewport(viewport, height);
        Rect composer = boundsWithin(viewport, input);
        Rect sendAction = boundsWithin(viewport, send);
        Rect transcript = boundsWithin(viewport, historyScroll);
        assertTrue("The composer must remain attached", input.isAttachedToWindow());
        assertTrue(
            "The history must genuinely exceed several screens",
            history.getHeight() > 3 * historyScroll.getHeight());
        assertTrue(historyScroll.getHeight() > 0);
        assertTrue(
            "The composer must have usable measured bounds",
            composer.width() > 0 && composer.height() > 0);
        assertTrue(
            "Long history must not push the composer below the viewport",
            composer.top >= 0 && composer.bottom <= viewport.getHeight());
        assertTrue(
            "The send action must also remain reachable",
            sendAction.height() > 0 && sendAction.bottom <= viewport.getHeight());
        assertTrue("History must end above the composer", transcript.bottom <= composer.top);
        historyScroll.scrollTo(0, history.getHeight());
        assertTrue("The history must actually scroll", historyScroll.getScrollY() > 0);
        assertEquals(
            "History scrolling must not move the composer",
            composer,
            boundsWithin(viewport, input));
        assertTrue(input.hasFocus());
      }
      var ime = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
      assertTrue(
          "An explicit editor tap must request the real Android IME API",
          Shadow.<ShadowInputMethodManager>extract(ime).isSoftInputVisible());
      input.setText("PINGOVERLAY196");
      drain();
      assertEquals("PINGOVERLAY196", session.snapshot().draft());
      var render = AssistantActivity.class.getDeclaredMethod("renderBound");
      render.setAccessible(true);
      render.invoke(activity);
      drain();
      assertSame(input, field(activity, "input"));
      assertTrue(input.hasFocus());
      assertEquals("PINGOVERLAY196", input.getText().toString());
      assertFalse(((TextView) field(activity, "messages")).isFocusable());
      assertEquals(
          "Editor admission does not cancel an idle or running model task", 0, cancellations.get());
      // Queue another real IME request just before leaving. A late callback must
      // neither restore the editor's focus nor show a keyboard after onPause.
      var pending = AssistantActivity.class.getDeclaredField("pendingInput");
      pending.setAccessible(true);
      pending.setBoolean(activity, true);
      var focus = AssistantActivity.class.getDeclaredMethod("focusComposer");
      focus.setAccessible(true);
      focus.invoke(activity);
      host.pause().stop();
      drain();
      assertFalse(input.hasFocus());
      assertFalse(input.isFocusable());
      assertEquals(
          ViewGroup.FOCUS_BLOCK_DESCENDANTS,
          ((ViewGroup) field(activity, "content")).getDescendantFocusability());
      assertFalse(Shadow.<ShadowInputMethodManager>extract(ime).isSoftInputVisible());
      assertTrue(PhoneControlCoordinator.get(app).snapshot().paused());
    } finally {
      host.destroy();
    }
  }
}
