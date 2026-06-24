package com.intellij.vssSupport.checkouts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public final class VssMyCheckoutsUiUtil {
  public static final String TOOL_WINDOW_ID = "VSS Search for Status";

  private VssMyCheckoutsUiUtil() {
  }

  public static void showCommitToolWindow(@NotNull Project project) {
    ToolWindow commit = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.COMMIT);
    if (commit != null) {
      commit.show(null);
      commit.activate(null);
    }
  }

  public static void showToolWindow(@NotNull Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
    if (toolWindow != null) {
      toolWindow.show(null);
      toolWindow.activate(null);
    }
  }
}
