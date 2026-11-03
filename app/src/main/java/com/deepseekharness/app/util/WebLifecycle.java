package com.deepseekharness.app.util;

/** 进程级启停门控：只管单飞、用户停止意图和会话代次，不执行 IO。 */
public final class WebLifecycle {
  private long generation;
  private boolean starting;
  private boolean stopping;
  private boolean userStopped;
  // 只记录本代实际创建进程的引擎；配置更改或旧进程恢复不能充当运行证据。
  private String runtimeMode = "";

  /** 仅未收到任何启停意图的宿主会话可认领完整核验的原Web，不执行命令。 */
  public synchronized boolean adoptRunning(long savedGeneration) {
    if (generation != 0
        || starting
        || stopping
        || userStopped
        || savedGeneration < 1
        || savedGeneration == Long.MAX_VALUE) return false;
    generation = savedGeneration;
    return true;
  }

  /** 返回新代次；忙碌或不允许自动拉起时返回 -1。 */
  public synchronized long beginStart(boolean automatic, boolean stopSentinelPresent) {
    if (starting || stopping || (automatic && (userStopped || stopSentinelPresent))) {
      return -1;
    }
    userStopped = false;
    starting = true;
    runtimeMode = "";
    return ++generation;
  }

  /** 停止请求立即撤销旧代次，重复停止合并。 */
  public synchronized long beginStop() {
    if (!stopping) {
      userStopped = true;
      starting = false;
      stopping = true;
      runtimeMode = "";
      generation++;
    }
    return generation;
  }

  /** 仅启动中的当前代次可发布实际引擎；兼容重试可在鉴权前更新为 proot。 */
  public synchronized boolean recordRuntime(long expected, String mode) {
    if (!isCurrent(expected) || !starting) return false;
    if (!"proot".equals(mode) && !"proroot".equals(mode) && !"bxroot".equals(mode)) return false;
    runtimeMode = mode;
    return true;
  }

  /** 空值表示尚无证据；恢复旧会话时不得根据当前偏好猜测实际引擎。 */
  public synchronized String runtimeMode() {
    return runtimeMode;
  }

  public synchronized boolean finishStart(long expected) {
    if (!isCurrent(expected) || !starting) return false;
    starting = false;
    return true;
  }

  public synchronized void finishStop(long expected) {
    if (isCurrent(expected)) stopping = false;
  }

  public synchronized boolean canAutoStart(boolean stopSentinelPresent) {
    return !starting && !stopping && !userStopped && !stopSentinelPresent;
  }

  public synchronized boolean isCurrent(long expected) {
    return expected > 0 && generation == expected;
  }

  public synchronized long generation() {
    return generation;
  }

  public synchronized boolean isStarting() {
    return starting;
  }

  public synchronized boolean isStopping() {
    return stopping;
  }

  public synchronized boolean isUserStopped() {
    return userStopped;
  }
}
