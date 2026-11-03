package com.deepseekharness.app.ui;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.deepseekharness.app.R;
import com.deepseekharness.app.transfer.DownloadEngine;
import com.deepseekharness.app.transfer.DownloadTask;
import com.deepseekharness.app.transfer.PendingFileAction;
import com.deepseekharness.app.util.SensitiveData;
import com.deepseekharness.app.util.UiText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;

/** User-controlled transfer history. Removing a row never deletes its completed destination. */
public final class FileTransfersActivity extends AppCompatActivity {
  private static final String PENDING_SAVE = "transfer_save_task";
  private static final String AUTO_TASK = "transfer_explicit_task",
      AUTO_ACTION = "transfer_explicit_action";
  private static final String AUTO_OWNER = "transfer_explicit_document";
  private static final String STATUS_TASK = "transfer_status_task";
  private static final String STATUS_DESTINATION = "transfer_status_destination";
  private DownloadEngine engine;
  private TransferAdapter adapter;
  private TextView summary, notice;
  private String pendingSaveId;
  private PendingFileAction pendingAction;
  private String actionOwner;
  private String statusTaskId;
  private String statusDestination;
  private DownloadTask.Status displayedTaskStatus;
  private boolean resumed;

  public static Intent intent(Context context) {
    return new Intent(context, FileTransfersActivity.class);
  }

  /** Called only after an admitted, current top-level page explicitly chose this destination. */
  static Intent destinationIntent(
      Context context, String taskId, String destination, String owner) {
    return intent(context)
        .putExtra(AUTO_TASK, taskId)
        .putExtra(AUTO_ACTION, destination)
        .putExtra(AUTO_OWNER, owner);
  }

  public static void open(Context context) {
    Intent target = intent(context);
    if (!(context instanceof android.app.Activity)) target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(target);
  }

  private static String t(String zh, String en) {
    return UiText.choose(zh, en);
  }

