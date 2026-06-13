package com.intellij.vssSupport.Configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.vssSupport.VssRootSettings;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps VSS project paths in {@link VssConfiguration} as a durable backup when platform
 * directory mappings are saved without {@link VssRootSettings}.
 */
public final class VssMappingStorage {
  private VssMappingStorage() {
  }

  public static String normalizeLocalKey(String localDirectory) {
    if (StringUtil.isEmpty(localDirectory)) {
      return "";
    }
    return VssUtil.getCanonicalLocalPath(localDirectory.replace('\\', '/'));
  }

  public static void put(Project project, String localDirectory, String vssProject) {
    VssConfiguration config = VssConfiguration.getInstance(project);
    String key = normalizeLocalKey(localDirectory);
    String vss = vssProject != null ? vssProject.trim() : "";

    for (int i = config.getMapItemCount() - 1; i >= 0; i--) {
      MapItem item = config.getMapItem(i);
      if (normalizeLocalKey(item.LOCAL_PATH).equalsIgnoreCase(key)) {
        config.removeMapItem(i);
      }
    }
    if (!StringUtil.isEmptyOrSpaces(vss)) {
      config.addMapItem(new MapItem(vss, key));
    }
  }

  public static void replaceAll(Project project, List<VssMappingEntry> entries) {
    VssConfiguration config = VssConfiguration.getInstance(project);
    config.clearMapItems();
    for (VssMappingEntry entry : entries) {
      if (!StringUtil.isEmptyOrSpaces(entry.getVssProject())) {
        config.addMapItem(new MapItem(entry.getVssProject(), normalizeLocalKey(entry.getDirectory())));
      }
    }
  }

  /**
   * Merges platform directory mappings with {@link MapItem} backup (workspace file).
   */
  @NotNull
  public static List<VssMappingEntry> loadMappingEntries(Project project) {
    Map<String, VssMappingEntry> merged = new LinkedHashMap<>();
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(project);
    String vcsName = VssVcs.getKey().getName();

    for (VcsDirectoryMapping mapping : mgr.getDirectoryMappings()) {
      if (!vcsName.equals(mapping.getVcs())) {
        continue;
      }
      VssMappingEntry entry = VssMappingEntry.fromMapping(mapping, project);
      merged.put(normalizeLocalKey(entry.getDirectory()), entry);
    }

    VssConfiguration config = VssConfiguration.getInstance(project);
    for (int i = 0; i < config.getMapItemCount(); i++) {
      MapItem item = config.getMapItem(i);
      if (StringUtil.isEmptyOrSpaces(item.VSS_PATH)) {
        continue;
      }
      String key = normalizeLocalKey(item.LOCAL_PATH);
      if (!merged.containsKey(key)) {
        merged.put(key, new VssMappingEntry(item.LOCAL_PATH, item.VSS_PATH));
      }
    }
    return new ArrayList<>(merged.values());
  }

  /**
   * Restores SourceSafe mappings from workspace {@link MapItem} backup when platform mappings were lost on reload.
   */
  public static void restoreMappingsFromBackup(Project project) {
    reconcilePlatformMappings(project);
  }

  /**
   * Ensures platform directory mappings include {@link VssRootSettings} after workspace state is available.
   */
  public static void ensureMappingsReady(Project project) {
    if (VssVcs.getInstance(project) == null) {
      return;
    }
    List<VssMappingEntry> entries = loadMappingEntries(project);
    if (entries.isEmpty()) {
      return;
    }
    if (!platformMappingsFunctional(project)) {
      syncMappings(project, entries);
    }
    else {
      reconcilePlatformMappings(project);
    }
  }

