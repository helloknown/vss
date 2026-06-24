package com.intellij.vssSupport.checkouts;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssTruncatedFileNameUtil;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import com.intellij.vssSupport.commands.MyCheckoutsDirCommand;
import com.intellij.vssSupport.commands.StatusMultipleCommand;
import com.intellij.vssSupport.commands.VssCheckoutStatusCommand;
import com.intellij.vssSupport.occupancy.VssCheckoutInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MyCheckoutsScanner {
  private static final Logger LOG = Logger.getInstance(MyCheckoutsScanner.class);

  public enum ScanMode {
    QUICK,
    FOLDER_DIR,
    FULL_TREE
  }

  private MyCheckoutsScanner() {
  }

  @NotNull
  public static List<VssCheckoutEntry> scan(@NotNull Project project,
                                          @NotNull ProgressIndicator indicator,
                                          @NotNull ScanMode mode,
                                          @Nullable VirtualFile scopeFolder) {
    MyCheckoutsUserFilter userFilter = VssMyCheckoutsService.getInstance(project).getUserFilter();
    if (mode == ScanMode.FOLDER_DIR && scopeFolder != null) {
      return scanFolderByDir(project, scopeFolder, userFilter, indicator);
    }

    VssVcs vcs = VssVcs.getInstance(project);
    if (vcs == null || VssConfiguration.getInstance(project).checkCmdPath() != null) {
      return List.of();
    }

    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(project);
    List<VcsDirectoryMapping> mappings = mgr.getDirectoryMappings(vcs);
    if (mappings.isEmpty()) {
      return List.of();
    }

    indicator.setIndeterminate(true);
    indicator.setText(mode == ScanMode.FULL_TREE
                        ? VssBundle.message("message.my.checkouts.collecting.writable")
                        : VssBundle.message("message.my.checkouts.collecting.candidates"));
    List<String> candidateFiles = mode == ScanMode.FULL_TREE
                                    ? collectWritableFiles(project, vcs, mgr, mappings, indicator)
                                    : collectQuickCandidates(project, vcs, indicator);
    return queryCheckoutEntries(project, candidateFiles, userFilter, indicator);
  }

  @NotNull
  private static List<VssCheckoutEntry> scanFolderByDir(@NotNull Project project,
                                                      @NotNull VirtualFile folder,
                                                      @NotNull MyCheckoutsUserFilter userFilter,
                                                      @NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(true);
    indicator.setText(VssBundle.message("message.my.checkouts.querying.folder", folder.getPresentableName()));

    List<VcsException> errors = new ArrayList<>();
    MyCheckoutsDirCommand cmd = new MyCheckoutsDirCommand(project, folder.getPath(), userFilter, errors);
    cmd.execute();
    if (!errors.isEmpty()) {
      LOG.info("My checkouts folder scan: " + errors.get(0).getMessage());
    }

    List<VssCheckoutEntry> entries = new ArrayList<>();
    for (VssCheckoutEntry entry : cmd.getEntries()) {
      indicator.checkCanceled();
      if (isUnderFolder(entry.localPath(), folder.getPath())) {
        entries.add(entry);
      }
    }
    return entries;
  }

  @NotNull
  private static List<VssCheckoutEntry> queryCheckoutEntries(@NotNull Project project,
                                                             @NotNull List<String> candidateFiles,
                                                             @NotNull MyCheckoutsUserFilter userFilter,
                                                             @NotNull ProgressIndicator indicator) {
    if (candidateFiles.isEmpty()) {
      return List.of();
    }

    Map<String, VssCheckoutEntry> byPath = new LinkedHashMap<>();
    indicator.setIndeterminate(false);
    indicator.setText(VssBundle.message("message.my.checkouts.querying.status"));

    try {
      StatusMultipleCommand batch = new StatusMultipleCommand(project, candidateFiles, !userFilter.matchAllUsers());
      batch.execute();

      List<String> checkedOut = new ArrayList<>();
      for (String localPath : candidateFiles) {
        indicator.checkCanceled();
        if (batch.isCheckedout(localPath)) {
          checkedOut.add(localPath);
        }
      }

      int detailTotal = checkedOut.size();
      for (int index = 0; index < detailTotal; index++) {
        indicator.checkCanceled();
        String localPath = checkedOut.get(index);
        indicator.setFraction(detailTotal == 0 ? 1.0 : (double) index / detailTotal);
        indicator.setText2(new File(localPath).getName());

        VssCheckoutStatusCommand cmd = new VssCheckoutStatusCommand(project, VcsUtil.getFilePath(localPath));
        cmd.execute();
        VssCheckoutInfo info = cmd.getCheckoutInfo();
        if (!info.isCheckedOut()) {
          continue;
        }
        String checkoutUser = StringUtil.notNullize(info.getUser());
        if (!userFilter.matches(checkoutUser)) {
          continue;
        }
        String canonical = VssUtil.getCanonicalLocalPath(localPath);
        byPath.put(canonical.toLowerCase(), toEntry(canonical, info));
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (RuntimeException e) {
      LOG.warn("My checkouts status query failed", e);
    }

    indicator.setFraction(1.0);
    return List.copyOf(byPath.values());
  }

  @NotNull
  private static List<String> collectQuickCandidates(@NotNull Project project,
                                                   @NotNull VssVcs vcs,
                                                   @NotNull ProgressIndicator indicator) {
    Set<String> paths = new LinkedHashSet<>();
    ChangeListManager clm = ChangeListManager.getInstance(project);
    for (Change change : clm.getAllChanges()) {
      indicator.checkCanceled();
      VirtualFile vf = change.getVirtualFile();
      if (vf == null || vf.isDirectory() || !vf.isWritable()) {
        continue;
      }
      if (VcsUtil.isFileForVcs(VcsUtil.getFilePath(vf), project, vcs)) {
        paths.add(vf.getPath());
      }
    }
    for (VssCheckoutEntry entry : VssMyCheckoutsService.getInstance(project).getEntries()) {
      indicator.checkCanceled();
      paths.add(entry.localPath());
    }
    return new ArrayList<>(paths);
  }

  @NotNull
  private static List<String> collectWritableFiles(@NotNull Project project,
                                                 @NotNull VssVcs vcs,
                                                 @NotNull ProjectLevelVcsManager mgr,
                                                 @NotNull List<VcsDirectoryMapping> mappings,
                                                 @NotNull ProgressIndicator indicator) {
    List<String> files = new ArrayList<>();
    for (VcsDirectoryMapping mapping : mappings) {
      indicator.checkCanceled();
      VirtualFile root = VssUtil.getVirtualFile(mapping.getDirectory());
      if (root == null || !root.isDirectory()) {
        continue;
      }
      mgr.iterateVcsRoot(root, file -> {
        indicator.checkCanceled();
        if (vcs.equals(mgr.getVcsFor(file))) {
          VirtualFile vf = file.getVirtualFile();
          if (vf != null && !vf.isDirectory() && vf.isWritable()) {
            files.add(vf.getPath());
          }
        }
        return true;
      });
    }
    return files;
  }

  static boolean isUnderFolder(@NotNull String localPath, @NotNull String folderPath) {
    String file = VssUtil.getCanonicalLocalPath(localPath).replace('\\', '/').toLowerCase();
    String folder = VssUtil.getCanonicalLocalPath(folderPath).replace('\\', '/').toLowerCase();
    return file.equals(folder) || file.startsWith(folder + "/");
  }

  @NotNull
  private static VssCheckoutEntry toEntry(@NotNull String localPath, @NotNull VssCheckoutInfo info) {
    File file = new File(localPath);
      String workingFolder = StringUtil.notNullize(info.getWorkingFolder());
      if (workingFolder.isEmpty()) {
        workingFolder = StringUtil.notNullize(file.getParent());
      }
      String resolvedPath = VssTruncatedFileNameUtil.completeTruncatedLocalPath(localPath);
      return new VssCheckoutEntry(
      resolvedPath,
      VssTruncatedFileNameUtil.displayFileName(resolvedPath, file.getName()),
      StringUtil.notNullize(info.getUser()),
      StringUtil.notNullize(info.getDateTime()),
      workingFolder
    );
  }
}
