package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.actions.StandardVcsGroup;
import com.intellij.vssSupport.VssIcons;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NotNull;

public class VssGroup extends StandardVcsGroup {
  public VssGroup() {
    getTemplatePresentation().setIcon(VssIcons.SOURCE_SAFE);
  }

  public AbstractVcs getVcs(Project project) {
    return VssVcs.getInstance(project);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setIcon(VssIcons.SOURCE_SAFE);
  }
}
