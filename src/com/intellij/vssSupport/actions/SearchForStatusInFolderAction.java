package com.intellij.vssSupport.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import com.intellij.vssSupport.checkouts.VssMyCheckoutsCommitHelper;
import com.intellij.vssSupport.checkouts.VssMyCheckoutsService;
import com.intellij.vssSupport.checkouts.VssMyCheckoutsUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Search for Status on a selected project folder (directories only).
 */
public final class SearchForStatusInFolderAction extends VssAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    VirtualFile folder = resolveFolder(e);
    if (project == null || folder == null || !folder.isDirectory()) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    VssVcs vcs = VssVcs.getInstance(project);
    if (vcs == null || !ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(vcs)) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    boolean underVss = VssUtil.isFolderUnderVss(project, vcs, folder);
    presentation.setVisible(true);
    presentation.setEnabled(underVss);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile folder = resolveFolder(e);
    if (project == null || folder == null || !folder.isDirectory()) {
      return;
    }
    VssMyCheckoutsUiUtil.showToolWindow(project);
    VssMyCheckoutsCommitHelper.removeLegacyChangeList(project);
    VssMyCheckoutsService.getInstance(project).refreshInFolder(folder);
  }

  @Nullable
  private static VirtualFile resolveFolder(@NotNull AnActionEvent e) {
    VirtualFile[] files = VssUtil.getVirtualFiles(e);
    if (files.length == 1 && files[0].isDirectory()) {
      return files[0];
    }
    for (VirtualFile file : files) {
      if (file.isDirectory()) {
        return file;
      }
    }

    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file != null && file.isDirectory()) {
      return file;
    }

    IdeView ideView = e.getData(LangDataKeys.IDE_VIEW);
    if (ideView != null) {
      PsiDirectory[] directories = ideView.getDirectories();
      if (directories.length == 1) {
        VirtualFile directory = directories[0].getVirtualFile();
        if (directory != null && directory.isDirectory()) {
          return directory;
        }
      }
      for (PsiDirectory directory : directories) {
        VirtualFile vf = directory.getVirtualFile();
        if (vf != null && vf.isDirectory()) {
          return vf;
        }
      }
    }
    return null;
  }
}
