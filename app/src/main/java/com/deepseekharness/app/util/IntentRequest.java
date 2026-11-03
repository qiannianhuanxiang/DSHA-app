package com.deepseekharness.app.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 以 DSHA 自身身份发起的 Intent 请求（替代 {@code am start} / {@code am broadcast}）。
 *
 * <p>纯逻辑：只解析与校验，不触碰 Android API，便于单测。系统 {@code am} 冒充
 * {@code com.android.shell} 发起，普通 App 身份必然被拒
 * （{@code package=com.android.shell does not belong to uid}）；以 DSHA 自己的包名发起
 * 才不需要任何特殊权限，这与 Termux 自带 {@code am} 的做法一致。
 *
 * <p>只支持两类动作，且都要求显式目标：启动界面（{@code -n 包名/.类} 或
 * {@code pkg} + {@code action}）、发广播（{@code action}）。不开放 service、不解析
 * {@code file:}，也不允许隐式启动没有包名的界面 —— 那些会把用户的下一步交给任意应用。
 */
public final class IntentRequest {
  public enum Kind {
    START,
    BROADCAST
  }

  /** 请求被拒的原因；调用方原样回给 agent，不再猜。 */
  public static final class Invalid extends Exception {
    public final String code;

    Invalid(String code) {
      super(code);
      this.code = code;
    }
  }

  public final Kind kind;
  public final String action;
  public final String packageName;
  public final String component;
  public final String data;
  public final String category;
  /** {@code --es key value} 形式的字符串附加项，按传入顺序保留。 */
  public final Map<String, String> extras;

  private IntentRequest(
      Kind kind,
      String action,
      String packageName,
      String component,
      String data,
      String category,
      Map<String, String> extras) {
    this.kind = kind;
    this.action = action;
    this.packageName = packageName;
    this.component = component;
    this.data = data;
    this.category = category;
    this.extras = Collections.unmodifiableMap(new LinkedHashMap<>(extras));
  }

  /** 与 {@code am} 的参数顺序无关的极小子集解析器；未知选项直接拒绝。 */
  public static IntentRequest parse(List<String> argv) throws Invalid {
    if (argv == null || argv.isEmpty()) throw new Invalid("INTENT_EMPTY");
    String verb = argv.get(0);
    Kind kind;
    if (verb.equals("start")) kind = Kind.START;
    else if (verb.equals("broadcast")) kind = Kind.BROADCAST;
    else throw new Invalid("INTENT_VERB_UNSUPPORTED");
    String action = "", pkg = "", component = "", data = "", category = "";
    Map<String, String> extras = new LinkedHashMap<>();
    for (int i = 1; i < argv.size(); i++) {
      String option = argv.get(i);
      switch (option) {
        case "-a" -> {
          action = value(argv, ++i);
          requireAction(action);
        }
        case "-n" -> {
          component = value(argv, ++i);
          requireComponent(component);
        }
        case "-p" -> {
          pkg = value(argv, ++i);
          if (!ScreenConfirmationPolicy.validPackage(pkg)) throw new Invalid("INTENT_PACKAGE_INVALID");
        }
        case "-d" -> data = requireData(value(argv, ++i));
        case "-c" -> {
          category = value(argv, ++i);
          if (!category.matches("[A-Za-z][A-Za-z0-9_.]*(?:[A-Za-z0-9_])")) {
            throw new Invalid("INTENT_CATEGORY_INVALID");
          }
        }
        case "--es" -> {
          String key = value(argv, ++i);
          if (!key.matches("[A-Za-z][A-Za-z0-9_.]{0,63}")) throw new Invalid("INTENT_EXTRA_KEY_INVALID");
          extras.put(key, value(argv, ++i));
        }
        default -> throw new Invalid("INTENT_OPTION_UNSUPPORTED");
      }
    }
    if (kind == Kind.START) {
      if (component.isEmpty() && (pkg.isEmpty() || action.isEmpty()))
        throw new Invalid("INTENT_TARGET_REQUIRED");
    } else if (action.isEmpty()) {
      throw new Invalid("INTENT_ACTION_REQUIRED");
    }
    if (!component.isEmpty() && !pkg.isEmpty() && !component.startsWith(pkg + "/"))
      throw new Invalid("INTENT_COMPONENT_PACKAGE_MISMATCH");
    // 广播可能唤起后台组件；只允许显式包名或带包名限制。
    if (kind == Kind.BROADCAST && pkg.isEmpty() && component.isEmpty())
      throw new Invalid("INTENT_BROADCAST_PACKAGE_REQUIRED");
    return new IntentRequest(kind, action, pkg, component, data, category, extras);
  }

