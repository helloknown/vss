package com.intellij.vssSupport;

import org.jetbrains.annotations.Nullable;

/**
 * Normalizes commit messages from the IDE commit UI (strips invisible placeholders used to bypass empty-message validation).
 */
public final class VssCommitMessageUtil {
  /** Not whitespace, but treated as empty when passed to VSS ({@code -C-}). */
  public static final String EMPTY_MESSAGE_PLACEHOLDER = "\u200B";

  private VssCommitMessageUtil() {
  }

  @Nullable
  public static String normalize(@Nullable String comment) {
    if (comment == null) {
      return null;
    }
    return comment.replace(EMPTY_MESSAGE_PLACEHOLDER, "").trim();
  }

  public static boolean isEffectivelyEmpty(@Nullable String comment) {
    String normalized = normalize(comment);
    return normalized == null || normalized.isEmpty();
  }
}
