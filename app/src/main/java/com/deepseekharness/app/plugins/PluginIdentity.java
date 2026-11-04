package com.deepseekharness.app.plugins;

/** 插件身份的唯一归一化点：可见包名、运行证据名和历史别名由同一规则映射。 */
public final class PluginIdentity {
  /** 官方 Auto review 的历史包装名；包装本身没有运行逻辑，只暴露锁定官方实验包。 */
  public static final String AUTO_REVIEW_ALIAS = "dsh-auto-review";

  public static final String AUTO_REVIEW_CANONICAL = "@deepseek-ai/dsh-experimental-auto-review";

  private PluginIdentity() {}

  /** 仅合并已有官方历史别名；其它名字保持原样，不能把不同插件折叠成同一身份。 */
  public static String canonical(String name) {
    return AUTO_REVIEW_ALIAS.equals(name) ? AUTO_REVIEW_CANONICAL : name;
  }

  /** profile 长度前缀避免插件名里的分隔符把不同 profile 拼成同一键。 */
  public static String key(String profile, String name) {
    return profile.length() + ":" + profile + ":" + canonical(name);
  }
}
