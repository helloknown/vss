package com.intellij.vssSupport;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.vssSupport.occupancy.VssFileOccupancyService;
import com.intellij.vssSupport.occupancy.VssOccupancyStatusBarWidget;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.vcsUtil.VcsImplUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.Configuration.VssMappingStorage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladimir Kondratyev
 * @author Michael Gerasimov
 */
public final class VssUtil {
  public static final int EXIT_CODE_FAILURE = 100;
  public static final int EXIT_CODE_WARNING = 1;
  public static final int EXIT_CODE_SUCCESS = 0;

  private static final char[] OUR_CHARS_TO_BE_CHOPPED = new char[]{'/', '\\'};

  private VssUtil() {
  }

  public static String getCanonicalVssPath(String vssPath) {
    vssPath = chopTrailingChars(vssPath.trim().replace('\\', '/').toLowerCase(), OUR_CHARS_TO_BE_CHOPPED);
    if ("$".equals(vssPath)) {
      vssPath = "$/";
    }
    return vssPath;
  }

  public static String normalizeDirPath(final String directory) {
    if (directory.endsWith("/") || directory.endsWith("\\")) {
      return directory.substring(0, directory.length() - 1);
    }
    return directory;
  }

  @Nullable
  public static String getLocalPath(String vssPath, Project project) {
    VcsDirectoryMapping nearestItem = getNearestMapItemForVssPath(vssPath, project);
    if (nearestItem == null) {
      return null;
    }

    String vssProject = ((VssRootSettings)nearestItem.getRootSettings()).getVssProject();
    String pathDifference = vssPath.substring(vssProject.length());
    String baseDir = nearestItem.getDirectory();
    if (StringUtil.isEmpty(baseDir)) {
      String basePath = project.getBasePath();
      baseDir = basePath != null ? basePath : "";
    }

    StringBuilder sb = new StringBuilder(baseDir);
    if (!StringUtil.endsWithChar(baseDir, '/')) {
      sb.append("/");
    }

    if (StringUtil.startsWithChar(pathDifference, '/')) {
      if (pathDifference.length() > 1) {
        sb.append(pathDifference.substring(1));
      }
    }
    else {
      sb.append(pathDifference);
    }
    String localPath = sb.toString();
    if (!StringUtil.endsWithChar(localPath, '/')) {
      return localPath.replace('/', File.separatorChar);
    }
    return localPath.substring(0, localPath.length() - 1).replace('/', File.separatorChar);
  }

  @Nullable
  private static VcsDirectoryMapping getNearestMapItemForVssPath(String vssPath, Project project) {
    vssPath = vssPath.toLowerCase();

    String vssProject = null;
    VcsDirectoryMapping nearestMapping = null;

    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(project);
    AbstractVcs vcs = VssVcs.getInstance(project);
    if (vcs == null) {
      return null;
    }
    List<VcsDirectoryMapping> roots = collectVssDirectoryMappings(mgr, vcs);

    for (VcsDirectoryMapping mapping : roots) {
      String mappingVssProject = VssMappingStorage.readVssProject(project, mapping);
      if (StringUtil.isEmptyOrSpaces(mappingVssProject)) {
        continue;
      }
      if (StringUtil.startsWithIgnoreCase(vssPath, mappingVssProject) &&
          (nearestMapping == null || vssProject.length() < mappingVssProject.length())) {
        nearestMapping = mapping;
        vssProject = mappingVssProject;
      }
    }
    return nearestMapping;
  }

  @Nullable
  public static String getVssPath(File localFile, Project project) {
    FilePath path = VcsUtil.getFilePath(localFile);
    return getVssPath(path, project);
  }

  @Nullable
  public static String getVssPath(VirtualFile file, Project project) {
    FilePath path = VcsUtil.getFilePath(file.getPath(), file.isDirectory());
    return getVssPath(path, project);
  }

  @Nullable
  public static String getVssPath(String localPath, boolean isDirectory, Project project) {
    FilePath path = VcsUtil.getFilePath(localPath, isDirectory);
    return getVssPath(path, project);
  }

  @Nullable
  public static String getVssPath(FilePath localPath, Project project) {
    VcsDirectoryMapping mapping = getNearestMappingForLocalPath(localPath.getPath(), project);
    if (mapping == null) {
      return null;
    }

    String rootVssPath = VssMappingStorage.readVssProject(project, mapping);
    if (StringUtil.isEmptyOrSpaces(rootVssPath)) {
      return null;
    }

    String baseDir = resolveMappingLocalDirectory(mapping, project);
    if (baseDir == null) {
      return null;
    }

    String pathDifference = "";
    if (!FileUtil.pathsEqual(localPath.getPath(), baseDir)) {
      String relative = FileUtil.getRelativePath(baseDir, localPath.getPath(), '/');
      if (relative == null) {
        return null;
      }
      pathDifference = relative.replace('\\', '/');
    }

    StringBuilder vssPath = new StringBuilder(rootVssPath);
    if (!StringUtil.endsWithChar(rootVssPath, '/')) {
      vssPath.append('/');
    }
    if (!pathDifference.isEmpty()) {
      vssPath.append(pathDifference);
    }
    return vssPath.toString();
  }

