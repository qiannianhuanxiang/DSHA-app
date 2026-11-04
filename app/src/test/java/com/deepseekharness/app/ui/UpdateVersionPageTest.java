package com.deepseekharness.app.ui;

import static org.junit.Assert.*;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.lifecycle.MutableLiveData;
import com.deepseekharness.app.BuildConfig;
import com.deepseekharness.app.DshaApp;
import com.deepseekharness.app.R;
import com.deepseekharness.app.core.UpdateEngine;
import com.deepseekharness.app.core.UpdateRepository;
import com.deepseekharness.app.util.CurrentReleaseNotes;
import com.deepseekharness.app.util.UiLanguagePreference;
import com.deepseekharness.app.util.UiText;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDialog;

/** Actual version page, bundled resource and opened notes; transport never runs in this fixture. */
@RunWith(RobolectricTestRunner.class)
@Config(
    sdk = 34,
    qualifiers = "zh",
    application = UpdateVersionPageTest.App.class,
    shadows = UpdateVersionPageTest.Engine.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class UpdateVersionPageTest {
  public static final class App extends DshaApp {
    @Override
    public void onCreate() {}
  }

  @Implements(value = UpdateEngine.class, isInAndroidSdk = false)
  public static final class Engine {
    static MutableLiveData<UpdateRepository.State> states;
    static int checks;

    @Implementation
    protected static UpdateEngine get(Context context) {
      return Shadow.newInstanceOf(UpdateEngine.class);
    }

    @Implementation
    protected androidx.lifecycle.LiveData<UpdateRepository.State> state() {
      return states;
    }

    @Implementation
    protected String channel() {
      return "preview";
    }

    @Implementation
    protected boolean hasTask() {
      return true;
    }

    @Implementation
    protected void setChannel(String channel) {}

    @Implementation
    protected void check() {
      checks++;
    }
  }

  private String previousLanguage;

  @Before
  public void prepare() throws Exception {
    previousLanguage = UiText.language();
    UiText.setLanguage("zh");
    Engine.checks = 0;
    Engine.states = new MutableLiveData<>(state("暂无可用更新"));
  }

  @After
  public void restore() {
    UiText.setLanguage(previousLanguage);
  }

  private static UpdateRepository.State state(String message) throws Exception {
    var constructor =
        UpdateRepository.State.class.getDeclaredConstructor(
            String.class,
            UpdateRepository.Stage.class,
            long.class,
            long.class,
            com.deepseekharness.app.util.UpdatePolicy.Release.class,
            File.class,
            String.class,
            String.class);
    constructor.setAccessible(true);
    return constructor.newInstance(
        message, UpdateRepository.Stage.IDLE, 0L, 0L, null, null, "preview", null);
  }

  @Test
  public void currentRowAndActuallyOpenedNotesUseTheInstalledIdentity() throws IOException {
    try (var controller = Robolectric.buildActivity(UpdateActivity.class).setup()) {
      UpdateActivity activity = controller.get();
      assertEquals(
          CurrentReleaseNotes.versionLabel(BuildConfig.VERSION_NAME)
              + " · build"
              + BuildConfig.VERSION_CODE,
          ((TextView) activity.findViewById(R.id.update_changelog_summary)).getText().toString());
      activity.findViewById(R.id.update_changelog).performClick();
      Dialog dialog = ShadowDialog.getLatestDialog();
      assertNotNull(dialog);
      String shown = text(dialog.getWindow().getDecorView());
      assertCurrentNotesShown(activity, shown);
      for (String language : UiLanguagePreference.LANGUAGES)
        bundledNotes(inLanguage(activity, language));
      assertTrue(shown.contains("系统字形/角标窗"));
      assertTrue(shown.contains("坐标点击与滑动完全不可用"));
      assertTrue(shown.contains("不再参与遮挡判定"));
      assertTrue(shown.contains("首条意外行"));
      assertTrue(shown.contains("pm 输出格式差异"));
      assertTrue(shown.contains("3.2.2"));
      assertFalse(shown.contains("build157"));
      assertFalse(shown.contains("0.2.0-rc2"));
      dialog.dismiss();
    }
  }

  @Test
  @Config(qualifiers = "en")
  public void englishNotesOpenTheEnglishCurrentResource() throws IOException {
    UiText.setLanguage("en");
    try (var controller = Robolectric.buildActivity(UpdateActivity.class).setup()) {
      controller.get().findViewById(R.id.update_changelog).performClick();
      Dialog dialog = ShadowDialog.getLatestDialog();
      assertNotNull(dialog);
      String shown = text(dialog.getWindow().getDecorView());
      String englishBody = assertCurrentNotesShown(controller.get(), shown);
      assertNotEquals(
          "English notes must resolve a localized body rather than the default Chinese resource",
          body(bundledNotes(inLanguage(controller.get(), "zh"))),
          englishBody);
      assertTrue(shown.contains("system glyph/badge windows"));
      assertTrue(shown.contains("coordinate taps and swipes entirely unusable"));
      assertTrue(shown.contains("no longer participate in the occlusion decision"));
      assertTrue(shown.contains("first unexpected line"));
      assertTrue(shown.contains("ROM-specific pm output differences"));
      assertTrue(shown.contains("3.2.2"));
      assertFalse(shown.contains("build157"));
      dialog.dismiss();
    }
  }

  @Test
  public void emptyResultIsNormalAndStillAllowsRecheckAndBrowser() {
    try (var controller = Robolectric.buildActivity(UpdateActivity.class).setup()) {
      UpdateActivity activity = controller.get();
      assertEquals(
          "暂无可用更新", ((TextView) activity.findViewById(R.id.update_status)).getText().toString());
      assertEquals(View.GONE, activity.findViewById(R.id.update_release_panel).getVisibility());
      assertEquals(View.GONE, activity.findViewById(R.id.update_download).getVisibility());
      assertEquals(View.GONE, activity.findViewById(R.id.update_install).getVisibility());
      assertEquals(View.GONE, activity.findViewById(R.id.update_progress).getVisibility());
      activity.findViewById(R.id.update_check).performClick();
      assertEquals(1, Engine.checks);
      assertTrue(activity.findViewById(R.id.update_browser).performClick());
      Intent browser =
          Shadow.<org.robolectric.shadows.ShadowActivity>extract(activity).getNextStartedActivity();
      assertEquals(Intent.ACTION_VIEW, browser.getAction());
      assertTrue(browser.getDataString().endsWith("/releases"));
    }
  }

  @Test
  public void certificateFailureIsNotDisplayedAsNoUpdateAndRawLegacyExceptionsAreHidden()
      throws Exception {
    String certificate = "无法验证更新服务的安全证书，请检查设备日期时间或切换网络后重试";
    Engine.states.setValue(state(certificate));
    try (var controller = Robolectric.buildActivity(UpdateActivity.class).setup()) {
      UpdateActivity activity = controller.get();
      TextView status = activity.findViewById(R.id.update_status);
      assertEquals(certificate, status.getText().toString());
      Engine.states.setValue(state("javax.net.ssl.SSLHandshakeException: Trust anchor missing"));
      assertFalse(status.getText().toString().contains("Exception"));
      assertFalse(status.getText().toString().contains("暂无可用更新"));
      assertTrue(activity.findViewById(R.id.update_check).isEnabled());
      assertTrue(activity.findViewById(R.id.update_browser).isEnabled());
    }
  }

  private static String assertCurrentNotesShown(Context context, String shown) throws IOException {
    String currentBody = body(bundledNotes(context));
    assertFalse(
        "The current bundled notes must contain an actual release body", currentBody.isEmpty());
    assertTrue(
        shown.contains("DSHA " + BuildConfig.VERSION_NAME + " / build" + BuildConfig.VERSION_CODE));
    assertTrue(
        "The opened sheet must show the complete current bundled body",
        shown.contains(currentBody));
    return currentBody;
  }

  private static String bundledNotes(Context context) throws IOException {
    try (var input = context.getResources().openRawResource(R.raw.changelog)) {
      String notes = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(
          "The localized bundled header must identify the installed build",
          CurrentReleaseNotes.matches(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, notes));
      return notes;
    }
  }

  private static Context inLanguage(Context context, String language) {
    Configuration configuration = new Configuration(context.getResources().getConfiguration());
    configuration.setLocale(Locale.forLanguageTag(language));
    return context.createConfigurationContext(configuration);
  }

  private static String body(String notes) {
    int end = notes.indexOf('\n');
    assertTrue("The current notes must contain a header followed by a release body", end >= 0);
    return notes.substring(end + 1).trim();
  }

  private static String text(View view) {
    StringBuilder result = new StringBuilder();
    if (view instanceof TextView) result.append(((TextView) view).getText()).append('\n');
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) result.append(text(group.getChildAt(i)));
    }
    return result.toString();
  }
}
