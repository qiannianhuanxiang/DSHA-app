package com.deepseekharness.app.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * 锁定桥的敏感路径判据。
 *
 * <p>真机实测过的攻击链：{@code /app/export?path=/root/.dsh/.bridge_token}
 * 能把桥 token 写到公共目录，同机任意应用据此完全接管 3090 桥。
 * 这些断言就是钉住「这条路必须被堵死」。
 */
public class BridgePathPolicyTest {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  /** 真机实测能泄露凭据的路径，全部必须拒绝。 */
  @Test
  public void deniesCredentialPaths() {
    String[] attacks = {
      "/root/.dsh/.bridge_token",
      "/root/.dsh/.credentials.yaml",
      "/root/.dsh/adbkeys/adbkey",
      "/root/.dsh",
      "/root/.dsh/",
      "/root/.android/debug.keystore",
      "/root/.ssh/id_rsa",
      "/.dsh/x",
    };
    for (String path : attacks) {
      assertTrue("必须拒绝：" + path, BridgePathPolicy.denied(path));
    }
  }

  /** 目录穿越、重复斜杠、反斜杠等写法不能绕过。 */
  @Test
  public void deniesObfuscatedCredentialPaths() {
    String[] attacks = {
      "/root/foo/../.dsh/.bridge_token",
      "/root/./.dsh/.bridge_token",
      "/root//.dsh//.bridge_token",
      "/root/.dsh/./../.dsh/.bridge_token",
      "\\root\\.dsh\\.bridge_token",
      "/root/x/../../root/.dsh/.bridge_token",
      "/../root/.dsh/.bridge_token",
    };
    for (String path : attacks) {
      assertTrue("混淆写法也必须拒绝：" + path, BridgePathPolicy.denied(path));
    }
  }

  /** 无关路径不能误伤：它们是桥的正当用途（导出 agent 产出的报告）。 */
  @Test
  public void allowsWorkProducts() {
    String[] allowed = {
      "/root/report.md",
      "/root/deepseek-harness/out.txt",
      "/root/我的报告.md",
      "/sdcard/Download/x.pdf",
      "/tmp/scratch.log",
      "/root/.dsh-backup-note.md", // 前缀相似但不是 .dsh 目录
    };
    for (String path : allowed) {
      assertFalse("不应误伤：" + path, BridgePathPolicy.denied(path));
    }
  }

  /** 空值/相对路径一律拒绝（桥只接受绝对路径）。 */
  @Test
  public void deniesEmptyAndRelative() {
    for (String path : new String[] {null, "", "   ", "relative/x", "../x"}) {
      assertTrue("必须拒绝：" + path, BridgePathPolicy.denied(path));
    }
  }

  /** 按路径段比较，不是字符串前缀：/root/.dshfoo 不是 .dsh 目录。 */
  @Test
  public void comparesByPathSegmentNotRawPrefix() {
    assertTrue(BridgePathPolicy.startsWithPath("/root/.dsh/x", "/root/.dsh"));
    assertTrue(BridgePathPolicy.startsWithPath("/root/.dsh", "/root/.dsh"));
    assertFalse(BridgePathPolicy.startsWithPath("/root/.dshfoo", "/root/.dsh"));
    assertFalse(BridgePathPolicy.startsWithPath("/root/.dshx/y", "/root/.dsh"));
    assertFalse(BridgePathPolicy.denied("/root/.dshfoo/token"));
  }

