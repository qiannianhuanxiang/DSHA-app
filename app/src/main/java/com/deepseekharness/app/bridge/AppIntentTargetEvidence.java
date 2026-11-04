package com.deepseekharness.app.bridge;

import com.deepseekharness.app.capabilities.TargetEvidence;
import com.deepseekharness.app.util.IntentRequest;

/**
 * 启动/广播许可绑定实际解析出的目标，不从动作描述或当前前台应用猜测。
 *
 * <p>无包名的广播（已由 IntentRequest 拒绝）不会有这类证据；这里要求解析后仍能给出
 * 明确目标，避免出现“确认时看不出会打到谁”的授权。
 */
public final class AppIntentTargetEvidence implements TargetEvidence {
  public final String summary;
  public final String targetPackage;

  public AppIntentTargetEvidence(IntentRequest request) {
    String target = request.targetPackage();
    if (target == null || target.isEmpty()) throw new IllegalArgumentException("INVALID_INTENT_TARGET");
    this.summary = request.summary();
    this.targetPackage = target;
  }

  @Override
  public String kind() {
    return "android-intent-dispatch";
  }
}
