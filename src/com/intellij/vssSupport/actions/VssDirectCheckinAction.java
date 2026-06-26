package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.checkouts.VssMyCheckoutsCommitHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Opens the IDE Commit tool window for the selected VSS file(s).
 */
public final class VssDirectCheckinAction extends VssAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      Project project = e.getProject();
      VirtualFile[] files = VssUtil.getVirtualFiles(e);
      presentation.setEnabled(project != null && VssActionEnablement.canCheckIn(project, files));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    VirtualFile[] files = VssUtil.getVirtualFiles(e);
    if (files.length == 0) {
      return;
    }
    performCheckin(project, files);
  }

  public static void performCheckin(@NotNull Project project, @NotNull VirtualFile[] files) {
    if (project.isDisposed() || files.length == 0) {
      return;
    }
    VssMyCheckoutsCommitHelper.openCommitPanelForFiles(project, List.of(files));
  }
}
