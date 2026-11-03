package com.deepseekharness.app.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 启动时「迁移旧审阅标记」的宿主前置检查。
 *
 * <p>plugin-lifecycle.py 的 migrate_legacy_reviews 只处理一种输入：web profile 的
 * {@code node_modules/<name>.disabled}（含 {@code @scope/<name>.disabled}），且是普通文件、
 * 内容恰为 {@code DSHA_REVIEW_REQUIRED\n}。这类标记只由已移除的旧审阅门写入，当前代码不再
 * 生成。宿主上一个都没有时，guest 脚本必然报告「已启用 0 个」且不写任何东西，启动可以不起
 * 这次 proot + python 进程（实测约 0.5 秒）。
 *
 * <p>这是对迁移前置条件的精确判定，不是「上次成功」回执：每次启动都实际扫描；
 * 扫描出错、目录不可读或发现任何候选时都回到原 guest 迁移。判定是候选集合的超集
 * （不检查清单引用和实体目录，那些由 guest 脚本决定是否跳过）。
 */
public final class LegacyReviewMarkers {
  static final byte[] CONTENT = "DSHA_REVIEW_REQUIRED\n".getBytes(StandardCharsets.US_ASCII);
  private static final String SUFFIX = ".disabled";
  private static final int MAX_ENTRIES = 100_000;

  private LegacyReviewMarkers() {}

  /** web profile 的 node_modules（宿主路径）。 */
  public static File nodeModules(File rootfs) {
    return new File(rootfs, "root/.dsh/profiles/web/node_modules");
  }

  /**
   * 是否可能存在需要迁移的旧标记。只有确定「一个都没有」才返回 false；
   * 任何不确定（读取失败、条目过多）都返回 true，交给 guest 脚本。
   */
  public static boolean mayExist(File nodeModules) {
    try {
      Path root = nodeModules.toPath();
      if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return false;
      if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return true;
      int[] seen = {0};
      if (scan(root, true, seen)) return true;
      return false;
    } catch (IOException | RuntimeException error) {
      return true;
    }
  }

  private static boolean scan(Path directory, boolean top, int[] seen) throws IOException {
    try (var entries = Files.newDirectoryStream(directory)) {
      for (Path entry : entries) {
        if (++seen[0] > MAX_ENTRIES) return true;
        String name = entry.getFileName().toString();
        BasicFileAttributes attributes =
            Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (top && name.startsWith("@") && attributes.isDirectory()) {
          if (scan(entry, false, seen)) return true;
          continue;
        }
        if (!name.endsWith(SUFFIX) || !attributes.isRegularFile()) continue;
        if (attributes.size() != CONTENT.length) continue;
        if (java.util.Arrays.equals(Files.readAllBytes(entry), CONTENT)) return true;
      }
    }
    return false;
  }
}
