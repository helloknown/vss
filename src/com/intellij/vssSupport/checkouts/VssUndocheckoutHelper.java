package com.intellij.vssSupport.checkouts;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssVcs;
import com.intellij.vssSupport.commands.UndocheckoutDirCommand;
import com.intellij.vssSupport.commands.UndocheckoutFilesCommand;
import com.intellij.vssSupport.ui.UndocheckoutDirDialog;
import com.intellij.vssSupport.ui.UndocheckoutFilesDialog;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/** Shared undo-checkout flow used by context menu and Search for Status panel. */
public final class VssUndocheckoutHelper {
  private VssUndocheckoutHelper() {
  }

  public static void undoCheckout(@NotNull Project project,
                                  @NotNull VirtualFile[] files,
                                  boolean shiftPressed) {
    if (files.length == 0) {
      return;
    }
    ArrayList<VcsException> errors = new ArrayList<>();
    try {
      VssVcs vcs = VssVcs.getInstance(project);
      if (vcs == null) {
        return;
      }
      boolean showOptions = vcs.getUndoCheckoutOptions().getValue() || shiftPressed;
      if (showOptions) {
        OptionsDialog editor = allFolders(files)
                               ? new UndocheckoutDirDialog(project)
                               : new UndocheckoutFilesDialog(project);
        editor.setTitle(files.length == 1
                        ? VssBundle.message("dialog.title.undo.check.out", files[0].getName())
                        : VssBundle.message("dialog.title.undo.check.out.multiple"));
        if (!editor.showAndGet()) {
          return;
        }
      }

      FileDocumentManager.getInstance().saveAllDocuments();

      if (allFolders(files)) {
        for (VirtualFile file : files) {
          new UndocheckoutDirCommand(project, file, errors).execute();
        }
      }
      else {
        new UndocheckoutFilesCommand(project, files, errors).execute();
      }
    }
    finally {
      if (!errors.isEmpty()) {
        Messages.showErrorDialog(errors.get(0).getLocalizedMessage(),
                                 VssBundle.message("message.title.could.not.start.process"));
      }
    }
  }

  private static boolean allFolders(@NotNull VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (!file.isDirectory()) {
        return false;
      }
    }
    return true;
  }
}
