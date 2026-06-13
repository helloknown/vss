package com.intellij.vssSupport.Configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.commands.VssCommandAbstract;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a VSS project path using the ss.exe Properties command.
 */
public final class VssMappingChecker {
  private static final String VSS_PROJECT_PREFIX = "$/";

  private VssMappingChecker() {
  }

  public static void checkVssProject(Project project, String vssProject) throws VcsException {
    String path = vssProject != null ? vssProject.trim() : "";
    if (!path.startsWith(VSS_PROJECT_PREFIX)) {
      throw new VcsException(VssBundle.message("message.text.configuration.invalid.project"));
    }
    List<VcsException> errors = new ArrayList<>();
    new CheckProjectCommand(project, path, errors).execute();
    if (!errors.isEmpty()) {
      throw errors.get(0);
    }
  }

  private static final class CheckProjectCommand extends VssCommandAbstract {
    @NonNls private static final String PROPS_COMMAND = "Properties";
    @NonNls private static final String NO_RECURSIVE_SWITCH = "-R-";

    private final String vssProjectPath;

    CheckProjectCommand(Project project, String vssProjectPath, List<VcsException> errors) {
      super(project, errors);
      this.vssProjectPath = vssProjectPath;
    }

    @Override
    public void execute() {
      List<String> options = formOptions(PROPS_COMMAND, NO_RECURSIVE_SWITCH, vssProjectPath, _I_Y_OPTION);
      String basePath = myProject.getBasePath();
      runProcess(options, basePath != null ? basePath : "");
    }
  }
}
