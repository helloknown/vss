package com.intellij.vssSupport.occupancy;

import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Parses text output of {@code ss Properties}.
 */
public final class VssPropertiesParser {
  @NonNls private static final String FILE_PREFIX = "File:";
  @NonNls private static final String TYPE_PREFIX = "Type:";
  @NonNls private static final String SIZE_PREFIX = "Size:";
  @NonNls private static final String STORE_PREFIX = "Store only latest version:";
  @NonNls private static final String VERSION_PREFIX = "Version:";
  @NonNls private static final String DATE_PREFIX = "Date:";
  @NonNls private static final String COMMENT_PREFIX = "Comment:";
  @NonNls private static final String LAST_LABEL_PREFIX = "Last Label:";

  private VssPropertiesParser() {
  }

  public static VssPropertiesInfo parse(@Nullable String output) {
    if (StringUtil.isEmpty(output)) {
      return new VssPropertiesInfo(null, null, null, null, null, null, null, null, null, null, output);
    }

    String vssPath = null;
    String fileType = null;
    String size = null;
    String storeOnlyLatest = null;
    String latestVersion = null;
    String latestDate = null;
    String latestComment = null;
    String lastLabel = null;
    String lastLabelVersion = null;
    String lastLabelDate = null;

    String[] lines = LineTokenizer.tokenize(output, false);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.startsWith(FILE_PREFIX)) {
        vssPath = line.substring(FILE_PREFIX.length()).trim();
      }
      else if (line.startsWith(TYPE_PREFIX)) {
        fileType = line.substring(TYPE_PREFIX.length()).trim();
      }
      else if (line.startsWith(SIZE_PREFIX)) {
        size = line.substring(SIZE_PREFIX.length()).trim();
      }
      else if (line.startsWith(STORE_PREFIX)) {
        storeOnlyLatest = line.substring(STORE_PREFIX.length()).trim();
      }
      else if (line.contains(VERSION_PREFIX) && line.contains(LAST_LABEL_PREFIX)) {
        latestVersion = extractAfter(line, VERSION_PREFIX);
        lastLabel = extractAfter(line, LAST_LABEL_PREFIX);
        lastLabelVersion = extractAfter(line, LAST_LABEL_PREFIX + " ");
        if (lastLabelVersion != null && lastLabelVersion.startsWith(":")) {
          lastLabelVersion = lastLabelVersion.substring(1).trim();
        }
      }
      else if (line.startsWith(VERSION_PREFIX) && latestVersion == null) {
        latestVersion = line.substring(VERSION_PREFIX.length()).trim();
      }
      else if (line.startsWith(DATE_PREFIX) && latestDate == null) {
        latestDate = line.substring(DATE_PREFIX.length()).trim();
        if (line.contains(LAST_LABEL_PREFIX)) {
          int labelIndex = line.indexOf(LAST_LABEL_PREFIX);
          if (labelIndex > 0) {
            latestDate = line.substring(DATE_PREFIX.length(), labelIndex).trim();
            String labelPart = line.substring(labelIndex + LAST_LABEL_PREFIX.length()).trim();
            if (labelPart.startsWith(":")) {
              labelPart = labelPart.substring(1).trim();
            }
            lastLabelDate = labelPart;
          }
        }
      }
      else if (line.startsWith(COMMENT_PREFIX)) {
        latestComment = collectComment(lines, i, COMMENT_PREFIX);
      }
    }

    return new VssPropertiesInfo(vssPath, fileType, size, storeOnlyLatest,
                                 latestVersion, latestDate, latestComment,
                                 lastLabel, lastLabelVersion, lastLabelDate, output);
  }

  @Nullable
  private static String extractAfter(String line, String marker) {
    int index = line.indexOf(marker);
    if (index < 0) {
      return null;
    }
    String value = line.substring(index + marker.length()).trim();
    if (value.startsWith(":")) {
      value = value.substring(1).trim();
    }
    return value.isEmpty() ? null : value;
  }

  private static String collectComment(String[] lines, int startIndex, String prefix) {
    StringBuilder comment = new StringBuilder();
    String first = lines[startIndex].trim();
    if (first.length() > prefix.length()) {
      comment.append(first.substring(prefix.length()).trim());
    }
    for (int i = startIndex + 1; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty() || line.startsWith(FILE_PREFIX) || line.startsWith(VERSION_PREFIX)) {
        break;
      }
      if (comment.length() > 0) {
        comment.append('\n');
      }
      comment.append(line);
    }
    return comment.toString();
  }
}
