package com.deepseekharness.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;
import com.deepseekharness.app.R;
import com.deepseekharness.app.core.PluginRepository;
import com.deepseekharness.app.plugins.PluginManagementEngine;
import com.deepseekharness.app.util.PluginSort;
import com.deepseekharness.app.util.PluginSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 插件市场入口与已装插件管理；页面只投影应用级管理引擎的快照。 */
public class PluginFragment extends Fragment {
  private PluginManagementEngine repository;
  private PluginRepository.State current;
  private View root, header, footer;
  private EditText linkInput, search;
  private TextView linkHint;
  private CheckBox hideBuiltin;
  private boolean market = true;
  private final java.util.Set<String> expandedPlugins = new java.util.HashSet<>();
  private PluginSort.Mode sortOrder = PluginSort.Mode.NAME_ASC;
  private final List<PluginRepository.Item> visibleItems = new ArrayList<>();
  private final PluginListPresenter.Projection<PluginRepository.Item> listProjection =
      new PluginListPresenter.Projection<>(
          item -> item.stableKey,
          item -> item.contentKey,
          item ->
              new PluginListPresenter.Entry<>(
                  item,
                  item.stableKey,
                  item.contentKey,
                  item.name,
                  item.description,
                  item.builtin || item.official || item.runtimeProvided,
                  item.enabled,
                  item.available,
                  item.dynamic,
                  item.updateAvailable));
  private long renderedSelectionVersion = -1;
  private final Adapter adapter = new Adapter();
  private ArrayList<String> pendingExports = new ArrayList<>();
  private android.net.Uri pendingImport;
  private PageState pageState;
  private boolean restoreScroll, createdView;

  /** Activity 范围只保存页面选择；不拥有插件任务或目录。Bundle 负责进程重建。 */
  public static final class PageState extends ViewModel {
    boolean initialized, market = true, hideBuiltin;
    String query = "", link = "";
    PluginSort.Mode sort = PluginSort.Mode.NAME_ASC;
    final java.util.Set<String> expanded = new java.util.HashSet<>();
    PluginListPresenter.Anchor marketAnchor, installedAnchor;
    ArrayList<String> exports = new ArrayList<>();
    String imported;
  }

  // 调试自测只覆写这些内存边界，不连接或修改生产仓储。
  PluginManagementEngine managementEngine() {
    return PluginManagementEngine.get(requireContext());
  }

  LiveData<PluginRepository.State> installedState() {
    return repository.state();
  }

  boolean safeModeActive() {
    return current != null && current.catalog != null && current.catalog.safeMode;
  }

  void syncInstalledState() {
    repository.ensureLoaded();
  }

  private void ensureInstalledState() {
    if (root != null && isResumed() && !market) syncInstalledState();
  }

