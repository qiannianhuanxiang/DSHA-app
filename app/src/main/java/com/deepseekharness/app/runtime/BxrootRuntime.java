package com.deepseekharness.app.runtime;

import android.content.Context;
import android.os.Build;
import com.deepseekharness.app.backup.AndroidBackupFileSystem;
import com.deepseekharness.app.backup.BackupFileSystem;
import com.deepseekharness.app.util.UiText;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * bxroot v0.2.4+13：APK 原生目录中的静态 launcher + runtime + ULX 三件套。
 *
 * <p>launcher 自行发现同目录 ULX，并为 guest 设置 LD_PRELOAD；不使用历史五库的
 * static-loader/linker/stub-loader 配置，也不把可执行文件复制到 filesDir。
 * 准备或执行失败交给调用方如实报告，本适配器不切换引擎。
 *
 * <p>停止仍须由调用方核对 guest 与 launcher 的 PID/出生身份；不能将启动器退出
 * 当成整个会话已退出，也不能借用 proot 的 --kill-on-exit 假定。
 */
public final class BxrootRuntime implements ContainerRuntime {
  public static final List<String> LIBS =
      List.of("libbxroot.so", "libbxroot-runtime.so", "libbxroot-ulx.so");

  /*
   * 原生调用（Android 原生程序 ↔ Ubuntu guest）的两件入口，均为无 libc 的
   * AArch64 程序，必须放在 nativeLibraryDir 才能被 Android 执行：
   *   libbxroot-host.so  = bx-host：guest 显式调用 Android 原生程序；
   *   libbxroot-enter.so = 回入口：Android 原生侧回到同一 guest 会话。
   * 两者都是可选的：缺失或为空时容器照常启动，只是不开启原生调用。
   * 不改变 App 的 UID/SELinux 权限；能调哪些系统命令仍由 Android 决定。
   */
  public static final String HOST_ENTRY = "libbxroot-host.so";
  public static final String ENTER_ENTRY = "libbxroot-enter.so";
  /** guest 内 bx-host 命令位置；单文件 bind，不写入 rootfs。 */
  public static final String GUEST_HOST_COMMAND = "/usr/local/bin/bx-host";
  public static final String HOST_SEARCH_PATH = "/system/bin:/system/xbin:/vendor/bin";

  // 必须配套两处 MAX_BINDS 均为 64 的重编三库；旧 16-bind 库不足以承载稳定数据域。
  // 这只是条数上限；native 还校验路径规范化后的 BXROOT_BINDS 完整环境项不超过
  // 16384 UTF-8 字节（含键名、等号、NUL），超长保留 native 原错误，不能截断或回退。
  public static final int MAX_BINDS = 64;
  private final Context context;
  private final File nativeDir;
  private final File filesDir;
  private final File shmDir;
  private final int sdk;
  private final BackupFileSystem fileSystem;

  public BxrootRuntime(Context context) {
    this(
        context.getApplicationContext(),
        defaultDir(context),
        context.getFilesDir(),
        context.getCacheDir(),
        Build.VERSION.SDK_INT,
        new AndroidBackupFileSystem());
  }

  public static File defaultDir(Context context) {
    return new File(context.getApplicationInfo().nativeLibraryDir);
  }

  // 宿主单测只注入文件系统和目录，不加载或执行 native 库。
  BxrootRuntime(File nativeDir, File filesDir, File cacheDir, int sdk, BackupFileSystem fs) {
    this(null, nativeDir, filesDir, cacheDir, sdk, fs);
  }

  private BxrootRuntime(
      Context context, File nativeDir, File filesDir, File cacheDir,
      int sdk, BackupFileSystem fs) {
    this.context = context;
    this.nativeDir = nativeDir;
    this.filesDir = filesDir;
    this.shmDir = new File(cacheDir, "bxroot-shm");
    this.sdk = sdk;
    this.fileSystem = fs;
  }

  @Override
  public String id() {
    return "bxroot";
  }

  @Override
  public String displayName() {
    return UiText.choose("bxroot（实验）", "bxroot (experimental)");
  }

