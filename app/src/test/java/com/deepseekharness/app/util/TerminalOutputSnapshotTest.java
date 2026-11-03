package com.deepseekharness.app.util;

import static org.junit.Assert.*;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSessionClient;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** 用实际终端模拟器核验可见区、换行、历史边界和不可变导出内容。 */
public class TerminalOutputSnapshotTest {
  private static TerminalEmulator emulator(int columns, int rows) {
    TerminalSessionClient client =
        (TerminalSessionClient)
            Proxy.newProxyInstance(
                TerminalSessionClient.class.getClassLoader(),
                new Class<?>[] {TerminalSessionClient.class},
                (proxy, method, args) -> null);
    TerminalOutput output =
        new TerminalOutput() {
          @Override
          public void write(byte[] data, int offset, int count) {
            fail("Snapshot must not write terminal input");
          }

          @Override
          public void titleChanged(String oldTitle, String newTitle) {}

          @Override
          public void onCopyTextToClipboard(String text) {}

          @Override
          public void onPasteTextFromClipboard() {}

          @Override
          public void onBell() {}

          @Override
          public void onColorsChanged() {}
        };
    return new TerminalEmulator(output, columns, rows, 2000, client);
  }

  private static void append(TerminalEmulator emulator, String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    emulator.append(bytes, bytes.length);
  }

  @Test
  public void visibleScreenUsesScrolledViewportAndHistoryIncludesLiveScreen() {
    TerminalEmulator emulator = emulator(20, 3);
    append(emulator, "first\r\nsecond\r\nthird\r\nfourth");
    TerminalOutputSnapshot snapshot = TerminalOutputSnapshot.capture("PTY 1", emulator, -1);
    assertEquals("first\nsecond\nthird", snapshot.screen.text());
    assertEquals("first\nsecond\nthird\nfourth", snapshot.history.text());
    assertFalse(snapshot.history.truncated());
  }

  @Test
  public void laterOutputDoesNotMutateEitherSnapshot() {
    TerminalEmulator emulator = emulator(20, 3);
    append(emulator, "before");
    TerminalOutputSnapshot snapshot = TerminalOutputSnapshot.capture("one", emulator, 0);
    String original = snapshot.history.text();
    append(emulator, "\r\nafter");
    assertEquals(original, snapshot.history.text());
    assertFalse(snapshot.screen.text().contains("after"));
    assertTrue(TerminalOutputSnapshot.capture("two", emulator, 0).history.text().contains("after"));
    assertEquals("one", snapshot.sessionName);
  }

  @Test
  public void unicodeSoftWrapsJoinButHardNewlinesAndIndentationRemain() {
    TerminalEmulator emulator = emulator(6, 4);
    append(emulator, "  😀中文Z\r\nnext");
    TerminalOutputSnapshot snapshot = TerminalOutputSnapshot.capture("PTY", emulator, 0);
    assertTrue(snapshot.history.text(), snapshot.history.text().startsWith("  😀中文Z\nnext"));
  }

  @Test
  public void redactsKnownPatternsAfterJoiningWrappedRows() {
    TerminalEmulator emulator = emulator(16, 8);
    String secret = "sk-abcdefghijklmnopqrstuvwx";
    append(emulator, "api_key=" + secret);
    TerminalOutputSnapshot snapshot =
        TerminalOutputSnapshot.capture("token=" + secret, emulator, 0);
    assertFalse(snapshot.history.text().contains(secret));
    assertTrue(snapshot.history.text().contains("api_key=***"));
    assertEquals("token=***", snapshot.sessionName);
  }

