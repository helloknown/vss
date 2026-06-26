package com.intellij.vssSupport.checkouts;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssTruncatedFileNameUtil;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.ignore.VssIgnoreService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service(Service.Level.PROJECT)
public final class VssMyCheckoutsService {
  private static final Logger LOG = Logger.getInstance(VssMyCheckoutsService.class);

  private final Project project;
  private volatile List<VssCheckoutEntry> entries = List.of();
  private volatile MyCheckoutsUserFilter userFilter = MyCheckoutsUserFilter.currentUser("");
  private volatile @Nullable VirtualFile focusFolder;
  private volatile boolean replaceOnNextResult;
  private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
  private final AtomicBoolean refreshPending = new AtomicBoolean(false);
  private final AtomicReference<ProgressIndicator> activeScan = new AtomicReference<>();

  private volatile @Nullable Runnable beforeSearchRunnable;

  public VssMyCheckoutsService(@NotNull Project project) {
    this.project = project;
    resetUserFilterToConfigured();
  }

  public static VssMyCheckoutsService getInstance(@NotNull Project project) {
    return project.getService(VssMyCheckoutsService.class);
  }

  @NotNull
  public List<VssCheckoutEntry> getEntries() {
    return entries;
  }

  @Nullable
  public VirtualFile getFocusFolder() {
    return focusFolder;
  }

  @NotNull
  public MyCheckoutsUserFilter getUserFilter() {
    return userFilter;
  }

  public void setUserFilter(@NotNull MyCheckoutsUserFilter filter) {
    userFilter = filter;
  }

  public void resetUserFilterToConfigured() {
    userFilter = MyCheckoutsUserFilter.currentUser(VssConfiguration.getInstance(project).USER_NAME);
  }

  public void addListener(@NotNull Runnable listener) {
    listeners.add(listener);
  }

  public void removeListener(@NotNull Runnable listener) {
    listeners.remove(listener);
  }

  public void scheduleQuickRefresh() {
    scheduleRefresh(false);
  }

  public void scheduleRefresh() {
    scheduleQuickRefresh();
  }

