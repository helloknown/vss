package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HistoryCommand extends VssCommandAbstract
{
  @NonNls private static final String HISTORY_COMMAND = "History";

  public ArrayList<HistoryParser.SubmissionData> changes;
  private final FilePath filePath;
  private final String localPath;

  public HistoryCommand(Project project, FilePath filePath, List<VcsException> errors)
  {
    super(project, errors);
    this.filePath = filePath;
    this.localPath = filePath.getPath();
    this.changes = new ArrayList<>();
  }

  public HistoryCommand(Project project, String localPath, List<VcsException> errors)
  {
    super(project, errors);
    this.localPath = localPath;
    this.filePath = VcsUtil.getFilePath(localPath, false);
    this.changes = new ArrayList<>();
  }

  public void execute()
  {
    String pathNorm = VssUtil.getCanonicalLocalPath(localPath);
    String vssPath = VssUtil.getVssPath(filePath, myProject);
    if (vssPath == null || vssPath.isEmpty()) {
      vssPath = VssUtil.getVssPath(pathNorm, filePath.isDirectory(), myProject);
    }

    if (vssPath == null || vssPath.isEmpty()) {
      myErrors.add(new VcsException(VssBundle.message("message.text.specify.content.roots")));
      return;
    }

    String workingPath = resolveWorkingPath(pathNorm);
    List<String> options = formOptions(HISTORY_COMMAND, vssPath, _I_Y_OPTION);
    runProcess(options, workingPath, new VssHistoryListener(myErrors));
  }

  private String resolveWorkingPath(String pathNorm)
  {
    File file = new File(pathNorm.replace('/', File.separatorChar));
    if (file.getParentFile() != null) {
      return file.getParentFile().getAbsolutePath();
    }
    String basePath = myProject.getBasePath();
    return basePath != null ? basePath : pathNorm;
  }

  public class VssHistoryListener extends VssOutputCollector
  {
    public VssHistoryListener(List<VcsException> errors) {
      super(errors);
    }

    public void everythingFinishedImpl(final String output)
    {
      if (VssUtil.EXIT_CODE_SUCCESS == getExitCode()) {
        changes = HistoryParser.parse(output);
      }
      else {
        myErrors.add(new VcsException(output));
      }
    }
  }
}