  /**
   * 自伤回归：容器 rootfs 就在 /data/data/... 下，
   * 不能让通用拒绝表把整个 rootfs 封死（否则连正常产物都导不出去）。
   */
  @Test
  public void rootfsContentIsNotBlanketDenied() {
    String rootfs = "/data/data/com.dsh.client/files/linux/ubuntu";
    // rootfs 内的正常产物：放行
    assertFalse(BridgePathPolicy.deniedGuestView(rootfs + "/root/report.md", rootfs));
    assertFalse(BridgePathPolicy.deniedGuestView(rootfs + "/root/harness/out.txt", rootfs));
    assertFalse(BridgePathPolicy.deniedGuestView(rootfs + "/tmp/x.log", rootfs));
    // rootfs 内的凭据：拒绝
    assertTrue(BridgePathPolicy.deniedGuestView(rootfs + "/root/.dsh/.bridge_token", rootfs));
    assertTrue(BridgePathPolicy.deniedGuestView(rootfs + "/root/.ssh/id_rsa", rootfs));
    // rootfs 之外的 App 私有数据：拒绝
    assertTrue(
        BridgePathPolicy.deniedGuestView("/data/data/com.dsh.client/shared_prefs/x.xml", rootfs));
    // rootfs 根本没有的宿主路径：拒绝
    assertTrue(BridgePathPolicy.deniedGuestView("/sdcard/report.md", rootfs));
  }

  /** rootfs 为 null（拿不到容器目录）时，只按通用宿主判据走，不能崩。 */
  @Test
  public void handlesMissingRootfs() {
    assertFalse(BridgePathPolicy.deniedGuestView("/tmp/x", null));
    assertTrue(BridgePathPolicy.deniedGuestView("/data/data/com.dsh.client/x", null));
    assertTrue(BridgePathPolicy.deniedGuestView(null, null));
    assertTrue(BridgePathPolicy.deniedGuestView("", null));
  }

  @Test
  public void readFileAllowsHostAndGuestWorkPathsButKeepsCredentialBoundary() {
    String rootfs = "/data/user/0/dsh.android.client/files/linux/ubuntu";
    String shared = "/storage/emulated/0";
    String media = "/data/media/0";
    for (String path :
        new String[] {
          rootfs + "/root/work/report.md",
          rootfs + "/root/nonexistent-xyz-123",
          rootfs + "/etc/hosts"
        }) {
      assertFalse(path, BridgePathPolicy.deniedReadCanonical(path, rootfs, shared, media, false));
    }
    for (String path :
        new String[] {shared, shared + "/Download/report.txt", media + "/Download/x"})
      assertFalse(path, BridgePathPolicy.deniedReadCanonical(path, rootfs, shared, media, true));
    for (String path :
        new String[] {
          rootfs,
          rootfs + "/root/.dsh/.bridge_token",
          rootfs + "/root/.ssh/id_rsa",
          "/root/.dsh/.bridge_token",
          "/data/user/0/dsh.android.client/shared_prefs/x.xml",
          "/data/data/dsh.android.client/files/secret",
          "/data/user_de/0/dsh.android.client/x",
          "/etc/hosts",
          shared + "/Download/report.txt"
        }) {
      assertTrue(path, BridgePathPolicy.deniedReadCanonical(path, rootfs, shared, media, false));
    }
    for (String path :
        new String[] {
          rootfs + "/root/report",
          "/data/user_de/0/app/x",
          "/storage/emulated/10/Download/x",
          "/data/media/10/Download/x",
          shared + "/Download/.env"
        })
      assertTrue(path, BridgePathPolicy.deniedReadCanonical(path, rootfs, shared, media, true));
    assertTrue(BridgePathPolicy.deniedReadCanonical(null, rootfs, shared, media, false));
    assertTrue(
        BridgePathPolicy.deniedReadCanonical(rootfs + "/root/x", null, shared, media, false));
    assertTrue(BridgePathPolicy.deniedReadCanonical(shared + "/x", rootfs, null, null, true));
    assertTrue(BridgePathPolicy.deniedReadRaw("/data/user_de/0/dsh.android.client/x"));
    assertTrue(BridgePathPolicy.deniedReadRaw("/data/user/0/dsh.android.client/x"));
    assertTrue(BridgePathPolicy.deniedReadRaw("/data/data/dsh.android.client/x"));
  }

