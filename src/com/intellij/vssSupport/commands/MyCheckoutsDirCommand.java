package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssTruncatedFileNameUtil;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.checkouts.MyCheckoutsUserFilter;
import com.intellij.vssSupport.checkouts.VssCheckoutEntry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs {@code ss Dir -R -E} and parses checked-out files for the My Checkouts tool window.
 */
public final class MyCheckoutsDirCommand extends VssCommandAbstract {
  @NonNls private static final String DIR_COMMAND = "Dir";
  @NonNls private static final String RECURSIVE_OPTION = "-R";
  @NonNls private static final String EXTENDED_FORMAT_OPTION = "-E";

  private final String localRootPath;
  private final String vssProjectPath;
  private final MyCheckoutsUserFilter userFilter;
  private final Map<String, VssCheckoutEntry> entries = new LinkedHashMap<>();

  public MyCheckoutsDirCommand(@NotNull Project project,
                               @NotNull String path,
                               @NotNull MyCheckoutsUserFilter userFilter,
                               @NotNull List<VcsException> errors) {
    super(project, errors);
    this.userFilter = userFilter;
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(myProject);
    VirtualFile aux = VssUtil.getVirtualFile(path);
    VirtualFile contentRoot = mgr.getVcsRootFor(aux);
    if (contentRoot == null) {
      throw new IllegalArgumentException("Directory command requires a path under a VCS root");
    }
    localRootPath = contentRoot.getPath();
    vssProjectPath = VssUtil.getVssPath(aux, myProject);
  }

  @NotNull
  public List<VssCheckoutEntry> getEntries() {
    return List.copyOf(entries.values());
  }

  @Override
  public void execute() {
    if (vssProjectPath == null || vssProjectPath.isEmpty()) {
      myErrors.add(new com.intellij.openapi.vcs.VcsException("VSS project path is not configured for this folder"));
      return;
    }
    List<String> options = formOptions(DIR_COMMAND, RECURSIVE_OPTION, EXTENDED_FORMAT_OPTION, vssProjectPath);
    runProcess(options, localRootPath, new MyCheckoutsDirListener(myErrors));
  }

  private final class MyCheckoutsDirListener extends VssOutputCollector {
    @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";
    @Nullable private String lastEntryKey;

    MyCheckoutsDirListener(List<VcsException> errors) {
      super(errors);
    }

    @Override
    public void everythingFinishedImpl(String output) {
      if (output.contains(NOT_EXISTING_MESSAGE)) {
        return;
      }
      String localFolder = localRootPath;
      String[] lines = LineTokenizer.tokenize(output, false);
      int offset = 0;
      while (offset < lines.length) {
        String line = lines[offset];
        if (!line.isEmpty()) {
          LineType lineType = whatSubProjectLine(lines, offset);
          if (lineType != LineType.NO_PROJECT) {
            localFolder = constructLocalFromSubproject(lines, offset);
            localFolder = VssUtil.getLocalPath(localFolder, myProject);
            offset += lineType == LineType.SIMPLE_FORMAT ? 0 : 1;
            lastEntryKey = null;
          }
          else if (isWorkingFolderContinuation(line)) {
            appendWorkingFolderContinuation(line.trim());
          }
          else if (!(line.charAt(0) == '$' && line.charAt(line.length() - 1) != ':')) {
            parseLine(localFolder, line);
          }
          else {
            lastEntryKey = null;
          }
        }
        offset++;
      }
    }

    private boolean isWorkingFolderContinuation(@NotNull String line) {
      if (lastEntryKey == null || line.isEmpty()) {
        return false;
      }
      if (line.startsWith("$") || line.startsWith("!")) {
        return false;
      }
      return !looksLikeFileCheckoutLine(line);
    }

