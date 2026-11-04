package com.deepseekharness.app.ui;

import static org.junit.Assert.*;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.deepseekharness.app.DshaApp;
import com.deepseekharness.app.LanProxyService;
import com.deepseekharness.app.HarnessService;
import com.deepseekharness.app.bridge.LocalNetworkAccess;
import com.deepseekharness.app.R;
import com.deepseekharness.app.core.ConfigStore;
import com.deepseekharness.app.core.HarnessController;
import com.deepseekharness.app.util.LanAccessPresentation;
import com.deepseekharness.app.util.LanAddressPolicy;
import com.deepseekharness.app.util.UiText;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDialog;

/** Real Launch layout, dialogs and clipboard with synthetic LAN snapshots; never opens a listener. */
@RunWith(RobolectricTestRunner.class)
@Config(
    sdk = 34,
    application = LaunchAccessAddressesTest.App.class,
    shadows = {
      LaunchAccessAddressesTest.Controller.class,
      LaunchAccessAddressesTest.Proxy.class,
      LaunchAccessAddressesTest.RuntimeProtection.class,
      LaunchAccessAddressesTest.NetworkAccess.class
    })
@LooperMode(LooperMode.Mode.PAUSED)
public final class LaunchAccessAddressesTest {
  private static final String LOCAL = "http://127.0.0.1:49123/?token=synthetic-local";
  private static final String LAN_TOKEN = "x".repeat(43);

  public static final class App extends DshaApp {
    @Override
    public void onCreate() {}
  }

  @Implements(value = HarnessController.class, isInAndroidSdk = false)
  public static final class Controller {
    static HarnessController value;
    static ConfigStore config;
    static long generation;
    static String local;

    @Implementation
    protected static HarnessController get(Context ignored) {
      return value;
    }

    @Implementation
    protected ConfigStore config() {
      return config;
    }

    @Implementation
    protected long getWebGeneration() {
      return generation;
    }

    @Implementation
    protected String getWebAuthUrl() {
      return local;
    }
  }

  @Implements(value = LanProxyService.class, isInAndroidSdk = false)
  public static final class Proxy {
    static int starts;
    static String token;

    @Implementation
    protected static String boundLanToken(long generation) {
      return token;
    }

    @Implementation
    protected static void start(String root, Context c, int port, long generation) {
      starts++;
    }
  }

  @Implements(value = HarnessService.class, isInAndroidSdk = false)
  public static final class RuntimeProtection {
    @Implementation
    protected static void ensureLanForeground(Context ignored) {}
  }

  @Implements(value = LocalNetworkAccess.class, isInAndroidSdk = false)
  public static final class NetworkAccess {
    static int applications;

    @Implementation
    protected static void applyConfiguredFeatures(Context ignored) {
      applications++;
    }
  }

