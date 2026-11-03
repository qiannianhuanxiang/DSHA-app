package com.deepseekharness.app.runtime;

import static org.junit.Assert.*;

import com.deepseekharness.app.backup.JvmBackupFileSystem;
import com.deepseekharness.app.backup.UserDataLayout;
import com.deepseekharness.app.util.ManagedRuntimeLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** 只核验适配合同；占位库绝不执行，不能作为 native 或设备运行成功证据。 */
public class BxrootRuntimeTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  private File nativeDir;
  private File files;
  private File cache;
  private File rootfs;
  private File tmp;
  private BxrootRuntime runtime;

  @Before
  public void setUp() throws Exception {
    nativeDir = temporary.newFolder("native libs");
    files = temporary.newFolder("files");
    cache = temporary.newFolder("cache");
    rootfs = new File(files, "linux/ubuntu");
    Files.createDirectories(rootfs.toPath());
    tmp = temporary.newFolder("tmp");
    for (String name : BxrootRuntime.LIBS) put(new File(nativeDir, name));
    runtime = runtime(files, 30);
  }

  private BxrootRuntime runtime(File domain, int sdk) {
    return new BxrootRuntime(nativeDir, domain, cache, sdk, new JvmBackupFileSystem());
  }

  private static void put(File file) throws IOException {
    Files.createDirectories(file.getParentFile().toPath());
    Files.writeString(file.toPath(), "fixture, not an executable");
  }

  private void apply(ProcessBuilder builder) throws IOException {
    runtime.applyEnv(builder, new File(files, "linux"), new File(files, "linux/lib"), tmp);
  }

  private static List<String> binds(List<String> argv) {
    List<String> out = new ArrayList<>();
    for (int i = 1; i < argv.size(); i++)
      if (argv.get(i).equals("-b")) out.add(argv.get(++i));
    return out;
  }

  @Test
  public void availabilityRequiresExactlyTheThreeNonemptyLibrariesAndApi30() throws Exception {
    assertEquals("bxroot", runtime.id());
    assertTrue(runtime.available());
    assertEquals("", runtime.unavailableReason());
    assertEquals(
        List.of("libbxroot.so", "libbxroot-runtime.so", "libbxroot-ulx.so"),
        BxrootRuntime.LIBS);
    assertThrows(UnsupportedOperationException.class, () -> BxrootRuntime.LIBS.clear());
    for (String name : BxrootRuntime.LIBS) {
      File library = new File(nativeDir, name);
      Files.delete(library.toPath());
      assertFalse(runtime.available());
      assertEquals("BXROOT_LIBRARY_MISSING: " + name,
          assertThrows(IOException.class, runtime::prepare).getMessage());
      Files.createFile(library.toPath());
      assertFalse(runtime.available());
      put(library);
      assertTrue(runtime.available());
    }
    BxrootRuntime oldApi = runtime(files, 29);
    assertFalse(oldApi.available());
    assertEquals("BXROOT_REQUIRES_API_30",
        assertThrows(IOException.class, oldApi::prepare).getMessage());
  }

  @Test
  public void rootfsArgumentIsCanonicalSoGuestCanOpenIt() throws Exception {
    // Android 上 getFilesDir() 返回 /data/user/0/<pkg>/files，而 guest 命名空间里
    // /data/user 被 tmpfs 遮蔽、只有 /data/data 是 bind mount。实测（vivo V2352A /
    // Android 16）非 canonical 路径会让 trampoline 打开 LDSO 时 EACCES。
    File link = new File(temporary.getRoot(), "user0-link");
    Files.createSymbolicLink(link.toPath(), files.toPath());
    File linkedRootfs = new File(link, "linux/ubuntu");
    assertTrue(linkedRootfs.exists());
    List<String> argv = runtime.baseArgv(linkedRootfs, true, null);
    int index = argv.indexOf("-r");
    assertTrue(index >= 0);
    String passed = argv.get(index + 1);
    assertEquals(new File(passed).getCanonicalPath(), passed);
    assertFalse(passed.startsWith(link.getAbsolutePath()));
  }

  @Test
  public void launcherUsesNativePathAndCurrentFlagsWithoutLegacyFiveLibraryOptions() {
    List<String> argv = runtime.baseArgv(rootfs, true, null);
    assertEquals(
        List.of(new File(nativeDir, "libbxroot.so").getAbsolutePath(),
            "-r", rootfs.getAbsolutePath(), "-0", "-w", "/root"),
        argv.subList(0, 6));
    for (String forbidden : List.of("--static-loader", "--no-static-loader", "--kill-on-exit",
        "-L", "--link2symlink")) assertFalse(argv.contains(forbidden));
    assertFalse(new File(rootfs, ".l2s").exists());
  }

  @Test
  public void optionalBindsRetainSharedContractAndShmOverridesDev() {
    List<String> actual = binds(runtime.baseArgv(rootfs, true, null));
    List<String> expected = new ArrayList<>();
    for (ContainerRuntime.Bind bind : ContainerRuntime.binds())
      if (new File(bind.host()).exists()) expected.add(bind.host() + ":" + bind.guest());
    expected.add(new File(cache, "bxroot-shm").getAbsolutePath() + ":/dev/shm");
    assertEquals(expected, actual);
    assertTrue(new File(cache, "bxroot-shm").isDirectory());
  }

  @Test
  public void unsupportedHardlinksUseExistingAbsoluteL2sContract() {
    List<String> argv = runtime.baseArgv(rootfs, false, null);
    File l2s = new File(rootfs, ".l2s");
    assertTrue(l2s.isDirectory());
    assertTrue(argv.contains("--link2symlink"));
    assertTrue(binds(argv).contains(l2s.getAbsolutePath() + ":" + l2s.getAbsolutePath()));
  }

  @Test
  public void failedShmOrL2sPreparationCannotAppearSuccessful() throws Exception {
    File shm = new File(cache, "bxroot-shm");
    put(shm);
    assertEquals("BXROOT_SHM_DIRECTORY_UNAVAILABLE",
        assertThrows(IOException.class, runtime::prepare).getMessage());
    Files.delete(shm.toPath());
    put(new File(rootfs, ".l2s"));
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> runtime.baseArgv(rootfs, false, null));
    assertEquals("BXROOT_L2S_DIRECTORY_UNAVAILABLE", error.getCause().getMessage());
  }

  @Test
  public void explicitNullIsIndependentEvenWhenRootfsLooksLikeProduction() {
    List<String> argv = runtime.baseArgv(rootfs, true, null);
    assertFalse(binds(argv).stream().anyMatch(s -> s.endsWith(":/run/dsha-rc1-state")));
    assertFalse(binds(argv).stream().anyMatch(s -> s.endsWith(":/root/.dsh")));
    assertFalse(new File(files, "rc1-migration-state").exists());
  }

  @Test
  public void twoArgumentEntryUsesOwnedDomainAndRequiresOne() {
    assertTrue(binds(runtime.baseArgv(rootfs, true)).contains(
        new File(files, "rc1-migration-state").getAbsolutePath() + ":/run/dsha-rc1-state"));
    BxrootRuntime noDomain = runtime(null, 30);
    assertEquals("DATA_DOMAIN_CONTEXT_MISSING",
        assertThrows(IllegalStateException.class,
            () -> noDomain.baseArgv(rootfs, true)).getMessage());
    assertNotNull(noDomain.baseArgv(rootfs, true, null));
  }

  @Test
  public void explicitTrialDomainNeverBindsTheProductionDomain() throws Exception {
    File trial = temporary.newFolder("trial");
    File trialRoot = new File(trial, "linux/ubuntu");
    Files.createDirectories(trialRoot.toPath());
    List<String> actual = binds(runtime.baseArgv(trialRoot, true, trial));
    assertTrue(actual.contains(
        new File(trial, "rc1-migration-state").getAbsolutePath() + ":/run/dsha-rc1-state"));
    assertFalse(new File(files, "rc1-migration-state").exists());
  }

  @Test
  public void mismatchedDomainFailsWithoutCreatingMigrationOrL2s() throws Exception {
    File unrelated = temporary.newFolder("unrelated root");
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> runtime.baseArgv(unrelated, false, files));
    assertEquals("DATA_LOCATION_UNREADABLE", error.getMessage());
    assertEquals("DATA_DOMAIN_ROOTFS_MISMATCH", error.getCause().getMessage());
    assertFalse(new File(files, "rc1-migration-state").exists());
    assertFalse(new File(unrelated, ".l2s").exists());
  }

  @Test
  public void stableDataRetainsEveryManagedOverlayAndAllowsTheFullBindBudget() throws Exception {
    File data = new File(files, UserDataLayout.STABLE);
    Files.createDirectories(data.toPath());
    List<String> managed = new ArrayList<>();
    for (String path : ManagedRuntimeLayout.paths()) {
      if (path.startsWith("root/.dsh/")) {
        File source = new File(rootfs, path);
        put(source);
        managed.add(source.getAbsolutePath() + ":/" + path);
      }
    }
    // 夹具同时含旧目录，选择记录必须生效，不能自行猜数据根。
    new UserDataLayout(new JvmBackupFileSystem(), files).choose(UserDataLayout.Home.STABLE);
    List<String> argv = runtime.baseArgv(rootfs, false, files);
    List<String> actual = binds(argv);
    int dataIndex = actual.indexOf(data.getAbsolutePath() + ":/root/.dsh");
    assertTrue(dataIndex >= 0);
    assertEquals(managed, actual.subList(dataIndex + 1, dataIndex + 1 + managed.size()));
    assertEquals(new File(files, "rc1-migration-state").getAbsolutePath()
        + ":/run/dsha-rc1-state", actual.get(actual.size() - 1));
    // 与 RuntimeLauncher 相同：先追加 preload bind，再追加 guest 命令。
    argv.addAll(List.of("-b", "/private/preloads:/run/dsha-preloads", "/bin/bash", "-c", "true"));
    assertTrue(binds(argv).size() > 16);
    apply(new ProcessBuilder(argv));
    assertTrue(binds(argv).containsAll(managed));
  }

  @Test
  public void newLaunchClearsInheritedEnginesAndLoaderOverridesButPreservesAppEnvironment()
      throws Exception {
    ProcessBuilder builder = new ProcessBuilder(runtime.baseArgv(rootfs, true, null));
    List<String> stale = List.of("PROOT_TMP_DIR", "PROOT_LOADER", "PROOT_L2S_DIR",
        "PROROOT_TRAMPOLINE_PATH", "PROROOT_CFG_FD", "PROROOT_ESCAPE_FD",
        "PROROOT_ROOTFS", "PROROOT_STUB_LOADER", "BXROOT_NO_ULX", "BXROOT_REENTRY",
        "BXROOT_BINDS", "BXROOT_HOST_ROOTFS", "BXROOT_LIB_PATH", "BXROOT_TMP_DIR",
        "BXROOT_ULX_PATH", "BXROOT_ULX_LDSO", "BXROOT_STUB_LOADER", "BXROOT_LINKER_PATH",
        "BXROOT_L2S_DIR", "LD_LIBRARY_PATH", "LD_PRELOAD", "LD_AUDIT");
    for (String key : stale) builder.environment().put(key, "stale");
    builder.environment().put("NODE_OPTIONS", "--require=/custom/preload.cjs");
    builder.environment().put("SSL_CERT_FILE", "/custom/cert.pem");
    builder.environment().put("DSHA_DNS_MODE", "ipv4");
    builder.environment().put("DSH_CONFIRM", "0");
    apply(builder);
    for (String key : stale)
      if (!key.equals("BXROOT_LIB_PATH") && !key.equals("BXROOT_TMP_DIR"))
        assertFalse(key, builder.environment().containsKey(key));
    assertEquals(new File(nativeDir, "libbxroot-runtime.so").getAbsolutePath(),
        builder.environment().get("BXROOT_LIB_PATH"));
    assertEquals(tmp.getAbsolutePath(), builder.environment().get("BXROOT_TMP_DIR"));
    assertEquals("--require=/custom/preload.cjs", builder.environment().get("NODE_OPTIONS"));
    assertEquals("/custom/cert.pem", builder.environment().get("SSL_CERT_FILE"));
    assertEquals("ipv4", builder.environment().get("DSHA_DNS_MODE"));
    assertEquals("0", builder.environment().get("DSH_CONFIRM"));
  }

  @Test
  public void finalArgvLimitIncludesCallerBindsButDoesNotCountGuestArguments() throws Exception {
    List<String> argv = runtime.baseArgv(rootfs, true, null);
    int count = binds(argv).size();
    for (int i = count; i < BxrootRuntime.MAX_BINDS; i++)
      argv.addAll(List.of("-b", "/host/" + i + ":/guest/" + i));
    List<String> accepted = new ArrayList<>(argv);
    accepted.addAll(List.of("/bin/bash", "-c", "printf test", "-b", "guest-only"));
    apply(new ProcessBuilder(accepted));
    argv.addAll(List.of("-b", "/extra:/extra", "/bin/bash", "-c", "true"));
    assertEquals("BXROOT_BIND_LIMIT_EXCEEDED: " + (BxrootRuntime.MAX_BINDS + 1)
        + "/" + BxrootRuntime.MAX_BINDS,
        assertThrows(IOException.class, () -> apply(new ProcessBuilder(argv))).getMessage());
  }

  @Test
  public void applyEnvironmentRechecksLibrariesAndTemporaryDirectory() throws Exception {
    ProcessBuilder builder = new ProcessBuilder(runtime.baseArgv(rootfs, true, null));
    File ulx = new File(nativeDir, "libbxroot-ulx.so");
    Files.delete(ulx.toPath());
    assertEquals("BXROOT_LIBRARY_MISSING: libbxroot-ulx.so",
        assertThrows(IOException.class, () -> apply(builder)).getMessage());
    put(ulx);
    File badTmp = new File(cache, "not-a-directory");
    put(badTmp);
    assertEquals("BXROOT_TMP_DIRECTORY_UNAVAILABLE",
        assertThrows(IOException.class,
            () -> runtime.applyEnv(builder, files, nativeDir, badTmp)).getMessage());
  }

  @Test
  public void nativeCallsStayOffUnlessBothEntriesArePresent() throws Exception {
    // 缺任一入口：容器照常可用，但不绑定 bx-host、不开启自动分流/回入。
    assertTrue(runtime.available());
    assertFalse(runtime.nativeCallsAvailable());
    put(new File(nativeDir, BxrootRuntime.HOST_ENTRY));
    assertFalse(runtime.nativeCallsAvailable());
    List<String> argv = runtime.baseArgv(rootfs, true, null);
    for (String bind : binds(argv))
      assertFalse(bind, bind.endsWith(":" + BxrootRuntime.GUEST_HOST_COMMAND));
    ProcessBuilder builder = new ProcessBuilder(argv);
    builder.environment().put("BXROOT_AUTO_HOST", "1");
    apply(builder);
    for (String key : List.of("BXROOT_AUTO_HOST", "BXROOT_HOST_PATH", "BXROOT_REENTRY",
        "BXROOT_ENTER", "BXROOT_HOST_HOME", "BXROOT_HOST_TMPDIR"))
      assertFalse(key, builder.environment().containsKey(key));
  }

  @Test
  public void nativeCallsBindHostCommandAndEnableDispatchAndReentry() throws Exception {
    File host = new File(nativeDir, BxrootRuntime.HOST_ENTRY);
    File enter = new File(nativeDir, BxrootRuntime.ENTER_ENTRY);
    put(host);
    put(enter);
    assertTrue(runtime.nativeCallsAvailable());
    List<String> argv = runtime.baseArgv(rootfs, true, null);
    // 单文件 bind：bx-host 直接在 guest PATH 中，不写入 rootfs。
    assertTrue(binds(argv).contains(
        host.getAbsolutePath() + ":" + BxrootRuntime.GUEST_HOST_COMMAND));
    assertFalse(new File(rootfs, "usr/local/bin/bx-host").exists());
    ProcessBuilder builder = new ProcessBuilder(argv);
    builder.environment().put("BXROOT_ENTER", "/stale/enter");
    builder.environment().put("BXROOT_SESSION_FD", "9");
    apply(builder);
    var env = builder.environment();
    assertEquals("1", env.get("BXROOT_AUTO_HOST"));
    assertEquals("/system/bin:/system/xbin:/vendor/bin", env.get("BXROOT_HOST_PATH"));
    assertEquals(tmp.getAbsolutePath(), env.get("BXROOT_HOST_HOME"));
    assertEquals(tmp.getAbsolutePath(), env.get("BXROOT_HOST_TMPDIR"));
    assertEquals("1", env.get("BXROOT_REENTRY"));
    // 回入口只信本 APK 的 nativeLibraryDir；继承来的旧路径和旧会话 fd 一律丢弃。
    assertEquals(enter.getAbsolutePath(), env.get("BXROOT_ENTER"));
    assertFalse(env.containsKey("BXROOT_SESSION_FD"));
    // 空入口文件视同缺失。
    Files.writeString(enter.toPath(), "");
    assertFalse(runtime.nativeCallsAvailable());
  }
}
