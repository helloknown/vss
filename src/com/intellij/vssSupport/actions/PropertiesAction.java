package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.occupancy.VssFileOccupancy;
import com.intellij.vssSupport.occupancy.VssFileOccupancyService;
import com.intellij.vssSupport.ui.VssPropertiesDialog;

public final class PropertiesAction extends VssAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    VirtualFile[] files = VssUtil.getVirtualFiles(e);
    if (files.length != 1 || files[0].isDirectory()) {
      return;
    }

    FilePath path = VcsUtil.getFilePath(files[0]);
    ProgressManager.getInstance().run(new Task.Modal(project, VssBundle.message("progress.title.vss.properties"), true) {
      private VssFileOccupancy occupancy;

      @Override
      public void run(@org.jetbrains.annotations.NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        occupancy = VssFileOccupancyService.getInstance(project).querySync(path);
      }

      @Override
      public void onSuccess() {
        if (occupancy == null) {
          Messages.showErrorDialog(project, VssBundle.message("message.text.vss.properties.failed"),
                                   VssBundle.message("message.title.error"));
          return;
        }
        VssPropertiesDialog dialog = new VssPropertiesDialog(project, occupancy);
        dialog.show();
      }
    });
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    if (!e.getPresentation().isEnabled()) {
      return;
    }
    VirtualFile[] files = VssUtil.getVirtualFiles(e);
    e.getPresentation().setEnabled(files.length == 1 && !files[0].isDirectory());
  }
}
