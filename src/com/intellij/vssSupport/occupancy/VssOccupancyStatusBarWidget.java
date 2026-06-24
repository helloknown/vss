package com.intellij.vssSupport.occupancy;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public final class VssOccupancyStatusBarWidget extends EditorBasedWidget implements StatusBarWidget.TextPresentation {
  @NonNls public static final String WIDGET_ID = "VssOccupancyStatus";

  private final AtomicReference<String> text = new AtomicReference<>(VssBundle.message("statusbar.vss.occupancy.idle"));

  public VssOccupancyStatusBarWidget(@NotNull Project project) {
    super(project);
    FileEditorManagerListener listener = new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        refreshForFile(file);
      }

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        VirtualFile file = event.getNewFile();
        if (file != null) {
          refreshForFile(file);
        }
      }
    };
    project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
    Disposer.register(this, () -> text.set(VssBundle.message("statusbar.vss.occupancy.idle")));
  }

  public static void refreshIfShowing(@NotNull Project project, @NotNull VirtualFile file) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar == null) {
      return;
    }
    StatusBarWidget widget = statusBar.getWidget(WIDGET_ID);
    if (widget instanceof VssOccupancyStatusBarWidget) {
      ((VssOccupancyStatusBarWidget)widget).refreshForFile(file);
    }
  }

  public void refreshForFile(VirtualFile file) {
    if (file.isDirectory() || VssVcs.getInstance(getProject()) == null) {
      text.set("");
      updateWidget();
      return;
    }
    FilePath path = VcsUtil.getFilePath(file);
    text.set(VssBundle.message("statusbar.vss.occupancy.loading"));
    updateWidget();

    VssFileOccupancyService.getInstance(getProject()).queryAsync(path, () -> {
      VssFileOccupancy occupancy = VssFileOccupancyService.getInstance(getProject()).getCached(path);
      if (occupancy == null) {
        text.set("");
      }
      else {
        text.set(formatOccupancy(occupancy));
        if (occupancy.isCheckedOutByCurrentUser()) {
          VcsDirtyScopeManager.getInstance(getProject()).fileDirty(file);
        }
      }
      updateWidget();
    });
  }

  private String formatOccupancy(VssFileOccupancy occupancy) {
    if (!occupancy.isCheckedOut()) {
      String version = occupancy.getLatestVersion();
      if (version != null) {
        return VssBundle.message("statusbar.vss.occupancy.not.checked.out.version", version);
      }
      return VssBundle.message("statusbar.vss.occupancy.not.checked.out");
    }
    if (occupancy.isCheckedOutByCurrentUser()) {
      return VssBundle.message("statusbar.vss.occupancy.checked.out.you", occupancy.getCheckoutUser());
    }
    return VssBundle.message("statusbar.vss.occupancy.checked.out.other", occupancy.getCheckoutUser());
  }

  private void updateWidget() {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(getProject());
    if (statusBar != null) {
      statusBar.updateWidget(WIDGET_ID);
    }
  }

  @Override
  public @NotNull String ID() {
    return WIDGET_ID;
  }

  @Override
  public @Nullable WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  public @NotNull String getText() {
    return text.get();
  }

  @Override
  public @Nullable String getTooltipText() {
    return getText();
  }

  @Override
  public float getAlignment() {
    return 0.5f;
  }

  public static final class Factory implements com.intellij.openapi.wm.StatusBarWidgetFactory {
    @Override
    public @NotNull String getId() {
      return WIDGET_ID;
    }

    @Override
    public @NotNull String getDisplayName() {
      return VssBundle.message("statusbar.vss.occupancy.display.name");
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
      return VssVcs.getInstance(project) != null;
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
      return new VssOccupancyStatusBarWidget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
      Disposer.dispose(widget);
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
      return true;
    }
  }
}
