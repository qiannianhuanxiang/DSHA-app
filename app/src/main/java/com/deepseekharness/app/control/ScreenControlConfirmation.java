package com.deepseekharness.app.control;

import com.deepseekharness.app.capabilities.CapabilityRequest;
import com.deepseekharness.app.util.ScreenTarget;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Installed by native UI. The coordinator rechecks all fences after an asynchronous answer. */
@FunctionalInterface
public interface ScreenControlConfirmation {
  /** The exact prompt owns cancellation; closing an old prompt cannot dismiss its successor. */
  interface Prompt extends AutoCloseable {
    @Override
    void close();
  }

  /** The removed prompt's accessibility identity is used only to drain that exact old window. */
  record Decision(Boolean allowed, int windowId) {}

  Prompt confirm(
      CapabilityRequest request,
      ScreenTarget target,
      BooleanSupplier current,
      Consumer<Decision> completion);

  default void confirm(
      CapabilityRequest request, ScreenTarget target, Consumer<Boolean> completion) {
    confirm(request, target, () -> true, decision -> completion.accept(decision.allowed()));
  }
}
