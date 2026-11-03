package com.deepseekharness.app.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class BrowserPresentationStateTest {
  @Test
  public void fullScreenPictureAndHiddenTransitionsKeepTheExistingPageState() {
    var state = new BrowserPresentationState();
    assertEquals(BrowserPresentationState.Mode.FULL, state.mode());
    state.picture(true);
    assertEquals(BrowserPresentationState.Mode.PIP, state.mode());
    state.picture(false);
    assertEquals(BrowserPresentationState.Mode.FULL, state.mode());
    state.hide();
    assertEquals(BrowserPresentationState.Mode.HIDDEN, state.mode());
    state.picture(false);
    assertEquals(BrowserPresentationState.Mode.HIDDEN, state.mode());
    state.picture(true);
    assertEquals(BrowserPresentationState.Mode.HIDDEN, state.mode());
    state.show();
    assertEquals(BrowserPresentationState.Mode.FULL, state.mode());
  }

  @Test
  public void retiredModesAndProcessRecreationCannotRestoreAQuickPanelOrFakePip() {
    var state = new BrowserPresentationState();
    for (String saved : new String[] {"FULL", "QUICK", "PIP", "invalid", null}) {
      state.restore(saved);
      assertEquals(BrowserPresentationState.Mode.FULL, state.mode());
    }
    state.restore("HIDDEN");
    assertEquals(BrowserPresentationState.Mode.HIDDEN, state.mode());
  }

  @Test
  public void productionModesContainOnlyFullScreenPipAndHidden() {
    assertArrayEquals(
        new BrowserPresentationState.Mode[] {
          BrowserPresentationState.Mode.FULL,
          BrowserPresentationState.Mode.PIP,
          BrowserPresentationState.Mode.HIDDEN
        },
        BrowserPresentationState.Mode.values());
  }
}
