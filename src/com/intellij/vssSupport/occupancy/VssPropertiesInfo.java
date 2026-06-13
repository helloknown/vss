package com.intellij.vssSupport.occupancy;

import org.jetbrains.annotations.Nullable;

/**
 * Parsed result of {@code ss Properties} for a file or project.
 */
public final class VssPropertiesInfo {
  @Nullable private final String vssPath;
  @Nullable private final String fileType;
  @Nullable private final String size;
  @Nullable private final String storeOnlyLatest;
  @Nullable private final String latestVersion;
  @Nullable private final String latestDate;
  @Nullable private final String latestComment;
  @Nullable private final String lastLabel;
  @Nullable private final String lastLabelVersion;
  @Nullable private final String lastLabelDate;
  @Nullable private final String rawOutput;

  public VssPropertiesInfo(@Nullable String vssPath,
                           @Nullable String fileType,
                           @Nullable String size,
                           @Nullable String storeOnlyLatest,
                           @Nullable String latestVersion,
                           @Nullable String latestDate,
                           @Nullable String latestComment,
                           @Nullable String lastLabel,
                           @Nullable String lastLabelVersion,
                           @Nullable String lastLabelDate,
                           @Nullable String rawOutput) {
    this.vssPath = vssPath;
    this.fileType = fileType;
    this.size = size;
    this.storeOnlyLatest = storeOnlyLatest;
    this.latestVersion = latestVersion;
    this.latestDate = latestDate;
    this.latestComment = latestComment;
    this.lastLabel = lastLabel;
    this.lastLabelVersion = lastLabelVersion;
    this.lastLabelDate = lastLabelDate;
    this.rawOutput = rawOutput;
  }

  @Nullable public String getVssPath() { return vssPath; }
  @Nullable public String getFileType() { return fileType; }
  @Nullable public String getSize() { return size; }
  @Nullable public String getStoreOnlyLatest() { return storeOnlyLatest; }
  @Nullable public String getLatestVersion() { return latestVersion; }
  @Nullable public String getLatestDate() { return latestDate; }
  @Nullable public String getLatestComment() { return latestComment; }
  @Nullable public String getLastLabel() { return lastLabel; }
  @Nullable public String getLastLabelVersion() { return lastLabelVersion; }
  @Nullable public String getLastLabelDate() { return lastLabelDate; }
  @Nullable public String getRawOutput() { return rawOutput; }
}
