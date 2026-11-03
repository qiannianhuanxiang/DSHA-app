package com.deepseekharness.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import org.junit.Test;

/** Intent 解析只放行显式目标；不解析 file:、service 与隐式无包名启动。 */
public final class IntentRequestTest {
  private static List<String> args(String... values) {
    return List.of(values);
  }

  private static String rejected(List<String> argv) {
    return assertThrows(IntentRequest.Invalid.class, () -> IntentRequest.parse(argv)).code;
  }

  @Test
  public void startAcceptsExplicitComponentWithExtras() throws Exception {
    IntentRequest request =
        IntentRequest.parse(
            args("start", "-n", "com.android.settings/.Settings", "--es", "k", "v", "-a", "android.intent.action.MAIN"));
    assertEquals(IntentRequest.Kind.START, request.kind);
    assertEquals("com.android.settings/.Settings", request.component);
    assertEquals("com.android.settings", request.targetPackage());
    assertEquals("v", request.extras.get("k"));
    assertEquals("start com.android.settings/.Settings", request.summary());
  }

  @Test
  public void startAcceptsPackageWithActionButNeverImplicitWithoutTarget() throws Exception {
    IntentRequest request =
        IntentRequest.parse(args("start", "-p", "com.example.app", "-a", "android.intent.action.VIEW"));
    assertEquals("com.example.app", request.targetPackage());
    assertEquals("INTENT_TARGET_REQUIRED", rejected(args("start", "-a", "android.intent.action.VIEW")));
    assertEquals("INTENT_TARGET_REQUIRED", rejected(args("start")));
  }

  @Test
  public void broadcastRequiresPackageOrComponent() throws Exception {
    IntentRequest request =
        IntentRequest.parse(args("broadcast", "-p", "com.example.app", "-a", "com.example.PING"));
    assertEquals(IntentRequest.Kind.BROADCAST, request.kind);
    assertEquals("INTENT_BROADCAST_PACKAGE_REQUIRED", rejected(args("broadcast", "-a", "com.example.PING")));
  }

  @Test
  public void rejectsUnsupportedVerbsOptionsAndSharedComponentMismatch() {
    assertEquals("INTENT_VERB_UNSUPPORTED", rejected(args("service", "-n", "a.b/.C")));
    assertEquals("INTENT_OPTION_UNSUPPORTED", rejected(args("start", "-n", "a.b/.C", "--ez", "k", "1")));
    assertEquals("INTENT_OPTION_UNSUPPORTED", rejected(args("start", "-n", "a.b/.C", "--user", "0")));
    // am 的 -W/-S/-f 会改变启动语义；不支持就明确拒绝，不静默忽略。
    assertEquals("INTENT_OPTION_UNSUPPORTED", rejected(args("start", "-W", "-n", "a.b/.C")));
    assertEquals(
        "INTENT_COMPONENT_PACKAGE_MISMATCH", rejected(args("start", "-n", "a.b/.C", "-p", "c.d")));
  }

  @Test
  public void rejectsFileSchemeAndMalformedValues() {
    assertEquals("INTENT_DATA_SCHEME_FORBIDDEN", rejected(args("start", "-n", "a.b/.C", "-d", "file:///data/x")));
    assertEquals("INTENT_DATA_SCHEME_FORBIDDEN", rejected(args("start", "-n", "a.b/.C", "-d", "content://sms")));
    assertEquals("INTENT_COMPONENT_INVALID", rejected(args("start", "-n", "noSlash")));
    assertEquals("INTENT_COMPONENT_INVALID", rejected(args("start", "-n", "a.b/")));
    assertEquals("INTENT_VALUE_MISSING", rejected(args("start", "-n")));
    assertEquals("INTENT_PACKAGE_INVALID", rejected(args("start", "-p", "nodot", "-a", "x.Y")));
    assertEquals("INTENT_EXTRA_KEY_INVALID", rejected(args("start", "-n", "a.b/.C", "--es", "bad key", "v")));
  }

  @Test
  public void httpsDataIsAcceptedForStart() throws Exception {
    IntentRequest request =
        IntentRequest.parse(args("start", "-n", "com.example.app/.View", "-d", "https://example.com/a?b=1"));
    assertEquals("https://example.com/a?b=1", request.data);
  }
}
