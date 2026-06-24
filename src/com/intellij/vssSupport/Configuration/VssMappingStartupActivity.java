package com.intellij.vssSupport.Configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.vssSupport.VssVcs;
import com.intellij.vssSupport.checkouts.VssMyCheckoutsCommitHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Runs after project/workspace state is loaded so directory mappings are applied to the platform VCS manager.
 */
public final class VssMappingStartupActivity implements StartupActivity.DumbAware {
  @Override
  public void runActivity(Project project) {
    if (project.isDefault() || VssVcs.getInstance(project) == null) {
      return;
    }
    VssMappingStorage.ensureMappingsReady(project);

    // Kick off a refresh as soon as mappings exist; repeat after indexing for IDE builds that defer VCS scans.
    scheduleVcsRefresh(project);

    DumbService.getInstance(project).runWhenSmart(() -> scheduleVcsRefresh(project));
  }

  private static void scheduleVcsRefresh(@NotNull Project project) {
    if (project.isDisposed() || VssVcs.getInstance(project) == null) {
      return;
    }
    Runnable refresh = () -> {
      if (project.isDisposed()) {
        return;
      }
      VssMyCheckoutsCommitHelper.removeLegacyChangeList(project);
      VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
      ChangeListManager.getInstance(project).invokeAfterUpdate(true, () -> { });
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().executeOnPooledThread(refresh);
    }
    else {
      refresh.run();
    }
  }
}
