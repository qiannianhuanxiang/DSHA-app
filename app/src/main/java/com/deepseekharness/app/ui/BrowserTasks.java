package com.deepseekharness.app.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.deepseekharness.app.util.BrowserTaskPolicy;
import com.deepseekharness.app.util.PreviewPageSession;
import com.deepseekharness.app.util.TaskNavigationPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Explicit native navigation, including tasks retained from versions with shared affinities. */
final class BrowserTasks {
  static final String NATIVE_HOME = "dsha-native-home";
  private static WeakReference<Activity> nativeMain = new WeakReference<>(null);

  private record Background(
      WeakReference<PictureInPictureActivity> owner,
      PreviewPageSession.Identity identity,
      boolean engine) {}

  private static Background background;
  private static Background dispatched;

  private BrowserTasks() {}

  static boolean launcher(Intent intent) {
    return intent != null
        && Intent.ACTION_MAIN.equals(intent.getAction())
        && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
  }

  static boolean nativeDestination(Intent intent) {
    // A launcher may replay Main's original action-less base Intent and stale open_launch.
    // Only an actual destination request overrides the pending browser return here;
    // Back/Home already cancels it synchronously in openMain/hideConversation.
    return intent != null
        && (intent.getBooleanExtra(NATIVE_HOME, false)
            || intent.getBooleanExtra("open_plugins", false)
            || intent.getBooleanExtra("open_terminal", false)
            || intent.getBooleanExtra("open_install", false)
            || intent.getBooleanExtra("open_web", false));
  }

  static void entry(String stage, Intent intent) {
    android.util.Log.i(
        "DSHA",
        "BROWSER_RETURN ENTRY_"
            + stage
            + " "
            + (nativeDestination(intent)
                ? "DESTINATION"
                : launcher(intent) ? "LAUNCHER" : "RETAINED"));
  }

  private static void trace(String stage) {
    android.util.Log.i("DSHA", "BROWSER_RETURN " + stage);
  }

  /** Home means a temporary background visit, not a request for the native start page. */
  static void rememberBackground(PictureInPictureActivity owner) {
    var controller = com.deepseekharness.app.core.HarnessController.get(owner);
    var identity = controller.getReadyWebPageIdentity();
    if (identity != null
        && !controller.isStopping()
        && !controller.isUserStopped()
        && !owner.isFinishing()
        && !owner.isDestroyed()
        && owner.pictureInPictureContentReady()) {
      background =
          new Background(new WeakReference<>(owner), identity, PreviewFallback.preferred(owner));
      dispatched = null;
      trace("SAVED");
    } else trace("SAVE_NOT_READY");
  }

  static void nativeNavigation() {
    if (background != null) trace("CANCEL_NATIVE");
    background = null;
    dispatched = null;
  }

  /** Only the original browser reaching full-screen resume acknowledges the Home return. */
  static void browserResumed(PictureInPictureActivity owner) {
    if (background != null
        && background.owner().get() == owner
        && !owner.conversationHidden()
        && !PictureInPictureActivity.showing(owner)) {
      background = null;
      dispatched = null;
      trace("ACK_WEB_RESUMED");
    }
  }

  static boolean resumeBackground(Context context) {
    return resumeBackground(context, false);
  }