  private String preparationFailure() {
    if (sdk < 30) return "BXROOT_REQUIRES_API_30";
    for (String name : LIBS) {
      File library = new File(nativeDir, name);
      if (!library.isFile() || library.length() == 0) return "BXROOT_LIBRARY_MISSING: " + name;
    }
    return "";
  }

  @Override
  public boolean available() {
    return preparationFailure().isEmpty();
  }

  @Override
  public String unavailableReason() {
    if (sdk < 30)
      return UiText.choose(
          "bxroot 需要 Android 11 或更高版本", "bxroot requires Android 11 or later");
    String failure = preparationFailure();
    return failure.isEmpty()
        ? ""
        : UiText.format(
            UiText.choose(
                "bxroot 运行时文件缺失或为空 (%s)",
                "bxroot runtime files are missing or empty (%s)"),
            failure);
  }

  @Override
  public void prepare() throws IOException {
    String failure = preparationFailure();
    if (!failure.isEmpty()) throw new IOException(failure);
    requireDirectory(shmDir, "BXROOT_SHM_DIRECTORY_UNAVAILABLE");
  }

  private boolean nativeEntry(String name) {
    File entry = new File(nativeDir, name);
    return entry.isFile() && entry.length() > 0;
  }

  /** 原生调用是否可用：两件入口都在才开启，避免只开一半的半残状态。 */
  public boolean nativeCallsAvailable() {
    return nativeEntry(HOST_ENTRY) && nativeEntry(ENTER_ENTRY);
  }

  private static void requireDirectory(File directory, String failure) throws IOException {
    if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException(failure);
  }

  @Override
  public List<String> baseArgv(File rootfsDir, boolean hardlinkSupported) {
    if (filesDir == null) throw new IllegalStateException("DATA_DOMAIN_CONTEXT_MISSING");
    return baseArgv(rootfsDir, hardlinkSupported, filesDir);
  }

  @Override
  public List<String> baseArgv(File rootfsDir, boolean hardlinkSupported, File dataDomain) {
    // 显式 null 是独立救援域；绝不从 rootfs 父目录推断或绑定生产数据。
    try {
      prepare();
      /*
       * ★ rootfs 必须用 canonical 形式（/data/data/…），不能用 /data/user/0/… ★
       *
       * 【缺陷（实测，vivo V2352A / Android 16）】Context#getFilesDir() 返回
       * /data/user/0/<pkg>/files，而 guest（bxroot container）的命名空间里
       * /data/user 被 tmpfs(mode=751) 遮蔽，只有 /data/data 是 bind mount。
       * 于是 trampoline 交接前打开 LDSO/目标二进制时拿到 EACCES，回退直接
       * execve 又撞上 SELinux app_data_file → 整条 execve 链全挂。
       *
       * proroot 之所以正常，正是因为它用的是 /data/data/<pkg>/…（canonical）。
       * 本文件其余路径（备份、文档 provider 等）都显式 getCanonicalFile()，
       * 只有这里漏了 —— 这就是同机同 rootfs 在 proroot 可用、在 bxroot 失败的
       * 唯一差异。
       *
       * 解析失败时回退原路径：不因 canonicalize 失败而阻断启动。
       */
      File resolvedRootfs;
      try {
        resolvedRootfs = rootfsDir.getCanonicalFile();
      } catch (IOException canonicalFailed) {
        resolvedRootfs = rootfsDir.getAbsoluteFile();
      }
      List<String> dataBinds = new ArrayList<>();
      if (dataDomain != null)
        UserDataBindings.append(dataBinds, resolvedRootfs, dataDomain, fileSystem);
      List<String> argv = new ArrayList<>();
      argv.add(new File(nativeDir, "libbxroot.so").getAbsolutePath());
      argv.add("-r");
      argv.add(resolvedRootfs.getAbsolutePath());
      argv.add("-0");
      argv.add("-w");
      argv.add("/root");
      for (ContainerRuntime.Bind bind : ContainerRuntime.binds()) {
        if (!new File(bind.host()).exists()) {
          if (context != null)
            RuntimeHostPorts.fromOwner(context)
                .record("BIND_SKIPPED", bind.host() + ": missing or inaccessible");
          continue;
        }
        addBind(argv, bind.host(), bind.guest());
      }
      addBind(argv, shmDir.getAbsolutePath(), "/dev/shm");
      if (nativeCallsAvailable())
        addBind(argv, new File(nativeDir, HOST_ENTRY).getAbsolutePath(), GUEST_HOST_COMMAND);
      if (!hardlinkSupported) {
        // 保留现有 .l2s 宿主绝对路径合同，launcher 默认也使用 <rootfs>/.l2s。
        File l2s = new File(resolvedRootfs, ".l2s");
        requireDirectory(l2s, "BXROOT_L2S_DIRECTORY_UNAVAILABLE");
        argv.add("--link2symlink");
        addBind(argv, l2s.getAbsolutePath(), l2s.getAbsolutePath());
      }
      argv.addAll(dataBinds);
      return argv;
    } catch (IOException error) {
      throw new IllegalStateException("BXROOT_PREPARE_FAILED", error);
    }
  }

