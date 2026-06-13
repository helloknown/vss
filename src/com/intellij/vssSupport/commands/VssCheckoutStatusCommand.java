package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.occupancy.VssCheckoutInfo;
import com.intellij.vssSupport.occupancy.VssCheckoutStatusParser;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@code ss Status} for a single file and parses checkout occupancy.
 */
public final class VssCheckoutStatusCommand extends VssCommandAbstract {
  @NonNls private static final String STATUS_COMMAND = "Status";

  private final FilePath filePath;
  private VssCheckoutInfo checkoutInfo = VssCheckoutInfo.NOT_CHECKED_OUT;

  public VssCheckoutStatusCommand(@NotNull Project project, @NotNull FilePath filePath) {
    super(project, new ArrayList<>());
    this.filePath = filePath;
  }

  public void execute() {
    String pathNorm = VssUtil.getCanonicalLocalPath(filePath.getPath());
    String vssPath = VssUtil.getVssPath(filePath, myProject);
    if (vssPath == null || vssPath.isEmpty()) {
      vssPath = VssUtil.getVssPath(pathNorm, filePath.isDirectory(), myProject);
    }
    if (vssPath == null || vssPath.isEmpty()) {
      return;
    }

    File file = new File(pathNorm.replace('/', File.separatorChar));
    String workingPath = file.getParentFile() != null
                         ? file.getParentFile().getAbsolutePath()
                         : StringUtil.notNullize(myProject.getBasePath(), pathNorm);

    List<String> options = formOptions(STATUS_COMMAND, vssPath, _I_Y_OPTION);
    runProcess(options, workingPath, new CheckoutStatusListener(myErrors));
  }

  public VssCheckoutInfo getCheckoutInfo() {
    return checkoutInfo;
  }

  public List<VcsException> getQueryErrors() {
    return myErrors;
  }

  private class CheckoutStatusListener extends VssOutputCollector {
    CheckoutStatusListener(List<VcsException> errors) {
      super(errors);
    }

    @Override
    public void everythingFinishedImpl(String output) {
      checkoutInfo = VssCheckoutStatusParser.parse(output);
    }
  }
}
