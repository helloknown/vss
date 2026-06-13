package com.intellij.vssSupport.Configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.vssSupport.VssRootSettings;
import com.intellij.vssSupport.VssVcs;

/**
 * Editable row for local directory to VSS project mapping.
 */
public final class VssMappingEntry {
  private String directory;
  private String vssProject;

  public VssMappingEntry(String directory, String vssProject) {
    this.directory = directory != null ? directory : "";
    this.vssProject = vssProject != null ? vssProject : "";
  }

  public static VssMappingEntry fromMapping(VcsDirectoryMapping mapping, Project project) {
    String directory = mapping.getDirectory();
    String vssProject = VssMappingStorage.readVssProject(project, mapping);
    if (StringUtil.isEmptyOrSpaces(vssProject)) {
      vssProject = "";
    }
    return new VssMappingEntry(directory, vssProject);
  }

  public VcsDirectoryMapping toMapping() {
    return new VcsDirectoryMapping(directory, VssVcs.getKey().getName(), new VssRootSettings(vssProject));
  }

  public String getDirectory() {
    return directory;
  }

  public void setDirectory(String directory) {
    this.directory = directory != null ? directory : "";
  }

  public String getVssProject() {
    return vssProject;
  }

  public void setVssProject(String vssProject) {
    this.vssProject = vssProject != null ? vssProject : "";
  }

  public boolean contentEquals(VssMappingEntry other) {
    return directory.equals(other.directory) && vssProject.equals(other.vssProject);
  }
}
