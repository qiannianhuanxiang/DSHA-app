package com.deepseekharness.app.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class BrowserThemeGateTest {
  private final PreviewPageSession.Identity identity =
      new PreviewPageSession.Identity(7, "runtime-a", "http://127.0.0.1:32911/?token=a");

  @Test
  public void RejectsWrongFrameOriginRuntimeNonceAndOwnerWithoutBurningSequence() {
    Object owner = new Object();
    var gate = new BrowserThemeGate(owner, identity);
    assertFalse(
        gate.accept(
            new Object(),
            identity,
            gate.nonce(),
            7,
            "runtime-a",
            0,
            true,
            "http://127.0.0.1:32911/"));
    assertFalse(
        gate.accept(
            owner, identity, gate.nonce(), 7, "runtime-a", 0, false, "http://127.0.0.1:32911/"));
    assertFalse(
        gate.accept(
            owner, identity, gate.nonce(), 7, "runtime-a", 0, true, "http://localhost:32911/"));
    assertFalse(
        gate.accept(
            owner, identity, gate.nonce(), 7, "runtime-a", 0, true, "http://127.0.0.1:32912/"));
    assertFalse(
        gate.accept(owner, identity, "old", 7, "runtime-a", 0, true, "http://127.0.0.1:32911/"));
    assertFalse(
        gate.accept(
            owner, identity, gate.nonce(), 8, "runtime-a", 0, true, "http://127.0.0.1:32911/"));
    assertFalse(
        gate.accept(
            owner, identity, gate.nonce(), 7, "runtime-b", 0, true, "http://127.0.0.1:32911/"));
    assertTrue(
        gate.accept(
            owner,
            identity,
            gate.nonce(),
            7,
            "runtime-a",
            0,
            true,
            "http://127.0.0.1:32911/session"));
  }

  @Test
  public void ReplaysAndStoppedOrSamePortReplacementAreRejected() {
    Object owner = new Object();
    var gate = new BrowserThemeGate(owner, identity);
    assertTrue(
        gate.accept(
            owner, identity, gate.nonce(), 7, "runtime-a", 1, true, "http://127.0.0.1:32911/"));
    assertFalse(
        gate.accept(
            owner, identity, gate.nonce(), 7, "runtime-a", 1, true, "http://127.0.0.1:32911/"));
    assertFalse(
        gate.accept(owner, null, gate.nonce(), 7, "runtime-a", 2, true, "http://127.0.0.1:32911/"));
    var replacement = new PreviewPageSession.Identity(8, "runtime-b", identity.authUrl());
    assertFalse(
        gate.accept(
            owner, replacement, gate.nonce(), 7, "runtime-a", 2, true, "http://127.0.0.1:32911/"));
    var nextDocument = new BrowserThemeGate(owner, identity);
    assertFalse(
        nextDocument.accept(
            owner, identity, gate.nonce(), 7, "runtime-a", 2, true, "http://127.0.0.1:32911/"));
    assertTrue(
        nextDocument.accept(
            owner,
            identity,
            nextDocument.nonce(),
            7,
            "runtime-a",
            0,
            true,
            "http://127.0.0.1:32911/"));
  }
}
