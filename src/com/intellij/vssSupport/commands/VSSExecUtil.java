package com.intellij.vssSupport.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author LloiX
 */
public class VSSExecUtil {
  public static final Logger LOG = Logger.getInstance("#com.intellij.vssSupport.commands.VSSExecUtil");

  @NonNls private static final String SYSTEMROOT_VAR = "SYSTEMROOT";
  @NonNls private static final String TEMP_VAR = "TEMP";
  @NonNls private static final String USER_SIG_OPTION_PREFIX = " -Y";

  private static final int TIMEOUT_LIMIT = 40;
  private static final int TIMEOUT_EXIT_CODE = -1000;
  private static final int DEFAULT_ERROR_EXIT_CODE = -1;

  private VSSExecUtil() {}

  public interface UserInput {
    void doInput(java.io.Writer writer);
  }

  public synchronized static void runProcess(@NotNull Project project,
                                             String exePath, List<String> paremeters,
                                             HashMap<String, String> envParams, String workingDir,
                                             VssOutputCollector listener) throws ExecutionException {
    String[] programParams = ArrayUtil.toStringArray(paremeters);
    addVSS2005Values(envParams);

    GeneralCommandLine cmdLine = new GeneralCommandLine();
    cmdLine.addParameters(programParams);
    cmdLine.setWorkDirectory(workingDir);
    cmdLine.getEnvironment().putAll(envParams);
    cmdLine.setExePath(exePath);

    LOG.info(cmdLine.getCommandLineString());

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      String descriptor = prepareTitleString(cmdLine.getCommandLineString());
      progress.setText2(descriptor);
    }

    runProcessImpl(project, listener, cmdLine);

    if (progress != null) {
      progress.setText("");
    }
  }

  private static void runProcessImpl(@NotNull final Project project, VssOutputCollector listener, GeneralCommandLine cmdLine)
    throws ExecutionException {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      LOG.warn("VSS command started on EDT and may freeze the UI: " + cmdLine.getCommandLineString());
    }

    Process process = null;
    VssStreamReader outListener = null;
    VssStreamReader errListener = null;
    Thread outThread = null;
    Thread errThread = null;
    try {
      int rc = DEFAULT_ERROR_EXIT_CODE;
      try {
        process = cmdLine.createProcess();
        outListener = new VssStreamReader(process.getInputStream(), project);
        errListener = new VssStreamReader(process.getErrorStream(), project);
        outThread = new Thread(outListener, "VSS stdout");
        errThread = new Thread(errListener, "VSS stderr");
        outThread.start();
        errThread.start();

        boolean completed = process.waitFor(TIMEOUT_LIMIT, TimeUnit.SECONDS);
        if (!completed) {
          rc = TIMEOUT_EXIT_CODE;
          process.destroyForcibly();
        }
        else {
          rc = process.exitValue();
        }

        joinQuietly(outThread);
        joinQuietly(errThread);
      }
      catch (ExecutionException e) {
        listener.onCommandCriticalFail(e.getMessage());
        return;
      }
      catch (InterruptedException e) {
        listener.onCommandCriticalFail(e.getMessage());
        return;
      }

      if (rc == TIMEOUT_EXIT_CODE) {
        listener.onCommandCriticalFail(VssBundle.message("message.text.process.shutdown.on.timeout"));
        LOG.info("++ Command Shutdown detected ++");
      }
      else if (outListener.getReason() != null || errListener.getReason() != null) {
        String reason = outListener.getReason() != null ? outListener.getReason() : errListener.getReason();

        LOG.info("++ Critical error detected: " + reason);
        listener.setExitCode(rc);
        listener.onCommandCriticalFail(reason);
        invokeListenerFinished(listener, reason);

        if (process != null && process.isAlive()) {
          process.destroyForcibly();
        }
      }
      else {
        String text = errListener.getReadString() + outListener.getReadString();

        listener.setExitCode(rc);
        invokeListenerFinished(listener, text);
      }
    }
    finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  private static void invokeListenerFinished(VssOutputCollector listener, String output) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      listener.everythingFinishedImpl(output);
      return;
    }
    ApplicationManager.getApplication().invokeAndWait(() -> listener.everythingFinishedImpl(output));
  }

  private static void joinQuietly(Thread thread) {
    if (thread != null) {
      try {
        thread.join(5000);
      }
      catch (InterruptedException ignored) {
      }
    }
  }

  public static void runProcessDoNotWaitForTermination(String exePath, String[] programParms,
                                                       HashMap<String, String> envParams) throws ExecutionException {
    addVSS2005Values(envParams);

    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.addParameters(programParms);
    commandLine.getEnvironment().putAll(envParams);
    commandLine.setExePath(exePath);

    final OSProcessHandler result = new OSProcessHandler(commandLine);
    result.startNotify();
  }

  /**
   * Add two system environment variables to the environment of this process.
   * This is required for Visual SourceSafe 2005 support.
   */
  private static void addVSS2005Values(HashMap<String, String> envParams) {
    String sysRootVar = System.getenv(SYSTEMROOT_VAR);
    String tempVar = System.getenv(TEMP_VAR);
    if (sysRootVar != null) {
      envParams.put(SYSTEMROOT_VAR, sysRootVar);
    }
    if (tempVar != null) {
      envParams.put(TEMP_VAR, tempVar);
    }
  }

  private static String prepareTitleString(String original) {
    String result = original;
    int index = result.indexOf(USER_SIG_OPTION_PREFIX);
    if (index != -1) {
      int blankIndex = result.indexOf(' ', index + 2);
      if (blankIndex == -1) {
        result = result.substring(0, index);
      }
      else {
        result = result.substring(0, index) + result.substring(blankIndex);
      }
    }
    return result;
  }
}
