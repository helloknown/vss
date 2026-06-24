package com.intellij.vssSupport.checkouts;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.Checkin.VssCheckinEnvironment;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.ContentRevisionFactory;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssCommitMessageUtil;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import com.intellij.vssSupport.ui.VssCheckinCommentDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Direct VSS check-in (comment dialog only, no Commit tool window).
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

  public static void checkIn(@NotNull Project project, @NotNull List<VssCheckoutEntry> selected) {
    checkInFiles(project, resolveVirtualFiles(selected));
  }

  public static void checkInFiles(@NotNull Project project, @NotNull List<VirtualFile> files) {
    if (project.isDisposed() || files.isEmpty()) {
      return;
    }
    VssVcs vcs = VssVcs.getInstance(project);
    if (vcs == null) {
      return;
    }

    String defaultComment = VssConfiguration.getInstance(project).getCheckinOptions().COMMENT;
    VssCheckinCommentDialog dialog = new VssCheckinCommentDialog(project, defaultComment);
    if (!dialog.showAndGet()) {
      return;
    }
    String comment = dialog.getComment();

    List<Change> changes = buildChanges(project, files);
    if (changes.isEmpty()) {
      Messages.showInfoMessage(project, VssBundle.message("message.my.checkouts.files.not.found"),
                              VssBundle.message("toolwindow.vss.search.for.status"));
      return;
    }

    List<String> paths = files.stream().map(VirtualFile::getPath).collect(Collectors.toList());
    String normalizedComment = VssCommitMessageUtil.normalize(comment);
    ProgressManager.getInstance().run(new Task.Backgroundable(project, VssBundle.message("dialog.title.checkin.file"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        VssCheckinEnvironment env = vcs.getCheckinEnvironment();
        List<VcsException> errors = env.commit(changes, normalizedComment, new CommitContext(), new HashSet<>());
        ApplicationManager.getApplication().invokeLater(() -> {
          if (project.isDisposed()) {
            return;
          }
          if (!errors.isEmpty()) {
            VcsException error = errors.get(0);
            String message = error.getLocalizedMessage();
            if (message != null && isNotCurrentlyCheckedOutMessage(message)) {
              for (VirtualFile file : files) {
                VssUtil.syncStaleCheckoutState(project, file);
              }
            }
            Messages.showErrorDialog(message, VssBundle.message("message.title.error"));
          }
        });
      }
    });
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

  @NotNull
  private static List<Change> buildChanges(@NotNull Project project, @NotNull List<VirtualFile> files) {
    ChangeListManager clm = ChangeListManager.getInstance(project);
    List<Change> changes = new ArrayList<>();
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
    return changes;
  }

  @NotNull
  private static List<VirtualFile> resolveVirtualFiles(@NotNull List<VssCheckoutEntry> entries) {
    List<VirtualFile> files = new ArrayList<>();
    for (VssCheckoutEntry entry : entries) {
      VirtualFile file = VssUtil.getVirtualFile(entry.localPath());
      if (file != null && !file.isDirectory()) {
        files.add(file);
      }
    }
    return files;
  }

  private static boolean isNotCurrentlyCheckedOutMessage(@NotNull String message) {
    String lower = message.toLowerCase();
    return lower.contains("do not have") && lower.contains("currently checked");
  }
}
