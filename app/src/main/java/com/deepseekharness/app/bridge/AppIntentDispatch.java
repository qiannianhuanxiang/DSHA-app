package com.deepseekharness.app.bridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.deepseekharness.app.util.IntentRequest;
import com.deepseekharness.app.util.SensitiveData;

/**
 * 以 DSHA 自身身份发出已授权的 Intent。
 *
 * <p>系统 {@code am} 以 {@code com.android.shell} 身份发起，普通 App 必然被拒；由 App
 * 进程自己 startActivity / sendBroadcast 不需要 root、ADB 或 Shizuku。授权与目标核验
 * 在调用方完成，这里只按解析结果构造 Intent 并如实回报结果。
 */
public final class AppIntentDispatch {
  private AppIntentDispatch() {}

  public static String send(Context ctx, IntentRequest request) {
    boolean submitted = false;
    try {
      Intent intent = build(request);
      if (intent == null) return "[ERR] INTENT_TARGET_UNRESOLVED";
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      submitted = true;
      if (request.kind == IntentRequest.Kind.BROADCAST) {
        ctx.sendBroadcast(intent);
        return "OK: " + request.summary();
      }
      ctx.startActivity(intent);
      return "OK: " + request.summary();
    } catch (Throwable error) {
      // 已经调用系统接口后才失败：结果未知，不能对调用方声称没发生。
      return (submitted ? "[RESULT_UNKNOWN] INTENT_DISPATCH_UNKNOWN: " : "ERROR: ")
          + SensitiveData.redact(String.valueOf(error));
    }
  }

  private static Intent build(IntentRequest request) {
    Intent intent = new Intent();
    if (!request.action.isEmpty()) intent.setAction(request.action);
    if (!request.component.isEmpty()) {
      int slash = request.component.indexOf('/');
      String pkg = request.component.substring(0, slash);
      String cls = request.component.substring(slash + 1);
      if (cls.startsWith(".")) cls = pkg + cls;
      intent.setComponent(new ComponentName(pkg, cls));
    } else if (!request.packageName.isEmpty()) {
      intent.setPackage(request.packageName);
    }
    if (!request.category.isEmpty()) intent.addCategory(request.category);
    if (!request.data.isEmpty()) intent.setData(Uri.parse(request.data));
    for (var entry : request.extras.entrySet())
      intent.putExtra(entry.getKey(), entry.getValue());
    return intent.getComponent() == null && intent.getPackage() == null && intent.getAction() == null
        ? null
        : intent;
  }
}
