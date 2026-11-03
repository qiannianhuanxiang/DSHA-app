package com.deepseekharness.app.util;

import java.util.List;

/**
 * 任务状态上岛的纯逻辑：岛位选举与短文案裁剪。
 *
 * <p>不导入任何 Android API，便于在宿主 JVM 上直接单测。真正的通知增强由
 * {@code com.deepseekharness.app.IslandSupport} 负责。
 *
 * <p>为什么需要选举：系统按包名只显示一个胶囊（SystemUI {@code filterByPackage}），
 * 多个活跃任务同时请求提升会互相顶掉，表现为胶囊文字乱跳。这里按
 * 「等待确认 &gt; 正在停止 &gt; 执行工具 &gt; 生成回复 &gt; 其他」选出唯一代表。
 */
public final class IslandText {
  /** 胶囊最大宽度约 96dp，实测中文约 7 字、英文约 9 字符即会开始截断。 */
  public static final int ZH_LIMIT = 7;

  public static final int EN_LIMIT = 9;

  private IslandText() {}

  /** 裁剪胶囊短文本；null/空返回空串。 */
  public static String clip(String text, boolean chinese) {
    if (text == null) return "";
    String value = text.trim();
    int limit = chinese ? ZH_LIMIT : EN_LIMIT;
    if (value.length() <= limit) return value;
    return value.substring(0, limit);
  }

  /**
   * 岛的候选优先级；0 表示不参与（终态、被划掉、状态不明）。
   *
   * <p>正在停止的任务排在执行前：用户已经点了停止，这时岛上更应该显示「正在停止」。
   */
  public static int rank(AgentTaskState.View row) {
    if (row == null || !row.active() || row.dismissed()) return 0;
    if ("unknown".equals(row.phase())) return 0;
    if (row.stopping() || "stop-requested".equals(row.phase())) return 4;
    return switch (row.phase()) {
      case "waiting" -> 5;
      case "tool" -> 3;
      case "reply" -> 2;
      default -> 1;
    };
  }

  /**
   * 选出当前应该占用岛位的任务；没有候选时返回 null。
   *
   * <p>同优先级时先开始的任务优先，保证胶囊文字稳定，不会来回跳。
   */
  public static AgentTaskState.View pick(List<AgentTaskState.View> rows) {
    if (rows == null) return null;
    AgentTaskState.View best = null;
    int bestRank = 0;
    for (AgentTaskState.View row : rows) {
      int rank = rank(row);
      if (rank == 0) continue;
      if (best == null || rank > bestRank || (rank == bestRank && row.started() < best.started())) {
        best = row;
        bestRank = rank;
      }
    }
    return best;
  }

  /** 通知 tag 与岛位的对应关系；选中任务对应唯一 tag。 */
  public static String tagOf(AgentTaskState.View row) {
    return row == null ? null : "dsha-task:" + row.key();
  }
}
