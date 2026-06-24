package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.checkouts.VssCheckoutEntry;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code ss Dir -R -E} output and collects checkout metadata for all checked-out files.
 */
public final class CheckoutScanCommandListener extends VssOutputCollector {
  @NonNls private static final String TOTAL_SIG = " items(s)";
  @NonNls private static final String NO_ITEMS_FOUND_SIG = "No items found under";
  @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";

  private static final Pattern CHECKOUT_LINE =
    Pattern.compile("^(\\S+)\\s+(\\d+-\\d+-\\d+)\\s+(\\d+:\\d+[ap])\\s*(.*)$", Pattern.CASE_INSENSITIVE);

  private final Project project;
  private final String startFolder;
  private final List<VssCheckoutEntry> checkouts;

  public CheckoutScanCommandListener(Project project,
                                     String startFolder,
                                     List<VssCheckoutEntry> checkouts,
                                     List<VcsException> errors) {
    super(errors);
    this.project = project;
    this.startFolder = startFolder;
    this.checkouts = checkouts;
  }

  @Override
  public void everythingFinishedImpl(final String output) {
    if (output.indexOf(NOT_EXISTING_MESSAGE) != -1) {
      myErrors.add(new VcsException(VssBundle.message("message.text.path.is.not.existing.filename.or.project", startFolder)));
      return;
    }

    String localPath = startFolder;
    String[] lines = LineTokenizer.tokenize(output, false);
    int offset = 0;
    while (offset < lines.length) {
      String line = lines[offset];
      if (line.length() > 0) {
        LineType lineType = whatSubProjectLine(lines, offset);
        if (lineType != LineType.NO_PROJECT) {
          localPath = constructLocalFromSubproject(lines, offset);
          localPath = VssUtil.getLocalPath(localPath, project);
          offset += (lineType == LineType.SIMPLE_FORMAT) ? 0 : 1;
        }
        else if (!(line.charAt(0) == '$' && line.charAt(line.length() - 1) != ':')
                 && line.indexOf(TOTAL_SIG) == -1
                 && line.indexOf(NO_ITEMS_FOUND_SIG) == -1) {
          int consumed = extractCheckoutInfo(localPath, lines, offset);
          offset += consumed;
          continue;
        }
      }
      offset++;
    }
  }

  /**
   * @return number of additional lines consumed after the current one (for wrapped working paths).
   */
  private int extractCheckoutInfo(String folder, String[] lines, int lineIndex) {
    String line = lines[lineIndex];
    if (line.length() <= 20 || line.charAt(19) != ' ') {
      return 0;
    }

    String shortName = line.substring(0, 19).trim();
    if (shortName.indexOf('\\') != -1) {
      return 0;
    }

    boolean fileIsCut = shortName.length() == 19;
    String localFilePath = folder + "\\" + shortName;
    if (fileIsCut) {
      localFilePath = completeFileName(localFilePath);
    }
    localFilePath = VssUtil.getCanonicalLocalPath(localFilePath);

    String rest = line.substring(20).trim();
    Matcher matcher = CHECKOUT_LINE.matcher(rest);
    if (!matcher.matches()) {
      return 0;
    }

    String checkoutUser = matcher.group(1);
    String checkoutDate = matcher.group(2) + " " + matcher.group(3);
    StringBuilder workingFolder = new StringBuilder(matcher.group(4).trim());

    int consumed = 0;
    int next = lineIndex + 1;
    while (next < lines.length) {
      String nextLine = lines[next].trim();
      if (nextLine.isEmpty()
          || nextLine.charAt(0) == '$'
          || nextLine.indexOf(TOTAL_SIG) != -1
          || nextLine.indexOf(NO_ITEMS_FOUND_SIG) != -1) {
        break;
      }
      if (nextLine.length() > 20 && nextLine.charAt(19) == ' ') {
        break;
      }
      if (!workingFolder.isEmpty()) {
        workingFolder.append(' ');
      }
      workingFolder.append(nextLine);
      consumed++;
      next++;
    }

    String fileName = new File(localFilePath).getName();
    checkouts.add(new VssCheckoutEntry(localFilePath, fileName, checkoutUser, checkoutDate, workingFolder.toString()));
    return consumed;
  }

  private static String completeFileName(String fileName) {
    File file = new File(fileName);
    final String parent = file.getParent();
    final String truncatedName = file.getName().toLowerCase();
    String fullName = truncatedName;
    String[] fullNames = file.getParentFile().list((dir, name) -> name.toLowerCase().startsWith(truncatedName));
    if (fullNames != null) {
      for (String name : fullNames) {
        if (new File(parent, name).canWrite()) {
          fullName = name.toLowerCase();
          break;
        }
      }
    }
    return parent + "/" + fullName;
  }
}
