package com.intellij.vssSupport.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NotNull;

/**
 * Enables Check Out / Undo Check Out based on VCS file status (actual checkout state in IDEA).
 */
public final class VssActionEnablement {
  private static final int FOLDER_PROBE_LIMIT = 5000;

  private VssActionEnablement() {
  }

  public static boolean canCheckout(@NotNull Project project, @NotNull VirtualFile[] files) {
    if (files.length == 0) {
      return false;
    }
    if (VssAction.allFilesAreFolders(files)) {
      return folderContainsStatus(project, files, FileStatus.NOT_CHANGED);
    }
    FileStatusManager statusManager = FileStatusManager.getInstance(project);
    for (VirtualFile file : files) {
      if (file.isDirectory()) {
        return false;
      }
      if (VssUtil.isPendingVssAddition(project, file)) {
        return false;
      }
      if (statusManager.getStatus(file) != FileStatus.NOT_CHANGED) {
        return false;
      }
    }
    return true;
  }

  public static boolean canCheckIn(@NotNull Project project, @NotNull VirtualFile[] files) {
    return canUndoCheckout(project, files);
  }

  public static boolean canUndoCheckout(@NotNull Project project, @NotNull VirtualFile[] files) {
    if (files.length == 0) {
      return false;
    }
    if (VssAction.allFilesAreFolders(files)) {
      return folderContainsStatus(project, files, FileStatus.MODIFIED);
    }
    FileStatusManager statusManager = FileStatusManager.getInstance(project);
    for (VirtualFile file : files) {
      if (file.isDirectory()) {
        return false;
      }
      if (statusManager.getStatus(file) != FileStatus.MODIFIED) {
        return false;
      }
    }
    return true;
  }

  private static boolean folderContainsStatus(@NotNull Project project,
                                              @NotNull VirtualFile[] roots,
                                              @NotNull FileStatus targetStatus) {
    VssVcs vcs = VssVcs.getInstance(project);
    if (vcs == null) {
      return false;
    }
    FileStatusManager statusManager = FileStatusManager.getInstance(project);
    for (VirtualFile root : roots) {
      if (probeFolder(project, vcs, statusManager, root, targetStatus)) {
        return true;
      }
    }
    return false;
  }

  private static boolean probeFolder(@NotNull Project project,
                                     @NotNull VssVcs vcs,
                                     @NotNull FileStatusManager statusManager,
                                     @NotNull VirtualFile root,
                                     @NotNull FileStatus targetStatus) {
    if (!root.isDirectory()) {
      return statusManager.getStatus(root) == targetStatus;
    }
    final boolean[] found = {false};
    final int[] visited = {0};
    ProjectLevelVcsManager.getInstance(project).iterateVcsRoot(root, file -> {
      if (found[0] || visited[0]++ > FOLDER_PROBE_LIMIT) {
        return false;
      }
      if (!vcs.equals(ProjectLevelVcsManager.getInstance(project).getVcsFor(file))) {
        return true;
      }
      VirtualFile vf = file.getVirtualFile();
      if (vf == null || vf.isDirectory()) {
        return true;
      }
      if (!VcsUtil.isFileForVcs(VcsUtil.getFilePath(vf), project, vcs)) {
        return true;
      }
      if (statusManager.getStatus(vf) == targetStatus) {
        found[0] = true;
        return false;
      }
      return true;
    });
    return found[0];
  }
}
