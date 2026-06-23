package com.intellij.vssSupport.Configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.vssSupport.VssVcs;

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

    VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
  }
}
