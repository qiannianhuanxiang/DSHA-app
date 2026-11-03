package com.deepseekharness.app.util;

import java.io.FileNotFoundException;
import java.io.IOException;

/** 只对源打开失败重试一次，绝不重放已经开始的读取或复制。 */
public final class WebUploadSourceRetry {
  public static final long DELAY_MILLIS = 400;

  private WebUploadSourceRetry() {}

  public interface Opener<T> {
    T open() throws IOException;
  }

  public interface Check {
    void check() throws IOException;
  }

  public interface Delay {
    void await(long millis) throws IOException;
  }

  public static <T> T open(Opener<T> opener, Check cancellation, Delay delay) throws IOException {
    cancellation.check();
    try {
      return opener.open();
    } catch (FileNotFoundException first) {
      cancellation.check();
      delay.await(DELAY_MILLIS);
      cancellation.check();
      try {
        return opener.open();
      } catch (FileNotFoundException second) {
        if (first != second) second.addSuppressed(first);
        throw second;
      }
    }
  }
}
