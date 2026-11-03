package com.deepseekharness.app.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.deepseekharness.app.R;

/**
 * 原生内页共用页面骨架：v5 标题 / 分组卡片 / 可点行 / 页脚操作。
 *
 * <p>2026-10-27 起统一走 shell_* token：画布、卡片、文字、描边与按钮不再各页一套。
 * 业务事件仍由页面自己绑定；这里只提供容器，不伪造任何状态或数据。
 */
final class CardPage {
  final Context context;
  final LinearLayout root, content, footer;
  final ScrollView scroll;
  private final boolean compact;

  CardPage(Context context, String title, String subtitle) {
    this(context, title, subtitle, false);
  }

  /** 底部弹层 / 对话框用紧凑标题，避免把页面级大标题塞进半屏容器。 */
  CardPage(Context context, String title, String subtitle, boolean compact) {
    this.context = context;
    this.compact = compact;
    root = column();
    root.setBackgroundColor(context.getColor(R.color.shell_bg));
    scroll = new ScrollView(context);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    content = column();
    content.setPadding(dp(pad()), dp(compact ? 16 : 18), dp(pad()), dp(compact ? 16 : 30));
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (title != null && !title.isEmpty()) {
      TextView heading = text(title, compact ? 20 : 26, R.color.shell_text);
      heading.setTypeface(null, android.graphics.Typeface.BOLD);
      heading.setLineSpacing(dp(2), 1);
      if (subtitle == null || subtitle.isEmpty())
        heading.setPadding(0, 0, 0, dp(compact ? 12 : 18));
      content.addView(heading);
    }
    if (subtitle != null && !subtitle.isEmpty()) {
      TextView sub = text(subtitle, compact ? 12 : 13, R.color.shell_text_secondary);
      sub.setLineSpacing(dp(3), 1);
      sub.setPadding(0, dp(8), 0, dp(compact ? 14 : 20));
      content.addView(sub);
    }
    footer = column();
    footer.setPadding(dp(pad()), dp(8), dp(pad()), dp(compact ? 12 : 16));
    root.addView(footer);
  }

  private int pad() {
    return compact ? 16 : 20;
  }

  int dp(int n) {
    return Math.round(n * context.getResources().getDisplayMetrics().density);
  }

  LinearLayout column() {
    LinearLayout view = new LinearLayout(context);
    view.setOrientation(LinearLayout.VERTICAL);
    return view;
  }

  TextView text(String value, int size, int color) {
    TextView view = new TextView(context);
    view.setText(value);
    view.setTextSize(size);
    view.setIncludeFontPadding(false);
    view.setTextColor(context.getColor(color));
    return view;
  }

  LinearLayout card() {
    LinearLayout card = column();
    card.setBackgroundResource(R.drawable.bg_shell_card);
    card.setPadding(dp(16), dp(12), dp(16), dp(12));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(14);
    content.addView(card, p);
    return card;
  }

  void label(String title) {
    TextView label = text(title, 12, R.color.shell_text_secondary);
    label.setTypeface(null, android.graphics.Typeface.BOLD);
    label.setLetterSpacing(0.03f);
    label.setPadding(0, dp(10), 0, dp(8));
    content.addView(label);
  }

  void kv(LinearLayout card, String label, String value) {
    LinearLayout row = new LinearLayout(context);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(dp(44));
    row.addView(
        text(label, 12, R.color.shell_text_secondary), new LinearLayout.LayoutParams(0, -2, 1));
    TextView content = text(value, 13, R.color.shell_text);
    content.setMaxWidth(dp(210));
    content.setGravity(Gravity.END);
    row.addView(content);
    card.addView(row);
  }

  void entry(LinearLayout card, String title, String subtitle, int icon, Runnable action) {
    if (card.getChildCount() > 0) card.addView(divider());
    LinearLayout row = new LinearLayout(context);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(10), 0, dp(10));
    row.setMinimumHeight(dp(56));
    row.setBackgroundResource(R.drawable.bg_action_plain);
    row.setFocusable(true);
    row.setOnClickListener(v -> action.run());
    ImageView mark = new ImageView(context);
    mark.setImageResource(icon);
    mark.setImageTintList(
        android.content.res.ColorStateList.valueOf(context.getColor(R.color.shell_accent)));
    mark.setBackgroundResource(R.drawable.bg_logo);
    mark.setPadding(dp(7), dp(7), dp(7), dp(7));
    mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(mark, new LinearLayout.LayoutParams(dp(32), dp(32)));
    LinearLayout words = column();
    TextView heading = text(title, 14, R.color.shell_text);
    heading.setTypeface(null, android.graphics.Typeface.BOLD);
    words.addView(heading);
    if (subtitle != null && !subtitle.isEmpty()) {
      TextView detail = text(subtitle, 12, R.color.shell_text_muted);
      detail.setPadding(0, dp(4), 0, 0);
      detail.setMaxLines(2);
      detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
      words.addView(detail);
    }
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.leftMargin = dp(12);
    p.rightMargin = dp(8);
    row.addView(words, p);
    ImageView arrow = new ImageView(context);
    arrow.setImageResource(R.drawable.ic_ui2_chevron);
    arrow.setImageTintList(
        android.content.res.ColorStateList.valueOf(context.getColor(R.color.shell_text_muted)));
    arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(arrow, new LinearLayout.LayoutParams(dp(16), dp(16)));
    card.addView(row);
  }

  private View divider() {
    View line = new View(context);
    line.setBackgroundColor(context.getColor(R.color.shell_line));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1));
    p.leftMargin = dp(44);
    line.setLayoutParams(p);
    return line;
  }

  Button button(LinearLayout parent, String title, boolean primary, Runnable action) {
    var button = new androidx.appcompat.widget.AppCompatButton(context);
    button.setText(title);
    button.setTextSize(14);
    button.setAllCaps(false);
    button.setTypeface(null, android.graphics.Typeface.BOLD);
    button.setIncludeFontPadding(false);
    button.setMinHeight(dp(primary ? 52 : 46));
    button.setMinimumHeight(dp(primary ? 52 : 46));
    button.setMinWidth(0);
    button.setBackgroundResource(primary ? R.drawable.bg_btn_primary : R.drawable.bg_btn);
    button.setTextColor(
        context.getColorStateList(primary ? R.color.button_primary_text : R.color.button_text));
    button.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(10);
    parent.addView(button, p);
    return button;
  }
}
