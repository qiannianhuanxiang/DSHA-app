package com.deepseekharness.app.util;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * 锁定主屏窗口选择/遮挡判据，特别是 #210：DSHA 自有的非焦点悬浮窗
 * （浮条、助手、确认窗）不得把目标应用判成 TARGET_WINDOW_OCCLUDED。
 */
public class MainScreenWindowPolicyTest {

  private static final ScreenWindowRegion.Bounds TARGET =
      new ScreenWindowRegion.Bounds(0, 100, 1260, 2800);
  private static final ScreenWindowRegion.Bounds OVERLAY =
      new ScreenWindowRegion.Bounds(0, 119, 1184, 316);

  private static MainScreenWindowPolicy.Window window(
      int id,
      MainScreenWindowPolicy.Kind kind,
      boolean focused,
      boolean active,
      int layer,
      ScreenWindowRegion.Bounds bounds,
      ScreenWindowRegion.Exclusion owned) {
    return new MainScreenWindowPolicy.Window(0, id, layer, kind, focused, active, bounds, owned);
  }

  /** #210 回归：未登记的自有非焦点悬浮窗按包名身份整窗排除，不构成遮挡。 */
  @Test
  public void ownUnregisteredOverlayBecomesAnExclusionNotAnOccluder() {
    ScreenWindowRegion.Exclusion fallback =
        MainScreenWindowPolicy.ownFallbackExclusion(true, false, 42, OVERLAY, null);
    assertNotNull("同包非焦点悬浮窗必须被整窗排除", fallback);
    assertEquals(42, fallback.windowId());
    assertEquals(OVERLAY, fallback.bounds());

    var windows =
        java.util.List.of(
            window(1, MainScreenWindowPolicy.Kind.APPLICATION, true, true, 10, TARGET, null),
            window(42, MainScreenWindowPolicy.Kind.APPLICATION, false, false, 20, OVERLAY, fallback));
    MainScreenWindowPolicy.Selection selection = MainScreenWindowPolicy.selection(windows);
    assertEquals("排除后的浮窗不参与目标选择", 0, selection.index());
    assertEquals("READABLE", selection.diagnostic());

    ScreenWindowRegion region = MainScreenWindowPolicy.region(0, windows);
    assertNotNull(region);
    assertFalse("自有浮窗不得把目标判成遮挡", region.obscured());
    assertEquals(1, region.exclusions().size());
    assertEquals(OVERLAY, region.exclusions().get(0).bounds());
  }

  /** 登记路径优先：match 已命中的排除原样返回，不重复构造。 */
  @Test
  public void registeredExclusionIsReturnedAsIs() {
    var registered = new ScreenWindowRegion.Exclusion(7, 9, OVERLAY);
    assertSame(
        registered,
        MainScreenWindowPolicy.ownFallbackExclusion(true, false, 7, OVERLAY, registered));
  }

  /** 可聚焦的自有窗口可能是观察目标本身，不适用整窗排除。 */
  @Test
  public void focusedOwnWindowIsNeverFallbackExcluded() {
    assertNull(
        MainScreenWindowPolicy.ownFallbackExclusion(true, true, 7, TARGET, null));
  }

  /** 其他应用的窗口绝不因本规则被排除。 */
  @Test
  public void foreignPackageIsNeverFallbackExcluded() {
    assertNull(
        MainScreenWindowPolicy.ownFallbackExclusion(false, false, 7, OVERLAY, null));
  }

  /** 无效边界的窗口拿不到 fallback 排除，继续按既有遮挡逻辑处理。 */
  @Test
  public void invalidBoundsNeverBecomeExclusions() {
    var invalid = new ScreenWindowRegion.Bounds(10, 10, 10, 10);
    assertNull(MainScreenWindowPolicy.ownFallbackExclusion(true, false, 7, invalid, null));
  }