  /** A new launcher entry can retry; its matching onResume does not dispatch twice. */
  static boolean resumeBackground(Context context, boolean newEntry) {
    Background pending = background;
    if (pending == null) {
      trace("EMPTY");
      return false;
    }
    var owner = pending.owner().get();
    if (owner == null || owner.isFinishing() || owner.isDestroyed()) {
      background = null;
      dispatched = null;
      trace("OWNER_GONE");
      return false;
    }
    var controller = com.deepseekharness.app.core.HarnessController.get(context);
    if (controller.getReadyWebPageIdentity() == null
        && controller.isWebSessionRetained(pending.identity())
        && !controller.isWebSessionExited(pending.identity())) {
      controller.requestWebSessionRecheck(
          () -> {
            if (background == pending
                && pending.identity().equals(controller.getReadyWebPageIdentity()))
              resumeBackground(context, false);
          });
      trace("WAIT_VERIFICATION");
      return false;
    }
    if (!BrowserTaskPolicy.resumeBackground(
        pending.identity(),
        controller.getReadyWebPageIdentity(),
        true,
        controller.isStopping(),
        controller.isUserStopped(),
        pending.engine() == PreviewFallback.preferred(context))) {
      background = null;
      dispatched = null;
      trace("REJECTED_STATE");
      return false;
    }
    if (!owner.pictureInPictureContentReady()) {
      trace("WAIT_CONTENT");
      return false;
    }
    if (dispatched == pending && !newEntry) {
      trace("IN_FLIGHT");
      return true;
    }
    Intent full =
        new Intent(context, owner.getClass())
            .putExtra(BrowserConversationRoute.MODE, "FULL")
            .putExtra(BrowserConversationRoute.GENERATION, pending.identity().generation())
            .putExtra(BrowserConversationRoute.INSTANCE, pending.identity().instanceId())
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    try {
      dispatched = pending;
      ActivityManager manager =
          (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      if (manager != null)
        for (ActivityManager.AppTask task : manager.getAppTasks())
          if (task.getTaskInfo().id == owner.getTaskId()) {
            task.startActivity(context, full, null);
            trace("DISPATCH_TASK");
            return true;
          }
    } catch (RuntimeException unavailable) {
      trace("TASK_ROUTE_REJECTED");
      android.util.Log.w(
          "DSHA", "BROWSER_BACKGROUND_TASK: " + unavailable.getClass().getSimpleName());
    }
    // Android/ROM may reject startActivityInTask even for our own retained task. The
    // explicit singleTask component and its existing affinity select the same live host;
    // NEW_TASK is required for this fallback, without CLEAR_* or MULTIPLE_TASK.
    try {
      context.startActivity(full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      trace("DISPATCH_COMPONENT");
      return true;
    } catch (RuntimeException unavailable) {
      dispatched = null;
      trace("DISPATCH_RETRYABLE");
      android.util.Log.w(
          "DSHA", "BROWSER_BACKGROUND_ROUTE: " + unavailable.getClass().getSimpleName());
      return false;
    }
  }

  static void rememberMain(Activity activity) {
    if (!activity.isFinishing() && !activity.isDestroyed() && activity.isTaskRoot())
      nativeMain = new WeakReference<>(activity);
    reconcileRecents(activity, activity.getTaskId());
  }

  static boolean duplicateLauncher(Activity activity, Bundle restored) {
    Intent intent = activity.getIntent();
    return intent != null
        && BrowserTaskPolicy.duplicateLauncher(
            restored != null,
            activity.isTaskRoot(),
            Intent.ACTION_MAIN.equals(intent.getAction()),
            intent.hasCategory(Intent.CATEGORY_LAUNCHER));
  }

  static Intent homeIntent(Context context) {
    return navigationIntent(context).putExtra("open_launch", true);
  }

  static Intent navigationIntent(Context context) {
    return new Intent(context, MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(NATIVE_HOME, true);
  }

  /** Main Back exits the UI, including a legacy task with a hidden browser below Main. */
  static void exitMain(Activity activity) {
    nativeNavigation();
    reconcileRecents(activity, activity.getTaskId());
    // A retained hidden browser may be the task immediately behind Main. Bring
    // the launcher forward first, or moving Main back could resume that browser,
    // whose hidden-mode callback would immediately reopen Main.
    try {
      activity.startActivity(
          new Intent(Intent.ACTION_MAIN)
              .addCategory(Intent.CATEGORY_HOME)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (android.content.ActivityNotFoundException unavailable) {
      android.util.Log.w("DSHA", "HOME_TASK_UNAVAILABLE");
    }
    // finish() would expose the retained browser in a shared legacy stack. Moving
    // the complete task preserves its page and mirrors current Android launcher Back.
    activity.moveTaskToBack(true);
  }

  /** One Main card represents the app; browser/PiP hosts and old Main shells stay retained. */
  static void reconcileRecents(Context context, int preferredMainTask) {
    try {
      ActivityManager manager =
          (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      if (manager == null) return;
      List<ActivityManager.AppTask> appTasks = manager.getAppTasks();
      List<BrowserTaskPolicy.Task> choices = new ArrayList<>();
      for (ActivityManager.AppTask task : appTasks) {
        var info = task.getTaskInfo();
        choices.add(new BrowserTaskPolicy.Task(info.id, mainRoot(context, info.baseActivity)));
      }
      int selected =
          BrowserTaskPolicy.nativeTask(
              choices, BrowserConversationRoute.taskId(), preferredMainTask);
      for (ActivityManager.AppTask task : appTasks) {
        var info = task.getTaskInfo();
        var visibility =
            TaskNavigationPolicy.recents(
                info.id,
                mainRoot(context, info.baseActivity),
                browserRoot(context, info.baseActivity),
                selected);
        if (visibility != TaskNavigationPolicy.Recents.KEEP)
          task.setExcludeFromRecents(visibility == TaskNavigationPolicy.Recents.HIDE);
      }
    } catch (RuntimeException unavailable) {
      android.util.Log.w("DSHA", "TASK_RECENTS_ROUTE: " + unavailable.getClass().getSimpleName());
    }
  }

  private static boolean ownRoot(Context context, android.content.ComponentName root) {
    return root != null && context.getPackageName().equals(root.getPackageName());
  }

  private static boolean mainRoot(Context context, android.content.ComponentName root) {
    return ownRoot(context, root) && MainActivity.class.getName().equals(root.getClassName());
  }

  private static boolean browserRoot(Context context, android.content.ComponentName root) {
    return ownRoot(context, root)
        && (WebPreviewActivity.class.getName().equals(root.getClassName())
            || "com.deepseekharness.app.ui.GeckoPreviewActivity".equals(root.getClassName()));
  }

  static void openMain(Context context) {
    nativeNavigation();
    int browserTask =
        context instanceof PictureInPictureActivity activity
            ? activity.getTaskId()
            : BrowserConversationRoute.taskId();
    Activity preferred = nativeMain.get();
    int preferredTask =
        preferred != null && !preferred.isFinishing() && !preferred.isDestroyed()
            ? preferred.getTaskId()
            : -1;
    try {
      ActivityManager manager =
          (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      List<ActivityManager.AppTask> appTasks = manager == null ? List.of() : manager.getAppTasks();
      List<BrowserTaskPolicy.Task> choices = new ArrayList<>();
      for (ActivityManager.AppTask task : appTasks) {
        var info = task.getTaskInfo();
        choices.add(new BrowserTaskPolicy.Task(info.id, mainRoot(context, info.baseActivity)));
      }
      int selected = BrowserTaskPolicy.nativeTask(choices, browserTask, preferredTask);
      for (ActivityManager.AppTask task : appTasks)
        if (selected >= 0 && task.getTaskInfo().id == selected) {
          task.startActivity(context, homeIntent(context), null);
          reconcileRecents(context, selected);
          return;
        }
    } catch (RuntimeException unavailable) {
      android.util.Log.w("DSHA", "NATIVE_TASK_ROUTE: " + unavailable.getClass().getSimpleName());
    }
    // Let Android reuse the app's native affinity even if task enumeration is denied.
    // MULTIPLE_TASK used to create a fresh Main on every Back when task lookup failed.
    context.startActivity(homeIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  static boolean finishEmptyHost(Activity activity) {
    int members = -1;
    try {
      ActivityManager manager =
          (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
      if (manager != null)
        for (ActivityManager.AppTask task : manager.getAppTasks()) {
          var info = task.getTaskInfo();
          if (info.id == activity.getTaskId()) {
            members = info.numActivities;
            break;
          }
        }
    } catch (RuntimeException unavailable) {
      android.util.Log.w("DSHA", "BROWSER_TASK_MEMBERS: " + unavailable.getClass().getSimpleName());
    }
    if (BrowserTaskPolicy.removeEmptyTask(activity.isTaskRoot(), members))
      activity.finishAndRemoveTask();
    else activity.finish(); // Never remove native pages or another host in a shared legacy task.
    return activity.isFinishing();
  }
}
