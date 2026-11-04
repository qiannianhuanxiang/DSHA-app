package com.deepseekharness.app.util;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class IslandTextTest {

  private static AgentTaskState.View row(
      String session, String phase, boolean active, long started, boolean stopping) {
    return new AgentTaskState.View(
        new AgentTaskState.Key(1, "run", session, 1),
        phase,
        "",
        active,
        started,
        started,
        "act-" + session,
        stopping,
        false);
  }

  @Test
  public void clipHonoursCapsuleLimitsAndNeverReturnsNull() {
    assertEquals("", IslandText.clip(null, true));
    assertEquals("", IslandText.clip("   ", true));
    assertEquals("等你确认", IslandText.clip("等你确认", true));
    assertEquals("1234567", IslandText.clip("1234567890", true));
    assertEquals("123456789", IslandText.clip("1234567890", false));
    // 边界值本身不裁剪。
    assertEquals(7, IslandText.clip("一二三四五六七", true).length());
    assertEquals(9, IslandText.clip("abcdefghi", false).length());
  }

  @Test
  public void rankExcludesTerminalDismissedAndUnknownRows() {
    assertEquals(0, IslandText.rank(null));
    assertEquals(0, IslandText.rank(row("a", "waiting", false, 1, false)));
    assertEquals(0, IslandText.rank(row("a", "unknown", true, 1, false)));
    AgentTaskState.View dismissed =
        new AgentTaskState.View(
            new AgentTaskState.Key(1, "run", "a", 1), "waiting", "", true, 1, 1, "x", false, true);
    assertEquals(0, IslandText.rank(dismissed));
  }

  @Test
  public void waitingOutranksRunningAndStoppingOutranksTool() {
    assertTrue(
        IslandText.rank(row("a", "waiting", true, 1, false))
            > IslandText.rank(row("a", "tool", true, 1, false)));
    assertTrue(
        IslandText.rank(row("a", "tool", true, 1, false))
            > IslandText.rank(row("a", "reply", true, 1, false)));
    // 已经点过停止的任务比普通执行更值得显示。
    assertTrue(
        IslandText.rank(row("a", "tool", true, 1, true))
            > IslandText.rank(row("a", "tool", true, 1, false)));
    assertEquals(
        IslandText.rank(row("a", "stop-requested", true, 1, false)),
        IslandText.rank(row("a", "tool", true, 1, true)));
  }

  @Test
  public void pickReturnsHighestPriorityAndBreaksTiesByEarliestStart() {
    assertNull(IslandText.pick(null));
    assertNull(IslandText.pick(List.of()));
    assertNull(IslandText.pick(List.of(row("done", "ended", false, 1, false))));

    var waiting = row("w", "waiting", true, 50, false);
    var tool = row("t", "tool", true, 10, false);
    assertEquals(waiting, IslandText.pick(List.of(tool, waiting)));

    // 同优先级取更早开始的，保证胶囊文字稳定。
    var older = row("o", "tool", true, 10, false);
    var newer = row("n", "tool", true, 20, false);
    assertEquals(older, IslandText.pick(List.of(newer, older)));
  }

  @Test
  public void tagOfMatchesNotificationTagFormat() {
    assertNull(IslandText.tagOf(null));
    var value = row("s", "tool", true, 1, false);
    assertTrue(IslandText.tagOf(value).startsWith("dsha-task:"));
  }
}
