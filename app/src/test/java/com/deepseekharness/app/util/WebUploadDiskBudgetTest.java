package com.deepseekharness.app.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class WebUploadDiskBudgetTest {
  @Test
  public void concurrentWritersCannotSpendTheSameAvailableChunk() throws Exception {
    WebUploadDiskBudget disk = new WebUploadDiskBudget();
    AtomicLong usable = new AtomicLong(WebTransferPolicy.UPLOAD_RESERVE + 65536);
    AtomicInteger accepted = new AtomicInteger(),
        rejected = new AtomicInteger(),
        probes = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    Thread[] workers = new Thread[2];
    for (int i = 0; i < workers.length; i++)
      workers[i] =
          new Thread(
              () -> {
                try {
                  start.await();
                  disk.write(
                      65536,
                      () -> {
                        probes.incrementAndGet();
                        return usable.get();
                      },
                      () -> {
                        accepted.incrementAndGet();
                        usable.addAndGet(-65536);
                      });
                } catch (IllegalArgumentException expected) {
                  rejected.incrementAndGet();
                } catch (Exception error) {
                  throw new AssertionError(error);
                }
              });
    for (Thread worker : workers) worker.start();
    start.countDown();
    for (Thread worker : workers) worker.join();
    assertEquals(1, accepted.get());
    assertEquals(1, rejected.get());
    assertEquals(2, probes.get());
    assertEquals(WebTransferPolicy.UPLOAD_RESERVE, usable.get());
  }

  @Test
  public void previouslyUsedCacheAndExternalWritesAreAlreadyIncludedInTheLiveProbe()
      throws Exception {
    WebUploadDiskBudget disk = new WebUploadDiskBudget();
    AtomicLong usable = new AtomicLong(WebTransferPolicy.UPLOAD_RESERVE + 100);
    disk.write(60, usable::get, () -> usable.addAndGet(-60));
    usable.addAndGet(-40); // 后端/其他应用占盘，不依赖启动时快照。
    assertThrows(
        IllegalArgumentException.class, () -> disk.write(1, usable::get, () -> fail("不应写入")));
    disk.write(0, usable::get, () -> {});
  }
}