    private void appendWorkingFolderContinuation(@NotNull String continuation) {
      if (lastEntryKey == null) {
        return;
      }
      VssCheckoutEntry existing = entries.get(lastEntryKey);
      if (existing == null) {
        return;
      }
      String folder = existing.workingFolder();
      if (!folder.isEmpty()) {
        folder += " ";
      }
      folder += continuation;
      entries.put(lastEntryKey,
                  new VssCheckoutEntry(existing.localPath(), existing.fileName(),
                                       existing.checkoutUser(), existing.checkoutDate(), folder));
    }

    private void parseLine(@NotNull String lastFolderName, @NotNull String line) {
      if (line.indexOf('\\') != -1) {
        lastEntryKey = null;
        return;
      }

      String shortName;
      String remainder;
      if (line.length() > 20 && line.charAt(19) == ' ') {
        shortName = line.substring(0, 19).trim();
        remainder = line.substring(20).trim();
      }
      else if (line.length() > 19 && line.charAt(18) != ' ') {
        shortName = line.substring(0, 19).trim();
        remainder = line.substring(19).trim();
      }
      else {
        lastEntryKey = null;
        return;
      }

      if (shortName.isEmpty()) {
        lastEntryKey = null;
        return;
      }
      CheckoutLineDetails details = CheckoutLineDetails.parse(remainder);
      if (details == null || !userFilter.matches(details.user())) {
        lastEntryKey = null;
        return;
      }
      String localPath = VssTruncatedFileNameUtil.resolveLocalPath(lastFolderName, shortName);
      String key = localPath.toLowerCase(Locale.ENGLISH);
      String workingFolder = details.workingFolder();
      if (workingFolder.isEmpty()) {
        workingFolder = StringUtil.notNullize(new File(localPath).getParent());
      }
      String fileName = VssTruncatedFileNameUtil.displayFileName(localPath, shortName);
      entries.put(key,
                  new VssCheckoutEntry(localPath, fileName,
                                       details.user(), details.dateTime(), workingFolder));
      lastEntryKey = key;
    }

    private static boolean looksLikeFileCheckoutLine(@NotNull String line) {
      if (line.length() <= 20) {
        return false;
      }
      if (line.charAt(19) != ' ') {
        return line.length() > 19 && line.charAt(18) != ' ';
      }
      return line.substring(0, 19).trim().indexOf('\\') == -1;
    }
  }

  private record CheckoutLineDetails(@NotNull String user,
                                     @NotNull String dateTime,
                                     @NotNull String workingFolder) {
    private static @org.jetbrains.annotations.Nullable CheckoutLineDetails parse(@NotNull String remainder) {
      if (remainder.isEmpty()) {
        return null;
      }
      String[] tokens = remainder.split("\\s+");
      if (tokens.length < 1) {
        return null;
      }
      String user = tokens[0];
      int index = 1;
      if (index < tokens.length && isCheckoutType(tokens[index])) {
        index++;
      }
      String dateTime = "";
      int pathStart = index;
      if (index < tokens.length) {
        if (index + 1 < tokens.length && looksLikeDate(tokens[index]) && looksLikeTime(tokens[index + 1])) {
          dateTime = tokens[index] + " " + tokens[index + 1];
          pathStart = index + 2;
        }
        else if (looksLikeDate(tokens[index])) {
          dateTime = tokens[index];
          pathStart = index + 1;
        }
      }
      String workingFolder = pathStart < tokens.length
                             ? String.join(" ", java.util.Arrays.copyOfRange(tokens, pathStart, tokens.length))
                             : "";
      return new CheckoutLineDetails(user, dateTime, workingFolder);
    }

    private static boolean isCheckoutType(String token) {
      String normalized = token.toUpperCase(Locale.ENGLISH);
      return "EXC".equals(normalized) || "SHR".equals(normalized) || "NON".equals(normalized);
    }

    private static boolean looksLikeDate(String token) {
      return token.matches("\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}");
    }

    private static boolean looksLikeTime(String token) {
      return token.matches("\\d{1,2}:\\d{2}[ap]?(m)?");
    }
  }
}
