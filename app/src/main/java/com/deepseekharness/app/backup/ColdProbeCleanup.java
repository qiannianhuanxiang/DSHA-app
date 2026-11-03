package com.deepseekharness.app.backup;

import java.io.File;
import java.io.IOException;

/** 只清理由冷测速创建并捕获身份的私有 UUID 槽位，不提供应用重置或公共根清理。 */
public final class ColdProbeCleanup {
  private static final int MAX_DEPTH = 512, MAX_PASSES = 8;
  private final BackupFileSystem fs;
  private final File files, parent, probe;
  private final BackupFileSystem.Node filesIdentity, parentIdentity, probeIdentity;
  private boolean completed;

  public static ColdProbeCleanup capture(File files, File probe) throws IOException {
    return capture(new AndroidBackupFileSystem(), files, probe);
  }

  static ColdProbeCleanup capture(BackupFileSystem fs, File files, File probe) throws IOException {
    return new ColdProbeCleanup(fs, files, probe);
  }

  private ColdProbeCleanup(BackupFileSystem fs, File files, File probe) throws IOException {
    this.fs = fs;
    this.files = files.getAbsoluteFile();
    parent = new File(this.files, "cold-install-probes");
    this.probe = probe.getAbsoluteFile();
    if (!this.probe.getName().matches(com.deepseekharness.app.util.Ids.UUID_PATTERN)
        || !parent.equals(this.probe.getParentFile())) throw new IOException("COLD_PROBE_SCOPE");
    filesIdentity = directory(fs.stat(this.files));
    parentIdentity = directory(fs.stat(parent));
    probeIdentity = directory(fs.stat(this.probe));
    if (filesIdentity.device != parentIdentity.device
        || filesIdentity.device != probeIdentity.device) throw new IOException("COLD_PROBE_DEVICE");
  }

  /** 仅在冷安装和真实 guest 退出核验成功后调用；失败槽位继续保留。 */
  public synchronized void clean(BackupControl control) throws IOException {
    if (completed) return;
    verifyScope();
    if (fs instanceof AndroidBackupFileSystem) {
      try (AndroidTreeFileSystem removal = new AndroidTreeFileSystem(probe)) {
        contents(removal, probe, control, 0);
      }
    } else contents(fs, probe, control, 0);
    verifyScope();
    fs.delete(probe);
    fs.syncDirectory(parent);
    completed = true;
  }

  private void verifyScope() throws IOException {
    if (!same(filesIdentity, fs.stat(files))
        || !same(parentIdentity, fs.stat(parent))
        || !same(probeIdentity, fs.stat(probe))) throw new IOException("COLD_PROBE_CHANGED");
  }

  private static BackupFileSystem.Node directory(BackupFileSystem.Node node) throws IOException {
    if (!node.type.equals("DIRECTORY")) throw new IOException("COLD_PROBE_DIRECTORY");
    return node;
  }

  private static boolean same(BackupFileSystem.Node a, BackupFileSystem.Node b) {
    return b.type.equals("DIRECTORY") && a.key.equals(b.key) && a.device == b.device;
  }

  private static void contents(BackupFileSystem fs, File root, BackupControl control, int depth)
      throws IOException {
    control.check();
    if (depth > MAX_DEPTH) throw new IOException("COLD_PROBE_DEPTH");
    BackupFileSystem.Node before = directory(fs.stat(root));
    fs.prepareOwnedRemoval(root);
    for (int pass = 0; pass < MAX_PASSES; pass++) {
      control.check();
      for (String name : fs.list(root)) {
        BackupLimits.path(name);
        if (name.isEmpty() || name.contains("/")) throw new IOException("COLD_PROBE_ENTRY");
        remove(fs, new File(root, name), control, depth + 1);
      }
      if (!same(before, fs.stat(root))) throw new IOException("COLD_PROBE_CHANGED");
      if (!fs.list(root).isEmpty()) continue;
      fs.syncDirectory(root);
      if (fs.list(root).isEmpty()) return;
    }
    throw new IOException("COLD_PROBE_BUSY");
  }

  private static void remove(BackupFileSystem fs, File target, BackupControl control, int depth)
      throws IOException {
    control.check();
    BackupFileSystem.Node before = fs.stat(target);
    if (before.type.equals("MISSING")) return;
    if (before.type.equals("DIRECTORY")) {
      for (int pass = 0; pass < MAX_PASSES; pass++) {
        contents(fs, target, control, depth);
        if (!same(before, fs.stat(target))) throw new IOException("COLD_PROBE_CHANGED");
        try {
          fs.delete(target);
          return;
        } catch (IOException busy) {
          if (!directoryNotEmpty(busy) || pass + 1 == MAX_PASSES) throw busy;
        }
      }
    } else {
      // lstat 判定链接；只删目录项，不遍历链接目标。
      fs.delete(target);
    }
  }

  private static boolean directoryNotEmpty(IOException error) {
    for (Throwable at = error; at != null; at = at.getCause()) {
      String message = at.getMessage();
      if ("FILESYSTEM_39".equals(message) || "DIRECTORY_NOT_EMPTY".equals(message)) return true;
      // Low API 23 不直接引用 API 26 的 java.nio.file 类型。
      for (Class<?> type = at.getClass(); type != null; type = type.getSuperclass())
        if (type.getName().equals("java.nio.file.DirectoryNotEmptyException")) return true;
    }
    return false;
  }
}
