package com.deepseekharness.app.control;

import android.content.Context;
import com.deepseekharness.app.DshaAccessibilityService;
import com.deepseekharness.app.DshaApp;
import com.deepseekharness.app.capabilities.CapabilityRequest;
import java.util.concurrent.CopyOnWriteArrayList;

/** Process-wide native authority for the current local DSH conversation and turn. */
public final class PhoneControlCoordinator {
  private static PhoneControlCoordinator instance;
  private final Context context;
  private final ControlTaskAuthority authority = new ControlTaskAuthority();
  private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
  private volatile ScreenControlConfirmation confirmation;
  private volatile java.util.function.Consumer<ControlTaskAuthority.Binding> turnCancellation;

  private PhoneControlCoordinator(Context context) {
    this.context = context.getApplicationContext();
    OwnedScreenOverlay.setExpansionListener(
        () -> {
          var state = snapshot();
          if (!state.paused()) pause("ASSISTANT_PANEL_EXPANDED");
        });
    authority.setTransientProbe(this::stillHeld);
  }

  /**
   * Whether the UI state that justified a transient pause is still present.
   *
   * <p>Every answer here must be based on observed state, never on a guess: returning false
   * releases the fence. Anything this class cannot prove is gone stays held.
   */
  private boolean stillHeld(String why) {
    return switch (why) {
      // The assistant window reports when it is actually attached again.
      case "OVERLAY_UNAVAILABLE", "OVERLAY_REMOVED" -> !Presence.overlayAttached();
      // An expanded panel is fenced by expand(); collapse() clears it explicitly, so a pause
      // still carrying this reason with no expansion left is stale.
      case "ASSISTANT_EXPANDED", "ASSISTANT_PANEL_EXPANDED" -> Presence.panelExpanded();
      // beginEditing() fences the IME; the flag is cleared whenever the editor goes away.
      case "ASSISTANT_IME" -> Presence.editorVisible();
      // Recognizer or voice UI still alive.
      case "ASSISTANT_VOICE" -> Presence.voiceActive();
      default -> true;
    };
  }

  /**
   * UI facts the fence depends on, published by the components that own them.
   *
   * <p>Each holder sets its flag while the condition is real. A pause whose holder has already
   * cleared the flag is provably stale and may be released; a missing holder defaults to held so
   * a wiring mistake can never silently drop a fence.
   */
  public static final class Presence {
    private static volatile boolean overlayAttached;
    private static volatile boolean panelExpanded;
    private static volatile boolean editorVisible;
    private static volatile boolean voiceActive;

    private Presence() {}

    public static void setOverlayAttached(boolean value) {
      overlayAttached = value;
    }

    public static boolean overlayAttached() {
      return overlayAttached;
    }

    public static void setPanelExpanded(boolean value) {
      panelExpanded = value;
    }

    public static boolean panelExpanded() {
      return panelExpanded;
    }

    public static void setEditorVisible(boolean value) {
      editorVisible = value;
    }

    public static boolean editorVisible() {
      return editorVisible;
    }

    public static void setVoiceActive(boolean value) {
      voiceActive = value;
    }

    public static boolean voiceActive() {
      return voiceActive;
    }
  }

  public static synchronized PhoneControlCoordinator get(Context context) {
    if (instance == null) instance = new PhoneControlCoordinator(context);
    return instance;
  }

  public ControlTaskAuthority.Snapshot snapshot() {
    return authority.snapshot();
  }

  public ControlTaskAuthority.Lease begin(ControlTaskAuthority.Binding binding) {
    var lease = authority.begin(binding);
    changed();
    return lease;
  }

  public void checkBegin(ControlTaskAuthority.Binding binding) {
    authority.checkBegin(binding);
  }

  public ControlTaskAuthority.Lease lease(ControlTaskAuthority.Binding binding) {
    return authority.lease(binding);
  }

  public boolean current(ControlTaskAuthority.Lease lease) {
    return authority.current(lease);
  }

  public boolean actionAllowed(ControlTaskAuthority.Lease lease) {
    return authority.actionAllowed(lease);
  }

  public boolean observed(ControlTaskAuthority.Lease lease) {
    boolean result = authority.observed(lease);
    if (result) changed();
    return result;
  }

  public long beginObservation(ControlTaskAuthority.Lease lease) {
    return authority.beginObservation(lease);
  }

  public boolean observed(
      ControlTaskAuthority.Lease lease,
      long sequence,
      com.deepseekharness.app.util.ScreenTarget target) {
    boolean result = authority.observed(lease, sequence, target);
    if (result) changed();
    return result;
  }

  public boolean actionAllowed(
      ControlTaskAuthority.Lease lease, com.deepseekharness.app.util.ScreenTarget target) {
    return authority.actionAllowed(lease, target);
  }

  public boolean checkActionTarget(
      ControlTaskAuthority.Lease lease, com.deepseekharness.app.util.ScreenTarget target) {
    return authority.checkActionTarget(lease, target);
  }

  public void requireObservation(ControlTaskAuthority.Binding binding) {
    authority.requireObservation(binding);
    fence(false, null);
  }

  /** Completion fences work but must not invoke the selected assistant's cancel callback. */
  public void completeLocal(ControlTaskAuthority.Binding binding) {
    if (authority.completeLocal(binding)) fence(false, null);
  }

  public void pause(String reason) {
    var previous = authority.pause(reason);
    fence(true, previous);
  }

  public boolean pauseCurrent(ControlTaskAuthority.Lease lease, String reason) {
    var previous = authority.pauseCurrent(lease, reason);
    if (previous == null) return false;
    fence(true, previous);
    return true;
  }

  public void resume() {
    authority.resume();
    fence(false, null);
  }

  public void stop(String reason) {
    var previous = authority.stop(reason);
    fence(true, previous);
  }

  public void stopLocal(String reason) {
    var binding = snapshot().binding();
    if (binding != null) stop(reason);
  }

  public void bindRequest(CapabilityRequest request, ControlTaskAuthority.Lease lease) {
    if (!current(lease)) throw new IllegalStateException("SCREEN_TASK_CHANGED");
    ScreenAuthorityBindings.bind(
        request, lease.binding().identity() + "\0" + lease.revision(), () -> current(lease));
  }

  public void setConfirmationHandler(ScreenControlConfirmation handler) {
    confirmation = handler;
  }

  public ScreenControlConfirmation confirmationHandler() {
    return confirmation;
  }

  public void setTurnCancellation(
      java.util.function.Consumer<ControlTaskAuthority.Binding> callback) {
    turnCancellation = callback;
  }

  public void addListener(Runnable listener) {
    listeners.addIfAbsent(listener);
  }

  public void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  private void fence(boolean cancelTurn, ControlTaskAuthority.Binding previous) {
    DshaApp.from(context).capabilityBroker().revoke("screen");
    DshaAccessibilityService.revokeRequests();
    if (cancelTurn && previous != null) {
      var callback = turnCancellation;
      if (callback != null)
        try {
          callback.accept(previous);
        } catch (RuntimeException ignored) {
        }
    }
    changed();
  }

  private void changed() {
    for (Runnable listener : listeners)
      try {
        listener.run();
      } catch (RuntimeException ignored) {
      }
  }
}
