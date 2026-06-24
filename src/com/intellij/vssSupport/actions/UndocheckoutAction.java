package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.checkouts.VssUndocheckoutHelper;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladimir Kondratyev
 */
public class UndocheckoutAction extends VssAction {
  public void update(AnActionEvent e) {
    super.update(e);

    if (e.getPresentation().isEnabled()) {
      Project project = e.getData(CommonDataKeys.PROJECT);
      VirtualFile[] files = VssUtil.getVirtualFiles(e);

      boolean isEnabled = VssActionEnablement.canUndoCheckout(project, files);
      e.getPresentation().setEnabled(isEnabled);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    VirtualFile[] files = VssUtil.getVirtualFiles(e);
    VssUndocheckoutHelper.undoCheckout(project, files, isShiftPressed(e));
  }
}