  @Nullable
  private static VcsDirectoryMapping getNearestMappingForLocalPath(String localPath, Project project) {
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(project);
    AbstractVcs vcs = VssVcs.getInstance(project);
    if (vcs == null) {
      return null;
    }

    VcsDirectoryMapping nearestMapping = null;
    int longestBase = -1;

    for (VcsDirectoryMapping mapping : collectVssDirectoryMappings(mgr, vcs)) {
      if (StringUtil.isEmptyOrSpaces(VssMappingStorage.readVssProject(project, mapping))) {
        continue;
      }
      String baseDir = resolveMappingLocalDirectory(mapping, project);
      if (baseDir == null || !isLocalPathUnderMapping(localPath, mapping.getDirectory(), project)) {
        continue;
      }
      int baseLen = baseDir.length();
      if (nearestMapping == null || baseLen > longestBase) {
        nearestMapping = mapping;
        longestBase = baseLen;
      }
    }
    return nearestMapping;
  }

  private static List<VcsDirectoryMapping> collectVssDirectoryMappings(ProjectLevelVcsManager mgr, AbstractVcs vcs) {
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

  @Nullable
  private static String resolveMappingLocalDirectory(VcsDirectoryMapping mapping, Project project) {
    String baseDir = mapping.getDirectory();
    if (StringUtil.isEmpty(baseDir)) {
      return project.getBasePath();
    }
    return baseDir;
  }

  private static boolean isLocalPathUnderMapping(String localPath, String mappingDirectory, Project project) {
    if (StringUtil.isEmpty(mappingDirectory)) {
      String basePath = project.getBasePath();
      return basePath != null
             && (FileUtil.pathsEqual(localPath, basePath) || FileUtil.isAncestor(basePath, localPath, false));
    }
    return FileUtil.pathsEqual(localPath, mappingDirectory)
           || FileUtil.isAncestor(mappingDirectory, localPath, false);
  }

  public static void showErrorOutput(@NonNls String message, Project project) {
    VcsImplUtil.showErrorMessage(project,
                                 VssBundle.message("message.text.operation.failed.text", message),
                                 VssBundle.message("message.title.error"));
  }

  public static void showStatusMessage(final Project project, final String message) {
    SwingUtilities.invokeLater(() -> {
      if (project != null && !project.isDisposed()) {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        if (statusBar != null) {
          statusBar.setInfo(message);
        }
      }
    });
  }

  public static boolean isRenameChange(Change change) {
    ContentRevision before = change.getBeforeRevision();
    ContentRevision after = change.getAfterRevision();
    if (before != null && after != null) {
      String prevFile = getCanonicalLocalPath(before.getFile().getPath());
      String newFile = getCanonicalLocalPath(after.getFile().getPath());
      return !prevFile.equals(newFile);
    }
    return false;
  }

  public static boolean isChangeForNew(Change change) {
    return change.getBeforeRevision() == null && change.getAfterRevision() != null;
  }

  public static boolean isChangeForDeleted(Change change) {
    return change.getBeforeRevision() != null && change.getAfterRevision() == null;
  }

  public static boolean isChangeForFolder(Change change) {
    ContentRevision revB = change.getBeforeRevision();
    ContentRevision revA = change.getAfterRevision();
    return (revA != null && revA.getFile().isDirectory()) || (revB != null && revB.getFile().isDirectory());
  }

  public static VirtualFile[] getVirtualFiles(AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    return files == null ? VirtualFile.EMPTY_ARRAY : files;
  }

  @Nullable
  public static VirtualFile getOneVirtualFile(AnActionEvent e) {
    VirtualFile[] files = getVirtualFiles(e);
    return files.length != 1 ? null : files[0];
  }

  public static FilePath[] sortPathsFromOutermost(FilePath[] files) {
    return VcsUtil.sortPathsFromOutermost(files);
  }

  /**
   * New or unversioned files are not in VSS yet — they must be edited locally and added on commit,
   * not checked out from the repository.
   */
  public static boolean isPendingVssAddition(Project project, VirtualFile file) {
    FileStatus status = FileStatusManager.getInstance(project).getStatus(file);
    if (status == FileStatus.ADDED || status == FileStatus.UNKNOWN) {
      return true;
    }
    VssVcs vcs = VssVcs.getInstance(project);
    return vcs != null && vcs.containsNew(file.getPath());
  }

  public static void ensureLocallyWritable(Project project, VirtualFile file) {
    if (file.isWritable()) {
      return;
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        ReadOnlyAttributeUtil.setReadOnlyAttribute(file, false);
      }
      catch (IOException ignored) {
        // Best effort; EditFileProvider shows a dialog when this path is user-initiated.
      }
    });
    file.refresh(false, false);
  }

  /**
   * Syncs IDE state after a successful undo checkout: occupancy cache, VCS dirty scope,
   * and local read-only attribute (unless the user chose to keep the file writable).
   */
  public static void afterUndoCheckout(@NotNull Project project, @NotNull VirtualFile file, boolean keepWritable) {
    if (project.isDisposed()) {
      return;
    }

    FilePath path = VcsUtil.getFilePath(file);
    VssFileOccupancyService occupancyService = VssFileOccupancyService.getInstance(project);
    occupancyService.invalidate(path);

    if (!keepWritable && file.isWritable()) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          ReadOnlyAttributeUtil.setReadOnlyAttribute(file, true);
        }
        catch (IOException ignored) {
        }
      });
    }

    file.refresh(true, false);
    VcsDirtyScopeManager.getInstance(project).fileDirty(file);

    ApplicationManager.getApplication().invokeLater(() -> {
      if (project.isDisposed()) {
        return;
      }
      occupancyService.queryAsync(path, () -> {
        EditorNotifications.getInstance(project).updateNotifications(file);
        VirtualFile[] selected = FileEditorManager.getInstance(project).getSelectedFiles();
        if (selected.length > 0 && selected[0].equals(file)) {
          VssOccupancyStatusBarWidget.refreshIfShowing(project, file);
        }
      });
    });
  }

  public static void afterUndoCheckoutDirectory(@NotNull Project project, @NotNull VirtualFile dir) {
    if (project.isDisposed()) {
      return;
    }
    VssFileOccupancyService.getInstance(project).invalidateAll();
    dir.refresh(true, true);
    VcsDirtyScopeManager.getInstance(project).fileDirty(dir);
  }

  public static void ensureLocallyWritableOrShowError(Project project, VirtualFile file) {
    if (file.isWritable()) {
      return;
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        ReadOnlyAttributeUtil.setReadOnlyAttribute(file, false);
      }
      catch (IOException e) {
        Messages.showErrorDialog(project,
                                 VssBundle.message("message.text.ro.set.error", file.getPath()),
                                 VssBundle.message("message.title.error"));
      }
    });
    file.refresh(false, false);
  }

  public static String getCanonicalLocalPath(String localPath) {
    localPath = chopTrailingChars(localPath.trim().replace('\\', '/'), OUR_CHARS_TO_BE_CHOPPED);
    if (localPath.length() == 2 && localPath.charAt(1) == ':') {
      localPath += '/';
    }
    return localPath;
  }

  public static String chopTrailingChars(String source, char[] chars) {
    StringBuilder sb = new StringBuilder(source);
    while (true) {
      boolean atLeastOneCharWasChopped = false;
      for (int i = 0; i < chars.length && sb.length() > 0; i++) {
        if (sb.charAt(sb.length() - 1) == chars[i]) {
          sb.deleteCharAt(sb.length() - 1);
          atLeastOneCharWasChopped = true;
        }
      }
      if (!atLeastOneCharWasChopped) {
        break;
      }
    }
    return sb.toString();
  }

  public static VirtualFile[] paths2VFiles(String[] paths) {
    VirtualFile[] files = new VirtualFile[paths.length];
    for (int i = 0; i < paths.length; i++) {
      files[i] = VcsUtil.getVirtualFile(paths[i]);
    }
    return files;
  }

  public static VirtualFile getVirtualFile(String path) {
    return VcsUtil.getVirtualFile(path);
  }

  public static VirtualFile getVirtualFile(File file) {
    return VcsUtil.getVirtualFile(file);
  }

  public static boolean isFileForVcs(@NotNull VirtualFile file, Project project, AbstractVcs host) {
    return VcsUtil.isFileForVcs(file, project, host);
  }

  public static boolean isFileForVcs(FilePath path, Project project, AbstractVcs host) {
    return VcsUtil.isFileForVcs(path, project, host);
  }

  public static boolean isFileForVcs(String path, Project project, AbstractVcs host) {
    return VcsUtil.isFileForVcs(path, project, host);
  }
}
