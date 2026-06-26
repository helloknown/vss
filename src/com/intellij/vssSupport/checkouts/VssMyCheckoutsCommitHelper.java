package com.intellij.vssSupport.checkouts;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.ContentRevisionFactory;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssTruncatedFileNameUtil;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Opens the Commit Changes dialog for selected VSS files.
 */
public final class VssMyCheckoutsCommitHelper {
  public static final String LEGACY_CHANGE_LIST_NAME = "VSS: My Checkouts";

  private static final Logger LOG = Logger.getInstance(VssMyCheckoutsCommitHelper.class);

  private VssMyCheckoutsCommitHelper() {
  }

  public static void removeLegacyChangeList(@NotNull Project project) {
    if (project.isDisposed()) {
      return;
    }
    ChangeListManager clm = ChangeListManager.getInstance(project);
    Runnable cleanup = () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        LocalChangeList legacy = findLegacyList(clm);
        if (legacy == null) {
          return;
        }
        if (legacy.isDefault()) {
          LocalChangeList changes = findNamedList(clm, "Changes");
          if (changes != null) {
            clm.setDefaultChangeList(changes);
          }
        }
        LocalChangeList target = clm.getDefaultChangeList();
        if (legacy != target && !legacy.getChanges().isEmpty()) {
          clm.moveChangesTo(target, new ArrayList<>(legacy.getChanges()));
        }
        if (!legacy.isDefault()) {
          clm.removeChangeList(legacy);
        }
      }
      catch (Exception e) {
        LOG.warn("Failed to remove legacy changelist: " + LEGACY_CHANGE_LIST_NAME, e);
      }
    });
    clm.invokeAfterUpdate(false, cleanup);
  }

  public static void openCommitPanel(@NotNull Project project, @NotNull List<VssCheckoutEntry> selected) {
    openCommitPanelForFiles(project, resolveEntryFiles(selected));
  }

  /**
   * Opens the Commit Changes dialog for the given local files.
   * Used by the context menu ({@link com.intellij.vssSupport.actions.VssDirectCheckinAction})
   * and Search for Status after resolving table rows to {@link VirtualFile}.
   */
  public static void openCommitPanelForFiles(@NotNull Project project, @NotNull List<VirtualFile> files) {
    if (project.isDisposed()) {
      return;
    }
    if (files.isEmpty()) {
      LOG.warn("Check-in: no local files resolved");
      Messages.showInfoMessage(project,
                               VssBundle.message("message.my.checkouts.files.not.found"),
                               VssBundle.message("toolwindow.vss.search.for.status"));
      return;
    }
    LOG.info("Check-in: opening commit dialog for " + files.size() + " file(s)");
    List<VirtualFile> filesCopy = List.copyOf(files);
    Runnable openDialog = () -> {
      saveAllDocumentsForCommit();
      showCommitDialog(project, filesCopy);
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      openDialog.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(openDialog, ModalityState.defaultModalityState());
    }
  }

  /**
   * IDEA 2026+ requires write-intent for {@link FileDocumentManager#saveAllDocuments()}.
   */
  private static void saveAllDocumentsForCommit() {
    Runnable save = () -> FileDocumentManager.getInstance().saveAllDocuments();
    WriteIntentReadAction.run(save);
  }

  private static void showCommitDialog(@NotNull Project project, @NotNull List<VirtualFile> files) {
    if (project.isDisposed()) {
      return;
    }
    List<Change> changes = buildChangesForFiles(project, files);
    if (changes.isEmpty()) {
      LOG.warn("Check-in: no changes built for selected files");
      CommitChangeListDialog.showNothingToCommitMessage(project);
      return;
    }

    AbstractVcs vcs = VssVcs.getInstance(project);
    if (vcs == null) {
      LOG.warn("Check-in: VSS is not configured for this project");
      return;
    }

    ChangeListManager clm = ChangeListManager.getInstance(project);
    LocalChangeList changeList = clm.getChangeList(changes.get(0));
    if (changeList == null) {
      changeList = clm.getDefaultChangeList();
    }
    CommitChangeListDialog.showCommitDialog(
      project,
      Set.of(vcs),
      changes,
      changeList,
      Collections.emptyList(),
      true,
      null,
      null
    );
  }

  /**
   * Resolves {@link VssCheckoutEntry} rows from Search for Status to {@link VirtualFile},
   * using the same local file identity as the VSS scan (including truncated {@code ss Dir -E} names).
   */
  @NotNull
  public static List<VirtualFile> resolveEntryFiles(@NotNull List<VssCheckoutEntry> entries) {
    List<VirtualFile> files = new ArrayList<>();
    for (VssCheckoutEntry entry : entries) {
      VirtualFile file = resolveEntryFile(entry);
      if (file != null && !file.isDirectory()) {
        files.add(file);
      }
    }
    return files;
  }

  @Nullable
  public static VirtualFile resolveEntryFile(@NotNull VssCheckoutEntry entry) {
    String completedPath = VssTruncatedFileNameUtil.completeTruncatedLocalPath(entry.localPath());
    VirtualFile file = findLocalFile(completedPath);
    if (file != null) {
      return file;
    }

    if (!StringUtil.isEmpty(entry.workingFolder()) && !StringUtil.isEmpty(entry.fileName())
        && isLikelyLocalDirectory(entry.workingFolder())) {
      String parent = VssUtil.getCanonicalLocalPath(entry.workingFolder());
      String resolvedPath = VssTruncatedFileNameUtil.resolveLocalPath(parent, entry.fileName());
      file = findLocalFile(resolvedPath);
      if (file != null) {
        return file;
      }
    }

    if (!StringUtil.isEmpty(entry.fileName())) {
      java.io.File parentDir = new java.io.File(completedPath).getParentFile();
      if (parentDir != null && parentDir.isDirectory()) {
        file = findLocalFile(VssTruncatedFileNameUtil.resolveLocalPath(parentDir.getPath(), entry.fileName()));
      }
    }
    return file;
  }

  @Nullable
  private static VirtualFile findLocalFile(@NotNull String localPath) {
    VirtualFile file = VssUtil.getVirtualFile(localPath);
    if (file != null) {
      return file;
    }
    java.io.File ioFile = new java.io.File(localPath);
    if (!ioFile.isFile()) {
      try {
        ioFile = ioFile.getCanonicalFile();
      }
      catch (java.io.IOException ignored) {
      }
    }
    if (ioFile.isFile()) {
      file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
      if (file != null) {
        return file;
      }
    }
    return LocalFileSystem.getInstance().findFileByIoFile(ioFile);
  }

  private static boolean isLikelyLocalDirectory(@NotNull String path) {
    if (path.isEmpty() || path.startsWith("$")) {
      return false;
    }
    java.io.File dir = new java.io.File(path);
    return dir.isDirectory();
  }

  @NotNull
  private static List<Change> buildChangesForFiles(@NotNull Project project, @NotNull List<VirtualFile> files) {
    ChangeListManager clm = ChangeListManager.getInstance(project);
    Set<Change> changes = new LinkedHashSet<>();
    for (VirtualFile file : files) {
      Change existing = clm.getChange(file);
      if (existing != null) {
        changes.add(existing);
        continue;
      }
      FilePath path = VcsUtil.getFilePath(file);
      changes.add(new Change(
        ContentRevisionFactory.getRevision(path, project),
        new CurrentContentRevision(path)
      ));
    }
    return List.copyOf(changes);
  }

  @Nullable
  private static LocalChangeList findLegacyList(@NotNull ChangeListManager clm) {
    for (LocalChangeList list : clm.getChangeLists()) {
      if (LEGACY_CHANGE_LIST_NAME.equals(list.getName())) {
        return list;
      }
    }
    return null;
  }

  @Nullable
  private static LocalChangeList findNamedList(@NotNull ChangeListManager clm, @NotNull String name) {
    for (LocalChangeList list : clm.getChangeLists()) {
      if (name.equals(list.getName())) {
        return list;
      }
    }
    return null;
  }
}