  /** 用于日志与确认文案的稳定摘要，不含附加项内容（可能含用户数据）。 */
  public String summary() {
    if (!component.isEmpty()) return kind.name().toLowerCase(java.util.Locale.ROOT) + " " + component;
    if (!packageName.isEmpty())
      return kind.name().toLowerCase(java.util.Locale.ROOT) + " " + packageName;
    return kind.name().toLowerCase(java.util.Locale.ROOT) + " " + action;
  }

  /** 该请求实际会作用的包名；确认策略与敏感应用判定都用它。 */
  public String targetPackage() {
    if (!component.isEmpty()) {
      int slash = component.indexOf('/');
      return slash > 0 ? component.substring(0, slash) : component;
    }
    return packageName;
  }

  private static String value(List<String> argv, int index) throws Invalid {
    if (index >= argv.size()) throw new Invalid("INTENT_VALUE_MISSING");
    String v = argv.get(index);
    if (v.isEmpty() || v.length() > 2048) throw new Invalid("INTENT_VALUE_INVALID");
    for (int i = 0; i < v.length(); i++) {
      char c = v.charAt(i);
      if (c < 0x20 || c == 0x7f) throw new Invalid("INTENT_VALUE_CONTROL_CHARACTER");
    }
    return v;
  }

  private static void requireAction(String action) throws Invalid {
    if (!action.matches("[A-Za-z][A-Za-z0-9_.]*")) throw new Invalid("INTENT_ACTION_INVALID");
  }

  /**
   * {@code am -n pkg/.Cls} 的简写要按 Android 语义展开成 {@code pkg.Cls}，
   * 否则 {@code com.android.settings/.Settings} 这种最常见的写法会被拒。
   */
  private static void requireComponent(String component) throws Invalid {
    int slash = component.indexOf('/');
    if (slash <= 0 || slash == component.length() - 1) throw new Invalid("INTENT_COMPONENT_INVALID");
    String pkg = component.substring(0, slash);
    String cls = component.substring(slash + 1);
    if (!ScreenConfirmationPolicy.validPackage(pkg)) throw new Invalid("INTENT_COMPONENT_INVALID");
    if (cls.startsWith(".")) cls = pkg + cls;
    if (!cls.matches("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
      throw new Invalid("INTENT_COMPONENT_INVALID");
  }

  /** 只放行无 scheme 或明确的 android-app 包名形式；file:// 会把私有文件暴露给任意应用。 */
  private static String requireData(String data) throws Invalid {
    String low = data.toLowerCase(java.util.Locale.ROOT);
    if (!low.startsWith("http://")
        && !low.startsWith("https://")
        && !low.startsWith("geo:")
        && !low.startsWith("tel:")
        && !low.startsWith("mailto:")
        && !low.startsWith("market://")) {
      throw new Invalid("INTENT_DATA_SCHEME_FORBIDDEN");
    }
    return data;
  }

  /** 回给 agent 的一行说明；不含附加项内容。 */
  public static List<String> describeOptions() {
    return new ArrayList<>(
        List.of(
            "start|broadcast",
            "-n 包名/.类",
            "-p 包名",
            "-a ACTION",
            "-c CATEGORY",
            "-d http(s)/geo/tel/mailto/market 链接",
            "--es 键 值"));
  }
}
