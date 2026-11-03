package com.deepseekharness.app.util;

import java.util.UUID;

/** 同源展示消息仍绑定本次页面、运行实例及递增序号；不是插件隔离沙箱。 */
public final class BrowserThemeGate {
  private final Object owner;
  private final PreviewPageSession.Identity identity;
  private final String nonce = UUID.randomUUID().toString().replace("-", "");
  private long sequence = -1;

  public BrowserThemeGate(Object source, PreviewPageSession.Identity page) {
    if (source == null || page == null)
      throw new IllegalArgumentException("BROWSER_THEME_IDENTITY");
    owner = source;
    identity = page;
  }

  public String nonce() {
    return nonce;
  }

  public PreviewPageSession.Identity identity() {
    return identity;
  }

  public boolean owns(Object source) {
    return source == owner;
  }

  public synchronized boolean accept(
      Object source,
      PreviewPageSession.Identity current,
      String token,
      long generation,
      String instance,
      long seq,
      boolean top,
      String url) {
    if (source != owner
        || !top
        || !identity.equals(current)
        || !nonce.equals(token)
        || generation != identity.generation()
        || !identity.instanceId().equals(instance)
        || !WebPreviewPolicy.sameService(WebPreviewPolicy.loopbackBaseUrl(identity.authUrl()), url)
        || seq < 0
        || seq > 9007199254740991L
        || seq <= sequence) return false;
    sequence = seq;
    return true;
  }
}
