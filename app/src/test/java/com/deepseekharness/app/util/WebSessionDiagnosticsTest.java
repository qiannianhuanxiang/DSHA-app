package com.deepseekharness.app.util;

import static org.junit.Assert.*;
import java.io.IOException;
import org.junit.Test;

public final class WebSessionDiagnosticsTest {
  @Test
  public void safeOuterStageAndKnownInnerBoundarySurviveWithoutFreeFormContent() {
    var error =
        new IOException(
            "WEB_SESSION_GUEST_PROCESS",
            new IOException(
                "WEB_SESSION_ENV_UNREADABLE",
                new java.io.FileNotFoundException("/private/secret token=never-log-this")));
    String detail = WebSessionDiagnostics.failure("CAPTURE", error);
    assertEquals(
        "stage=CAPTURE code=WEB_SESSION_GUEST_PROCESS detail=WEB_SESSION_ENV_UNREADABLE kind=IO",
        detail);
    assertFalse(detail.contains("private"));
    assertFalse(detail.contains("never-log"));
    assertFalse(detail.contains("token"));
  }

  @Test
  public void storageAliasFailureHasSpecificSafeCode() {
    assertEquals(
        "stage=STORE_WRITE code=PARENT_LINK detail=PARENT_LINK kind=IO",
        WebSessionDiagnostics.failure("STORE_WRITE", new IOException("PARENT_LINK")));
  }

  @Test
  public void cyclicOrUntrustedMessagesRemainBoundedAndNeverBecomeLogMetadata() {
    var a = new IOException("WEB_SESSION_BAD\nsecret");
    var b = new IOException("URL secret");
    a.initCause(b);
    b.initCause(a);
    assertEquals(
        "stage=UNKNOWN code=UNCLASSIFIED detail=UNCLASSIFIED kind=IO",
        WebSessionDiagnostics.failure("secret\nfield", a));
    assertEquals(
        "stage=READ code=UNCLASSIFIED detail=UNCLASSIFIED kind=IO",
        WebSessionDiagnostics.failure(
            "READ", new IOException("WEB_SESSION_SECRET_WITH_VALID_SYNTAX")));
  }
}
