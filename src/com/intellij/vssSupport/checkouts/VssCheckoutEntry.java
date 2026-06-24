package com.intellij.vssSupport.checkouts;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public record VssCheckoutEntry(@NotNull String localPath,
                                 @NotNull String fileName,
                                 @NotNull String checkoutUser,
                                 @NotNull String checkoutDate,
                                 @NotNull String workingFolder) {
  public String presentablePath() {
    return new File(localPath).getPath();
  }
}
