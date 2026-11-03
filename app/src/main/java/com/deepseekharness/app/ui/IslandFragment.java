package com.deepseekharness.app.ui;

import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.deepseekharness.app.IslandSupport;
import com.deepseekharness.app.R;
import com.deepseekharness.app.core.ConfigStore;
import com.deepseekharness.app.core.HarnessController;
import com.deepseekharness.app.util.UiText;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * 「任务状态上岛」设置页。
 *
 * <p>只暴露两个开关和一个系统设置入口：开关落到 {@link ConfigStore}，
 * 能力状态直接读系统（{@link IslandSupport#state}），不猜测、不缓存。
 */
public class IslandFragment extends Fragment {

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    ScrollView scroll = new ScrollView(requireContext());
    scroll.setFillViewport(true);
    LinearLayout root = new LinearLayout(requireContext());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(20), dp(20), dp(20), dp(28));
    scroll.addView(root, new ViewGroup.LayoutParams(-1, -2));

    ConfigStore config = HarnessController.get(requireContext()).config();

    root.addView(
        title(
            com.deepseekharness.app.BuildConfig.ISLAND_EDITION
                ? UiText.text("任务状态上岛（独立版）")
                : UiText.text("任务状态上岛")));
    root.addView(
        body(
            UiText.text(
                "任务运行时把当前阶段显示在系统的状态栏胶囊或灵动岛/流体云上。"
                    + "只使用 Android 16 的公开实时活动接口，不需要 root、不需要额外授权，"
                    + "任何环节失败都会退回普通通知。")));

    // ---- 系统状态 ----
    root.addView(section(UiText.text("系统状态")));
    LinearLayout status = card();
    TextView state = new TextView(requireContext());
    state.setTextSize(14);
    state.setLineSpacing(dp(3), 1f);
    state.setTextColor(requireContext().getColor(R.color.shell_text));
    state.setText(statusText());
    status.addView(state);
    root.addView(status);

    Button open = new Button(requireContext());
    open.setText(UiText.text("打开系统实时活动设置"));
    open.setAllCaps(false);
    open.setOnClickListener(
        v -> {
          try {
            startActivity(IslandSupport.settingsIntent(requireContext()));
          } catch (Exception unavailable) {
            // 系统没有对应设置页时不阻塞用户。
          }
        });
    root.addView(open, new LinearLayout.LayoutParams(-1, -2));

    // ---- 开关 ----
    root.addView(section(UiText.text("行为")));
    LinearLayout behaviors = card();

    MaterialSwitch enabled = new MaterialSwitch(requireContext());
    enabled.setChecked(config.isTaskIslandEnabled());
    enabled.setText(UiText.text("任务状态上岛"));
    enabled.setContentDescription(UiText.text("任务状态上岛"));
    enabled.setOnCheckedChangeListener((button, checked) -> config.setTaskIslandEnabled(checked));
    behaviors.addView(enabled);

    behaviors.addView(hint(UiText.text("多任务并行时只把优先级最高的一个放上岛，避免胶囊文字来回跳动。")));

    MaterialSwitch chronometer = new MaterialSwitch(requireContext());
    chronometer.setChecked(config.isTaskIslandChronometer());
    chronometer.setText(UiText.text("用系统计时器显示耗时"));
    chronometer.setContentDescription(UiText.text("用系统计时器显示耗时"));
    chronometer.setOnCheckedChangeListener(
        (button, checked) -> config.setTaskIslandChronometer(checked));
    behaviors.addView(chronometer);

    behaviors.addView(
        hint(UiText.text("开启后胶囊里的秒数由系统自己走，不再每秒重绘通知，更省电；" + "代价是胶囊只显示计时，不显示「执行中」「等你确认」这类阶段文字。")));
    root.addView(behaviors);

    // ---- 说明 ----
    root.addView(section(UiText.text("说明")));
    LinearLayout notes = card();
    notes.addView(
        body(
            UiText.text(
                "支持的设备：Android 16 及以上的原生系统、小米澎湃 OS 3.0.300+/4、"
                    + "ColorOS 16、OriginOS 6，以及后续跟进同一接口的厂商。")));
    notes.addView(body(UiText.text("更老的系统、以及鸿蒙系统不会显示岛，只会显示普通通知 —— 这是系统能力差异，不是开关失效。")));
    notes.addView(
        body(UiText.text("小米的官方超级岛大岛样式需要向小米申请白名单；本应用不依赖它，" + "自带的能力在未申请时也会自动使用系统生成的胶囊。")));
    root.addView(notes);

    return scroll;
  }

  private String statusText() {
    int state = IslandSupport.state(requireContext());
    String api = "API " + Build.VERSION.SDK_INT;
    return switch (state) {
      case IslandSupport.STATE_ALLOWED -> UiText.format("已允许：任务运行时会显示在状态栏胶囊上。（%s）", api);
      case IslandSupport.STATE_DENIED ->
          UiText.format("系统支持，但本应用当前未被允许。请用上面的按钮在系统设置里打开「实时活动」。（%s）", api);
      case IslandSupport.STATE_UNSUPPORTED ->
          UiText.format("当前系统版本不支持实时活动（需要 Android 16 及以上），只会显示普通通知。（%s）", api);
      default -> UiText.format("无法确认系统状态。（%s）", api);
    };
  }

  private TextView title(String text) {
    TextView view = new TextView(requireContext());
    view.setText(text);
    view.setTextSize(20);
    view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
    view.setTextColor(requireContext().getColor(R.color.shell_text));
    view.setIncludeFontPadding(false);
    view.setPadding(0, 0, 0, dp(10));
    return view;
  }

  private TextView section(String text) {
    TextView view = new TextView(requireContext());
    view.setText(text);
    view.setTextSize(13);
    view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
    view.setTextColor(requireContext().getColor(R.color.shell_text_muted));
    view.setIncludeFontPadding(false);
    view.setPadding(dp(4), dp(18), 0, dp(8));
    return view;
  }

  private TextView body(String text) {
    TextView view = new TextView(requireContext());
    view.setText(text);
    view.setTextSize(13);
    view.setLineSpacing(dp(3), 1f);
    view.setTextColor(requireContext().getColor(R.color.shell_text_muted));
    return view;
  }

  private TextView hint(String text) {
    TextView view = body(text);
    view.setTextSize(12);
    view.setPadding(0, dp(2), 0, dp(10));
    return view;
  }

  private LinearLayout card() {
    LinearLayout layout = new LinearLayout(requireContext());
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setBackgroundResource(R.drawable.bg_shell_card);
    layout.setPadding(dp(16), dp(14), dp(16), dp(14));
    layout.setGravity(Gravity.START);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.topMargin = dp(2);
    layout.setLayoutParams(params);
    return layout;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