  private final ActivityResultLauncher<Intent> savePicker =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            String id = pendingSaveId;
            pendingSaveId = null;
            Intent data = result.getData();
            Uri uri = result.getResultCode() == RESULT_OK && data != null ? data.getData() : null;
            if (uri == null) {
              showNotice(
                  t(
                      "已取消另存为，已校验文件仍保留在下载记录中，可稍后继续保存。",
                      "Save as was cancelled. The verified file remains in history to save later."));
              return;
            }
            DownloadTask current = id == null ? null : find(id);
            if (current == null || current.status != DownloadTask.Status.WAITING_DESTINATION) {
              showNotice(
                  t(
                      "原下载记录已不可用，请重新选择记录。",
                      "The original transfer is unavailable. Select the record again."));
              return;
            }
            if (!"content".equals(uri.getScheme())
                || uri.getAuthority() == null
                || uri.getAuthority().isEmpty()) {
              showNotice(
                  t(
                      "文件提供方没有返回可用的文档位置。",
                      "The provider did not return a usable document location."));
              return;
            }
            // Only persist flags actually granted by a provider that supports persistence.
            int flags =
                data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if ((data.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
                && flags != 0) {
              try {
                getContentResolver().takePersistableUriPermission(uri, flags);
              } catch (SecurityException | IllegalArgumentException unsupported) {
                // A transient grant still permits the foreground save; the engine reports failures.
              }
            }
            perform(
                () -> {
                  engine.provideSaveTarget(id, uri);
                  showNotice(t("正在保存文件…", "Saving file…"));
                });
          });

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    if (saved != null) pendingSaveId = saved.getString(PENDING_SAVE);
    pendingAction =
        new PendingFileAction(
            saved == null ? getIntent().getStringExtra(AUTO_TASK) : saved.getString(AUTO_TASK),
            saved == null ? getIntent().getStringExtra(AUTO_ACTION) : saved.getString(AUTO_ACTION));
    actionOwner =
        saved == null ? getIntent().getStringExtra(AUTO_OWNER) : saved.getString(AUTO_OWNER);
    statusTaskId =
        saved == null ? getIntent().getStringExtra(AUTO_TASK) : saved.getString(STATUS_TASK);
    statusDestination =
        saved == null ? pendingAction.action() : saved.getString(STATUS_DESTINATION);
    engine = DownloadEngine.get(this);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(getColor(R.color.surface));
    setContentView(root);
    String title =
        pendingAction.action().equals("share")
            ? t("分享文件", "Share file")
            : pendingAction.action().equals("saveas")
                ? t("另存为", "Save as")
                : t("下载记录", "Downloads");
    UiNavigation.addHeader(this, root, title, this::finish);
    summary = text("", 13, R.color.text_secondary);
    summary.setPadding(dp(18), dp(6), dp(18), dp(8));
    root.addView(summary);
    notice = text("", 13, R.color.text);
    notice.setPadding(dp(18), dp(6), dp(18), dp(8));
    notice.setTextIsSelectable(true);
    notice.setVisibility(View.GONE);
    root.addView(notice);
    ListView list = new ListView(this);
    list.setDivider(null);
    list.setPadding(dp(12), 0, dp(12), dp(12));
    list.setClipToPadding(false);
    adapter = new TransferAdapter();
    list.setAdapter(adapter);
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView explanation =
        text(
            t(
                "同名文件自动使用新名称。移除记录不会删除已完成文件；分享选择器打开不代表对方已收到。",
                "Duplicate names use a new name. Removing a record keeps completed files. Opening the share picker does not confirm delivery."),
            12,
            R.color.text_secondary);
    explanation.setPadding(dp(18), dp(8), dp(18), dp(12));
    root.addView(explanation);
    if (!pendingAction.id().isEmpty() && pendingAction.action().equals("share"))
      showNotice(
          t(
              "正在准备分享，校验完成后将打开系统分享选择器。",
              "Preparing to share. The system share picker will open after verification."));
    else if (!pendingAction.id().isEmpty() && pendingAction.action().equals("saveas"))
      showNotice(t("正在准备另存为，完成后请选择保存位置。", "Preparing Save as. Choose a destination when ready."));
    engine.state().observe(this, this::render);
  }

  @Override
  protected void onPostResume() {
    super.onPostResume();
    resumed = true;
    launchExplicitDestination(engine.state().getValue());
  }

  @Override
  protected void onPause() {
    // A backgrounded request cannot surprise the user with a later picker.
    if (resumed && !isChangingConfigurations()) retireExplicitDestination();
    resumed = false;
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (!isChangingConfigurations()) retireExplicitDestination();
    super.onDestroy();
  }

  private void retireExplicitDestination() {
    boolean pending = pendingAction != null && !pendingAction.id().isEmpty();
    if (pendingAction != null) pendingAction.retire();
    WebFileActions.retireDestination(actionOwner);
    if (pending && notice != null && !isFinishing())
      showNotice(
          t(
              "自动打开已取消，文件仍保留在记录中，可手动继续分享或保存。",
              "Automatic opening was cancelled. The file remains in history to share or save manually."));
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    if (pendingSaveId != null) state.putString(PENDING_SAVE, pendingSaveId);
    state.putString(AUTO_TASK, pendingAction.id());
    state.putString(AUTO_ACTION, pendingAction.action());
    state.putString(AUTO_OWNER, actionOwner);
    state.putString(STATUS_TASK, statusTaskId);
    state.putString(STATUS_DESTINATION, statusDestination);
  }

  private void render(DownloadEngine.State state) {
    if (state == null) return;
    adapter.replace(state.tasks);
    summary.setText(
        state.tasks.isEmpty()
            ? t(
                "暂无下载记录。网页文件菜单中的下载会显示在这里。",
                "No downloads yet. Downloads from the web file menu appear here.")
            : state.tasks.size()
                + t(" 条记录", " records")
                + (state.busy ? t(" · 正在传输", " · Transfer in progress") : ""));
    for (DownloadTask task : state.tasks) {
      if (statusTaskId == null
          || !statusTaskId.equals(task.id)
          || !task.destination.equals(statusDestination)) continue;
      if (task.status != displayedTaskStatus) {
        displayedTaskStatus = task.status;
        String message =
            requestStatusNotice(
                statusTaskId,
                statusDestination,
                task.id,
                task.destination,
                task.status,
                displayName(task));
        if (!message.isEmpty()) showNotice(message);
      }
      break;
    }
    launchExplicitDestination(state);
  }

  /** A consumed picker-launch ticket does not retire its exact request's status text. */
  static String requestStatusNotice(
      String requestId,
      String destination,
      String taskId,
      String taskDestination,
      DownloadTask.Status status,
      String displayName) {
    if (requestId == null
        || requestId.isEmpty()
        || !requestId.equals(taskId)
        || !("saveas".equals(destination) || "share".equals(destination))
        || !destination.equals(taskDestination)
        || status == null) return "";
    return switch (status) {
      case COMPLETE ->
          destination.equals("saveas")
              ? UiText.format("已保存文件：%s", displayName)
              : t(
                  "分享文件已就绪；打开分享选择器不代表对方已收到。",
                  "The file is ready to share. Opening the share picker does not confirm delivery.");
      case CANCELLED -> t("文件操作已取消。", "File operation cancelled.");
      case FAILED ->
          t(
              "文件操作失败，请在当前记录中查看原因并重试。",
              "File operation failed. Check this record for the reason and retry.");
      case PAUSED -> t("文件操作已暂停，可在当前记录中继续。", "File operation paused. Resume it from this record.");
      default -> "";
    };
  }

  private void launchExplicitDestination(DownloadEngine.State state) {
    if (state == null || pendingAction == null || isFinishing()) return;
    if (!WebFileActions.destinationCurrent(actionOwner)) {
      retireExplicitDestination();
      return;
    }
    for (DownloadTask task : state.tasks) {
      boolean pending = !pendingAction.id().isEmpty();
      String action = pendingAction.consume(task.id, task.destination, task.status, resumed);
      if (pendingAction.id().isEmpty()) WebFileActions.retireDestination(actionOwner);
      if (action.equals("share")) share(task);
      else if (action.equals("saveas")) chooseDestination(task);
      else if (pending && pendingAction.id().isEmpty())
        showNotice(
            t(
                "文件准备已停止，请在记录中查看原因并手动继续。",
                "File preparation stopped. Check the record and continue manually."));
    }
  }

  private DownloadTask find(String id) {
    DownloadEngine.State state = engine.state().getValue();
    if (state != null) for (DownloadTask task : state.tasks) if (task.id.equals(id)) return task;
    return null;
  }

  private void perform(Action action) {
    try {
      action.run();
    } catch (Exception failed) {
      showNotice(errorText(failed.getMessage()));
    }
  }

  private interface Action {
    void run() throws Exception;
  }

  private void showNotice(String message) {
    notice.setText(message);
    notice.setVisibility(View.VISIBLE);
    notice.announceForAccessibility(message);
  }

  private void chooseDestination(DownloadTask task) {
    if (pendingSaveId != null) {
      showNotice(t("请先完成当前保存位置选择。", "Complete the current destination picker first."));
      return;
    }
    pendingSaveId = task.id;
    statusTaskId = task.id;
    statusDestination = task.destination;
    displayedTaskStatus = null;
    Intent picker =
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mime(task, null))
            .putExtra(Intent.EXTRA_TITLE, displayName(task))
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    try {
      savePicker.launch(picker);
      showNotice(t("请选择保存位置。", "Choose a save destination."));
    } catch (RuntimeException unavailable) {
      pendingSaveId = null;
      showNotice(
          t("系统文件保存界面不可用，请稍后重试。", "The system save picker is unavailable. Try again later."));
    }
  }

  private Uri completedUri(DownloadTask task) {
    if (task.status != DownloadTask.Status.COMPLETE || task.uri == null || task.uri.isEmpty())
      throw new IllegalStateException("FILE_NOT_COMPLETE");
    Uri uri = Uri.parse(task.uri);
    if (!"content".equals(uri.getScheme())) throw new IllegalStateException("FILE_URI_UNAVAILABLE");
    return uri;
  }

  private String mime(DownloadTask task, Uri uri) {
    String actual = null;
    if (uri != null) {
      try {
        actual = getContentResolver().getType(uri);
      } catch (RuntimeException unavailable) {
        // The recorded final MIME is retained when a provider is temporarily unavailable.
      }
    }
    if (actual == null || actual.isEmpty()) actual = task.mime;
    if (actual == null || actual.isEmpty()) actual = task.artifact.mime;
    return actual == null || !actual.contains("/") ? "application/octet-stream" : actual;
  }

  private void openFile(DownloadTask task) {
    perform(
        () -> {
          Uri uri = completedUri(task);
          Intent view =
              new Intent(Intent.ACTION_VIEW)
                  .setDataAndType(uri, mime(task, uri))
                  .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          view.setClipData(ClipData.newRawUri(displayName(task), uri));
          try {
            startActivity(view);
          } catch (android.content.ActivityNotFoundException noViewer) {
            showNotice(
                t(
                    "没有可打开此类文件的应用，可使用分享选择其他应用。",
                    "No app can open this file type. Try sharing it to another app."));
          }
        });
  }

  private void share(DownloadTask task) {
    perform(
        () -> {
          Uri uri = completedUri(task);
          String actualMime = mime(task, uri);
          startActivity(
              NativeFileShare.chooser(uri, displayName(task), actualMime, t("分享文件", "Share file")));
          showNotice(
              t(
                  "已打开分享选择器，请在接收应用中完成分享。",
                  "The share picker is open. Complete sharing in the receiving app."));
        });
  }

  private void location(DownloadTask task) {
    TextView detail = text("", 14, R.color.text);
    detail.setPadding(dp(24), dp(8), dp(24), dp(8));
    detail.setTextIsSelectable(true);
    detail.setText(
        displayName(task)
            + "\n"
            + locationText(task)
            + (task.uri == null || task.uri.isEmpty() ? "" : "\n" + task.uri)
            + "\n\n"
            + t(
                "系统不保证能按此文档精确定位文件夹。下方可打开系统下载列表；图库及其他文件提供方保存的文件可能不在该列表中。",
                "The system does not guarantee exact folder navigation for this document. You can open the system Downloads list below; gallery files and files saved by other providers may not appear there."));
    new MaterialAlertDialogBuilder(this)
        .setTitle(t("保存位置", "Saved location"))
        .setView(detail)
        .setPositiveButton(
            t("系统下载列表", "System Downloads"),
            (dialog, which) ->
                perform(
                    () -> {
                      try {
                        startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
                      } catch (android.content.ActivityNotFoundException unavailable) {
                        showNotice(
                            t(
                                "此设备没有提供系统下载列表，请按上方真实位置在文件管理器中查找。",
                                "This device has no system Downloads list. Use the saved location above in your file manager."));
                      }
                    }))
        .setNegativeButton(t("关闭", "Close"), null)
        .show();
  }

  private void remove(DownloadTask task) {
    new MaterialAlertDialogBuilder(this)
        .setTitle(t("移除下载记录？", "Remove this record?"))
        .setMessage(
            t(
                "只移除此条下载记录，已完成文件仍保留在原保存位置。",
                "Only this download record will be removed. Completed files stay in their saved location."))
        .setPositiveButton(
            t("移除记录", "Remove record"), (dialog, which) -> perform(() -> engine.remove(task.id)))
        .setNegativeButton(t("取消", "Cancel"), null)
        .show();
  }

  private String locationText(DownloadTask task) {
    if ("所选文档".equals(task.location)) return t("所选文档", "Selected document");
    if ("应用分享缓存".equals(task.location)) return t("应用分享缓存", "App share storage");
    if (task.location != null && !task.location.isEmpty()) return task.location;
    return switch (task.destination) {
      case "downloads" -> "Download/DSHA";
      case "gallery" -> "Pictures/DSHA";
      case "share" -> t("应用分享缓存", "App share storage");
      default -> t("等待选择文档位置", "Waiting for a document destination");
    };
  }

  private static String displayName(DownloadTask task) {
    return task.displayName == null || task.displayName.isEmpty()
        ? task.artifact.name
        : task.displayName;
  }

  private String size(long bytes) {
    return bytes < 0 ? t("大小未知", "Unknown size") : Formatter.formatFileSize(this, bytes);
  }

  private String statusText(DownloadTask.Status status) {
    return switch (status) {
      case QUEUED -> t("排队中", "Queued");
      case DOWNLOADING -> t("正在下载", "Downloading");
      case VERIFYING -> t("正在校验", "Verifying");
      case PUBLISHING -> t("正在保存", "Saving");
      case WAITING_DESTINATION -> t("已校验，等待选择保存位置", "Verified; choose a destination");
      case PAUSED -> t("已暂停", "Paused");
      case FAILED -> t("失败，可重试", "Failed; retry available");
      case COMPLETE -> t("已完成", "Complete");
      case CANCELLED -> t("已取消", "Cancelled");
    };
  }

  private String errorText(String raw) {
    String value = raw == null ? "" : raw;
    String lower = value.toLowerCase(java.util.Locale.ROOT);
    if (lower.contains("task_cancelled")) return cancelledText();
    if (lower.contains("enospc")
        || lower.contains("no space")
        || lower.contains("no_space")
        || lower.contains("insufficient")
        || lower.contains("space_low"))
      return t(
          "存储空间不足。请释放空间后重试；已完成文件不会被覆盖。",
          "Not enough storage. Free some space and retry. Completed files will not be overwritten.");
    if (lower.contains("permission")
        || lower.contains("eacces")
        || lower.contains("securityexception"))
      return t(
          "无法访问保存位置。请检查文件权限，或重新选择保存位置。",
          "Cannot access the destination. Check file permissions or choose another destination.");
    if (lower.contains("source_changed")
        || lower.contains("artifact_changed")
        || lower.contains("http_409")
        || lower.contains("integrity")
        || lower.contains("sha256"))
      return t(
          "文件身份或完整性校验未通过，未发布文件。请重新获取下载。",
          "File identity or integrity verification failed. No file was published. Request a new download.");
    if (lower.contains("runtime_not_ready")
        || lower.contains("runtime_changed")
        || lower.contains("auth_required"))
      return t(
          "当前网页运行环境已停止、切换或未就绪。请确认网页正常后重试，下载记录仍保留。",
          "The web runtime stopped, changed, or is not ready. Check that the web app is ready and retry. The download record is retained.");
    if (lower.contains("http_401") || lower.contains("http_403"))
      return t(
          "文件访问未获授权。请返回当前网页重新获取下载。",
          "File access was not authorized. Request the download again from the current web page.");
    if (lower.contains("http_404")
        || lower.contains("http_410")
        || lower.contains("local_copy_missing"))
      return t(
          "源文件已不可用。请返回网页重新获取文件。",
          "The source file is no longer available. Request the file again from the web app.");
    if (lower.contains("transfer_interrupted")
        || lower.contains("transfer_failed")
        || lower.contains("http_5"))
      return t(
          "传输中断或文件服务暂不可用。已下载进度保留，请检查运行环境后重试。",
          "The transfer was interrupted or the file service is unavailable. Downloaded progress is retained. Check the runtime and retry.");
    if (lower.contains("length_invalid")
        || lower.contains("range_invalid")
        || lower.contains("partial_invalid"))
      return t(
          "下载长度或续传响应校验失败，未发布文件。请重新获取下载。",
          "Download size or resume response verification failed. No file was published. Request a new download.");
    if (lower.contains("limit_5gb"))
      return t("文件超过当前单个文件 5 GB 的上限。", "The file exceeds the current 5 GB per-file limit.");
    if (lower.contains("task_limit"))
      return t(
          "下载记录已达上限，请移除不再需要的记录后重试。已完成文件会保留。",
          "Download history is full. Remove unneeded records and retry. Completed files will be kept.");
    if (lower.contains("not_real_image"))
      return t(
          "文件未通过真实图片校验，未写入相册。可从网页选择普通下载。",
          "The file did not pass image verification and was not saved to the gallery. Use a regular download from the web app.");
    if (lower.contains("task_busy"))
      return t(
          "下载器正在完成本条记录的收尾，请稍后再移除。",
          "The downloader is finishing this record. Try removing it again shortly.");
    if (lower.contains("save_target")
        || lower.contains("destination_create")
        || lower.contains("publication_failed"))
      return t(
          "无法完成文件保存。请确认保存位置仍可用后重试；另存为可重新选择位置。",
          "The file could not be saved. Check that the destination is available and retry. Save As lets you choose a destination again.");
    if (lower.contains("task_not_found")
        || lower.contains("not_complete")
        || lower.contains("uri_unavailable"))
      return t(
          "下载记录或完成文件已不可用，请重新获取文件。",
          "The download record or completed file is unavailable. Request the file again.");
    String redacted = SensitiveData.redact(value.length() > 350 ? value.substring(0, 350) : value);
    return t("操作未完成", "Operation did not complete") + (redacted.isEmpty() ? "。" : "：" + redacted);
  }

  private String cancelledText() {
    return t(
        "已取消。需要再次下载请从网页重新发起。",
        "Cancelled. To download again, start a new download from the web app.");
  }

  private TextView text(String value, int sp, int color) {
    TextView text = new TextView(this);
    text.setText(value);
    text.setTextSize(sp);
    text.setTextColor(getColor(color));
    return text;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private final class TransferAdapter extends BaseAdapter {
    private final List<DownloadTask> tasks = new ArrayList<>();

    void replace(List<DownloadTask> rows) {
      tasks.clear();
      tasks.addAll(rows);
      notifyDataSetChanged();
    }

    @Override
    public int getCount() {
      return tasks.size();
    }

    @Override
    public DownloadTask getItem(int position) {
      return tasks.get(position);
    }

    @Override
    public long getItemId(int position) {
      return tasks.get(position).id.hashCode();
    }

    @Override
    public View getView(int position, View convert, ViewGroup parent) {
      Holder holder;
      if (convert == null) {
        holder = new Holder();
        convert = holder.root;
        convert.setTag(holder);
      } else holder = (Holder) convert.getTag();
      holder.bind(getItem(position));
      return convert;
    }
  }

  private final class Holder {
    final LinearLayout root = new LinearLayout(FileTransfersActivity.this);
    final LinearLayout card = new LinearLayout(FileTransfersActivity.this);
    final TextView name = text("", 16, R.color.text);
    final TextView progressText = text("", 13, R.color.text_secondary);
    final TextView destination = text("", 12, R.color.text_secondary);
    final TextView error = text("", 13, R.color.text);
    final ProgressBar progress =
        new ProgressBar(
            FileTransfersActivity.this, null, android.R.attr.progressBarStyleHorizontal);
    final LinearLayout actions = new LinearLayout(FileTransfersActivity.this);
    String actionKey;

    Holder() {
      root.setOrientation(LinearLayout.VERTICAL);
      root.setPadding(0, dp(5), 0, dp(5));
      card.setOrientation(LinearLayout.VERTICAL);
      card.setPadding(dp(14), dp(12), dp(14), dp(12));
      card.setBackgroundResource(R.drawable.bg_card);
      root.addView(card);
      name.setTypeface(null, android.graphics.Typeface.BOLD);
      name.setTextIsSelectable(true);
      card.addView(name);
      progressText.setPadding(0, dp(6), 0, dp(4));
      card.addView(progressText);
      progress.setMax(1000);
      card.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
      destination.setPadding(0, dp(6), 0, 0);
      destination.setTextIsSelectable(true);
      card.addView(destination);
      error.setPadding(0, dp(5), 0, 0);
      error.setTextIsSelectable(true);
      card.addView(error);
      actions.setOrientation(LinearLayout.VERTICAL);
      card.addView(actions);
    }

    void bind(DownloadTask task) {
      name.setText(displayName(task));
      progressText.setText(
          statusText(task.status)
              + " · "
              + size(task.downloaded)
              + " / "
              + size(task.total)
              + (task.status == DownloadTask.Status.DOWNLOADING && task.speedBytesPerSecond > 0
                  ? " · " + size(task.speedBytesPerSecond) + "/s"
                  : ""));
      boolean transferring =
          task.status == DownloadTask.Status.DOWNLOADING
              || task.status == DownloadTask.Status.QUEUED;
      progress.setIndeterminate(task.total <= 0 && transferring);
      progress.setProgress(
          task.total > 0 ? (int) Math.min(1000, task.downloaded / (double) task.total * 1000) : 0);
      progress.setVisibility(
          task.status == DownloadTask.Status.COMPLETE
                  || task.status == DownloadTask.Status.CANCELLED
              ? View.GONE
              : View.VISIBLE);
      destination.setText(t("保存位置：", "Destination: ") + locationText(task));
      error.setVisibility(
          task.status == DownloadTask.Status.CANCELLED
                  || task.message != null && !task.message.isEmpty()
              ? View.VISIBLE
              : View.GONE);
      if (task.status == DownloadTask.Status.CANCELLED) error.setText(cancelledText());
      else if (task.message != null && !task.message.isEmpty())
        error.setText(
            task.status == DownloadTask.Status.FAILED
                ? errorText(task.message)
                : SensitiveData.redact(task.message));
      String key = task.id + ":" + task.status + ":" + task.uri;
      if (key.equals(actionKey)) return;
      actionKey = key;
      actions.removeAllViews();
      switch (task.status) {
        case QUEUED, DOWNLOADING -> {
          buttons(
              t("暂停", "Pause"),
              () -> perform(() -> engine.pause(task.id)),
              t("取消", "Cancel"),
              () -> perform(() -> engine.cancel(task.id)));
        }
        case VERIFYING, PUBLISHING ->
            buttons(t("取消", "Cancel"), () -> perform(() -> engine.cancel(task.id)), null, null);
        case PAUSED ->
            buttons(
                t("继续", "Resume"),
                () -> perform(() -> engine.resume(task.id)),
                t("取消", "Cancel"),
                () -> perform(() -> engine.cancel(task.id)));
        case WAITING_DESTINATION ->
            buttons(
                t("选择保存位置", "Choose destination"),
                () -> chooseDestination(task),
                t("取消", "Cancel"),
                () -> perform(() -> engine.cancel(task.id)));
        case FAILED ->
            buttons(
                t("重试", "Retry"),
                () -> perform(() -> engine.resume(task.id)),
                t("移除记录", "Remove record"),
                () -> remove(task));
        case CANCELLED -> buttons(t("移除记录", "Remove record"), () -> remove(task), null, null);
        case COMPLETE -> {
          buttons(t("打开", "Open"), () -> openFile(task), t("分享", "Share"), () -> share(task));
          buttons(
              t("查看位置", "View location"),
              () -> location(task),
              t("移除记录", "Remove record"),
              () -> remove(task));
        }
      }
    }

    void buttons(String first, Runnable action, String second, Runnable secondAction) {
      LinearLayout row = new LinearLayout(FileTransfersActivity.this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      actions.addView(row);
      addButton(row, first, action);
      if (second != null) addButton(row, second, secondAction);
    }

    void addButton(LinearLayout row, String label, Runnable action) {
      Button button = new androidx.appcompat.widget.AppCompatButton(FileTransfersActivity.this);
      button.setText(label);
      button.setTextSize(13);
      button.setAllCaps(false);
      button.setMinHeight(dp(48));
      button.setBackgroundResource(R.drawable.bg_btn);
      button.setTextColor(getColorStateList(R.color.button_text));
      button.setOnClickListener(v -> action.run());
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
      params.setMargins(dp(2), dp(6), dp(2), 0);
      row.addView(button, params);
    }
  }
}
