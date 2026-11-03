package com.deepseekharness.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.deepseekharness.app.BuildConfig;
import com.deepseekharness.app.R;
import com.deepseekharness.app.core.HarnessController;

import java.util.function.Supplier;

/**
 * 设置页：模块入口（安装/配置/数据与备份）+ 其他（更新/自检/重新解压/关于）。
 */
public class SettingsFragment extends Fragment {

  private static final TabOption[] TAB_OPTIONS = {
    new TabOption("安装", "安装与修复运行环境", InstallFragment::new, R.drawable.ic_ui2_box),
    new TabOption("配置", "接口、显示与运行", ConfigFragment::new, R.drawable.ic_settings),
    new TabOption("数据与备份", "备份恢复 · 文件共享", WorkspaceFragment::new, R.drawable.ic_ui2_folder),
    new TabOption(
        "设备能力授权", "Root · Shizuku · ADB · 权限", DeviceGrantsFragment::new, R.drawable.ic_ui_shield),
    new TabOption("任务状态上岛", "状态栏胶囊 · 灵动岛 · 实时活动", IslandFragment::new, R.drawable.ic_ui2_box),
  };

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.fragment_settings, container, false);

    LinearLayout tabs = v.findViewById(R.id.settings_tabs);
    int optionCount = BuildConfig.ISLAND_EDITION ? TAB_OPTIONS.length : TAB_OPTIONS.length - 1;
    for (int i = 0; i < optionCount; i++) {
      if (i > 0) {
        View line = divider();
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, dp(1));
        lineParams.leftMargin = dp(44);
        tabs.addView(line, lineParams);
      }
      tabs.addView(buildRow(i), new LinearLayout.LayoutParams(-1, -2));
    }
    com.deepseekharness.app.core.ConfigStore config =
        HarnessController.get(requireContext()).config();
    TextView powerTitle = v.findViewById(R.id.settings_power_title);
    powerTitle.setText(com.deepseekharness.app.util.UiText.text("省电模式"));
    TextView powerHint = v.findViewById(R.id.settings_power_hint);
    com.google.android.material.materialswitch.MaterialSwitch eco =
        v.findViewById(R.id.settings_eco);
    eco.setContentDescription(com.deepseekharness.app.util.UiText.text("省电模式"));
    eco.setChecked(config.isEcoMode());
    java.util.function.Consumer<Boolean> describe =
        enabled ->
            powerHint.setText(
                enabled
                    ? com.deepseekharness.app.util.UiText.text("熄屏空闲 1 分钟后减少保活；有任务时继续运行。")
                    : com.deepseekharness.app.util.UiText.text("持续保持运行，适合长时间任务。"));
    describe.accept(config.isEcoMode());
    eco.setOnCheckedChangeListener(
        (button, checked) -> {
          config.setEcoMode(checked);
          com.deepseekharness.app.HarnessService.refreshPowerMode(requireContext());
          describe.accept(checked);
        });

    String version = "unknown";
    try {
      version =
          requireContext()
              .getPackageManager()
              .getPackageInfo(requireContext().getPackageName(), 0)
              .versionName;
    } catch (Exception ignored) {
    }
    TextView ver = v.findViewById(R.id.settings_ver);
    ver.setText(com.deepseekharness.app.util.UiText.format("DSHA v%s · MIT 许可", version));
    TextView updateSub = v.findViewById(R.id.settings_update_sub);
    updateSub.setText(com.deepseekharness.app.util.UiText.text("稳定版与预览版更新"));

    v.findViewById(R.id.settings_about)
        .setOnClickListener(
            x ->
                UiMotion.page(requireContext(), getParentFragmentManager().beginTransaction())
                    .replace(R.id.fragment_container, new AboutFragment())
                    .addToBackStack("settings")
                    .commit());
    v.findViewById(R.id.settings_update).setOnClickListener(x -> checkUpdate());
    v.findViewById(R.id.settings_selftest).setOnClickListener(x -> runSelftest());

    // 语言入口：标题在中文界面下也带英文「Language」（只写「语言 · 简体中文」时，
    // 非中文用户根本认不出这是语言开关）；摘要直接显示当前生效语言。
    // 放在「常用设置」分组最前面，不用滚动就能看到。
    String preference = config.getUiLanguagePreference();
    boolean followSystem =
        com.deepseekharness.app.util.UiLanguagePreference.followsSystem(preference);
    String effective = config.getUiLanguage();
    java.util.List<String> languages = com.deepseekharness.app.util.UiLanguagePreference.LANGUAGES;
    String[] optionValues = new String[languages.size() + 1];
    String[] optionLabels = new String[optionValues.length];
    optionValues[0] = com.deepseekharness.app.util.UiLanguagePreference.SYSTEM;
    optionLabels[0] = com.deepseekharness.app.util.UiText.choose("跟随系统", "Follow system");
    for (int i = 0; i < languages.size(); i++) {
      optionValues[i + 1] = languages.get(i);
      // Native labels (including English) stay recognizable in an unfamiliar locale.
      optionLabels[i + 1] =
          com.deepseekharness.app.util.UiLanguagePreference.nativeName(languages.get(i));
    }
    int checked = followSystem ? 0 : languages.indexOf(preference) + 1;
    // 摘要始终显示当前**生效**语言的名字：跟随系统时补上来由，用户一眼能看出实际结果。
    String currentLabel = com.deepseekharness.app.util.UiLanguagePreference.nativeName(effective);
    String summary =
        followSystem
            ? com.deepseekharness.app.util.UiText.format("跟随系统 · %s", currentLabel)
            : currentLabel;
    LinearLayout appearance = v.findViewById(R.id.settings_appearance);
    TextView languageSummary = v.findViewById(R.id.settings_language);
    languageSummary.setText(summary);
    View.OnClickListener openLanguageDialog =
        x ->
            new com.deepseekharness.app.ui.DshaDialogBuilder(requireContext())
                .setTitle(
                    com.deepseekharness.app.util.UiText.choose(
                        "界面语言 / Interface language", "Interface language"))
                .setSingleChoiceItems(
                    optionLabels,
                    checked,
                    (dialog, which) -> {
                      String selected = optionValues[which];
                      if (dialog instanceof android.app.Dialog)
                        ((android.app.Dialog) dialog)
                            .setOnDismissListener(
                                ignored ->
                                    new android.os.Handler(android.os.Looper.getMainLooper())
                                        .post(
                                            () ->
                                                LanguageController.select(
                                                    requireContext(), selected)));
                      dialog.dismiss();
                    })
                .setNegativeButton(com.deepseekharness.app.util.UiText.choose("取消", "Cancel"), null)
                .show();
    appearance.setOnClickListener(openLanguageDialog);
    languageSummary.setOnClickListener(openLanguageDialog);
    v.findViewById(R.id.settings_overlay)
        .setOnClickListener(
            x ->
                UiMotion.page(requireContext(), getParentFragmentManager().beginTransaction())
                    .replace(R.id.fragment_container, new OverlayFragment())
                    .addToBackStack("settings")
                    .commit());
    return v;
  }

  private void runSelftest() {
    startActivity(new Intent(requireContext(), DiagnosticActivity.class));
  }

  private void checkUpdate() {
    startActivity(new Intent(requireContext(), UpdateActivity.class));
  }

  private LinearLayout buildRow(final int index) {
    TabOption opt = TAB_OPTIONS[index];
    LinearLayout row = new LinearLayout(requireContext());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(dp(56));
    row.setPadding(dp(16), dp(12), dp(16), dp(12));
    row.setFocusable(true);
    // 行内按压反馈沿用主题 ripple；卡片圆角由父容器 bg_shell_card 承担。
    row.setBackgroundResource(R.drawable.bg_action_plain);

    LinearLayout body = new LinearLayout(requireContext());
    body.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams bodyParams =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    bodyParams.leftMargin = dp(12);
    bodyParams.rightMargin = dp(8);
    body.setLayoutParams(bodyParams);

    TextView title = new TextView(requireContext());
    title.setText(
        index == 0
            ? getString(R.string.ui2_install_environment)
            : index == 1
                ? getString(R.string.ui2_runtime_config)
                : com.deepseekharness.app.util.UiText.text(opt.title));
    title.setTextSize(14);
    title.setTextColor(requireContext().getColor(R.color.shell_text));
    title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
    title.setIncludeFontPadding(false);

    TextView sub = new TextView(requireContext());
    sub.setText(com.deepseekharness.app.util.UiText.text(opt.sub));
    sub.setTextSize(12);
    sub.setTextColor(requireContext().getColor(R.color.shell_text_muted));
    sub.setIncludeFontPadding(false);
    sub.setMaxLines(2);
    sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams slp =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    slp.topMargin = dp(4);
    sub.setLayoutParams(slp);

    body.addView(title);
    body.addView(sub);

    android.widget.ImageView icon = new android.widget.ImageView(requireContext());
    icon.setImageResource(opt.icon);
    icon.setImageTintList(
        android.content.res.ColorStateList.valueOf(
            requireContext().getColor(R.color.shell_accent)));
    icon.setBackgroundResource(R.drawable.bg_logo);
    icon.setPadding(dp(7), dp(7), dp(7), dp(7));
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
    row.addView(body);

    android.widget.ImageView chevron = new android.widget.ImageView(requireContext());
    chevron.setImageResource(R.drawable.ic_ui2_chevron);
    chevron.setImageTintList(
        android.content.res.ColorStateList.valueOf(
            requireContext().getColor(R.color.shell_text_muted)));
    chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(chevron, new LinearLayout.LayoutParams(dp(16), dp(16)));

    row.setOnClickListener(
        v -> {
          if (opt.activity != null) {
            startActivity(new Intent(requireContext(), opt.activity));
          } else {
            UiMotion.page(requireContext(), getParentFragmentManager().beginTransaction())
                .replace(R.id.fragment_container, opt.factory.get())
                .addToBackStack("settings")
                .commit();
          }
        });
    return row;
  }

  private View divider() {
    View line = new View(requireContext());
    line.setBackgroundColor(requireContext().getColor(R.color.shell_line));
    return line;
  }

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  private static final class TabOption {
    final String title;
    final String sub;
    final Supplier<Fragment> factory;
    final Class<? extends android.app.Activity> activity;
    final int icon;

    TabOption(String title, String sub, Supplier<Fragment> factory, int icon) {
      this.title = title;
      this.sub = sub;
      this.factory = factory;
      this.activity = null;
      this.icon = icon;
    }

    TabOption(String title, String sub, Class<? extends android.app.Activity> activity, int icon) {
      this.title = title;
      this.sub = sub;
      this.factory = null;
      this.activity = activity;
      this.icon = icon;
    }
  }
}
