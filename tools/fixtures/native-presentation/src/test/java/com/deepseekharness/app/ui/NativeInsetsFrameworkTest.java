package com.deepseekharness.app.ui;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(
    sdk = {23, 29, 34},
    manifest = Config.NONE)
public class NativeInsetsFrameworkTest {
  @Test
  public void actualAndroidXDispatchDoesNotAccumulateInsetsOrDoubleCountKeyboard() {
    var controller = Robolectric.buildActivity(Activity.class).setup();
    Activity activity = controller.get();
    activity.setContentView(new FrameLayout(activity));
    View content = activity.findViewById(android.R.id.content);
    content.setPadding(2, 3, 4, 5);
    new ModernAndroidUi().onActivityPostCreated(activity, null);
    var bars =
        new WindowInsetsCompat.Builder().setSystemWindowInsets(Insets.of(12, 24, 16, 36)).build();
    ViewCompat.dispatchApplyWindowInsets(content, bars);
    assertEquals(14, content.getPaddingLeft());
    assertEquals(27, content.getPaddingTop());
    assertEquals(20, content.getPaddingRight());
    assertEquals(41, content.getPaddingBottom());
    ViewCompat.dispatchApplyWindowInsets(content, bars);
    assertEquals(41, content.getPaddingBottom());
    var keyboard =
        new WindowInsetsCompat.Builder().setSystemWindowInsets(Insets.of(12, 24, 16, 350)).build();
    ViewCompat.dispatchApplyWindowInsets(content, keyboard);
    assertEquals(355, content.getPaddingBottom());
    ViewCompat.dispatchApplyWindowInsets(content, bars);
    assertEquals(41, content.getPaddingBottom());
    assertEquals(0, activity.getWindow().getStatusBarColor());
    assertEquals(
        Build.VERSION.SDK_INT < 26 ? 0xff000000 : 0xffffffff,
        activity.getWindow().getNavigationBarColor());
    controller.pause().stop().destroy();
  }

  @Test
  public void realBrowserWindowKeepsStatusBarVisibleAndPaintsFullSafeArea() {
    var controller = Robolectric.buildActivity(PictureInPictureActivity.class).setup();
    var activity = controller.get();
    activity.setContentView(new FrameLayout(activity));
    View content = activity.findViewById(android.R.id.content);
    WebFullscreenUi.install(activity);
    WebFullscreenUi.applyThemeColor(activity, 0xff123456);
    assertEquals(0, activity.getWindow().getStatusBarColor());
    assertEquals(
        0xff123456, ((android.graphics.drawable.ColorDrawable) content.getBackground()).getColor());
    var window =
        androidx.core.view.WindowCompat.getInsetsController(
            activity.getWindow(), activity.getWindow().getDecorView());
    assertFalse(window.isAppearanceLightStatusBars());
    assertEquals(
        0,
        activity.getWindow().getDecorView().getSystemUiVisibility()
            & View.SYSTEM_UI_FLAG_FULLSCREEN);
    var bars =
        new WindowInsetsCompat.Builder().setSystemWindowInsets(Insets.of(12, 24, 16, 36)).build();
    ViewCompat.dispatchApplyWindowInsets(content, bars);
    assertEquals(24, content.getPaddingTop());
    var keyboard =
        new WindowInsetsCompat.Builder().setSystemWindowInsets(Insets.of(12, 24, 16, 350)).build();
    ViewCompat.dispatchApplyWindowInsets(content, keyboard);
    assertEquals(350, content.getPaddingBottom());
    ViewCompat.dispatchApplyWindowInsets(content, bars);
    assertEquals(36, content.getPaddingBottom());
    WebFullscreenUi.applyThemeColor(activity, 0xfff8f8f8);
    assertEquals(
        0xfff8f8f8, ((android.graphics.drawable.ColorDrawable) content.getBackground()).getColor());
    assertEquals(24, content.getPaddingTop());
    assertTrue(window.isAppearanceLightStatusBars());
    assertEquals(
        0,
        activity.getWindow().getDecorView().getSystemUiVisibility()
            & View.SYSTEM_UI_FLAG_FULLSCREEN);
    activity.picture = true;
    ViewCompat.dispatchApplyWindowInsets(content, bars);
    assertEquals(0, content.getPaddingTop());
    assertEquals(0, content.getPaddingBottom());
    activity.picture = false;
    ViewCompat.dispatchApplyWindowInsets(content, bars);
    assertEquals(24, content.getPaddingTop());
    assertEquals(36, content.getPaddingBottom());
    controller.pause().stop().destroy();
  }

  @Test
  public void originalPictureFrameUsesSameChildAcrossCompactResizeAndRestore() {
    PictureInPictureFrame frame = new PictureInPictureFrame(RuntimeEnvironment.getApplication());
    FrameLayout child = new FrameLayout(frame.getContext());
    frame.addView(child, new FrameLayout.LayoutParams(-1, -1));
    frame.restoreViewport(1440, 3016);
    frame.setCompact(true);
    traverse(frame, 432, 905);
    assertEquals(1440, child.getWidth());
    assertEquals(3016, child.getHeight());
    traverse(frame, 500, 900);
    assertSame(child, frame.getChildAt(0));
    assertEquals(1440, child.getWidth());
    assertEquals(3016, child.getHeight());
    frame.setCompact(false);
    traverse(frame, 1440, 3016);
    assertSame(child, frame.getChildAt(0));
    assertEquals(1f, child.getScaleX(), 0);
  }

  private static void traverse(PictureInPictureFrame frame, int width, int height) {
    frame.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    frame.layout(0, 0, width, height);
  }
}
