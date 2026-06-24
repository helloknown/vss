package com.intellij.vssSupport.occupancy;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vssSupport.commands.DirectoryCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches {@code ss Dir} results to avoid repeated slow VSS calls during change-list refresh.
 */
public final class VssDirectoryStatusCache {
  private static final long TTL_MS = 60_000L;

  private final Map<String, Entry> cache = new ConcurrentHashMap<>();

  public static VssDirectoryStatusCache getInstance(@NotNull Project project) {
    return project.getService(VssDirectoryStatusCache.class);
  }

  public void invalidateAll() {
    cache.clear();
  }

  public void invalidate(@NotNull String contentRootPath) {
    cache.remove(normalizeKey(contentRootPath));
  }

  @Nullable
  public Snapshot getOrQuery(@NotNull Project project, @NotNull String path, @NotNull List<VcsException> errors) {
    String key = normalizeKey(path);
    Entry entry = cache.get(key);
    if (entry != null && !entry.isExpired()) {
      return entry.snapshot;
    }

    DirectoryCommand cmd = new DirectoryCommand(project, path, errors);
    cmd.execute();
    if (!errors.isEmpty()) {
      return null;
    }

    Snapshot snapshot = new Snapshot(
      Collections.unmodifiableSet(new HashSet<>(cmd.getFilesInProject())),
      Collections.unmodifiableSet(new HashSet<>(cmd.getFilesCheckedOut()))
    );
    cache.put(key, new Entry(snapshot, System.currentTimeMillis()));
    return snapshot;
  }

  public boolean isInProject(@NotNull Snapshot snapshot, @NotNull String path) {
    return snapshot.filesInProject().contains(path);
  }

  public boolean isCheckedOut(@NotNull Snapshot snapshot, @NotNull String path) {
    return snapshot.filesCheckedOut().contains(path);
  }

  private static String normalizeKey(String path) {
    return path.replace('\\', '/').toLowerCase();
  }

  public record Snapshot(@NotNull Set<String> filesInProject, @NotNull Set<String> filesCheckedOut) {
  }

  private static final class Entry {
    private final Snapshot snapshot;
    private final long createdAtMs;

    private Entry(Snapshot snapshot, long createdAtMs) {
      this.snapshot = snapshot;
      this.createdAtMs = createdAtMs;
    }

    private boolean isExpired() {
      return System.currentTimeMillis() - createdAtMs > TTL_MS;
    }
  }
}
