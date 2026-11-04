package com.deepseekharness.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 不受限模式只放宽白名单：脚本、管道、input 等放行，硬禁止项无论如何拒绝。 */
public final class DeviceShellPolicyUnrestrictedTest {
  @Test
  public void allowsScriptsPipesAndCommandsTheAllowlistRejects() {
    for (String command :
        new String[] {
          "input keyevent 4",
          "screencap -p /sdcard/Download/a.png",
          "am start -n com.android.settings/.Settings",
          "settings put system screen_brightness 100",
          "dumpsys battery | grep level",
          "for p in $(pm list packages -3); do echo $p; done",
          "cat /proc/net/tcp > /sdcard/Download/tcp.txt",
          "/system/bin/sh -c 'id; whoami'"
        }) {
      assertTrue(command, DeviceShellPolicy.inspectUnrestricted(command).allowed());
      // 同一条命令在默认白名单下应仍被拒，证明放宽只发生在显式模式里。
    }
    assertEquals(
        DeviceShellPolicy.Kind.DENY, DeviceShellPolicy.inspect("input keyevent 4").kind);
    assertEquals(
        DeviceShellPolicy.Kind.DENY, DeviceShellPolicy.inspect("dumpsys battery | grep level").kind);
  }

  @Test
  public void hardBlockedOperationsStayDeniedInAnySpelling() {
    for (String command :
        new String[] {
          "setenforce 0",
          "/system/bin/setenforce 0",
          "toybox setenforce 0",
          "mount -o remount,rw /system",
          "setprop persist.sys.x 1",
          "dd if=/dev/zero of=/dev/block/by-name/boot",
          "mkfs.ext4 /dev/block/sda",
          "reboot recovery",
          "fastboot flash boot x.img"
        }) {
      DeviceShellPolicy.Plan plan = DeviceShellPolicy.inspectUnrestricted(command);
      assertEquals(command, DeviceShellPolicy.Kind.DENY, plan.kind);
      assertTrue(command, plan.reason.startsWith("[POLICY_BLOCKED]"));
    }
  }

  @Test
  public void emptyCommandIsDenied() {
    assertEquals(DeviceShellPolicy.Kind.DENY, DeviceShellPolicy.inspectUnrestricted("").kind);
    assertEquals(DeviceShellPolicy.Kind.DENY, DeviceShellPolicy.inspectUnrestricted("   ").kind);
    assertEquals(DeviceShellPolicy.Kind.DENY, DeviceShellPolicy.inspectUnrestricted(null).kind);
  }
}
