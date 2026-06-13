package com.intellij.vssSupport.occupancy;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.commands.PropertiesCommand;
import com.intellij.vssSupport.commands.VssCheckoutStatusCommand;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Queries and caches VSS file occupancy (checkout user + properties).
 */
public final class VssFileOccupancyService {
  private final Project project;
  private final Map<String, VssFileOccupancy> cache = new ConcurrentHashMap<>();

  public VssFileOccupancyService(@NotNull Project project) {
    this.project = project;
  }

  public static VssFileOccupancyService getInstance(@NotNull Project project) {
    return project.getService(VssFileOccupancyService.class);
  }

  @Nullable
  public VssFileOccupancy getCached(@NotNull FilePath path) {
    return cache.get(normalizeKey(path.getPath()));
  }

  public void invalidate(@NotNull FilePath path) {
    cache.remove(normalizeKey(path.getPath()));
  }

  public void invalidateAll() {
    cache.clear();
  }

  @Nullable
  public VssFileOccupancy querySync(@NotNull FilePath path) {
    if (!isVssPath(path)) {
      return null;
    }
    VssFileOccupancy occupancy = query(path);
    cache.put(normalizeKey(path.getPath()), occupancy);
    return occupancy;
  }

  public void queryAsync(@NotNull FilePath path, @NotNull Runnable onDone) {
    if (!isVssPath(path)) {
      onDone.run();
      return;
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      VssFileOccupancy occupancy = query(path);
      cache.put(normalizeKey(path.getPath()), occupancy);
      ApplicationManager.getApplication().invokeLater(onDone);
    });
  }

  private boolean isVssPath(@NotNull FilePath path) {
    VssVcs vss = VssVcs.getInstance(project);
    if (vss == null) {
      return false;
    }
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(path) == vss;
  }

  private VssFileOccupancy query(@NotNull FilePath path) {
    String localPath = VssUtil.getCanonicalLocalPath(path.getPath());

    VssCheckoutStatusCommand statusCommand = new VssCheckoutStatusCommand(project, path);
    statusCommand.execute();
    VssCheckoutInfo checkout = statusCommand.getCheckoutInfo();

    VssPropertiesInfo properties = null;
    PropertiesCommand propertiesCommand = new PropertiesCommand(project, localPath, path.isDirectory());
    propertiesCommand.execute();
    if (propertiesCommand.isValidRepositoryObject()) {
      properties = propertiesCommand.getPropertiesInfo();
    }

    boolean checkedOutByCurrentUser = isCheckedOutByCurrentUser(checkout);
    return new VssFileOccupancy(localPath, properties, checkout, checkedOutByCurrentUser);
  }

  private boolean isCheckedOutByCurrentUser(VssCheckoutInfo checkout) {
    if (!checkout.isCheckedOut()) {
      return false;
    }
    String configuredUser = VssConfiguration.getInstance(project).USER_NAME;
    if (StringUtil.isEmpty(configuredUser) || checkout.getUser() == null) {
      return false;
    }
    return configuredUser.equalsIgnoreCase(checkout.getUser().trim());
  }

  private static String normalizeKey(String path) {
    return VssUtil.getCanonicalLocalPath(path).toLowerCase();
  }

  public static FilePath filePathFromVirtualFile(@NotNull com.intellij.openapi.vfs.VirtualFile file) {
    return VcsUtil.getFilePath(file);
  }
}
