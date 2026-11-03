package com.deepseekharness.app.util;

import static org.junit.Assert.*;
import org.junit.Test;

public final class TerminalWorkspacePolicyTest {
  @Test
  public void pageBackNeverRepresentsProcessEnd() {
    assertEquals(
        TerminalWorkspacePolicy.Back.LEAVE_PAGE, TerminalWorkspacePolicy.back(false, false, false));
    assertEquals(
        TerminalWorkspacePolicy.Back.HIDE_IME, TerminalWorkspacePolicy.back(false, false, true));
    assertEquals(
        TerminalWorkspacePolicy.Back.LEAVE_SELECTION,
        TerminalWorkspacePolicy.back(false, true, true));
    assertEquals(
        TerminalWorkspacePolicy.Back.DISMISS_PANEL, TerminalWorkspacePolicy.back(true, true, true));
  }

  @Test
  public void inlineNeedsWideWindowAndKeepsTerminalWidth() {
    assertFalse(TerminalWorkspacePolicy.canInline(839));
    assertTrue(TerminalWorkspacePolicy.canInline(840));
    assertTrue(TerminalWorkspacePolicy.canInline(1024));
    assertEquals(240, TerminalWorkspacePolicy.inlineWidth(840));
    assertEquals(256, TerminalWorkspacePolicy.inlineWidth(1024));
    assertEquals(296, TerminalWorkspacePolicy.inlineWidth(1280));
    assertEquals(296, TerminalWorkspacePolicy.inlineWidth(1600));
    assertTrue(840 - TerminalWorkspacePolicy.inlineWidth(840) >= 480);
  }

  @Test
  public void drawerWidthUsesTabletMaxAndScrimMargin() {
    assertEquals(336, TerminalWorkspacePolicy.drawerMaxWidth());
    assertEquals(336, TerminalWorkspacePolicy.drawerWidth(1000, 336, 32));
    assertEquals(336, TerminalWorkspacePolicy.drawerWidth(368, 336, 32));
    assertEquals(288, TerminalWorkspacePolicy.drawerWidth(320, 336, 32));
    assertEquals(208, TerminalWorkspacePolicy.drawerWidth(240, 336, 32));
    assertEquals(336, TerminalWorkspacePolicy.drawerWidth(0, 336, 32));
    assertEquals(12, TerminalWorkspacePolicy.drawerWidth(16, 336, 32));
    for (int width = 1; width <= 400; width++) {
      int drawer = TerminalWorkspacePolicy.drawerWidth(width, 336, 32);
      assertTrue(drawer > 0 && drawer <= width);
    }
  }

  @Test
  public void shortWindowsSwitchToCompactAndDenseChrome() {
    assertTrue(TerminalWorkspacePolicy.compactHeight(559));
    assertFalse(TerminalWorkspacePolicy.compactHeight(560));
    assertTrue(TerminalWorkspacePolicy.denseFooter(479));
    assertFalse(TerminalWorkspacePolicy.denseFooter(480));
    assertTrue(TerminalWorkspacePolicy.shortLandscape(1422, 889));
    assertTrue(TerminalWorkspacePolicy.shortLandscape(800, 400));
    assertFalse(TerminalWorkspacePolicy.shortLandscape(800, 889));
    assertFalse(TerminalWorkspacePolicy.shortLandscape(1600, 1200));
  }

  @Test
  public void actionColumnsFollowPanelWidthAndFontScale() {
    assertEquals(3, TerminalWorkspacePolicy.footerColumns(336, 1f, true));
    assertEquals(2, TerminalWorkspacePolicy.footerColumns(240, 1f, true));
    assertEquals(2, TerminalWorkspacePolicy.footerColumns(336, 1.3f, true));
    assertEquals(1, TerminalWorkspacePolicy.footerColumns(336, 2f, true));
    assertEquals(1, TerminalWorkspacePolicy.footerColumns(208, 1f, false));
    assertEquals(2, TerminalWorkspacePolicy.footerColumns(336, Float.NaN, false));
    assertTrue(TerminalWorkspacePolicy.stackSessionDetails(240, 1f));
    assertTrue(TerminalWorkspacePolicy.stackSessionDetails(336, 1.3f));
    assertFalse(TerminalWorkspacePolicy.stackSessionDetails(336, 1f));
    assertTrue(TerminalWorkspacePolicy.compactHeight(700, 1.5f));
    assertTrue(TerminalWorkspacePolicy.denseFooter(700, 1.5f));
  }

  @Test
  public void measuredChromeCannotConsumeTheSessionViewport() {
    int minimum = TerminalWorkspacePolicy.minimumSessionHeight(1f);
    assertFalse(TerminalWorkspacePolicy.scrollWholeSidebar(640, 320, minimum));
    assertFalse(TerminalWorkspacePolicy.scrollWholeSidebar(432, 320, minimum));
    assertTrue(TerminalWorkspacePolicy.scrollWholeSidebar(431, 320, minimum));
    assertTrue(TerminalWorkspacePolicy.scrollWholeSidebar(160, 320, minimum));
    assertFalse(TerminalWorkspacePolicy.scrollWholeSidebar(0, 320, minimum));
    assertTrue(
        TerminalWorkspacePolicy.scrollWholeSidebar(
            640, 420, TerminalWorkspacePolicy.minimumSessionHeight(2f)));
    assertTrue(TerminalWorkspacePolicy.scrollWholeSidebar(100, Integer.MAX_VALUE, minimum));
  }

  @Test
  public void processEpochPreventsOldIdReclaimingNewSession() {
    assertFalse(TerminalWorkspacePolicy.restoreSelection("old", "new", 1, true));
    assertFalse(TerminalWorkspacePolicy.restoreSelection(null, "new", 1, true));
    assertFalse(TerminalWorkspacePolicy.restoreSelection("new", "new", 1, false));
    assertFalse(TerminalWorkspacePolicy.restoreSelection("new", "new", 0, true));
    assertTrue(TerminalWorkspacePolicy.restoreSelection("new", "new", 1, true));
  }

  @Test
  public void floatingMenuNeverLeavesShortKeyboardWindow() {
    for (int height : new int[] {80, 160, 320, 640}) {
      for (float fraction : new float[] {-1, 0, .6f, 1, 4, Float.NaN}) {
        int y = TerminalWorkspacePolicy.ballY(height, 48, 12, 100, fraction);
        assertTrue(y >= 0);
        assertTrue(y + 48 <= height);
      }
    }
  }

  @Test
  public void ballUsesWholeTouchTargetAndClampsInvalidSavedState() {
    assertEquals(12, TerminalWorkspacePolicy.ballX(360, 48, 12, false));
    assertEquals(300, TerminalWorkspacePolicy.ballX(360, 48, 12, true));
    assertEquals(.6f, TerminalWorkspacePolicy.fraction(Float.NaN), 0f);
    assertEquals(0f, TerminalWorkspacePolicy.fraction(-3), 0f);
    assertEquals(1f, TerminalWorkspacePolicy.fraction(3), 0f);
  }
}