  private final ActivityResultLauncher<android.content.Intent> importPicker =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (repository == null) repository = PluginManagementEngine.get(requireContext());
            android.content.Intent data = result.getData();
            android.net.Uri uri = data == null ? null : data.getData();
            if (uri == null
                && data != null
                && data.getClipData() != null
                && data.getClipData().getItemCount() > 0)
              uri = data.getClipData().getItemAt(0).getUri();
            if (result.getResultCode() != android.app.Activity.RESULT_OK || uri == null) {
              repository.selectionMessage(
                  com.deepseekharness.app.util.UiText.text("未选择文件。可再次点击导入插件包。"));
              return;
            }
            if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme())) {
              repository.selectionMessage(
                  com.deepseekharness.app.util.UiText.text("文件管理器返回的地址无法读取，请改用系统文件选择器。"));
              return;
            }
            if ("file".equals(uri.getScheme())
                && android.os.Build.VERSION.SDK_INT < 30
                && requireContext()
                        .checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
              pendingImport = uri;
              PluginFragment.this.readPermission.launch(
                  android.Manifest.permission.READ_EXTERNAL_STORAGE);
              return;
            }
            repository.importArchive(uri);
          });
  private final ActivityResultLauncher<String> readPermission =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          allowed -> {
            android.net.Uri selected = pendingImport;
            pendingImport = null;
            if (allowed && selected != null) repository.importArchive(selected);
            else
              repository.selectionMessage(
                  com.deepseekharness.app.util.UiText.text("未获得文件读取权限，请改用系统文件选择器导入。"));
          });
  private final ActivityResultLauncher<String> exportPicker =
      registerForActivityResult(
          new ActivityResultContracts.CreateDocument("application/gzip"),
          uri -> {
            if (uri != null && !pendingExports.isEmpty())
              repository.exportArchives(new ArrayList<>(pendingExports), uri);
            pendingExports.clear();
          });

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
    return inflater.inflate(R.layout.fragment_plugins, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
    root = view;
    RecyclerView list = view.findViewById(R.id.pluginList);
    list.setLayoutManager(new LinearLayoutManager(requireContext()));
    // Header/footer 是 RecyclerView 条目，必须在设置 LayoutManager 后 inflate。
    // RecyclerView.generateLayoutParams() 在父级尚未有 LayoutManager 时会直接抛出
    // InflateException，导致点击“插件”导航后整个 Activity 崩溃。
    header = getLayoutInflater().inflate(R.layout.plugin_list_header, list, false);
    footer = getLayoutInflater().inflate(R.layout.plugin_list_footer, list, false);
    adapter.reset();
    repository = managementEngine();
    pageState =
        new ViewModelProvider(requireActivity())
            .get("plugin-page:" + getClass().getName(), PageState.class);
    if (!pageState.initialized) {
      pageState.sort =
          new com.deepseekharness.app.core.ConfigStore(requireContext()).getPluginSort();
      pageState.market =
          getArguments() == null || !getArguments().getBoolean("show_installed", false);
      restorePageState(saved);
      pageState.initialized = true;
    }
    market = pageState.market;
    if (!createdView
        && saved == null
        && getArguments() != null
        && getArguments().getBoolean("show_installed", false)) market = false;
    createdView = true;
    sortOrder = pageState.sort;
    expandedPlugins.clear();
    expandedPlugins.addAll(pageState.expanded);
    pendingExports = new ArrayList<>(pageState.exports);
    pendingImport = pageState.imported == null ? null : android.net.Uri.parse(pageState.imported);
    restoreScroll = true;
    linkInput = find(R.id.appbar_github_input);
    linkHint = find(R.id.pluginLinkHint);
    search = find(R.id.pluginSearch);
    hideBuiltin = find(R.id.chkHideBuiltin);
    // 这些控件位于可回收 header；页面状态自行保存，避免系统按旧层级覆盖更新的值。
    list.setSaveEnabled(false);
    search.setSaveEnabled(false);
    linkInput.setSaveEnabled(false);
    hideBuiltin.setSaveEnabled(false);
    search.setText(pageState.query);
    linkInput.setText(pageState.link);
    hideBuiltin.setChecked(pageState.hideBuiltin);
    // 单一回收列表负责整页滚动，标题和操作区也是列表条目。
    list.setItemAnimator(null);
    list.setAdapter(adapter);
    list.addOnScrollListener(
        new RecyclerView.OnScrollListener() {
          @Override
          public void onScrolled(@NonNull RecyclerView recycler, int dx, int dy) {
            if (root != null && !restoreScroll) rememberAnchor(captureAnchor());
          }
        });
    find(R.id.btnMarket).setOnClickListener(v -> selectTab(true));
    find(R.id.btnPluginWebsite).setOnClickListener(v -> openPluginWebsite());
    find(R.id.btnInstalled).setOnClickListener(v -> selectTab(false));
    find(R.id.btnRefresh).setOnClickListener(v -> repository.refresh());
    find(R.id.btnPluginUpdates).setOnClickListener(v -> repository.checkUpdates(null));
    find(R.id.btnPluginDownloadSource).setOnClickListener(v -> showDownloadSources());
    updateDownloadSourceLabel();
    ((TextView) find(R.id.pluginDownloadHint))
        .setText(
            com.deepseekharness.app.util.UiText.text("自动选择可用的 npm 源；镜像失败时回退官方源。GitHub 使用官方直连。"));
    find(R.id.btnCancelPluginTask).setOnClickListener(v -> repository.cancelTask());
    find(R.id.btnPluginRestore)
        .setOnClickListener(
            v ->
                new com.deepseekharness.app.ui.DshaDialogBuilder(requireContext())
                    .setTitle(com.deepseekharness.app.util.UiText.text("恢复第三方插件？"))
                    .setMessage(
                        com.deepseekharness.app.util.UiText.text(
                            "恢复安全启动前已启用的插件；之后手动禁用的插件保持禁用。恢复后重启 Web 生效。"))
                    .setNegativeButton(com.deepseekharness.app.util.UiText.text("取消"), null)
                    .setPositiveButton(
                        com.deepseekharness.app.util.UiText.text("恢复"),
                        (d, which) -> repository.safeMode(false, null))
                    .show());
    find(R.id.btnPluginInstall).setOnClickListener(v -> installLink());
    find(R.id.btnPluginPaste).setOnClickListener(v -> pasteLink());
    find(R.id.btnImport).setOnClickListener(v -> chooseImport(false));
    find(R.id.btnExport).setOnClickListener(v -> chooseExport());
    find(R.id.btnSort).setOnClickListener(v -> showSortOptions());
    hideBuiltin.setOnCheckedChangeListener((v, checked) -> render());
    search.addTextChangedListener(watcher(this::render));
    search.setOnEditorActionListener(
        (v, action, event) -> {
          if (action != EditorInfo.IME_ACTION_DONE
              && (event == null
                  || event.getKeyCode() != KeyEvent.KEYCODE_ENTER
                  || event.getAction() != KeyEvent.ACTION_UP)) return false;
          search.clearFocus();
          android.view.inputmethod.InputMethodManager keyboard =
              (android.view.inputmethod.InputMethodManager)
                  requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
          if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
          return true;
        });
    linkInput.addTextChangedListener(watcher(this::recognizeLink));
    linkInput.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == EditorInfo.IME_ACTION_GO
              || action == EditorInfo.IME_ACTION_DONE
              || (event != null
                  && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                  && event.getAction() == KeyEvent.ACTION_UP)) {
            installLink();
            return true;
          }
          return false;
        });
    TextView status = find(R.id.statusText);
    status.setOnClickListener(v -> showPluginStatus());
    installedState()
        .observe(
            getViewLifecycleOwner(),
            state -> {
              current = state;
              render();
            });
    recognizeLink();
  }

  @Override
  public void onResume() {
    super.onResume();
    ensureInstalledState();
  }

  @Override
  public void onPause() {
    capturePageState();
    super.onPause();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle state) {
    capturePageState();
    super.onSaveInstanceState(state);
    if (pageState == null) return;
    state.putBoolean("market", pageState.market);
    state.putString("sortOrder", pageState.sort.name());
    state.putString("query", pageState.query);
    state.putString("link", pageState.link);
    state.putBoolean("hideBuiltin", pageState.hideBuiltin);
    state.putStringArrayList("expandedPlugins", new ArrayList<>(pageState.expanded));
    state.putStringArrayList("pendingExports", new ArrayList<>(pageState.exports));
    state.putString("pendingImport", pageState.imported);
    saveAnchor(state, "marketAnchor", pageState.marketAnchor);
    saveAnchor(state, "installedAnchor", pageState.installedAnchor);
  }

  private void restorePageState(Bundle state) {
    if (state == null) return;
    pageState.market = state.getBoolean("market", pageState.market);
    if (state.containsKey("sortOrder"))
      pageState.sort = PluginSort.Mode.parse(state.getString("sortOrder"));
    else if (state.getBoolean("enabledFirst")) pageState.sort = PluginSort.Mode.ENABLED_FIRST;
    pageState.query = state.getString("query", "");
    pageState.link = state.getString("link", "");
    pageState.hideBuiltin = state.getBoolean("hideBuiltin");
    ArrayList<String> expanded = state.getStringArrayList("expandedPlugins");
    if (expanded != null) pageState.expanded.addAll(expanded);
    ArrayList<String> exports = state.getStringArrayList("pendingExports");
    if (exports != null) pageState.exports = exports;
    pageState.imported = state.getString("pendingImport");
    pageState.marketAnchor = restoreAnchor(state, "marketAnchor");
    pageState.installedAnchor = restoreAnchor(state, "installedAnchor");
  }

  private static void saveAnchor(Bundle bundle, String prefix, PluginListPresenter.Anchor anchor) {
    if (anchor == null) return;
    bundle.putString(prefix + "Key", anchor.key);
    bundle.putInt(prefix + "Position", anchor.position);
    bundle.putInt(prefix + "Offset", anchor.offset);
  }

  private static PluginListPresenter.Anchor restoreAnchor(Bundle bundle, String prefix) {
    if (!bundle.containsKey(prefix + "Position")) return null;
    return new PluginListPresenter.Anchor(
        bundle.getString(prefix + "Key", ""),
        bundle.getInt(prefix + "Position"),
        bundle.getInt(prefix + "Offset"));
  }

  private void capturePageState() {
    if (pageState == null || root == null) return;
    pageState.market = market;
    pageState.sort = sortOrder;
    pageState.query = search.getText().toString();
    pageState.link = linkInput.getText().toString();
    pageState.hideBuiltin = hideBuiltin.isChecked();
    pageState.expanded.clear();
    pageState.expanded.addAll(expandedPlugins);
    pageState.exports = new ArrayList<>(pendingExports);
    pageState.imported = pendingImport == null ? null : pendingImport.toString();
    if (!restoreScroll) rememberAnchor(captureAnchor());
  }

  private void rememberAnchor(PluginListPresenter.Anchor anchor) {
    if (anchor == null || pageState == null) return;
    if (market) pageState.marketAnchor = anchor;
    else pageState.installedAnchor = anchor;
  }

  private PluginListPresenter.Anchor captureAnchor() {
    RecyclerView list = find(R.id.pluginList);
    if (list.hasPendingAdapterUpdates()) return null;
    LinearLayoutManager layout = (LinearLayoutManager) list.getLayoutManager();
    int position = layout.findFirstVisibleItemPosition();
    if (position == RecyclerView.NO_POSITION || position >= adapter.getItemCount()) return null;
    View first = layout.findViewByPosition(position);
    return new PluginListPresenter.Anchor(
        adapter.keyAt(position),
        position,
        first == null ? 0 : layout.getDecoratedTop(first) - list.getPaddingTop());
  }

  private void restoreAnchor(PluginListPresenter.Anchor anchor) {
    if (anchor == null) return;
    rememberAnchor(anchor);
    RecyclerView list = find(R.id.pluginList);
    ((LinearLayoutManager) list.getLayoutManager())
        .scrollToPositionWithOffset(
            PluginListPresenter.anchorPosition(anchor, adapter.rows), anchor.offset);
  }

  @Override
  public void onDestroyView() {
    capturePageState();
    ((RecyclerView) find(R.id.pluginList)).setAdapter(null);
    adapter.reset();
    root = null;
    header = null;
    footer = null;
    linkInput = null;
    search = null;
    linkHint = null;
    hideBuiltin = null;
    super.onDestroyView();
  }

  <T extends View> T find(int id) {
    T result = root.findViewById(id);
    if (result == null && header != null) result = header.findViewById(id);
    if (result == null && footer != null) result = footer.findViewById(id);
    return result;
  }

  private static TextWatcher watcher(Runnable callback) {
    return new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        callback.run();
      }

      @Override
      public void afterTextChanged(Editable e) {}
    };
  }

  private void updateDownloadSourceLabel() {
    com.deepseekharness.app.util.PluginDownloadSource source =
        new com.deepseekharness.app.core.ConfigStore(requireContext()).getPluginDownloadSource();
    String[] labels = downloadSourceLabels();
    ((TextView) find(R.id.btnPluginDownloadSource))
        .setText(com.deepseekharness.app.util.UiText.format("下载源：%s", labels[source.ordinal()]));
  }

  private String[] downloadSourceLabels() {
    return new String[] {
      com.deepseekharness.app.util.UiText.text("自动选择（推荐）"),
      com.deepseekharness.app.util.UiText.text("npm 官方源"),
      com.deepseekharness.app.util.UiText.text("npmmirror 国内镜像")
    };
  }

  private void showDownloadSources() {
    if (repository.isBusy()) return;
    com.deepseekharness.app.core.ConfigStore config =
        new com.deepseekharness.app.core.ConfigStore(requireContext());
    new DshaDialogBuilder(requireContext())
        .setTitle(com.deepseekharness.app.util.UiText.text("插件下载源"))
        .setSingleChoiceItems(
            downloadSourceLabels(),
            config.getPluginDownloadSource().ordinal(),
            (dialog, which) -> {
              if (!repository.isBusy()) {
                config.setPluginDownloadSource(
                    com.deepseekharness.app.util.PluginDownloadSource.values()[which]);
                updateDownloadSourceLabel();
              }
              dialog.dismiss();
            })
        .setNegativeButton(com.deepseekharness.app.util.UiText.text("取消"), null)
        .show();
  }

  private void showPluginStatus() {
    if (current == null || current.message.isEmpty()) return;
    var dialog =
        new com.deepseekharness.app.ui.DshaDialogBuilder(requireContext())
            .setTitle(com.deepseekharness.app.util.UiText.text("插件操作结果"))
            .setMessage(com.deepseekharness.app.util.UiStateText.render(current.message));
    if (!repository.isBusy() && repository.preview().getValue() != null) {
      dialog
          .setPositiveButton(
              com.deepseekharness.app.util.UiText.choose("重试安装", "Retry installation"),
              (d, w) -> repository.confirmPreview())
          .setNeutralButton(
              com.deepseekharness.app.util.UiText.choose("清理临时包", "Remove temporary package"),
              (d, w) -> repository.discardPreview())
          .setNegativeButton(com.deepseekharness.app.util.UiText.text("关闭"), null);
    } else dialog.setPositiveButton(com.deepseekharness.app.util.UiText.text("关闭"), null);
    dialog.show();
  }

  private void recognizeLink() {
    if (root == null) return;
    String input = linkInput.getText().toString();
    boolean valid = false;
    try {
      PluginSource source = PluginSource.parse(input);
      linkHint.setText(com.deepseekharness.app.util.UiText.format("已识别：%s", source.description()));
      valid = true;
    } catch (IllegalArgumentException error) {
      linkHint.setText(
          input.trim().isEmpty()
              ? com.deepseekharness.app.util.UiText.text("支持仓库、分支/子目录、Release 下载和压缩包直链")
              : error.getMessage());
    }
    find(R.id.btnPluginInstall).setEnabled(valid);
  }

  private void selectTab(boolean showMarket) {
    if (market == showMarket) return;
    capturePageState();
    market = showMarket;
    restoreScroll = true;
    linkInput.clearFocus();
    search.clearFocus();
    android.view.inputmethod.InputMethodManager keyboard =
        (android.view.inputmethod.InputMethodManager)
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (keyboard != null) keyboard.hideSoftInputFromWindow(root.getWindowToken(), 0);
    render();
    ensureInstalledState();
  }

  private void openPluginWebsite() {
    try {
      startActivity(new android.content.Intent(requireContext(), CommunityActivity.class));
    } catch (RuntimeException error) {
      toast(
          com.deepseekharness.app.util.UiText.format(
              "无法打开浏览器，请在浏览器中访问 %s", com.deepseekharness.app.util.Constants.SITE_URL));
    }
  }

  private void installLink() {
    try {
      PluginSource source = PluginSource.parse(linkInput.getText().toString());
      android.view.inputmethod.InputMethodManager keyboard =
          (android.view.inputmethod.InputMethodManager)
              requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
      if (keyboard != null) keyboard.hideSoftInputFromWindow(linkInput.getWindowToken(), 0);
      linkInput.clearFocus();
      repository.install(source);
    } catch (IllegalArgumentException error) {
      toast(error.getMessage());
    }
  }

  private void chooseImport(boolean alternative) {
    View focus = requireActivity().getCurrentFocus();
    if (focus != null) {
      android.view.inputmethod.InputMethodManager keyboard =
          (android.view.inputmethod.InputMethodManager)
              requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (keyboard != null) keyboard.hideSoftInputFromWindow(focus.getWindowToken(), 0);
      focus.clearFocus();
    }
    repository.selectionMessage(com.deepseekharness.app.util.UiText.text("请选择 ZIP / TAR.GZ 插件包。"));
    try {
      importPicker.launch(PluginFilePicker.intent(requireContext(), alternative));
    } catch (android.content.ActivityNotFoundException error) {
      if (!alternative) {
        chooseImport(true);
        return;
      }
      repository.selectionMessage(
          com.deepseekharness.app.util.UiText.text("未找到可用的文件选择器，请启用系统「文件」应用后重试。"));
      toast(com.deepseekharness.app.util.UiText.text("没有可用的文件选择器"));
    } catch (RuntimeException error) {
      repository.selectionMessage(
          com.deepseekharness.app.util.UiText.format(
              "无法打开文件选择器，请检查系统文件应用：%s", error.getClass().getSimpleName()));
    }
  }

  private void pasteLink() {
    ClipboardManager clipboard =
        (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0) {
      toast(com.deepseekharness.app.util.UiText.text("剪贴板没有链接"));
      return;
    }
    CharSequence text = clip.getItemAt(0).coerceToText(requireContext());
    if (text != null) linkInput.setText(text);
  }

  private void render() {
    if (root == null || current == null) return;
    boolean catalogKnown = current.catalog != null && current.catalog.known;
    boolean catalogLoading = current.catalog != null && current.catalog.loading;
    boolean catalogStale = current.catalog != null && current.catalog.stale;
    String catalogError = current.catalog == null ? "" : current.catalog.error;
    find(R.id.pluginMarketCard).setVisibility(market ? View.VISIBLE : View.GONE);
    find(R.id.pluginLocalTitle).setVisibility(market ? View.VISIBLE : View.GONE);
    find(R.id.pluginLocalCard).setVisibility(market ? View.VISIBLE : View.GONE);
    find(R.id.pluginWebsiteSection).setVisibility(market ? View.VISIBLE : View.GONE);
    find(R.id.pluginLinkSection).setVisibility(market ? View.VISIBLE : View.GONE);
    find(R.id.btnPluginRestore).setVisibility(safeModeActive() ? View.VISIBLE : View.GONE);
    find(R.id.installedControls).setVisibility(market ? View.GONE : View.VISIBLE);
    find(R.id.btnMarket).setSelected(market);
    find(R.id.btnInstalled).setSelected(!market);
    ((TextView) find(R.id.btnMarket))
        .setTextColor(
            requireContext()
                .getColor(market ? R.color.shell_accent : R.color.shell_text_secondary));
    ((TextView) find(R.id.btnInstalled))
        .setTextColor(
            requireContext()
                .getColor(market ? R.color.shell_text_secondary : R.color.shell_accent));
    find(R.id.pluginBusy).setVisibility(current.busy ? View.VISIBLE : View.GONE);
    find(R.id.statusText).setVisibility(current.message.isEmpty() ? View.GONE : View.VISIBLE);
    android.widget.ProgressBar progress = find(R.id.pluginBusy);
    progress.setIndeterminate(current.percent < 0);
    if (current.percent >= 0) progress.setProgress(current.percent);
    find(R.id.btnCancelPluginTask).setVisibility(current.busy ? View.VISIBLE : View.GONE);
    find(R.id.btnCancelPluginTask).setEnabled(current.cancellable);
    ((TextView) find(R.id.statusText))
        .setText(com.deepseekharness.app.util.UiStateText.render(current.message));
    // busy 表示队列在工作，不阻止继续提交其他插件意图。
    find(R.id.btnPluginDownloadSource).setEnabled(!current.busy);
    TextView sort = find(R.id.btnSort);
    sort.setText(sortLabels()[sortOrder.ordinal()]);
    sort.setContentDescription(getString(R.string.plugin_sort_title) + " · " + sort.getText());
    List<PluginListPresenter.Operation> operations = new ArrayList<>();
    for (var operation : current.operations)
      operations.add(
          new PluginListPresenter.Operation(
              operation.operationId,
              operation.targetKey,
              operation.desired,
              String.valueOf(operation.phase),
              operation.message,
              operation.percent,
              operation.pending()));
    List<PluginListPresenter.Row<PluginRepository.Item>> rows =
        listProjection.project(
            current.items,
            operations,
            search.getText().toString(),
            hideBuiltin.isChecked(),
            sortOrder,
            expandedPlugins);
    if (renderedSelectionVersion != listProjection.selectionVersion()) {
      visibleItems.clear();
      for (var row : rows) visibleItems.add(row.entry.value);
      renderedSelectionVersion = listProjection.selectionVersion();
    }
    List<PluginListPresenter.Row<PluginRepository.Item>> shown =
        market ? java.util.Collections.emptyList() : rows;
    PluginListPresenter.Anchor before =
        !restoreScroll && !market && shown != adapter.rows ? captureAnchor() : null;
    boolean structural = adapter.submit(shown);
    if (restoreScroll) {
      PluginListPresenter.Anchor anchor =
          market ? pageState.marketAnchor : pageState.installedAnchor;
      if (anchor == null
          || market
          || catalogKnown
          || PluginListPresenter.HEADER_KEY.equals(anchor.key)) {
        restoreAnchor(
            anchor == null
                ? new PluginListPresenter.Anchor(PluginListPresenter.HEADER_KEY, 0, 0)
                : anchor);
        restoreScroll = false;
      }
    } else if (structural) restoreAnchor(before);
    PluginListPresenter.EmptyState emptyState =
        PluginListPresenter.emptyState(
            catalogKnown, catalogLoading, catalogError, current.items.size(), visibleItems.size());
    String count =
        catalogKnown
            ? getResources()
                .getQuantityString(R.plurals.plugin_count, visibleItems.size(), visibleItems.size())
            : com.deepseekharness.app.util.UiText.choose(
                catalogLoading ? "正在检测插件…" : "插件列表尚未确认",
                catalogLoading ? "Checking plugins…" : "Plugin list unconfirmed");
    if (catalogKnown && !catalogError.isEmpty())
      count +=
          com.deepseekharness.app.util.UiText.choose(
              " · 刷新失败，保留上次列表", " · Refresh failed; showing the last verified list");
    else if (catalogKnown && catalogStale)
      count += com.deepseekharness.app.util.UiText.choose(" · 等待同步", " · Awaiting refresh");
    ((TextView) find(R.id.pluginCount)).setText(count);
    TextView empty = find(R.id.pluginEmpty);
    empty.setVisibility(!market && visibleItems.isEmpty() ? View.VISIBLE : View.GONE);
    empty.setText(
        switch (emptyState) {
          case LOADING -> com.deepseekharness.app.util.UiText.text("正在读取插件…");
          case INITIAL ->
              com.deepseekharness.app.util.UiText.choose(
                  "插件列表尚未确认，请刷新检测", "The plugin list is unconfirmed. Refresh to check.");
          case FAILED ->
              com.deepseekharness.app.util.UiText.choose(
                  "读取插件失败，请查看状态并刷新重试",
                  "Could not read plugins. Check the status and refresh to retry.");
          case EMPTY ->
              com.deepseekharness.app.util.UiText.choose("尚未安装插件", "No plugins installed");
          default -> com.deepseekharness.app.util.UiText.text("没有符合条件的插件");
        });
    recognizeLink();
  }

  private String[] sortLabels() {
    return new String[] {
      getString(R.string.plugin_sort_az),
      getString(R.string.plugin_sort_za),
      getString(R.string.plugin_sort_enabled),
      getString(R.string.plugin_sort_updates)
    };
  }

  private void showSortOptions() {
    new DshaDialogBuilder(requireContext())
        .setTitle(R.string.plugin_sort_title)
        .setSingleChoiceItems(
            sortLabels(),
            sortOrder.ordinal(),
            (dialog, index) -> {
              sortOrder = PluginSort.Mode.values()[index];
              new com.deepseekharness.app.core.ConfigStore(requireContext())
                  .setPluginSort(sortOrder);
              dialog.dismiss();
              render();
            })
        .setNegativeButton(com.deepseekharness.app.util.UiText.text("取消"), null)
        .show();
  }

  private void chooseExport() {
    if (current == null) return;
    List<String> names = new ArrayList<>();
    for (PluginRepository.Item item : current.items) if (item.exportable) names.add(item.name);
    if (names.isEmpty()) {
      toast(com.deepseekharness.app.util.UiText.text("没有可导出的插件"));
      return;
    }
    boolean[] checked = new boolean[names.size()];
    AlertDialog dialog =
        new com.deepseekharness.app.ui.DshaDialogBuilder(requireContext())
            .setTitle(com.deepseekharness.app.util.UiText.text("选择要导出的插件"))
            .setMultiChoiceItems(
                names.toArray(new String[0]),
                checked,
                (d, which, value) -> {
                  checked[which] = value;
                  boolean any = false;
                  for (boolean selected : checked) any |= selected;
                  ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(any);
                })
            .setNegativeButton(com.deepseekharness.app.util.UiText.text("取消"), null)
            .setPositiveButton(
                com.deepseekharness.app.util.UiText.text("选择保存位置"),
                (d, which) -> {
                  ArrayList<String> selected = new ArrayList<>();
                  for (int i = 0; i < checked.length; i++)
                    if (checked[i]) selected.add(names.get(i));
                  beginExport(selected);
                })
            .create();
    dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false));
    dialog.show();
  }

  private void beginExport(ArrayList<String> names) {
    if (names.isEmpty()) return;
    pendingExports = names;
    String name =
        names.size() == 1 ? names.get(0).replaceAll("[^A-Za-z0-9._-]", "_") : "DSHA-plugins";
    String stamp =
        new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new java.util.Date());
    try {
      exportPicker.launch(name + "-" + stamp + ".tar.gz");
    } catch (Exception error) {
      pendingExports.clear();
      toast(com.deepseekharness.app.util.UiText.text("无法打开保存位置选择器"));
    }
  }

  private void toggle(PluginRepository.Item item, boolean enabled) {
    if (item.official && !enabled) {
      new com.deepseekharness.app.ui.DshaDialogBuilder(requireContext(), true)
          .setTitle(com.deepseekharness.app.util.UiText.text("禁用官方核心？"))
          .setMessage(
              item.name + com.deepseekharness.app.util.UiText.text(" 是 Web 运行所需的核心，禁用后页面可能无法启动。"))
          .setPositiveButton(
              com.deepseekharness.app.util.UiText.text("禁用"),
              (d, which) -> repository.setEnabled(item, false))
          .setNegativeButton(com.deepseekharness.app.util.UiText.text("取消"), null)
          .setOnDismissListener(d -> adapter.rebindTarget(item.stableKey))
          .show();
    } else repository.setEnabled(item, enabled);
  }

  private void itemActions(PluginRepository.Item item) {
    List<PluginAction> actions = new ArrayList<>();
    actions.add(PluginAction.DETAIL);
    actions.add(PluginAction.COPY_NAME);
    if (!item.source.isEmpty()) actions.add(PluginAction.COPY_SOURCE);
    if (item.exportable) actions.add(PluginAction.EXPORT);
    if (item.updatable) actions.add(PluginAction.CHECK);
    if (item.updateAvailable) actions.add(PluginAction.UPDATE);
    if (!item.rollbackVersion.isEmpty()) actions.add(PluginAction.ROLLBACK);
    if (item.deletable) actions.add(PluginAction.DELETE);
    new com.deepseekharness.app.ui.DshaDialogBuilder(requireContext())
        .setTitle(item.name)
        .setItems(
            actions.stream().map(action -> actionLabel(action, item)).toArray(String[]::new),
            (d, which) -> {
              PluginAction action = actions.get(which);
              if (action == PluginAction.DETAIL) {
                new com.deepseekharness.app.ui.DshaDialogBuilder(requireContext())
                    .setTitle(item.name)
                    .setMessage(
                        com.deepseekharness.app.util.UiText.format(
                            "%s\n\n版本：%s%s%s%s%s%s",
                            item.builtin
                                ? com.deepseekharness.app.util.UiStateText.render(item.description)
                                : item.description,
                            item.version,
                            (item.location.isEmpty()
                                ? ""
                                : com.deepseekharness.app.util.UiText.format(
                                    "\n位置：%s", item.location)),
                            (item.source.isEmpty()
                                ? ""
                                : com.deepseekharness.app.util.UiText.format(
                                    "\n来源：%s", item.source)),
                            (item.latestVersion.isEmpty()
                                ? ""
                                : com.deepseekharness.app.util.UiText.format(
                                    "\n上次检查版本：%s", item.latestVersion)),
                            (item.updateMessage.isEmpty()
                                ? ""
                                : "\n"
                                    + com.deepseekharness.app.util.UiStateText.render(
                                        item.updateMessage)),
                            (item.rollbackVersion.isEmpty()
                                ? ""
                                : com.deepseekharness.app.util.UiText.format(
                                    "\n可回退：%s", item.rollbackVersion))))
                    .setPositiveButton(com.deepseekharness.app.util.UiText.text("关闭"), null)
                    .setNeutralButton(
                        com.deepseekharness.app.util.UiText.choose("能力声明", "Capabilities"),
                        (dialog, button) ->
                            repository.inspectCapabilities(
                                item,
                                details -> {
                                  if (!isAdded()) return;
                                  new DshaDialogBuilder(requireContext())
                                      .setTitle(item.name)
                                      .setMessage(details)
                                      .setPositiveButton(
                                          com.deepseekharness.app.util.UiText.text("关闭"), null)
                                      .show();
                                }))
                    .show();
              } else if (action == PluginAction.CHECK) {
                repository.checkUpdates(item);
              } else if (action == PluginAction.UPDATE) {
                repository.prepareUpdate(item);
              } else if (action == PluginAction.ROLLBACK) {
                new com.deepseekharness.app.ui.DshaDialogBuilder(requireContext(), true)
                    .setTitle(com.deepseekharness.app.util.UiText.text("回退插件？"))
                    .setMessage(
                        item.name
                            + com.deepseekharness.app.util.UiText.text("：")
                            + item.version
                            + " → "
                            + item.rollbackVersion
                            + com.deepseekharness.app.util.UiText.text(
                                "\n只恢复插件文件，当前启用状态和对话数据保留；重启 Web 生效。"))
                    .setNegativeButton(com.deepseekharness.app.util.UiText.text("取消"), null)
                    .setPositiveButton(
                        com.deepseekharness.app.util.UiText.text("回退"),
                        (confirm, button) -> repository.rollback(item))
                    .show();
              } else if (action == PluginAction.EXPORT) {
                ArrayList<String> names = new ArrayList<>();
                names.add(item.name);
                beginExport(names);
              } else if (action == PluginAction.DELETE) {
                confirmDelete(item);
              } else {
                ClipboardManager clipboard =
                    (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null)
                  clipboard.setPrimaryClip(
                      ClipData.newPlainText(
                          com.deepseekharness.app.util.UiText.text("插件"),
                          action == PluginAction.COPY_NAME ? item.name : item.source));
                toast(com.deepseekharness.app.util.UiText.text("已复制"));
              }
            })
        .show();
  }

  private enum PluginAction {
    DETAIL,
    COPY_NAME,
    COPY_SOURCE,
    EXPORT,
    CHECK,
    UPDATE,
    ROLLBACK,
    DELETE
  }

  private String actionLabel(PluginAction action, PluginRepository.Item item) {
    return switch (action) {
      case DETAIL -> com.deepseekharness.app.util.UiText.text("查看详情");
      case COPY_NAME -> com.deepseekharness.app.util.UiText.text("复制插件名称");
      case COPY_SOURCE -> com.deepseekharness.app.util.UiText.text("复制来源链接");
      case EXPORT -> com.deepseekharness.app.util.UiText.text("导出插件包");
      case CHECK -> com.deepseekharness.app.util.UiText.text("检查插件更新");
      case UPDATE -> com.deepseekharness.app.util.UiText.format("更新至 %s", item.latestVersion);
      case ROLLBACK -> com.deepseekharness.app.util.UiText.format("回退至 %s", item.rollbackVersion);
      case DELETE -> com.deepseekharness.app.util.UiText.text("删除插件");
    };
  }

  private void confirmDelete(PluginRepository.Item item) {
    if (!item.deletable) return;
    new DshaDialogBuilder(requireContext(), true)
        .setTitle(com.deepseekharness.app.util.UiText.choose("删除插件？", "Delete plugin?"))
        .setMessage(
            com.deepseekharness.app.util.UiText.format(
                "%s 将从加载路径移除；原件保留在数据页的保留区。对话、其他插件和外部源码目录保留。", item.name))
        .setNegativeButton(com.deepseekharness.app.util.UiText.choose("取消", "Cancel"), null)
        .setPositiveButton(
            com.deepseekharness.app.util.UiText.choose("删除", "Delete"),
            (dialog, which) -> {
              repository.delete(item);
            })
        .show();
  }

  private void toast(String message) {
    if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
  }

  static String runtimeText(PluginRepository.Item item) {
    String state =
        switch (item.runtimeState) {
          case "loaded", "running", "active" ->
              com.deepseekharness.app.util.UiText.choose("已加载", "loaded");
          case "failed" -> com.deepseekharness.app.util.UiText.choose("加载失败", "load failed");
          case "pending", "waiting" ->
              com.deepseekharness.app.util.UiText.choose("等待服务", "waiting for services");
          case "loading" -> com.deepseekharness.app.util.UiText.choose("正在加载", "loading");
          case "stopped", "unloaded", "disabled", "inactive" ->
              com.deepseekharness.app.util.UiText.choose("未加载", "not loaded");
          default -> "";
        };
    if (state.isEmpty()) {
      String unknown =
          com.deepseekharness.app.util.UiText.choose(
              "当前 Web：加载状态未确认", "Current Web: load unconfirmed");
      String history =
          switch (item.loadState) {
            case "loaded" -> com.deepseekharness.app.util.UiText.choose("已加载", "loaded");
            case "failed" -> com.deepseekharness.app.util.UiText.choose("加载失败", "load failed");
            case "queued" -> com.deepseekharness.app.util.UiText.choose("等待生效", "queued");
            case "attempted" ->
                com.deepseekharness.app.util.UiText.choose("已尝试加载", "load attempted");
            default -> "";
          };
      return history.isEmpty()
          ? unknown
          : String.format(
              java.util.Locale.ROOT,
              com.deepseekharness.app.util.UiText.choose(
                  "%s\n上次激活记录：%s", "%s\nLast activation record: %s"),
              unknown,
              history);
    }
    if (item.runtimeStale)
      return String.format(
          java.util.Locale.ROOT,
          com.deepseekharness.app.util.UiText.choose(
              "当前 Web：待核验；上次状态：%s", "Current Web: awaiting verification; last state: %s"),
          state);
    return String.format(
        java.util.Locale.ROOT,
        com.deepseekharness.app.util.UiText.choose("当前 Web：%s", "Current Web: %s"),
        state);
  }

  private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
    private List<PluginListPresenter.Row<PluginRepository.Item>> rows =
        java.util.Collections.emptyList();
    private final java.util.Map<String, Long> ids = new java.util.HashMap<>();
    private long nextId = 1;

    Adapter() {
      setHasStableIds(true);
    }

    void reset() {
      rows = java.util.Collections.emptyList();
    }

    String keyAt(int position) {
      if (position == 0) return PluginListPresenter.HEADER_KEY;
      if (position == getItemCount() - 1) return PluginListPresenter.FOOTER_KEY;
      return rows.get(position - PluginListPresenter.HEADER_COUNT).entry.stableKey;
    }

    @Override
    public long getItemId(int position) {
      if (position == 0) return -2;
      if (position == getItemCount() - 1) return -3;
      return ids.computeIfAbsent(keyAt(position), ignored -> nextId++);
    }

    boolean submit(List<PluginListPresenter.Row<PluginRepository.Item>> next) {
      if (rows == next) return false;
      List<PluginListPresenter.Row<PluginRepository.Item>> previous = rows;
      boolean structural = previous.size() != next.size();
      if (!structural)
        for (int i = 0; i < next.size(); i++)
          if (!PluginListPresenter.sameItem(previous.get(i), next.get(i))) {
            structural = true;
            break;
          }
      DiffUtil.DiffResult diff =
          DiffUtil.calculateDiff(
              new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                  return previous.size();
                }

                @Override
                public int getNewListSize() {
                  return next.size();
                }

                @Override
                public boolean areItemsTheSame(int oldPosition, int newPosition) {
                  return PluginListPresenter.sameItem(
                      previous.get(oldPosition), next.get(newPosition));
                }

                @Override
                public boolean areContentsTheSame(int oldPosition, int newPosition) {
                  return PluginListPresenter.changes(
                          previous.get(oldPosition), next.get(newPosition))
                      == 0;
                }

                @Override
                public Object getChangePayload(int oldPosition, int newPosition) {
                  return PluginListPresenter.changes(
                      previous.get(oldPosition), next.get(newPosition));
                }
              });
      rows = next;
      // DiffUtil 只比较插件行；四种通知均显式跳过 header，footer 由插删自然移动。
      diff.dispatchUpdatesTo(
          new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int count) {
              notifyItemRangeInserted(PluginListPresenter.adapterPosition(position), count);
            }

            @Override
            public void onRemoved(int position, int count) {
              notifyItemRangeRemoved(PluginListPresenter.adapterPosition(position), count);
            }

            @Override
            public void onMoved(int from, int to) {
              notifyItemMoved(
                  PluginListPresenter.adapterPosition(from),
                  PluginListPresenter.adapterPosition(to));
            }

            @Override
            public void onChanged(int position, int count, Object payload) {
              notifyItemRangeChanged(PluginListPresenter.adapterPosition(position), count, payload);
            }
          });
      return structural;
    }

    void rebindTarget(String stableKey) {
      for (int i = 0; i < rows.size(); i++)
        if (rows.get(i).entry.stableKey.equals(stableKey)) {
          notifyItemChanged(PluginListPresenter.adapterPosition(i), PluginListPresenter.OPERATION);
          return;
        }
    }

    class Holder extends RecyclerView.ViewHolder {
      final TextView name, state, description;
      final android.widget.CompoundButton toggle;

      Holder(View view) {
        super(view);
        name = view.findViewById(R.id.pluginName);
        state = view.findViewById(R.id.pluginStatus);
        description = view.findViewById(R.id.pluginDesc);
        toggle = view.findViewById(R.id.pluginSwitch);
      }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
      if (type != 1) {
        View content = type == 0 ? header : footer;
        if (content.getParent() instanceof ViewGroup)
          ((ViewGroup) content.getParent()).removeView(content);
        return new Holder(content);
      }
      return new Holder(
          LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plugin, parent, false));
    }

    @Override
    public void onBindViewHolder(
        @NonNull Holder holder, int position, @NonNull List<Object> payloads) {
      if (getItemViewType(position) != 1) return;
      int changed = 0;
      for (Object payload : payloads) {
        if (!(payload instanceof Integer)) {
          changed |= PluginListPresenter.CONTENT;
          break;
        }
        changed |= (Integer) payload;
      }
      if (payloads.isEmpty() || (changed & PluginListPresenter.CONTENT) != 0) {
        onBindViewHolder(holder, position);
        return;
      }
      var row = rows.get(position - PluginListPresenter.HEADER_COUNT);
      if ((changed & PluginListPresenter.OPERATION) != 0) bindStatus(holder, row);
      if ((changed & PluginListPresenter.EXPANSION) != 0) bindExpansion(holder, row);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
      if (getItemViewType(position) != 1) return;
      var row = rows.get(position - PluginListPresenter.HEADER_COUNT);
      PluginRepository.Item item = row.entry.value;
      holder.name.setText(item.name);
      bindStatus(holder, row);
      holder.description.setText(
          item.description.isEmpty()
              ? (item.official
                  ? com.deepseekharness.app.util.UiText.text("官方核心")
                  : item.runtimeProvided
                      ? com.deepseekharness.app.util.UiText.choose("随 DSH 安装提供", "Provided by DSH")
                      : item.builtin
                          ? com.deepseekharness.app.util.UiText.text("DSHA 内置插件")
                          : item.runtimeLocated
                              ? com.deepseekharness.app.util.UiText.choose(
                                  "DSH 安装目录中的用户/未验证插件",
                                  "User or unverified plugin in the DSH installation")
                              : com.deepseekharness.app.util.UiText.text("第三方插件"))
              : item.builtin
                  ? com.deepseekharness.app.util.UiStateText.render(item.description)
                  : com.deepseekharness.app.util.UiText.raw(item.description));
      holder.itemView.findViewById(R.id.pluginActions).setOnClickListener(v -> itemActions(item));
      holder
          .itemView
          .findViewById(R.id.pluginActions)
          .setContentDescription(com.deepseekharness.app.util.UiText.format("更多操作：%s", item.name));
      String location =
          item.runtimeProvided
              ? com.deepseekharness.app.util.UiText.choose("随 DSH 安装提供", "Provided by DSH")
              : item.location.isEmpty()
                  ? com.deepseekharness.app.util.UiText.choose("未提供路径", "Path not provided")
                  : item.location;
      String source =
          item.source.isEmpty()
              ? (item.builtin
                  ? com.deepseekharness.app.util.UiText.choose("随包内置", "Bundled")
                  : item.runtimeProvided
                      ? com.deepseekharness.app.util.UiText.choose(
                          "已核对当前 APK 受管内容", "Verified against the current APK")
                      : item.official
                          ? com.deepseekharness.app.util.UiText.choose(
                              "DSH 官方组件", "Official DSH component")
                          : com.deepseekharness.app.util.UiText.choose(
                              "来源未记录", "Source not recorded"))
              : item.source;
      ((TextView) holder.itemView.findViewById(R.id.pluginDetails))
          .setText(
              com.deepseekharness.app.util.UiText.format(
                  "%s\n\n版本：%s\n位置：%s\n来源：%s",
                  item.builtin
                      ? com.deepseekharness.app.util.UiStateText.render(item.description)
                      : item.description,
                  item.version,
                  location,
                  source));
      bindExpansion(holder, row);
      holder
          .itemView
          .findViewById(R.id.pluginExpand)
          .setOnClickListener(
              v -> {
                if (!expandedPlugins.add(item.stableKey)) expandedPlugins.remove(item.stableKey);
                render();
              });
      android.widget.Button delete = holder.itemView.findViewById(R.id.pluginDelete);
      delete.setVisibility(item.deletable ? View.VISIBLE : View.GONE);
      delete.setText(com.deepseekharness.app.util.UiText.choose("删除插件", "Delete plugin"));
      delete.setOnClickListener(v -> confirmDelete(item));
      holder.itemView.setOnLongClickListener(
          v -> {
            itemActions(item);
            return true;
          });
    }

    private void bindExpansion(Holder holder, PluginListPresenter.Row<PluginRepository.Item> row) {
      holder
          .itemView
          .findViewById(R.id.pluginDetails)
          .setVisibility(row.expanded ? View.VISIBLE : View.GONE);
      ((android.widget.Button) holder.itemView.findViewById(R.id.pluginExpand))
          .setText(
              row.expanded
                  ? com.deepseekharness.app.util.UiText.choose("收起详情", "Collapse details")
                  : com.deepseekharness.app.util.UiText.choose("展开插件详情", "Plugin details"));
    }

    private void bindStatus(Holder holder, PluginListPresenter.Row<PluginRepository.Item> row) {
      PluginRepository.Item item = row.entry.value;
      String configured =
          item.dynamic
              ? com.deepseekharness.app.util.UiText.choose("临时插件", "Temporary plugin")
              : !item.available
                  ? com.deepseekharness.app.util.UiText.text("实体缺失，请重新导入")
                  : item.enabled
                      ? com.deepseekharness.app.util.UiText.choose(
                          "配置：已启用", "Configuration: enabled")
                      : item.detected
                          ? com.deepseekharness.app.util.UiText.text("已检测，可开启以加入 Web")
                          : com.deepseekharness.app.util.UiText.choose(
                              "配置：已禁用", "Configuration: disabled");
      String runtime = runtimeText(item);
      String text =
          configured + (item.version.isEmpty() ? "" : " · " + item.version) + "\n" + runtime;
      if (item.restartRequired)
        text +=
            com.deepseekharness.app.util.UiText.choose("\n重启 Web 后生效", "\nRestart Web to apply");
      if (item.updateAvailable)
        text += com.deepseekharness.app.util.UiText.format("\n可更新：%s", item.latestVersion);
      if (row.operation != null) {
        if (row.pending()) {
          text +=
              row.operation.desired == null
                  ? com.deepseekharness.app.util.UiText.choose("\n操作待完成", "\nOperation pending")
                  : row.operation.desired
                      ? com.deepseekharness.app.util.UiText.choose("\n等待启用", "\nEnable pending")
                      : com.deepseekharness.app.util.UiText.choose("\n等待禁用", "\nDisable pending");
          String phase =
              switch (row.operation.phase) {
                case "RUNNING" -> com.deepseekharness.app.util.UiText.choose("正在执行", "Running");
                case "COMMITTING" ->
                    com.deepseekharness.app.util.UiText.choose("正在确认提交", "Confirming commit");
                default -> com.deepseekharness.app.util.UiText.choose("排队中", "Queued");
              };
          text += " · " + phase;
          if (row.operation.percent >= 0) text += " · " + row.operation.percent + "%";
        }
        if (!row.operation.message.isEmpty())
          text += "\n" + com.deepseekharness.app.util.UiStateText.render(row.operation.message);
      }
      holder.state.setText(text);
      holder.state.setTextColor(
          requireContext()
              .getColor(
                  !item.available || row.pending()
                      ? R.color.warn
                      : item.enabled ? R.color.shell_accent : R.color.shell_text_muted));
      holder.itemView.findViewById(R.id.pluginDelete).setEnabled(!row.pending());
      holder.toggle.setVisibility(item.dynamic ? View.GONE : View.VISIBLE);
      holder.toggle.setOnCheckedChangeListener(null);
      holder.toggle.setChecked(row.checked());
      holder.toggle.setContentDescription(
          com.deepseekharness.app.util.UiText.format(row.checked() ? "禁用 %s" : "启用 %s", item.name));
      holder.toggle.jumpDrawablesToCurrentState();
      holder.toggle.setEnabled(row.toggleEnabled());
      holder.toggle.setOnCheckedChangeListener((v, checked) -> toggle(item, checked));
    }

    @Override
    public int getItemViewType(int position) {
      return position == 0 ? 0 : position == getItemCount() - 1 ? 2 : 1;
    }

    @Override
    public int getItemCount() {
      return 2 + rows.size();
    }
  }
}
