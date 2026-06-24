package com.intellij.vssSupport.checkouts;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssIcons;
import org.jetbrains.annotations.NotNull;

public final class VssMyCheckoutsToolWindowFactory implements ToolWindowFactory, DumbAware {
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    VssMyCheckoutsPanel panel = new VssMyCheckoutsPanel(project);
    Content content = ContentFactory.getInstance().createContent(panel, "", false);
    content.setDisposer(panel::dispose);
    toolWindow.getContentManager().addContent(content);
    toolWindow.setStripeTitle(VssBundle.message("toolwindow.vss.search.for.status"));
    toolWindow.setIcon(VssIcons.SEARCH_FOR_STATUS);
  }
}