  @Test
  public void longHistoryRetainsRecentRowsAndReportsItsLimit() {
    TerminalEmulator emulator = emulator(80, 24);
    StringBuilder text = new StringBuilder("OLDEST\r\n");
    for (int i = 0; i < 1500; i++)
      text.append("line ").append(i).append(" ").append("x".repeat(60)).append("\r\n");
    text.append("LATEST 😀");
    append(emulator, text.toString());
    TerminalOutputSnapshot snapshot = TerminalOutputSnapshot.capture("PTY", emulator, 0);
    assertTrue(snapshot.history.truncated());
    assertTrue(snapshot.history.text().length() <= TerminalOutputSnapshot.MAX_CHARACTERS);
    assertFalse(snapshot.history.text().contains("OLDEST"));
    assertTrue(snapshot.history.text().contains("LATEST 😀"));
    assertFalse(Character.isLowSurrogate(snapshot.history.text().charAt(0)));
    assertFalse(snapshot.screen.truncated());
  }

  @Test
  public void redactionExpansionStillRespectsFinalCharacterLimit() {
    TerminalEmulator emulator = emulator(80, 24);
    append(emulator, ("token=x ".repeat(8) + "\r\n").repeat(1100));
    TerminalOutputSnapshot snapshot = TerminalOutputSnapshot.capture("PTY", emulator, 0);
    assertTrue(snapshot.history.truncated());
    assertTrue(snapshot.history.text().length() <= TerminalOutputSnapshot.MAX_CHARACTERS);
    assertFalse(snapshot.history.text().contains("token=x"));
    assertTrue(snapshot.history.text().contains("token=***"));
  }

  @Test
  public void truncatedSoftWrappedCredentialIsOmittedBeforeRedaction() {
    TerminalEmulator emulator = emulator(80, 24);
    append(
        emulator,
        "password=" + "A".repeat(TerminalOutputSnapshot.MAX_CHARACTERS + 160) + "\r\nsafe tail");
    TerminalOutputSnapshot snapshot = TerminalOutputSnapshot.capture("PTY", emulator, 0);
    assertTrue(snapshot.history.truncated());
    assertTrue(snapshot.history.text().contains("safe tail"));
    assertFalse(snapshot.history.text().contains("AAAA"));
  }

  @Test
  public void finalContinuationRowIsDiscardedEvenWhenItDoesNotWrapAgain() {
    TerminalEmulator emulator = emulator(80, 24);
    String complete = "b".repeat(TerminalOutputSnapshot.MAX_CHARACTERS - 20);
    append(emulator, "password=" + "A".repeat(82) + "\r\n" + complete);
    TerminalOutputSnapshot snapshot = TerminalOutputSnapshot.capture("PTY", emulator, 0);
    assertTrue(snapshot.history.truncated());
    assertEquals(complete, snapshot.history.text());
  }

  @Test
  public void oversizedLogicalLineHasNoCredentialTailPreview() {
    TerminalEmulator emulator = emulator(80, 24);
    append(emulator, "password=" + "A".repeat(TerminalOutputSnapshot.MAX_CHARACTERS + 160));
    TerminalOutputSnapshot snapshot = TerminalOutputSnapshot.capture("PTY", emulator, 0);
    assertTrue(snapshot.history.truncated());
    assertTrue(snapshot.history.text().isEmpty());
  }

  @Test
  public void staleScrollPositionClampsToAvailableHistory() {
    TerminalEmulator emulator = emulator(20, 3);
    append(emulator, "first\r\nsecond\r\nthird\r\nfourth");
    assertEquals(
        "first\nsecond\nthird",
        TerminalOutputSnapshot.capture("PTY", emulator, -999).screen.text());
    assertEquals(
        "second\nthird\nfourth", TerminalOutputSnapshot.capture("PTY", emulator, 99).screen.text());
  }

  @Test
  public void alternateScreenNeverMixesHiddenMainHistory() {
    TerminalEmulator emulator = emulator(20, 3);
    append(emulator, "main history\r\n\u001b[?1049hALT");
    TerminalOutputSnapshot snapshot = TerminalOutputSnapshot.capture("PTY", emulator, 0);
    assertTrue(snapshot.history.text().contains("ALT"));
    assertFalse(snapshot.history.text().contains("main history"));
    assertEquals(snapshot.screen.text(), snapshot.history.text());
  }
}
