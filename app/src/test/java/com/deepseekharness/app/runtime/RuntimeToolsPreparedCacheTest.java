package com.deepseekharness.app.runtime;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

/** 跨进程准备缓存的编解码与路径约束；命中仍由 stamp 重算决定，不在此测。 */
public class RuntimeToolsPreparedCacheTest {
  private static final String ROOT = "/data/data/pkg/files/linux/ubuntu";
  private static final List<String> PARENTS =
      List.of("/data/user/0/pkg/files/linux", "/data/data/pkg/files/linux");

  private static RuntimeTools.PreparedCache sample() {
    return new RuntimeTools.PreparedCache(
        ROOT,
        "/data/app/base.apk:123:456",
        true,
        "1:2|3:4:5:6:7",
        List.of(
            "/data/user/0/pkg/files/linux/ubuntu/root/dsh-bin/pnpm",
            "/data/user/0/pkg/files/linux/.runtime-descriptor.json"));
  }

  @Test
  public void roundTripKeepsEveryField() {
    var decoded = RuntimeTools.PreparedCache.decode(sample().encode(), ROOT, PARENTS);
    assertNotNull(decoded);
    assertEquals(ROOT, decoded.root);
    assertEquals("/data/app/base.apk:123:456", decoded.apk);
    assertTrue(decoded.withNpm);
    assertEquals("1:2|3:4:5:6:7", decoded.stamp);
    assertEquals(sample().files, decoded.files);
  }

  @Test
  public void differentRootfsIsAMiss() {
    assertNull(RuntimeTools.PreparedCache.decode(sample().encode(), ROOT + "2", PARENTS));
  }

  @Test
  public void pathsOutsideRuntimeParentAreRejected() {
    var outside =
        new RuntimeTools.PreparedCache(
            ROOT, "apk", false, "s", List.of("/data/data/other/files/x"));
    assertNull(RuntimeTools.PreparedCache.decode(outside.encode(), ROOT, PARENTS));
    var dotdot =
        new RuntimeTools.PreparedCache(
            ROOT, "apk", false, "s", List.of("/data/data/pkg/files/linux/../../../x"));
    assertNull(RuntimeTools.PreparedCache.decode(dotdot.encode(), ROOT, PARENTS));
  }

  @Test
  public void malformedOrForeignVersionIsAMiss() {
    assertNull(RuntimeTools.PreparedCache.decode(null, ROOT, PARENTS));
    assertNull(RuntimeTools.PreparedCache.decode("", ROOT, PARENTS));
    assertNull(
        RuntimeTools.PreparedCache.decode(
            sample().encode().replaceFirst("v1", "v2"), ROOT, PARENTS));
    var noFiles = new RuntimeTools.PreparedCache(ROOT, "apk", true, "s", List.of());
    assertNull(RuntimeTools.PreparedCache.decode(noFiles.encode(), ROOT, PARENTS));
    var emptyStamp = new RuntimeTools.PreparedCache(ROOT, "apk", true, "", sample().files);
    assertNull(RuntimeTools.PreparedCache.decode(emptyStamp.encode(), ROOT, PARENTS));
    assertNull(RuntimeTools.PreparedCache.decode(sample().encode(), ROOT, List.of("/")));
  }

  @Test
  public void newlineInAnyFieldIsNotEncoded() {
    assertNull(new RuntimeTools.PreparedCache(ROOT, "a\nb", true, "s", sample().files).encode());
    assertNull(
        new RuntimeTools.PreparedCache(ROOT, "apk", true, "s", List.of(ROOT + "/a\nb")).encode());
    assertNull(new RuntimeTools.PreparedCache(ROOT, "apk", true, null, sample().files).encode());
  }
}