  public static final class Host extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle saved) {
      setTheme(R.style.Theme_DeepseekHarness);
      super.onCreate(saved);
      FrameLayout root = new FrameLayout(this);
      root.setId(10002);
      setContentView(root);
    }
  }

  public static final class Entry extends LaunchFragment {
    LanAccessPresentation snapshot;

    @Override
    LanAccessPresentation lanAccess() {
      return snapshot;
    }
  }

  private org.robolectric.android.controller.ActivityController<Host> host;
  private Entry entry;

  @Before
  public void prepare() {
    UiText.setLanguage("en");
    Controller.value = Shadow.newInstanceOf(HarnessController.class);
    Controller.config = new ConfigStore(RuntimeEnvironment.getApplication());
    Controller.config.setLanMode(false);
    RuntimeEnvironment.getApplication()
        .getSharedPreferences(com.deepseekharness.app.util.Constants.PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(com.deepseekharness.app.util.LanPassword.PREF_KEY, "synthetic-verifier")
        .commit();
    Controller.generation = 7;
    Controller.local = LOCAL;
    Proxy.starts = 0;
    Proxy.token = LAN_TOKEN;
    NetworkAccess.applications = 0;
    host = Robolectric.buildActivity(Host.class).setup();
    entry = new Entry();
    entry.snapshot = snapshot(false, true, List.of("192.168.1.2"));
    host.get().getSupportFragmentManager().beginTransaction().add(10002, entry).commitNow();
  }

  @After
  public void close() {
    host.pause().stop().destroy();
    UiText.setLanguage("zh");
  }

  private LanAccessPresentation snapshot(
      boolean enabled, boolean permission, List<String> addresses) {
    return LanAccessPresentation.capture(
        enabled,
        permission,
        Controller.generation,
        true,
        49231,
        new LanAddressPolicy.Discovery(addresses, false, LanAddressPolicy.Failure.NONE));
  }

  private AlertDialog open() {
    entry.requireView().findViewById(R.id.launch_address).performClick();
    return (AlertDialog) ShadowDialog.getLatestDialog();
  }

  private static String message(AlertDialog dialog) {
    return ((TextView) dialog.findViewById(android.R.id.message)).getText().toString();
  }

  private ClipboardManager clipboard() {
    return (ClipboardManager) host.get().getSystemService(Context.CLIPBOARD_SERVICE);
  }

  private static void clickDialogButton(AlertDialog dialog, int button) {
    assertTrue("the tested dialog must be visible", dialog.isShowing());
    assertTrue(dialog.getButton(button).performClick());
    // AlertController dispatches its button listener through the main Handler.
    // Observe the resulting state only after that listener has actually run.
    org.robolectric.shadows.ShadowLooper shadow =
        org.robolectric.shadow.api.Shadow.extract(Looper.getMainLooper());
    shadow.idle();
  }

  @Test
  public void disabledLanIsShownWithExplicitEnableAndOpeningDoesNotExposeIt() {
    AlertDialog dialog = open();
    assertTrue(message(dialog).contains(LOCAL));
    assertTrue(message(dialog).contains("LAN access: off"));
    assertEquals("Enable LAN access", dialog.getButton(DialogInterface.BUTTON_NEUTRAL).getText());
    assertFalse(Controller.config.isLanMode());
    assertEquals(0, Proxy.starts);
    clickDialogButton(dialog, DialogInterface.BUTTON_NEUTRAL);
    AlertDialog confirmation = (AlertDialog) ShadowDialog.getLatestDialog();
    assertTrue(message(confirmation).contains("unencrypted"));
    assertFalse("showing confirmation cannot enable LAN", Controller.config.isLanMode());
    clickDialogButton(confirmation, DialogInterface.BUTTON_NEGATIVE);
    assertFalse(Controller.config.isLanMode());
    assertEquals(0, Proxy.starts);
  }

  @Test
  public void currentDynamicLanUrlsAreListedAndCopyContainsNoCredential() {
    entry.snapshot = snapshot(true, true, List.of("192.168.1.2", "10.2.0.2"));
    AlertDialog dialog = open();
    assertTrue(message(dialog).contains("http://192.168.1.2:49231/"));
    assertTrue(message(dialog).contains("http://10.2.0.2:49231/"));
    assertFalse("overview must not expose the LAN token", message(dialog).contains(LAN_TOKEN));
    clickDialogButton(dialog, DialogInterface.BUTTON_NEUTRAL);
    AlertDialog choice = (AlertDialog) ShadowDialog.getLatestDialog();
    choice.getListView().performItemClick(null, 1, 1);
    AlertDialog credential = (AlertDialog) ShadowDialog.getLatestDialog();
    assertTrue(message(credential).contains("http://10.2.0.2:49231/"));
    assertFalse(message(credential).contains(LAN_TOKEN));
    clickDialogButton(credential, DialogInterface.BUTTON_POSITIVE);
    assertEquals(
        "http://10.2.0.2:49231/", clipboard().getPrimaryClip().getItemAt(0).getText().toString());
    assertEquals(0, Proxy.starts);
  }

  @Test
  public void explicitEnableConfirmationAppliesConfiguredNetworkFeaturesOnce() {
    AlertDialog dialog = open();
    assertEquals(0, NetworkAccess.applications);
    clickDialogButton(dialog, DialogInterface.BUTTON_NEUTRAL);
    AlertDialog confirmation = (AlertDialog) ShadowDialog.getLatestDialog();
    assertFalse(Controller.config.isLanMode());
    assertTrue("confirmation applies only to the visible resumed launch page", entry.isResumed());
    clickDialogButton(confirmation, DialogInterface.BUTTON_POSITIVE);
    assertTrue(Controller.config.isLanMode());
    assertEquals(1, NetworkAccess.applications);
  }

  @Test
  public void generationAndAddressChangesBlockStaleCopies() {
    for (int change = 0; change < 2; change++) {
      Controller.generation = 7;
      Proxy.token = LAN_TOKEN;
      entry.snapshot = snapshot(true, true, List.of("192.168.1.2"));
      AlertDialog dialog = open();
      clickDialogButton(dialog, DialogInterface.BUTTON_NEUTRAL);
      AlertDialog credential = (AlertDialog) ShadowDialog.getLatestDialog();
      if (change == 0) Controller.generation = 8;
      else entry.snapshot = snapshot(true, true, List.of("192.168.2.2"));
      clickDialogButton(credential, DialogInterface.BUTTON_POSITIVE);
      assertFalse("changed connection must not be copied", clipboard().hasPrimaryClip());
    }
  }

  @Test
  public void missingPasswordIsShownAndDoesNotExposeAnAddress() {
    RuntimeEnvironment.getApplication()
        .getSharedPreferences(com.deepseekharness.app.util.Constants.PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove(com.deepseekharness.app.util.LanPassword.PREF_KEY)
        .commit();
    entry.snapshot = snapshot(true, true, List.of("192.168.1.2"));
    AlertDialog dialog = open();
    assertTrue(message(dialog).contains("No LAN password"));
    assertFalse(message(dialog).contains("http://192.168.1.2"));
    assertEquals("Set LAN password", dialog.getButton(DialogInterface.BUTTON_NEUTRAL).getText());
  }

  @Test
  public void permissionFailureIsShownAndMissingLocalUrlDisablesItsCopyButton() {
    Controller.local = "";
    entry.snapshot = snapshot(true, false, List.of("192.168.1.2"));
    AlertDialog dialog = open();
    assertTrue(message(dialog).contains("permission is missing"));
    assertFalse(message(dialog).contains("http://192.168"));
    assertFalse(dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled());
    assertEquals("Allow local network", dialog.getButton(DialogInterface.BUTTON_NEUTRAL).getText());
  }

  @Test
  public void changedLocalGenerationAlsoBlocksTheOriginalCopyCallback() {
    AlertDialog dialog = open();
    Controller.generation++;
    clickDialogButton(dialog, DialogInterface.BUTTON_POSITIVE);
    assertFalse(clipboard().hasPrimaryClip());
  }
}
