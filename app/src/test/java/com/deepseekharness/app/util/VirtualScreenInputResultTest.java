package com.deepseekharness.app.util;

import static org.junit.Assert.*;
import org.junit.Test;

public final class VirtualScreenInputResultTest {
  @Test
  public void partialSubmissionAndUnknownNeverBecomeRejected() {
    assertEquals(
        VirtualScreenInputResult.UNKNOWN,
        VirtualScreenInputResult.after(
            VirtualScreenInputResult.ACCEPTED, VirtualScreenInputResult.REJECTED));
    assertEquals(
        VirtualScreenInputResult.UNKNOWN,
        VirtualScreenInputResult.after(
            VirtualScreenInputResult.UNKNOWN, VirtualScreenInputResult.ACCEPTED));
    assertEquals(
        VirtualScreenInputResult.REJECTED,
        VirtualScreenInputResult.after(
            VirtualScreenInputResult.REJECTED, VirtualScreenInputResult.REJECTED));
    assertEquals(
        VirtualScreenInputResult.ACCEPTED,
        VirtualScreenInputResult.after(
            VirtualScreenInputResult.REJECTED, VirtualScreenInputResult.ACCEPTED));
  }
}