  private static void addBind(List<String> argv, String host, String guest) {
    argv.add("-b");
    argv.add(host + ":" + guest);
  }

  @Override
  public void applyEnv(ProcessBuilder builder, File baseDir, File libDir, File tmpDir)
      throws IOException {
    prepare();
    // RuntimeLauncher 在 baseArgv 后追加 preloads/trial binds，此处核对最终前缀。
    // 不能为了满足 native 上限而丢弃数据域或受管脚本覆盖。
    validateBindCount(builder.command());
    requireDirectory(tmpDir, "BXROOT_TMP_DIRECTORY_UNAVAILABLE");
    Map<String, String> env = builder.environment();
    for (String key : new ArrayList<>(env.keySet())) {
      if (key.startsWith("PROOT_") || key.startsWith("PROROOT_") || key.startsWith("BXROOT_"))
        env.remove(key);
    }
    env.remove("LD_PRELOAD");
    env.remove("LD_LIBRARY_PATH");
    env.remove("LD_AUDIT");
    // 特别不能遗留 PROROOT_TRAMPOLINE_PATH / BXROOT_NO_ULX，二者会禁用 ULX。
    env.put("BXROOT_LIB_PATH", new File(nativeDir, "libbxroot-runtime.so").getAbsolutePath());
    env.put("BXROOT_TMP_DIR", tmpDir.getAbsolutePath());
    if (nativeCallsAvailable()) {
      // guest 同名程序优先；只有 guest PATH 找不到时才落到 Android 原生命令。
      env.put("BXROOT_AUTO_HOST", "1");
      env.put("BXROOT_HOST_PATH", HOST_SEARCH_PATH);
      // 原生程序的 HOME/TMPDIR 必须是 App 实际可写的宿主目录。
      env.put("BXROOT_HOST_HOME", tmpDir.getAbsolutePath());
      env.put("BXROOT_HOST_TMPDIR", tmpDir.getAbsolutePath());
      env.put("BXROOT_REENTRY", "1");
      env.put("BXROOT_ENTER", new File(nativeDir, ENTER_ENTRY).getAbsolutePath());
    }
  }

  private static void validateBindCount(List<String> command) throws IOException {
    int count = 0;
    for (int i = 1; i < command.size(); i++) {
      String option = command.get(i);
      if (option.equals("-b")) {
        count++;
        i++;
      } else if (option.equals("-r") || option.equals("-w")) {
        i++;
      } else if (option.equals("-0") || option.equals("--link2symlink")) {
        // 本适配器生成的无参数选项。
      } else {
        // 最终 guest 命令之后的 -b 是 guest 自己的参数，不属于 launcher。
        break;
      }
    }
    if (count > MAX_BINDS)
      throw new IOException("BXROOT_BIND_LIMIT_EXCEEDED: " + count + "/" + MAX_BINDS);
  }
}
