package com.deepseekharness.app.util;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import java.util.ArrayDeque;

/** 主线程读取终端缓冲区得到的只读快照；后续输出和会话切换不会改变导出内容。 */
public final class TerminalOutputSnapshot {
  public static final int MAX_CHARACTERS = 65536;
  private static final int MAX_ROWS = 10000;

  public record Part(String text, boolean truncated) {}

  public final String sessionName;
  public final Part screen;
  public final Part history;

  private TerminalOutputSnapshot(String sessionName, Part screen, Part history) {
    this.sessionName = sessionName;
    this.screen = screen;
    this.history = history;
  }

  /** 必须与 emulator 的更新在同一线程调用；当前屏幕遵循用户实际滚动到的位置。 */
  public static TerminalOutputSnapshot capture(
      String sessionName, TerminalEmulator emulator, int topRow) {
    TerminalBuffer buffer = emulator.getScreen();
    int first = -buffer.getActiveTranscriptRows();
    int top = Math.max(first, Math.min(0, topRow));
    int last = emulator.mRows - 1;
    Part screen = read(buffer, emulator.mColumns, top, Math.min(last, top + emulator.mRows - 1));
    Part history = read(buffer, emulator.mColumns, first, last);
    return new TerminalOutputSnapshot(SensitiveData.redact(sessionName), screen, history);
  }

  private record Row(String text, boolean wrapped) {}

  private static Part read(TerminalBuffer buffer, int columns, int first, int last) {
    ArrayDeque<Row> lines = new ArrayDeque<>();
    int length = 0;
    boolean truncated = false;
    for (int row = last; row >= first; row--) {
      String line = buffer.getSelectedText(0, row, columns - 1, row);
      boolean wrapped = buffer.getLineWrap(row);
      if (row < last && !wrapped) line += "\n";
      if (lines.size() >= MAX_ROWS || length + line.length() > MAX_CHARACTERS) {
        // 截断点若落在软换行中，舍弃不完整逻辑行，保留后面的完整行。
        // 否则 password= 等前缀可能被剪掉，令凭据尾部失去脱敏上下文。
        if (wrapped) {
          while (!lines.isEmpty()) {
            if (!lines.removeFirst().wrapped()) break;
          }
        }
        truncated = true;
        break;
      }
      lines.addFirst(new Row(line, wrapped));
      length += line.length();
    }
    StringBuilder raw = new StringBuilder(length);
    for (Row line : lines) raw.append(line.text());
    String text = SensitiveData.redact(raw.toString());
    // 短敏感值替换为 *** 可能变长；最后的预算以真正展示/导出的字符串为准。
    if (text.length() > MAX_CHARACTERS) {
      int start = text.length() - MAX_CHARACTERS;
      if (Character.isLowSurrogate(text.charAt(start))) start++;
      text = text.substring(start);
      truncated = true;
    }
    return new Part(text, truncated);
  }
}