  /** 排除区内的坐标轨迹仍被拒绝；完全盖满时依旧无可点面积。 */
  @Test
  public void excludedRegionStillRefusesTrajectoriesAndFullCoverStillUnusable() {
    ScreenWindowRegion.Exclusion fallback =
        MainScreenWindowPolicy.ownFallbackExclusion(true, false, 42, OVERLAY, null);
    var windows =
        java.util.List.of(
            window(1, MainScreenWindowPolicy.Kind.APPLICATION, true, true, 10, TARGET, null),
            window(42, MainScreenWindowPolicy.Kind.APPLICATION, false, false, 20, OVERLAY, fallback));
    ScreenWindowRegion region = MainScreenWindowPolicy.region(0, windows);
    assertFalse("穿过浮条的滑动不得放行", region.containsTrajectory(600, 150, 600, 2700, 8));
    assertTrue("避开浮条的滑动照常可用", region.containsTrajectory(600, 400, 600, 2700, 8));
    assertTrue("扣除浮窗后仍有可点面积", region.hasUnexcludedArea(region.content()));

    var fullCover =
        new ScreenWindowRegion.Bounds(0, 0, 1260, 2800);
    var full =
        MainScreenWindowPolicy.ownFallbackExclusion(true, false, 43, fullCover, null);
    var covered =
        java.util.List.of(
            window(1, MainScreenWindowPolicy.Kind.APPLICATION, true, true, 10, TARGET, null),
            window(43, MainScreenWindowPolicy.Kind.APPLICATION, false, false, 20, fullCover, full));
    ScreenWindowRegion coveredRegion = MainScreenWindowPolicy.region(0, covered);
    assertFalse("浮窗盖满目标时依然没有可点面积", coveredRegion.hasUnexcludedArea(coveredRegion.content()));
  }

  /** #212 回归：1×1 的系统字形/角标窗不触发遮挡，坐标手势保持可用；2×2 起仍按遮挡处理。 */
  @Test
  public void tinyGlyphWindowsNeverTriggerOcclusion() {
    for (ScreenWindowRegion.Bounds glyph :
        new ScreenWindowRegion.Bounds[] {
          new ScreenWindowRegion.Bounds(0, 200, 1, 201), // 1×1
          new ScreenWindowRegion.Bounds(1259, 2799, 1260, 2800) // 角落 1×1
        }) {
      var windows =
          java.util.List.of(
              window(1, MainScreenWindowPolicy.Kind.APPLICATION, true, true, 10, TARGET, null),
              window(
                  77,
                  MainScreenWindowPolicy.Kind.APPLICATION,
                  false,
                  false,
                  30,
                  glyph,
                  null));
      ScreenWindowRegion region = MainScreenWindowPolicy.region(0, windows);
      assertFalse(
          "微窗不得触发遮挡：" + glyph, region.obscured());
      assertTrue("微窗不得记入排除表", region.exclusions().isEmpty());
      assertTrue("坐标手势保持可用", region.hasUnexcludedArea(region.content()));
      assertTrue(region.containsTrajectory(600, 150, 600, 2700, 8));
    }
    // 2×2 的内部覆盖窗必须保持拒绝：MainScreenObservationTest 锁定的既有契约。
    var interior =
        java.util.List.of(
            window(1, MainScreenWindowPolicy.Kind.APPLICATION, true, true, 10, TARGET, null),
            window(
                77,
                MainScreenWindowPolicy.Kind.APPLICATION,
                false,
                false,
                30,
                new ScreenWindowRegion.Bounds(600, 600, 602, 602),
                null));
    assertTrue("2×2 内部覆盖窗仍触发遮挡", MainScreenWindowPolicy.region(0, interior).obscured());
  }

  /** 稍大于豁免阈值的窗口仍按遮挡处理，规则不因此放松。 */
  @Test
  public void windowsAboveTheTinyThresholdStillOcclude() {
    var small = new ScreenWindowRegion.Bounds(0, 200, 2, 202); // 2×1
    var windows =
        java.util.List.of(
            window(1, MainScreenWindowPolicy.Kind.APPLICATION, true, true, 10, TARGET, null),
            window(78, MainScreenWindowPolicy.Kind.APPLICATION, false, false, 30, small, null));
    ScreenWindowRegion region = MainScreenWindowPolicy.region(0, windows);
    assertTrue("超过豁免阈值的窗口仍触发遮挡", region.obscured());
  }
}
