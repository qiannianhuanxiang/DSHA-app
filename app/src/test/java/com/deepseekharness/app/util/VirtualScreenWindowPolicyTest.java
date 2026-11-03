package com.deepseekharness.app.util;

import static org.junit.Assert.*;
import org.junit.Test;

public final class VirtualScreenWindowPolicyTest {
  @Test
  public void metadataSelectsFocusedWindowBeforeRootReadAndNeverAnotherDisplay() {
    var windows =
        java.util.List.of(
            new VirtualScreenWindowPolicy.Window(0, 1, 100, true, true, true),
            new VirtualScreenWindowPolicy.Window(8, 2, 90, true, false, true),
            new VirtualScreenWindowPolicy.Window(8, 3, 1, true, true, false),
            new VirtualScreenWindowPolicy.Window(8, 4, 110, false, true, true));
    assertEquals(2, VirtualScreenWindowPolicy.select(8, windows));
    assertEquals(0, VirtualScreenWindowPolicy.select(0, windows));
    assertEquals(-1, VirtualScreenWindowPolicy.select(9, windows));
  }

  @Test
  public void ambiguousWindowsAndInvalidIdentityFailClosed() {
    assertEquals(
        -1,
        VirtualScreenWindowPolicy.select(
            8,
            java.util.List.of(
                new VirtualScreenWindowPolicy.Window(8, 2, 1, true, true, false),
                new VirtualScreenWindowPolicy.Window(8, 3, 1, true, true, false))));
    assertEquals(
        -1,
        VirtualScreenWindowPolicy.select(
            8,
            java.util.List.of(new VirtualScreenWindowPolicy.Window(8, -1, 1, true, true, true))));
  }

  @Test
  public void focusedWindowWinsConsistently() {
    assertTrue(
        VirtualScreenWindowPolicy.rank(true, false) > VirtualScreenWindowPolicy.rank(false, true));
    assertTrue(
        VirtualScreenWindowPolicy.rank(false, true) > VirtualScreenWindowPolicy.rank(false, false));
  }

  @Test
  public void approvalCannotFollowAChangedApplicationOrDisplay() {
    assertTrue(VirtualScreenWindowPolicy.approved(8, "app.a", 8, "app.a"));
    assertFalse(VirtualScreenWindowPolicy.approved(8, "app.a", 8, "app.b"));
    assertFalse(VirtualScreenWindowPolicy.approved(8, "app.a", 9, "app.a"));
    assertFalse(VirtualScreenWindowPolicy.approved(8, "", 8, "app.b"));
  }
}