  private static void reconcilePlatformMappings(Project project) {
    AbstractVcs vcs = VssVcs.getInstance(project);
    if (vcs == null) {
      return;
    }
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(project);
    List<VcsDirectoryMapping> current = mgr.getDirectoryMappings();
    String vcsName = vcs.getName();
    List<VcsDirectoryMapping> merged = new ArrayList<>(current.size());
    List<VcsDirectoryMapping> toAdd = new ArrayList<>();
    boolean changed = false;

    for (VcsDirectoryMapping mapping : current) {
      if (!vcsName.equals(mapping.getVcs())) {
        merged.add(mapping);
        continue;
      }
      String vssProject = readVssProject(project, mapping);
      if (!StringUtil.isEmptyOrSpaces(vssProject) && needsRootSettingsUpdate(mapping, vssProject)) {
        merged.add(new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), new VssRootSettings(vssProject)));
        changed = true;
      }
      else {
        merged.add(mapping);
      }
    }

    VssConfiguration config = VssConfiguration.getInstance(project);
    for (int i = 0; i < config.getMapItemCount(); i++) {
      MapItem item = config.getMapItem(i);
      if (StringUtil.isEmptyOrSpaces(item.VSS_PATH)) {
        continue;
      }
      if (!hasSourceSafeMapping(project, item.LOCAL_PATH, current)) {
        toAdd.add(new VcsDirectoryMapping(item.LOCAL_PATH, vcsName, new VssRootSettings(item.VSS_PATH)));
      }
    }

    if (!toAdd.isEmpty()) {
      merged.addAll(toAdd);
      changed = true;
    }

    if (changed) {
      mgr.setDirectoryMappings(merged);
    }
    enrichDirectoryMappings(project);
  }

  private static boolean platformMappingsFunctional(Project project) {
    List<VcsDirectoryMapping> mappings = getSourceSafeMappings(project);
    if (mappings.isEmpty()) {
      return false;
    }
    for (VcsDirectoryMapping mapping : mappings) {
      if (needsRootSettingsUpdate(mapping, readVssProject(project, mapping))) {
        return false;
      }
    }
    return true;
  }

  private static boolean needsRootSettingsUpdate(VcsDirectoryMapping mapping, String vssProject) {
    if (StringUtil.isEmptyOrSpaces(vssProject)) {
      return mapping.getRootSettings() == null;
    }
    if (mapping.getRootSettings() == null) {
      return true;
    }
    if (!(mapping.getRootSettings() instanceof VssRootSettings settings)) {
      return true;
    }
    return StringUtil.isEmptyOrSpaces(settings.getVssProject())
           || !vssProject.equals(settings.getVssProject());
  }

  public static void syncMappings(Project project, List<VssMappingEntry> entries) {
    AbstractVcs vcs = VssVcs.getInstance(project);
    if (vcs == null) {
      return;
    }
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(project);
    List<VcsDirectoryMapping> mappings = new ArrayList<>(mgr.getDirectoryMappings());
    String vcsName = vcs.getName();
    mappings.removeIf(mapping -> vcsName.equals(mapping.getVcs()));

    for (VssMappingEntry entry : entries) {
      if (!StringUtil.isEmptyOrSpaces(entry.getVssProject())) {
        mappings.add(entry.toMapping());
      }
    }
    mgr.setDirectoryMappings(mappings);
    replaceAll(project, entries);
    enrichDirectoryMappings(project);
  }

  static boolean hasSourceSafeMapping(Project project, String localDirectory, List<VcsDirectoryMapping> mappings) {
    String vcsName = VssVcs.getKey().getName();
    String normalized = normalizeLocalKey(localDirectory);
    for (VcsDirectoryMapping mapping : mappings) {
      if (!vcsName.equals(mapping.getVcs())) {
        continue;
      }
      if (FileUtil.pathsEqual(normalizeLocalKey(mapping.getDirectory()), normalized)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static String getVssProject(Project project, String localDirectory) {
    VssConfiguration config = VssConfiguration.getInstance(project);
    String key = normalizeLocalKey(localDirectory);
    for (int i = 0; i < config.getMapItemCount(); i++) {
      MapItem item = config.getMapItem(i);
      if (normalizeLocalKey(item.LOCAL_PATH).equalsIgnoreCase(key)) {
        return item.VSS_PATH;
      }
    }
    return null;
  }

  public static void enrichDirectoryMappings(Project project) {
    AbstractVcs vcs = VssVcs.getInstance(project);
    if (vcs == null) {
      return;
    }
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(project);
    List<VcsDirectoryMapping> mappings = mgr.getDirectoryMappings();
    List<VcsDirectoryMapping> updated = new ArrayList<>(mappings.size());
    boolean changed = false;

    for (VcsDirectoryMapping mapping : mappings) {
      if (!vcs.getName().equals(mapping.getVcs())) {
        updated.add(mapping);
        continue;
      }
      String vssProject = readVssProject(project, mapping);
      if (StringUtil.isEmptyOrSpaces(vssProject)) {
        updated.add(mapping);
        continue;
      }
      VssRootSettings current = mapping.getRootSettings() instanceof VssRootSettings settings ? settings : null;
      if (current != null && vssProject.equals(current.getVssProject())) {
        updated.add(mapping);
      }
      else {
        updated.add(new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), new VssRootSettings(vssProject)));
        changed = true;
      }
    }

    if (changed) {
      mgr.setDirectoryMappings(updated);
    }
  }

  public static String readVssProject(Project project, VcsDirectoryMapping mapping) {
    if (mapping.getRootSettings() instanceof VssRootSettings settings) {
      String vssProject = settings.getVssProject();
      if (!StringUtil.isEmptyOrSpaces(vssProject)) {
        return vssProject;
      }
    }
    return getVssProject(project, mapping.getDirectory());
  }

  public static boolean isSameDirectoryMapping(VcsDirectoryMapping left, VcsDirectoryMapping right) {
    return left.getVcs().equals(right.getVcs())
           && FileUtil.pathsEqual(left.getDirectory(), right.getDirectory());
  }

  public static List<VcsDirectoryMapping> getSourceSafeMappings(Project project) {
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(project);
    AbstractVcs vcs = VssVcs.getInstance(project);
    if (vcs == null) {
      return List.of();
    }
    List<VcsDirectoryMapping> roots = mgr.getDirectoryMappings(vcs);
    if (!roots.isEmpty()) {
      return roots;
    }
    String vcsName = vcs.getName();
    List<VcsDirectoryMapping> filtered = new ArrayList<>();
    for (VcsDirectoryMapping mapping : mgr.getDirectoryMappings()) {
      if (vcsName.equals(mapping.getVcs())) {
        filtered.add(mapping);
      }
    }
    return filtered;
  }

  public static boolean hasValidMappings(Project project) {
    List<VcsDirectoryMapping> mappings = getSourceSafeMappings(project);
    if (mappings.isEmpty()) {
      return false;
    }
    for (VcsDirectoryMapping mapping : mappings) {
      if (StringUtil.isEmptyOrSpaces(readVssProject(project, mapping))) {
        return false;
      }
    }
    return true;
  }
}
