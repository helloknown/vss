package com.intellij.vssSupport.ignore;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads {@code .vssignore} from project and VCS content roots.
 */
@Service(Service.Level.PROJECT)
public final class VssIgnoreService {
  public static final String IGNORE_FILE_NAME = ".vssignore";

  private static final Logger LOG = Logger.getInstance(VssIgnoreService.class);

  private final Project project;
  private final AtomicReference<CachedMatchers> cache = new AtomicReference<>();

  public VssIgnoreService(@NotNull Project project) {
    this.project = project;
  }

  public static VssIgnoreService getInstance(@NotNull Project project) {
    return project.getService(VssIgnoreService.class);
  }

  public void invalidate() {
    cache.set(null);
  }

  public boolean isIgnored(@NotNull String localPath) {
    String canonical = VssUtil.getCanonicalLocalPath(localPath);
    String path = canonical;
    while (true) {
      if (matchesRelativePath(path)) {
        return true;
      }
      int slash = path.lastIndexOf('/');
      if (slash < 0) {
        break;
      }
      path = path.substring(0, slash);
    }
    return false;
  }

  private boolean matchesRelativePath(@NotNull String absolutePath) {
    for (RootMatcher matcher : getMatchers().matchers()) {
      String relative = matcher.relativePath(absolutePath);
      if (relative != null && matcher.matcher().isIgnored(relative)) {
        return true;
      }
    }
    return false;
  }

  public boolean isIgnored(@NotNull VirtualFile file) {
    return isIgnored(file.getPath());
  }

  @NotNull
  private CachedMatchers getMatchers() {
    CachedMatchers cached = cache.get();
    long stamp = computeStamp();
    if (cached != null && cached.stamp() == stamp) {
      return cached;
    }
    CachedMatchers loaded = loadMatchers(stamp);
    cache.set(loaded);
    return loaded;
  }

  private long computeStamp() {
    long stamp = 0L;
    for (Path ignoreFile : locateIgnoreFiles()) {
      try {
        if (Files.isRegularFile(ignoreFile)) {
          stamp += Files.getLastModifiedTime(ignoreFile).toMillis();
          stamp += Files.size(ignoreFile);
        }
      }
      catch (IOException ignored) {
      }
    }
    return stamp;
  }

  @NotNull
  private CachedMatchers loadMatchers(long stamp) {
    List<RootMatcher> matchers = new ArrayList<>();
    for (Path ignoreFile : locateIgnoreFiles()) {
      try {
        if (!Files.isRegularFile(ignoreFile)) {
          continue;
        }
        String content = Files.readString(ignoreFile, StandardCharsets.UTF_8);
        VssIgnoreMatcher matcher = VssIgnoreMatcher.parse(content);
        if (matcher == VssIgnoreMatcher.empty()) {
          continue;
        }
        String root = VssUtil.getCanonicalLocalPath(ignoreFile.getParent().toString());
        matchers.add(new RootMatcher(root, matcher));
      }
      catch (IOException e) {
        LOG.info("Failed to read " + ignoreFile + ": " + e.getMessage());
      }
    }
    return new CachedMatchers(stamp, List.copyOf(matchers));
  }

  @NotNull
  private List<Path> locateIgnoreFiles() {
    List<Path> files = new ArrayList<>();
    String basePath = project.getBasePath();
    if (basePath != null) {
      files.add(Path.of(basePath, IGNORE_FILE_NAME));
    }

    VssVcs vcs = VssVcs.getInstance(project);
    if (vcs != null) {
      for (VcsDirectoryMapping mapping : ProjectLevelVcsManager.getInstance(project).getDirectoryMappings(vcs)) {
        Path root = Path.of(VssUtil.getCanonicalLocalPath(mapping.getDirectory()));
        Path ignoreFile = root.resolve(IGNORE_FILE_NAME);
        if (!files.contains(ignoreFile)) {
          files.add(ignoreFile);
        }
      }
    }
    return files;
  }

  private record CachedMatchers(long stamp, List<RootMatcher> matchers) {
  }

  private static final class RootMatcher {
    private final String rootPath;
    private final VssIgnoreMatcher matcher;

    private RootMatcher(@NotNull String rootPath, @NotNull VssIgnoreMatcher matcher) {
      this.rootPath = rootPath.replace('\\', '/').toLowerCase();
      this.matcher = matcher;
    }

    @NotNull
    VssIgnoreMatcher matcher() {
      return matcher;
    }

    @Nullable
    String relativePath(@NotNull String absolutePath) {
      String normalized = absolutePath.replace('\\', '/');
      String root = rootPath.endsWith("/") ? rootPath : rootPath + "/";
      String lower = normalized.toLowerCase();
      if (lower.equals(rootPath)) {
        return "";
      }
      if (!lower.startsWith(root)) {
        return null;
      }
      return normalized.substring(root.length());
    }
  }
}
