package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.vssSupport.VssVcs;
import com.intellij.vssSupport.checkouts.VssMyCheckoutsCommitHelper;
import com.intellij.vssSupport.checkouts.VssMyCheckoutsUiUtil;
import org.jetbrains.annotations.NotNull;

public final class ShowMyCheckoutsAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    boolean visible = project != null
                      && ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(VssVcs.getInstance(project));
    e.getPresentation().setEnabledAndVisible(visible);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    VssMyCheckoutsUiUtil.showToolWindow(project);
    VssMyCheckoutsCommitHelper.removeLegacyChangeList(project);
  }
}
