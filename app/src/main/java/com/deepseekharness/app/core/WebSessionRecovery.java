package com.deepseekharness.app.core;

import com.deepseekharness.app.runtime.StoredWebSession;

/** 有界只读识别；未知证据不启动、不停止，也不擦除任何现场。 */
final class WebSessionRecovery {
  enum Result {
    NONE,
    RESTORED,
    UNKNOWN,
    STALE
  }

  interface Ports {
    StoredWebSession.Record read() throws Exception;

    boolean current();

    boolean compatible(StoredWebSession.Record record) throws Exception;

    boolean verify(StoredWebSession.Record record) throws Exception;

    boolean authenticate(StoredWebSession.Record record) throws Exception;

    boolean adopt(StoredWebSession.Record record) throws Exception;

    default void unavailable(String stage, Exception failure) {}
  }

  static Result recover(Ports ports) {
    String stage = "READ";
    try {
      var saved = ports.read();
      if (!ports.current()) return Result.STALE;
      if (saved == null) return Result.NONE;
      stage = "COMPATIBILITY";
      if (!ports.compatible(saved)) return Result.UNKNOWN;
      stage = "KERNEL_VERIFY";
      if (!ports.verify(saved)) return Result.UNKNOWN;
      if (!ports.current()) return Result.STALE;
      stage = "AUTHENTICATE";
      if (!ports.authenticate(saved)) return Result.UNKNOWN;
      if (!ports.current()) return Result.STALE;
      stage = "KERNEL_REVERIFY";
      if (!ports.verify(saved)) return Result.UNKNOWN;
      stage = "ADOPT";
      return ports.current() && ports.adopt(saved) ? Result.RESTORED : Result.STALE;
    } catch (Exception unavailable) {
      try {
        ports.unavailable(stage, unavailable);
      } catch (RuntimeException ignored) {
        // Diagnostic delivery cannot change the unknown result or grant adoption.
      }
      return Result.UNKNOWN;
    }
  }
}
