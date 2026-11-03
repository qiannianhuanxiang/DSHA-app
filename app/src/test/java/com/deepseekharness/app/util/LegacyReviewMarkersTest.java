package com.deepseekharness.app.util;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** 宿主前置检查必须是 migrate_legacy_reviews 候选集合的超集：宁可多跑，不可漏跑。 */
public class LegacyReviewMarkersTest {
  private Path root;

  @Before
  public void setUp() throws Exception {
    root = Files.createTempDirectory("legacy-review-markers-");
  }

  @After
  public void tearDown() throws Exception {
    try (var paths = Files.walk(root)) {
      for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList())
        Files.deleteIfExists(p);
    }
  }

  private Path modules() throws Exception {
    return Files.createDirectories(root.resolve("root/.dsh/profiles/web/node_modules"));
  }

  @Test
  public void pathMatchesGuestProfile() {
    assertEquals(
        root.resolve("root/.dsh/profiles/web/node_modules").toFile(),
        LegacyReviewMarkers.nodeModules(root.toFile()));
  }

  @Test
  public void missingProfileHasNoMarkers() {
    assertFalse(LegacyReviewMarkers.mayExist(LegacyReviewMarkers.nodeModules(root.toFile())));
  }

  @Test
  public void userDisabledAndForeignFilesAreNotMarkers() throws Exception {
    Path m = modules();
    Files.write(m.resolve("plain.disabled"), new byte[0]);
    Files.writeString(m.resolve("safe.disabled"), "DSHA_SAFE_MODE\n");
    Files.writeString(m.resolve("near.disabled"), "DSHA_REVIEW_REQUIREX\n");
    Files.writeString(m.resolve("pkg"), "DSHA_REVIEW_REQUIRED\n");
    Files.createDirectories(m.resolve("dir.disabled"));
    assertFalse(LegacyReviewMarkers.mayExist(m.toFile()));
  }

  @Test
  public void exactMarkerIsFoundAtTopLevelAndInScope() throws Exception {
    Path m = modules();
    Files.write(m.resolve("old.disabled"), LegacyReviewMarkers.CONTENT);
    assertTrue(LegacyReviewMarkers.mayExist(m.toFile()));
    Files.delete(m.resolve("old.disabled"));
    Path scope = Files.createDirectories(m.resolve("@acme"));
    Files.write(
        scope.resolve("tool.disabled"),
        "DSHA_REVIEW_REQUIRED\n".getBytes(StandardCharsets.US_ASCII));
    assertTrue(LegacyReviewMarkers.mayExist(m.toFile()));
  }

  @Test
  public void symlinkMarkerIsIgnoredLikeTheGuestScript() throws Exception {
    Path m = modules();
    Path target = Files.write(root.resolve("target"), LegacyReviewMarkers.CONTENT);
    try {
      Files.createSymbolicLink(m.resolve("link.disabled"), target);
    } catch (UnsupportedOperationException | java.io.IOException unsupported) {
      return;
    }
    assertFalse(LegacyReviewMarkers.mayExist(m.toFile()));
  }

  @Test
  public void nonDirectoryOrUnreadableProfileFallsBackToGuest() throws Exception {
    Path parent = Files.createDirectories(root.resolve("root/.dsh/profiles/web"));
    Path file = Files.writeString(parent.resolve("node_modules"), "not a directory");
    assertTrue(LegacyReviewMarkers.mayExist(file.toFile()));
  }
}
