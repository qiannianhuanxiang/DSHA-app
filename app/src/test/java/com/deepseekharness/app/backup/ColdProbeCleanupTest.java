package com.deepseekharness.app.backup;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ColdProbeCleanupTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  private static void put(File file, String value) throws IOException {
    Files.createDirectories(file.toPath().getParent());
    Files.writeString(file.toPath(), value);
  }

  private static void unchanged(File file, String value) throws IOException {
    assertEquals(value, Files.readString(file.toPath()));
  }

  @Test
  public void successDeletesOnlyCapturedProbeAndKeepsExternalManualBackupsAndFailedProbes()
      throws Exception {
    File files = temporary.newFolder("private-files"), storage = temporary.newFolder("storage");
    File probe = new File(files, "cold-install-probes/" + UUID.randomUUID());
    File failed = new File(files, "cold-install-probes/" + UUID.randomUUID() + "/original.txt");
    File documents = new File(storage, "Documents/dshdata/manual-backup.tar.gz");
    File download = new File(storage, "Download/workspace/personal.txt");
    File backup = new File(storage, "Download/workspace/backups/backup.tar.gz");
    File report = new File(files, "cold-install-probes/latest.json");
    File task = new File(files, "host-backup-operations/manual/data.dshbak");
    put(new File(probe, "files/linux/ubuntu/root/scratch.txt"), "probe-only");
    put(failed, "retained failure");
    put(documents, "manual documents backup");
    put(download, "personal workspace file");
    put(backup, "manual workspace backup");
    put(report, "timing report");
    put(task, "retained private backup");

    ColdProbeCleanup cleanup = ColdProbeCleanup.capture(new JvmBackupFileSystem(), files, probe);
    cleanup.clean(new BackupControl(null));
    cleanup.clean(new BackupControl(null));

    assertFalse(probe.exists());
    unchanged(failed, "retained failure");
    unchanged(documents, "manual documents backup");
    unchanged(download, "personal workspace file");
    unchanged(backup, "manual workspace backup");
    unchanged(report, "timing report");
    unchanged(task, "retained private backup");
  }

  @Test
  public void arbitraryExternalAndWholePrivateRootsCannotBeClaimed() throws Exception {
    File files = temporary.newFolder("files"), storage = temporary.newFolder("external");
    File external = new File(storage, "Download/" + UUID.randomUUID());
    put(new File(external, "backup.tar.gz"), "keep");
    for (File target : new File[] {files, external, new File(files, "cold-install-probes")}) {
      try {
        ColdProbeCleanup.capture(new JvmBackupFileSystem(), files, target);
        fail("outside the exact probe slot");
      } catch (IOException expected) {
        assertEquals("COLD_PROBE_SCOPE", expected.getMessage());
      }
    }
    unchanged(new File(external, "backup.tar.gz"), "keep");
  }

  @Test
  public void linksInsideProbeAreRemovedWithoutDeletingWorkspaceTarget() throws Exception {
    File files = temporary.newFolder("link-files"), outside = temporary.newFolder("workspace");
    File probe = new File(files, "cold-install-probes/" + UUID.randomUUID());
    Files.createDirectories(probe.toPath());
    put(new File(outside, "manual-backup.tar.gz"), "external bytes");
    try {
      Files.createSymbolicLink(new File(probe, "external-workspace").toPath(), outside.toPath());
    } catch (java.nio.file.FileSystemException unavailable) {
      org.junit.Assume.assumeFalse(
          "Windows process lacks symbolic-link privilege; this case also runs on Linux",
          System.getProperty("os.name").startsWith("Windows"));
      throw unavailable;
    }

    ColdProbeCleanup.capture(new JvmBackupFileSystem(), files, probe)
        .clean(new BackupControl(null));

    assertFalse(probe.exists());
    unchanged(new File(outside, "manual-backup.tar.gz"), "external bytes");
  }

  @Test
  public void replacedProbeIdentityIsRejectedBeforeAnyDeletion() throws Exception {
    File files = temporary.newFolder("changed-files");
    File probe = new File(files, "cold-install-probes/" + UUID.randomUUID());
    put(new File(probe, "old.txt"), "old");
    final boolean[] replacement = {false};
    JvmBackupFileSystem identities =
        new JvmBackupFileSystem() {
          public Node stat(File file) throws IOException {
            Node actual = super.stat(file);
            if (!file.equals(probe)) return actual;
            // Windows 的 JDK fileKey 可能缺失；用显式后端身份变更验证拒绝分支。
            return new Node(
                actual.type,
                actual.key + ":" + replacement[0],
                actual.size,
                actual.modified,
                actual.device,
                actual.mode);
          }
        };
    ColdProbeCleanup cleanup = ColdProbeCleanup.capture(identities, files, probe);
    File retained = new File(probe.getParentFile(), UUID.randomUUID().toString());
    Files.move(probe.toPath(), retained.toPath());
    put(new File(probe, "new.txt"), "new");
    replacement[0] = true;
    try {
      cleanup.clean(new BackupControl(null));
      fail("replacement must remain untouched");
    } catch (IOException expected) {
      assertEquals("COLD_PROBE_CHANGED", expected.getMessage());
    }
    unchanged(new File(retained, "old.txt"), "old");
    unchanged(new File(probe, "new.txt"), "new");
  }

  @Test
  public void realHostDirectoryIdentityReplacementIsRejected() throws Exception {
    File files = temporary.newFolder("real-identity-files");
    File probe = new File(files, "cold-install-probes/" + UUID.randomUUID());
    put(new File(probe, "old.txt"), "old host bytes");
    JvmBackupFileSystem fs = new JvmBackupFileSystem();
    org.junit.Assume.assumeFalse(
        "JDK host does not expose directory identity; the backend tuple case remains covered",
        fs.stat(probe).key.equals("null"));
    ColdProbeCleanup cleanup = ColdProbeCleanup.capture(fs, files, probe);
    File retained = new File(probe.getParentFile(), UUID.randomUUID().toString());
    Files.move(probe.toPath(), retained.toPath());
    put(new File(probe, "new.txt"), "new host bytes");
    try {
      cleanup.clean(new BackupControl(null));
      fail("changed real directory identity must remain untouched");
    } catch (IOException expected) {
      assertEquals("COLD_PROBE_CHANGED", expected.getMessage());
    }
    unchanged(new File(retained, "old.txt"), "old host bytes");
    unchanged(new File(probe, "new.txt"), "new host bytes");
  }

  @Test
  public void lateDirectoryWriterIsRelistedAndCancellationRetainsTheProbe() throws Exception {
    File files = temporary.newFolder("late-files");
    File probe = new File(files, "cold-install-probes/" + UUID.randomUUID());
    File late = new File(probe, "nested");
    put(new File(late, "first.txt"), "first");
    JvmBackupFileSystem fs =
        new JvmBackupFileSystem() {
          boolean wrote;

          public void delete(File file) throws IOException {
            if (file.equals(late) && !wrote) {
              wrote = true;
              put(new File(late, "late.txt"), "late");
              throw new java.nio.file.DirectoryNotEmptyException(file.getPath());
            }
            super.delete(file);
          }
        };
    ColdProbeCleanup.capture(fs, files, probe).clean(new BackupControl(null));
    assertFalse(probe.exists());

    File cancelled = new File(files, "cold-install-probes/" + UUID.randomUUID());
    put(new File(cancelled, "preserved.txt"), "not cleaned");
    ColdProbeCleanup cleanup = ColdProbeCleanup.capture(fs, files, cancelled);
    BackupControl control = new BackupControl(null);
    control.cancel();
    try {
      cleanup.clean(control);
      fail("cancelled cleanup must stop");
    } catch (java.io.InterruptedIOException expected) {
      unchanged(new File(cancelled, "preserved.txt"), "not cleaned");
    }
  }
}
