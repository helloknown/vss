package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.commands.GetFileCommand;

import java.util.ArrayList;

/**
 * Gets the selected history revision into the working folder (replaces local file).
 */
public final class GetRevisionToWorkingCopyAction extends AnAction implements DumbAware {
  public GetRevisionToWorkingCopyAction() {
    super(VssBundle.message("action.Vss.GetRevision.text"), VssBundle.message("action.Vss.GetRevision.description"), null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    FilePath path = e.getData(VcsDataKeys.FILE_PATH);
    VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    if (project == null || path == null || revision == null) {
      return;
    }

    int answer = Messages.showYesNoDialog(project,
                                          VssBundle.message("message.text.get.revision.replace.local", revision.getRevisionNumber().asString()),
                                          VssBundle.message("action.Vss.GetRevision.text"),
                                          Messages.getWarningIcon());
    if (answer != Messages.YES) {
      return;
    }

    String version = revisionNumberAsString(revision.getRevisionNumber());
    ArrayList<VcsException> errors = new ArrayList<>();
    GetFileCommand cmd = new GetFileCommand(project, path.getPath(), version, errors);
    cmd.execute();

    if (!errors.isEmpty()) {
      Messages.showErrorDialog(project, errors.get(0).getLocalizedMessage(), VssBundle.message("message.title.error"));
    }
  }

  @Override
  public void update(AnActionEvent e) {
    VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
  e.getPresentation().setEnabled(revision != null && e.getData(VcsDataKeys.FILE_PATH) != null);
  }

  private static String revisionNumberAsString(VcsRevisionNumber revisionNumber) {
    if (revisionNumber instanceof VcsRevisionNumber.Int intRevision) {
      return String.valueOf(intRevision.getValue());
    }
    return revisionNumber.asString();
  }
}
