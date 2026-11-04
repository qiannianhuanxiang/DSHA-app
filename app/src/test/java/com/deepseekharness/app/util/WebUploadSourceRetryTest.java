package com.deepseekharness.app.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class WebUploadSourceRetryTest {
  @Test
  public void onlyOpeningMissingSourceRetriesOnce() throws Exception {
    AtomicInteger attempts = new AtomicInteger(), waits = new AtomicInteger();
    String result =
        WebUploadSourceRetry.open(
            () -> {
              if (attempts.incrementAndGet() == 1)
                throw new FileNotFoundException("private source");
              return "owned stream";
            },
            () -> {},
            millis -> {
              assertEquals(400, millis);
              waits.incrementAndGet();
            });
    assertEquals("owned stream", result);
    assertEquals(2, attempts.get());
    assertEquals(1, waits.get());
  }

  @Test
  public void persistentMissingSourceStopsAfterTwoAttempts() {
    AtomicInteger attempts = new AtomicInteger();
    FileNotFoundException error =
        assertThrows(
            FileNotFoundException.class,
            () ->
                WebUploadSourceRetry.open(
                    () -> {
                      attempts.incrementAndGet();
                      throw new FileNotFoundException();
                    },
                    () -> {},
                    millis -> {}));
    assertEquals(2, attempts.get());
    assertEquals(1, error.getSuppressed().length);
  }

  @Test
  public void permissionAndOrdinaryIoFailuresDoNotRetry() {
    AtomicInteger attempts = new AtomicInteger(), waits = new AtomicInteger();
    assertThrows(
        IOException.class,
        () ->
            WebUploadSourceRetry.open(
                () -> {
                  attempts.incrementAndGet();
                  throw new IOException("SAF_PERMISSION_REVOKED");
                },
                () -> {},
                millis -> waits.incrementAndGet()));
    assertEquals(1, attempts.get());
    assertEquals(0, waits.get());
  }

  @Test
  public void cancellationBeforeOrDuringWaitPreventsTheSecondOpen() {
    AtomicInteger attempts = new AtomicInteger();
    assertThrows(
        InterruptedIOException.class,
        () ->
            WebUploadSourceRetry.open(
                () -> {
                  attempts.incrementAndGet();
                  return "unused";
                },
                () -> {
                  throw new InterruptedIOException();
                },
                millis -> {}));
    assertEquals(0, attempts.get());
    assertThrows(
        InterruptedIOException.class,
        () ->
            WebUploadSourceRetry.open(
                () -> {
                  attempts.incrementAndGet();
                  throw new FileNotFoundException();
                },
                () -> {},
                millis -> {
                  throw new InterruptedIOException();
                }));
    assertEquals(1, attempts.get());
    java.util.concurrent.atomic.AtomicBoolean cancelled =
        new java.util.concurrent.atomic.AtomicBoolean();
    assertThrows(
        InterruptedIOException.class,
        () ->
            WebUploadSourceRetry.open(
                () -> {
                  attempts.incrementAndGet();
                  throw new FileNotFoundException();
                },
                () -> {
                  if (cancelled.get()) throw new InterruptedIOException();
                },
                millis -> cancelled.set(true)));
    assertEquals(2, attempts.get());
  }
}
