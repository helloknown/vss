package com.intellij.vssSupport.occupancy;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class VssOccupancyEditorNotifier extends EditorNotifications.Provider {
  private static final Key<Boolean> KEY = Key.create("vss.occupancy.notifier");

  @Override
  public @NotNull Key<Boolean> getKey() {
    return KEY;
  }

  @Override
  @Nullable
  public Function<? super @NotNull FileEditor, ? extends @Nullable EditorNotificationPanel> collectNotificationData(
    @NotNull Project project,
    @NotNull VirtualFile file) {
    if (file.isDirectory() || VssVcs.getInstance(project) == null) {
      return null;
    }

  return editor -> {
      FilePath path = VcsUtil.getFilePath(file);
      VssFileOccupancy occupancy = VssFileOccupancyService.getInstance(project).getCached(path);
      if (occupancy == null) {
        VssFileOccupancyService.getInstance(project).queryAsync(path, () ->
          EditorNotifications.getInstance(project).updateNotifications(file));
        return null;
      }
      if (!occupancy.isCheckedOutByOtherUser()) {
        return null;
      }

      EditorNotificationPanel panel = new EditorNotificationPanel(EditorNotificationPanel.Status.Warning);
      String user = occupancy.getCheckoutUser();
      String date = occupancy.getCheckout().getDateTime();
      panel.setText(VssBundle.message("notification.vss.checked.out.by.other", user, date));
      return panel;
    };
  }
}