  @Test
  public void readFileMapsGuestPathsWithoutHostFallback() throws Exception {
    File rootfs = temp.newFolder("rootfs");
    String guestPath = "/root/dsha-test-" + java.util.UUID.randomUUID();
    File guestDirectory = new File(rootfs, guestPath.substring(1));
    assertTrue(guestDirectory.mkdirs());
    File guestFile = new File(guestDirectory, "report.txt");
    assertTrue(guestFile.createNewFile());
    assertEquals(guestDirectory, BridgePathPolicy.resolveReadFile(guestPath, rootfs));
    assertEquals(guestFile, BridgePathPolicy.resolveReadFile(guestPath + "/report.txt", rootfs));
    assertEquals(
        new File(guestDirectory, "missing"),
        BridgePathPolicy.resolveReadFile(guestPath + "/missing", rootfs));
    assertEquals(new File(rootfs, "etc"), BridgePathPolicy.resolveReadFile("/etc", rootfs));
    assertEquals(
        new File("/sdcard/Download"), BridgePathPolicy.resolveReadFile("/sdcard/Download", rootfs));
    assertNull(BridgePathPolicy.resolveReadFile(guestPath, null));
    assertTrue(BridgePathPolicy.readSharedDomain("/storage/emulated/0/Download"));
    assertTrue(BridgePathPolicy.readSharedDomain("/data/media/0/Download"));
    assertFalse(BridgePathPolicy.readSharedDomain("/storage/emulatedfake/0"));
    assertFalse(BridgePathPolicy.readSharedDomain("/sdcard/../root/report"));
  }

  @Test
  public void readFileRejectsSensitiveDirectoriesInAnyHome() {
    String root = "/data/user/0/dsh.android.client/files/linux/ubuntu";
    for (String guest :
        new String[] {
          "/home/alice/.ssh/id_rsa", "/home/alice/.aws/credentials",
          "/home/alice/.android/debug.keystore", "/home/alice/.kube/config",
          "/home/alice/.dsh/session", "/home/alice/.config/gcloud/configurations/x"
        }) {
      assertTrue(guest, BridgePathPolicy.deniedReadRaw(guest));
      assertTrue(
          guest, BridgePathPolicy.deniedReadCanonical(root + guest, root, null, null, false));
    }
    assertFalse(BridgePathPolicy.deniedReadRaw("/home/alice/.ssh-backup/report.txt"));
    assertFalse(BridgePathPolicy.deniedReadRaw("/home/alice/.config/gcloud-backup/report.txt"));
  }

  @Test
  public void readFileCanonicalChecksBlockRealSymlinkTargets() throws Exception {
    Path root = temp.newFolder("guest-root").toPath();
    Path home = Files.createDirectories(root.resolve("home/alice"));
    Path sensitive = Files.createDirectories(home.resolve(".ssh")).resolve("id_rsa");
    Files.write(sensitive, new byte[] {1});
    Path external = temp.newFile("outside").toPath();
    Path secretLink = home.resolve("report");
    Path escapeLink = home.resolve("outside-link");
    try {
      Files.createSymbolicLink(secretLink, sensitive);
      Files.createSymbolicLink(escapeLink, external);
    } catch (IOException | UnsupportedOperationException | SecurityException e) {
      Assume.assumeNoException("Symbolic links unavailable on this host", e);
    }
    String rootCanonical = root.toFile().getCanonicalPath();
    assertFalse(BridgePathPolicy.deniedReadRaw("/home/alice/report"));
    assertTrue(
        BridgePathPolicy.deniedReadCanonical(
            secretLink.toFile().getCanonicalPath(), rootCanonical, null, null, false));
    assertTrue(
        BridgePathPolicy.deniedReadCanonical(
            escapeLink.toFile().getCanonicalPath(), rootCanonical, null, null, false));
  }

  /** normalize 的边界。 */
  @Test
  public void normalizesPaths() {
    assertEquals("/root/.dsh/x", BridgePathPolicy.normalize("/root/./foo/../.dsh/x"));
    assertEquals("/root", BridgePathPolicy.normalize("/root/"));
    assertEquals("/", BridgePathPolicy.normalize("/"));
    assertEquals("/a/b", BridgePathPolicy.normalize("//a///b//"));
    assertEquals("/x", BridgePathPolicy.normalize("/../x"));
    assertEquals("/", BridgePathPolicy.normalize("/../../.."));
  }
}
