package com.intellij.vssSupport;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Locale;

/**
 * Completes file names truncated to 19 characters in {@code ss Dir -E} output.
 */
public final class VssTruncatedFileNameUtil {
  private VssTruncatedFileNameUtil() {
  }

  @NotNull
  public static String resolveLocalPath(@NotNull String parentFolder, @NotNull String shortName) {
    String candidatePath = VssUtil.getCanonicalLocalPath(parentFolder + File.separator + shortName);
    if (new File(candidatePath).isFile()) {
      return candidatePath;
    }
    return completeTruncatedLocalPath(candidatePath);
  }

  @NotNull
  public static String completeTruncatedLocalPath(@NotNull String localPathWithPossibleTruncation) {
    File file = new File(localPathWithPossibleTruncation);
    String parent = file.getParent();
    if (parent == null) {
      return localPathWithPossibleTruncation;
    }
    String completedName = completeFileNameInParent(parent, file.getName());
    return VssUtil.getCanonicalLocalPath(parent + File.separator + completedName);
  }

  @NotNull
  public static String displayFileName(@NotNull String localPath, @NotNull String fallbackName) {
    String resolvedPath = completeTruncatedLocalPath(localPath);
    File file = new File(resolvedPath);
    if (file.isFile()) {
      return file.getName();
    }
    VirtualFile virtualFile = VssUtil.getVirtualFile(resolvedPath);
    if (virtualFile != null && !virtualFile.isDirectory()) {
      return virtualFile.getName();
    }
    return fallbackName;
  }

  @NotNull
  private static String completeFileNameInParent(@NotNull String parent, @NotNull String truncatedName) {
    File parentFile = new File(parent);
    if (!parentFile.isDirectory()) {
      return truncatedName;
    }
    String lowerTruncated = truncatedName.toLowerCase(Locale.ENGLISH);
    String[] matches = parentFile.list((dir, name) -> name.toLowerCase(Locale.ENGLISH).startsWith(lowerTruncated));
    if (matches == null || matches.length == 0) {
      return truncatedName;
    }
    String fullName = truncatedName;
    for (String name : matches) {
      if (new File(parent, name).isFile()) {
        fullName = name;
        if (new File(parent, name).canWrite()) {
          break;
        }
      }
    }
    return fullName;
  }
}
