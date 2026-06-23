package com.intellij.vssSupport;

import org.jetbrains.annotations.Nullable;

/**
 * Normalizes commit messages from the IDE commit UI before passing them to VSS.
 */
public final class VssCommitMessageUtil {

  private VssCommitMessageUtil() {
  }

  @Nullable
  public static String normalize(@Nullable String comment) {
    if (comment == null) {
      return null;
    }
    String trimmed = comment.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public static boolean isEffectivelyEmpty(@Nullable String comment) {
    return normalize(comment) == null;
  }
}
