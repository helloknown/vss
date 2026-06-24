package com.intellij.vssSupport.commands;



import com.intellij.openapi.project.Project;

import com.intellij.openapi.vcs.VcsException;

import com.intellij.openapi.vfs.VirtualFile;

import com.intellij.vssSupport.VssUtil;

import com.intellij.vssSupport.checkouts.VssCheckoutEntry;

import org.jetbrains.annotations.NonNls;



import java.util.ArrayList;

import java.util.List;



public final class CheckoutScanCommand extends VssCommandAbstract {

  @NonNls private static final String DIR_COMMAND = "Dir";

  @NonNls private static final String RECURSIVE_OPTION = "-R";

  @NonNls private static final String EXTENDED_FORMAT_OPTION = "-E";



  private final String localDirectoryPath;

  private final String vssProjectPath;

  private final boolean recursive;

  private final List<VssCheckoutEntry> checkouts = new ArrayList<>();



  public CheckoutScanCommand(Project project, String localDirectoryPath, List<VcsException> errors, boolean recursive) {

    super(project, errors);

    VirtualFile dir = VssUtil.getVirtualFile(localDirectoryPath);

    if (dir == null || !dir.isDirectory()) {

      throw new IllegalArgumentException("Checkout scan requires an existing local directory: " + localDirectoryPath);

    }

    this.localDirectoryPath = dir.getPath();

    this.vssProjectPath = VssUtil.getVssPath(dir, myProject);

    this.recursive = recursive;

  }



  public List<VssCheckoutEntry> getCheckouts() {

    return checkouts;

  }



  public void execute() {

    CheckoutScanCommandListener listener =

      new CheckoutScanCommandListener(myProject, localDirectoryPath, checkouts, myErrors);

    List<String> options = formOptions(DIR_COMMAND, EXTENDED_FORMAT_OPTION, vssProjectPath);

    if (recursive) {

      options.add(1, RECURSIVE_OPTION);

    }

    runProcess(options, localDirectoryPath, listener);

  }

}