  private void scheduleRefresh(boolean fullScan) {
    if (project.isDisposed()) {
      return;
    }
    if (!refreshPending.compareAndSet(false, true)) {
      return;
    }
    AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
      refreshPending.set(false);
      if (project.isDisposed()) {
        return;
      }
      DumbService.getInstance(project).runWhenSmart(() -> refreshInBackgroundIfIdle(
        fullScan ? MyCheckoutsScanner.ScanMode.FULL_TREE : MyCheckoutsScanner.ScanMode.QUICK, null, false));
    }, 300, java.util.concurrent.TimeUnit.MILLISECONDS);
  }

  public void setBeforeSearchRunnable(@Nullable Runnable runnable) {
    beforeSearchRunnable = runnable;
  }

  private void runBeforeSearch() {
    Runnable runnable = beforeSearchRunnable;
    if (runnable != null) {
      runnable.run();
    }
  }

  public void refreshOnUserRequest(boolean fullScan) {
    runBeforeSearch();
    cancelActiveScan();
    focusFolder = null;
    replaceOnNextResult = false;
    startBackgroundScan(fullScan ? MyCheckoutsScanner.ScanMode.FULL_TREE : MyCheckoutsScanner.ScanMode.QUICK, null, false);
  }

  public void refreshInFolder(@NotNull VirtualFile folder) {
    if (project.isDisposed() || !folder.isDirectory()) {
      return;
    }
    runBeforeSearch();
    cancelActiveScan();
    focusFolder = folder;
    replaceOnNextResult = true;
    startBackgroundScan(MyCheckoutsScanner.ScanMode.FOLDER_DIR, folder, true);
  }

  public void cancelActiveScan() {
    ProgressIndicator indicator = activeScan.getAndSet(null);
    if (indicator != null) {
      indicator.cancel();
    }
  }

  public void removeCheckoutEntries(@NotNull Collection<String> localPaths) {
    if (localPaths.isEmpty() || project.isDisposed()) {
      return;
    }
    Set<String> keys = localPaths.stream()
      .map(path -> VssUtil.getCanonicalLocalPath(path).toLowerCase())
      .collect(Collectors.toSet());
    List<VssCheckoutEntry> updated = entries.stream()
      .filter(entry -> !keys.contains(entry.localPath().toLowerCase()))
      .toList();
    if (updated.size() == entries.size()) {
      return;
    }
    entries = updated;
    notifyListeners();
  }

  private void refreshInBackgroundIfIdle(@NotNull MyCheckoutsScanner.ScanMode mode,
                                         @Nullable VirtualFile scopeFolder,
                                         boolean replaceResults) {
    if (DumbService.getInstance(project).isDumb()) {
      return;
    }
    startBackgroundScan(mode, scopeFolder, replaceResults);
  }

  private void startBackgroundScan(@NotNull MyCheckoutsScanner.ScanMode mode,
                                   @Nullable VirtualFile scopeFolder,
                                   boolean replaceResults) {
    if (project.isDisposed()) {
      return;
    }
    String title = mode == MyCheckoutsScanner.ScanMode.FOLDER_DIR && scopeFolder != null
                     ? VssBundle.message("message.my.checkouts.querying.folder", scopeFolder.getPresentableName())
                     : VssBundle.message("message.my.checkouts.querying.status");

    ProgressManager.getInstance().run(
      new Task.Backgroundable(project, title, true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          activeScan.set(indicator);
          try {
            List<VssCheckoutEntry> scanned = MyCheckoutsScanner.scan(project, indicator, mode, scopeFolder);
            ApplicationManager.getApplication().invokeLater(() ->
              applyScanResult(scanned, scopeFolder, replaceResults || replaceOnNextResult));
          }
          catch (ProcessCanceledException ignored) {
          }
          catch (RuntimeException e) {
            LOG.warn("My checkouts scan failed", e);
          }
          finally {
            activeScan.compareAndSet(indicator, null);
            replaceOnNextResult = false;
          }
        }
      }
    );
  }

  private void applyScanResult(@NotNull List<VssCheckoutEntry> scanned,
                               @Nullable VirtualFile folderScope,
                               boolean replaceResults) {
    if (project.isDisposed()) {
      return;
    }
    if (replaceResults) {
      entries = scanned;
      if (folderScope != null) {
        focusFolder = folderScope;
      }
    }
    else if (folderScope != null) {
      String folderPath = folderScope.getPath();
      List<VssCheckoutEntry> kept = entries.stream()
        .filter(entry -> !MyCheckoutsScanner.isUnderFolder(entry.localPath(), folderPath))
        .toList();
      entries = mergeEntries(kept, scanned);
    }
    else {
      entries = scanned;
      focusFolder = null;
    }
    scheduleVcsStatusRefresh(entries);
    notifyListeners();
  }

  private void scheduleVcsStatusRefresh(@NotNull List<VssCheckoutEntry> entries) {
    if (entries.isEmpty()) {
      return;
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<VirtualFile> files = new ArrayList<>();
      for (VssCheckoutEntry entry : entries) {
        VirtualFile file = VssUtil.getVirtualFile(
          VssTruncatedFileNameUtil.completeTruncatedLocalPath(entry.localPath()));
        if (file != null && !file.isDirectory()) {
          files.add(file);
        }
      }
      if (files.isEmpty()) {
        return;
      }
      ApplicationManager.getApplication().invokeLater(() -> {
        if (project.isDisposed()) {
          return;
        }
        VcsDirtyScopeManager.getInstance(project).filesDirty(files, null);
      });
    });
  }

  @NotNull
  private static List<VssCheckoutEntry> mergeEntries(@NotNull List<VssCheckoutEntry> kept,
                                                   @NotNull List<VssCheckoutEntry> scanned) {
    java.util.Map<String, VssCheckoutEntry> merged = new java.util.LinkedHashMap<>();
    for (VssCheckoutEntry entry : kept) {
      merged.put(entry.localPath().toLowerCase(), entry);
    }
    for (VssCheckoutEntry entry : scanned) {
      merged.put(entry.localPath().toLowerCase(), entry);
    }
    return List.copyOf(merged.values());
  }

  private void notifyListeners() {
    for (Runnable listener : listeners) {
      listener.run();
    }
  }

  public void checkInEntries(@NotNull List<VssCheckoutEntry> selected) {
    if (selected.isEmpty() || project.isDisposed()) {
      return;
    }
    VssMyCheckoutsCommitHelper.openCommitPanel(project, selected);
  }
}
