package com.intellij.vssSupport.occupancy;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.commands.PropertiesCommand;
import com.intellij.vssSupport.commands.VssCheckoutStatusCommand;
import com.intellij.vssSupport.ignore.VssIgnoreService;
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
    refreshFileColor(path);
  }

  public void invalidateAll() {
    cache.clear();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!project.isDisposed()) {
        FileStatusManager.getInstance(project).fileStatusesChanged();
      }
    });
  }

  @Nullable
  public VssFileOccupancy querySync(@NotNull FilePath path) {
    if (!isVssPath(path)) {
      return null;
    }
    String key = normalizeKey(path.getPath());
    if (VssIgnoreService.getInstance(project).isIgnored(path.getPath())) {
      cache.remove(key);
      refreshFileColor(path);
      return null;
    }
    VssFileOccupancy occupancy = query(path);
    if (occupancy != null) {
      cache.put(key, occupancy);
    }
    refreshFileColor(path);
    return occupancy;
  }

  /**
   * True when VSS reports this file checked out by the configured user.
   * Uses the occupancy cache when present, otherwise {@code ss Status} (not {@code -U}).
   * Does not cache a partial result, so Properties can still load the full record.
   */
  public boolean isCheckedOutByCurrentUserNow(@NotNull FilePath path) {
    VssFileOccupancy cached = getCached(path);
    if (cached != null) {
      return cached.isCheckedOutByCurrentUser();
    }
    if (!isVssPath(path) || VssIgnoreService.getInstance(project).isIgnored(path.getPath())) {
      return false;
    }
    VssCheckoutStatusCommand statusCommand = new VssCheckoutStatusCommand(project, path);
    statusCommand.execute();
    VssCheckoutInfo checkout = statusCommand.getCheckoutInfo();
    boolean checkedOut = isCheckedOutByCurrentUser(checkout);
    if (getCached(path) == null) {
      cache.put(normalizeKey(path.getPath()),
                new VssFileOccupancy(VssUtil.getCanonicalLocalPath(path.getPath()), null, checkout, checkedOut));
      refreshFileColor(path);
    }
    return checkedOut;
  }

  public void queryAsync(@NotNull FilePath path, @NotNull Runnable onDone) {
    if (!isVssPath(path)) {
      onDone.run();
      return;
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      String key = normalizeKey(path.getPath());
      if (VssIgnoreService.getInstance(project).isIgnored(path.getPath())) {
        cache.remove(key);
        refreshFileColor(path);
        ApplicationManager.getApplication().invokeLater(onDone);
        return;
      }
      VssFileOccupancy occupancy = query(path);
      if (occupancy != null) {
        cache.put(key, occupancy);
      }
      refreshFileColor(path);
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

  @Nullable
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

  /**
   * Tree color is cached by the IDE. After checkout data changes, ask it to read {@link VssFileStatusProvider} again.
   */
  private void refreshFileColor(@NotNull FilePath path) {
    VirtualFile file = path.getVirtualFile();
    if (file == null || project.isDisposed()) {
      return;
    }
    Runnable refresh = () -> {
      if (!project.isDisposed() && file.isValid()) {
        FileStatusManager.getInstance(project).fileStatusChanged(file);
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      refresh.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(refresh);
    }
  }

  private static String normalizeKey(String path) {
    return VssUtil.getCanonicalLocalPath(path).toLowerCase();
  }

  public static FilePath filePathFromVirtualFile(@NotNull com.intellij.openapi.vfs.VirtualFile file) {
    return VcsUtil.getFilePath(file);
  }
}
