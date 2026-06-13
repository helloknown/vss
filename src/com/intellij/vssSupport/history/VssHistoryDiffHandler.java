package com.intellij.vssSupport.history;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.DiffFromHistoryHandler;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.commands.GetFileCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public final class VssHistoryDiffHandler implements DiffFromHistoryHandler {
  private final Project project;

  public VssHistoryDiffHandler(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void showDiffForOne(@NotNull AnActionEvent event,
                             @NotNull Project project,
                             @NotNull FilePath path,
                             @NotNull VcsFileRevision revision1,
                             @Nullable VcsFileRevision revision2) {
    if (revision2 != null) {
      showDiffForTwo(project, path, revision1, revision2);
      return;
    }
    showRevisionAgainstLocal(path, revision1);
  }

  @Override
  public void showDiffForTwo(@NotNull Project project,
                             @NotNull FilePath path,
                             @NotNull VcsFileRevision revision1,
                             @NotNull VcsFileRevision revision2) {
  showDiff(path, revision1, revision2);
  }

  private void showRevisionAgainstLocal(@NotNull FilePath path, @NotNull VcsFileRevision revision) {
    VirtualFile localFile = path.getVirtualFile();
    if (localFile == null) {
      return;
    }
    FileDocumentManager.getInstance().saveAllDocuments();

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        byte[] revisionBytes = loadRevisionBytes(path, revision);
        DiffContent revisionContent = createDiffContent(path, revisionBytes);
        DiffContent localContent = DiffContentFactory.getInstance().create(project, localFile);

        String title = VssBundle.message("dialog.title.diff.for.file", path.getPresentableUrl());
        String leftTitle = revisionTitle(revision);
        String rightTitle = VssBundle.message("diff.content.title.local");

        DiffRequest request = new SimpleDiffRequest(title, revisionContent, localContent, leftTitle, rightTitle);
        ApplicationManager.getApplication().invokeLater(() ->
          DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME));
      }
      catch (VcsException | IOException e) {
        ApplicationManager.getApplication().invokeLater(() ->
          com.intellij.openapi.ui.Messages.showErrorDialog(project, e.getMessage(),
            VssBundle.message("message.title.error")));
      }
    });
  }

  private void showDiff(@NotNull FilePath path,
                          @NotNull VcsFileRevision revision1,
                          @NotNull VcsFileRevision revision2) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        byte[] leftBytes = loadRevisionBytes(path, revision1);
        byte[] rightBytes = loadRevisionBytes(path, revision2);
        DiffContent left = createDiffContent(path, leftBytes);
        DiffContent right = createDiffContent(path, rightBytes);

        String title = VssBundle.message("dialog.title.diff.for.file", path.getPresentableUrl());
        DiffRequest request = new SimpleDiffRequest(title, left, right,
                                                    revisionTitle(revision1), revisionTitle(revision2));
        ApplicationManager.getApplication().invokeLater(() ->
          DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME));
      }
      catch (VcsException | IOException e) {
        ApplicationManager.getApplication().invokeLater(() ->
          com.intellij.openapi.ui.Messages.showErrorDialog(project, e.getMessage(),
            VssBundle.message("message.title.error")));
      }
    });
  }

  private byte[] loadRevisionBytes(@NotNull FilePath path, @NotNull VcsFileRevision revision) throws VcsException, IOException {
    byte[] fromRevision = revision.loadContent();
    if (fromRevision != null && fromRevision.length > 0) {
      return fromRevision;
    }

    String version = revisionNumberAsString(revision.getRevisionNumber());
    ArrayList<VcsException> errors = new ArrayList<>();
    String tmpDir = FileUtil.getTempDirectory();
    GetFileCommand cmd = new GetFileCommand(project, path.getPath(), version, errors);
    cmd.setOutputPath(tmpDir);
    cmd.execute();
    if (!errors.isEmpty()) {
      throw errors.get(0);
    }

    File fileContent = new File(tmpDir, path.getName());
    return FileUtil.loadFileBytes(fileContent);
  }

  private static String revisionNumberAsString(VcsRevisionNumber revisionNumber) {
    if (revisionNumber instanceof VcsRevisionNumber.Int intRevision) {
      return String.valueOf(intRevision.getValue());
    }
    return revisionNumber.asString();
  }

  private DiffContent createDiffContent(@NotNull FilePath path, byte[] bytes) throws IOException {
    String extension = path.getName();
    int dot = extension.lastIndexOf('.');
    String suffix = dot >= 0 ? extension.substring(dot) : ".tmp";
    File tempFile = FileUtil.createTempFile("vss-history", suffix, true);
    FileUtil.writeToFile(tempFile, bytes);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
    if (virtualFile == null) {
      throw new IOException("Cannot create temp file for diff: " + tempFile.getPath());
    }
    return DiffContentFactory.getInstance().create(project, virtualFile);
  }

  private static String revisionTitle(@NotNull VcsFileRevision revision) {
    return VssBundle.message("diff.content.title.revision", revision.getRevisionNumber().asString());
  }
}
