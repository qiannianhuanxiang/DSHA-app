package com.deepseekharness.app.util;

import java.io.IOException;
import java.util.function.LongSupplier;

/** 同一宿主缓存卷的检查与写入串行；不在这里等待或读取远端 Provider。 */
public final class WebUploadDiskBudget {
  public interface Write {
    void run() throws IOException;
  }

  public synchronized void write(long nextBytes, LongSupplier usableSpace, Write write)
      throws IOException {
    WebTransferPolicy.checkUploadSpace(nextBytes, usableSpace.getAsLong());
    write.run();
  }
}
