package com.deepseekharness.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

/**
 * Android 16 Live Updates（实时活动 / promoted ongoing）的门控与通知增强。
 *
 * <p>纯展示层：任何失败都返回原通知，绝不影响任务本身。它只做三件事——
 * 判断系统是否允许提升、给已有通知补上胶囊短文本与提升请求、给出系统设置入口。
 *
 * <p>为什么用 extras 而不是 setter：平台侧 {@code setRequestPromotedOngoing} 是 API 36.1 才有的
 * 方法，在 Android 16 GA（36.0，如 ColorOS 16）上调用会抛 {@code NoSuchMethodError}；
 * 而官方文档明确允许直接写 {@code android.requestPromotedOngoing} 这个 extra 来请求提升。
 *
 * <p>两代 ROM 的判据是相反的：36.0 要求 {@code android.colorized=true} 才会被提升，
 * 36.1+ 要求它必须为 false、改用请求位。因此这里按「系统是否提供
 * canPostPromotedNotifications」分成两支，不能只写一支。
 */
public final class IslandSupport {
  /** NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING 的字符串值。 */
  static final String REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing";

  /** 36.0 旧模型的提升判据；36.1+ 上必须保持 false。 */
  static final String EXTRA_COLORIZED = "android.colorized";

  /** 系统完全不支持实时活动（低于 Android 16）。 */
  public static final int STATE_UNSUPPORTED = 0;

  /** 支持，但用户没有为本应用打开「实时活动」，或系统拒绝了提升。 */
  public static final int STATE_DENIED = 1;

  /** 支持且当前允许提升。 */
  public static final int STATE_ALLOWED = 2;

  /** 未知（旧 ROM 没有查询接口时的保守结果）。 */
  public static final int STATE_UNKNOWN = 3;

  private IslandSupport() {}

  /** 36.0+ 才有 {@code setShortCriticalText}。 */
  public static boolean supported() {
    return Build.VERSION.SDK_INT >= 36;
  }

  /**
   * 系统当前是否允许本应用发实时活动。
   *
   * <p>该权限是 {@code normal|appops}：装机即授予，但用户可在系统设置里随时关掉，
   * 因此必须在每次发通知前查询，不能缓存一次就当成永久能力。
   */
  public static boolean canPost(Context context) {
    if (context == null || !supported()) return false;
    try {
      NotificationManager manager = context.getSystemService(NotificationManager.class);
      return manager != null && manager.canPostPromotedNotifications();
    } catch (Throwable unavailable) {
      // 36.0 没有该方法，或厂商裁剪了实现：一律视为不可提升。
      return false;
    }
  }

  /** 供设置页展示的三态。 */
  public static int state(Context context) {
    if (!supported()) return STATE_UNSUPPORTED;
    if (canPost(context)) return STATE_ALLOWED;
    return STATE_DENIED;
  }

  /**
   * 把已有通知就地增强为实时活动；任何异常都原样返回。
   *
   * @param base 已经构建好的普通通知
   * @param shortText 胶囊短文本，已被裁剪到 7/9 字符
   */
  public static Notification enhance(Context context, Notification base, String shortText) {
    if (context == null || base == null || shortText == null || shortText.isEmpty()) return base;
    if (!supported()) return base;
    boolean modern = canPost(context);
    // 新模型要求 colorized 为 false；只有旧模型才需要它。两种都不满足时仍然尝试，
    // 由系统判定是否提升 —— 失败只是保持普通通知，没有副作用。
    try {
      Notification.Builder builder = Notification.Builder.recoverBuilder(context, base);
      builder.setShortCriticalText(shortText);
      Bundle extras = new Bundle();
      if (modern) {
        extras.putBoolean(REQUEST_PROMOTED_ONGOING, true);
      } else {
        extras.putBoolean(EXTRA_COLORIZED, true);
      }
      builder.addExtras(extras);
      return builder.build();
    } catch (Throwable unavailable) {
      return base;
    }
  }

  /** 系统「实时活动」设置入口；不可用时回退到应用通知设置。 */
  public static Intent settingsIntent(Context context) {
    if (supported() && context != null) {
      try {
        Intent promotion =
            new Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        if (promotion.resolveActivity(context.getPackageManager()) != null) return promotion;
      } catch (Throwable unavailable) {
        // 落到下面的普通通知设置。
      }
    }
    return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context == null ? "" : context.getPackageName());
  }
}
