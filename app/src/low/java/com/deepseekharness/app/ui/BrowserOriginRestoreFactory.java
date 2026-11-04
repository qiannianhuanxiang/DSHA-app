package com.deepseekharness.app.ui;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import com.deepseekharness.app.util.WebPreviewPolicy;
import org.json.JSONObject;
import org.mozilla.geckoview.*;

final class BrowserOriginRestoreFactory {
  static boolean supportsGecko() {
    return true;
  }

  static BrowserOriginRestoreActivity.Surface create(Context context, boolean gecko) {
    return gecko ? new Gecko(context) : new BrowserOriginRestoreWebView(context);
  }

  private static final class Gecko implements BrowserOriginRestoreActivity.Surface {
    private final GeckoView view;
    private final GeckoSession session;
    private WebExtension extension;
    private String base, url, configuration;
    private boolean closed, authenticating;

    Gecko(Context context) {
      view = new GeckoView(context);
      session = new GeckoSession(new GeckoSessionSettings.Builder().usePrivateMode(false).build());
      GeckoRuntime runtime = GeckoRuntime.getDefault(context);
      session.open(runtime);
      view.setSession(session);
      runtime
          .getWebExtensionController()
          .ensureBuiltIn("resource://android/assets/web-integration/", "dsha-page@dsh.client")
          .accept(
              value -> {
                view.post(
                    () -> {
                      if (closed) return;
                      extension = value;
                      attach();
                      if (url != null) session.loadUri(url);
                    });
              },
              failure -> {
                view.post(
                    () -> {
                      if (!closed) view.setContentDescription("RESTORE_EXTENSION_UNAVAILABLE");
                    });
              });
      session.setNavigationDelegate(
          new GeckoSession.NavigationDelegate() {
            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(
                GeckoSession source, LoadRequest request) {
              return GeckoResult.fromValue(
                  allowed(request.uri) ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
            }

            @Override
            public void onLocationChange(
                GeckoSession source,
                String location,
                java.util.List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                Boolean hasUserGesture) {
              if (!closed
                  && authenticating
                  && WebPreviewPolicy.sameService(base, location)
                  && "/".equals(Uri.parse(location).getPath())
                  && Uri.parse(location).getEncodedQuery() == null) {
                authenticating = false;
                url = base + "__dsha/browser-origin-restore";
                session.loadUri(url);
              }
            }
          });
    }

    private boolean allowed(String candidate) {
      return candidate != null
          && base != null
          && WebPreviewPolicy.sameService(base, candidate)
          && ("/__dsha/browser-origin-restore".equals(Uri.parse(candidate).getPath())
              || authenticating && "/".equals(Uri.parse(candidate).getPath()));
    }

    private void attach() {
      session
          .getWebExtensionController()
          .setMessageDelegate(
              extension,
              new WebExtension.MessageDelegate() {
                @Override
                public void onConnect(WebExtension.Port port) {
                  if (closed
                      || port.sender.session != session
                      || !port.sender.isTopLevel()
                      || !allowed(port.sender.url)
                      || !"/__dsha/browser-origin-restore"
                          .equals(Uri.parse(port.sender.url).getPath())) {
                    port.disconnect();
                    return;
                  }
                  try {
                    port.postMessage(
                        new JSONObject()
                            .put("type", "origin-restore")
                            .put("configuration", configuration));
                  } catch (Exception ignored) {
                    port.disconnect();
                  }
                }
              },
              "dsha-origin-restore");
    }

    public View view() {
      return view;
    }

    public void load(String url, String configuration) {
      this.base = WebPreviewPolicy.loopbackBaseUrl(url);
      this.url = url;
      this.configuration = configuration;
      authenticating = "/".equals(Uri.parse(url).getPath());
      if (base == null) throw new IllegalArgumentException("RESTORE_DOCUMENT_ORIGIN");
      if (extension != null) session.loadUri(url);
    }

    public void close() {
      closed = true;
      view.releaseSession();
      session.close();
    }
  }
}
