package com.deepseekharness.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;

/**
 * 2dp 活动条：只表达真实的进行 / 完成 / 异常状态，不推算安装或启动阶段进度。
 *
 * <p>横扫光带由当前视图的帧回调推进，不持有无限 ValueAnimator；系统关闭动画或视图不可见时
 * 保持静态。颜色全部取自 shell_* token，不在此硬编码。
 */
public final class ShellActivityTrack extends View {

  /** 没有进行中的真实任务：静态线条色。 */
  public static final int IDLE = 0;

  /** 正在启动 / 停止 / 核对：缓慢横扫的强调光带。 */
  public static final int RUNNING = 1;

  /** 已就绪：静态成功色。 */
  public static final int SETTLED = 2;

  /** 需要处理（原实例待确认、环境待恢复）：静态错误色。 */
  public static final int ERROR = 3;

  /** 与设计源 activity-track 的 sweep 时长一致。 */
  private static final long SWEEP_DURATION_MS = 2600L;

  /** 光带宽度占总宽度的比例（设计源 34%）。 */
  private static final float BAND_WIDTH_FRACTION = 0.34f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int idleColor;
  private final int runningColor;
  private final int settledColor;
  private final int errorColor;

  private int state = IDLE;
  private long sweepStartMs;
  private Shader bandShader;
  private int bandShaderWidth = -1;
  private boolean animationsEnabled = true;
  private boolean windowVisible = true;

  public ShellActivityTrack(Context context) {
    this(context, null);
  }

  public ShellActivityTrack(Context context, AttributeSet attrs) {
    super(context, attrs);
    idleColor = context.getColor(com.deepseekharness.app.R.color.shell_line);
    runningColor = context.getColor(com.deepseekharness.app.R.color.shell_accent);
    settledColor = context.getColor(com.deepseekharness.app.R.color.shell_good);
    errorColor = context.getColor(com.deepseekharness.app.R.color.shell_bad);
    refreshAnimationSetting();
  }

  /** 设置展示状态；同一状态重复设置不会重置光带或启动新的重绘循环。 */
  public void setState(int nextState) {
    int normalized =
        nextState == RUNNING || nextState == SETTLED || nextState == ERROR ? nextState : IDLE;
    if (normalized == state) return;
    state = normalized;
    sweepStartMs = android.os.SystemClock.uptimeMillis();
    refreshAnimationSetting();
    invalidate();
  }

  public int state() {
    return state;
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    sweepStartMs = android.os.SystemClock.uptimeMillis();
    refreshAnimationSetting();
    invalidate();
  }

  @Override
  protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    // 退到后台时停掉帧回调；回到可见时下一帧重新按真实状态绘制。
    windowVisible = visibility == VISIBLE;
    invalidate();
  }

  @Override
  protected void onDetachedFromWindow() {
    // 仅用帧回调推进；脱离窗口后不再请求下一帧，不需要额外取消动作。
    bandShader = null;
    bandShaderWidth = -1;
    super.onDetachedFromWindow();
  }

  /** 只在状态或附着状态变化时读取，避免每帧查询系统设置。 */
  private void refreshAnimationSetting() {
    try {
      animationsEnabled =
          Settings.Global.getFloat(
                  getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
              > 0f;
    } catch (RuntimeException ignored) {
      // 读取设置失败按开启处理；仍然不会伪造任何任务状态。
      animationsEnabled = true;
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    int width = getWidth();
    int height = getHeight();
    if (width <= 0 || height <= 0) return;
    paint.setShader(null);
    paint.setColor(idleColor);
    canvas.drawRect(0f, 0f, width, height, paint);
    if (state == SETTLED || state == ERROR) {
      paint.setColor(state == SETTLED ? settledColor : errorColor);
      canvas.drawRect(0f, 0f, width, height, paint);
      return;
    }
    if (state != RUNNING) return;
    float sweepWidth = Math.max(1f, width * BAND_WIDTH_FRACTION);
    if (!animationsEnabled || !windowVisible || !isShown()) {
      // 系统关闭动画或视图不可见时保持静态强调段：仍能看出“进行中”，但不再逐帧重绘。
      drawBand(canvas, width * 0.33f, sweepWidth, height);
      return;
    }
    long now = android.os.SystemClock.uptimeMillis();
    if (sweepStartMs <= 0) sweepStartMs = now;
    float phase = (now - sweepStartMs) % SWEEP_DURATION_MS / (float) SWEEP_DURATION_MS;
    float start = -sweepWidth + (width + sweepWidth) * phase;
    drawBand(canvas, start, sweepWidth, height);
    postInvalidateOnAnimation();
  }

  private void drawBand(Canvas canvas, float start, float sweepWidth, float height) {
    int bandWidth = Math.max(1, Math.round(sweepWidth));
    if (bandShader == null || bandShaderWidth != bandWidth) {
      int transparent = runningColor & 0x00FFFFFF;
      bandShader =
          new LinearGradient(
              0f,
              0f,
              bandWidth,
              0f,
              new int[] {transparent, runningColor, transparent},
              new float[] {0f, 0.5f, 1f},
              Shader.TileMode.CLAMP);
      bandShaderWidth = bandWidth;
    }
    canvas.save();
    canvas.translate(start, 0f);
    paint.setShader(bandShader);
    canvas.drawRect(0f, 0f, sweepWidth, height, paint);
    paint.setShader(null);
    canvas.restore();
  }
}
