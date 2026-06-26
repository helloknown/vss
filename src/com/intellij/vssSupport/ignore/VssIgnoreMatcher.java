package com.intellij.vssSupport.ignore;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Matches paths using the same rules as {@code .gitignore}.
 * See <a href="https://git-scm.com/docs/gitignore">gitignore pattern format</a>.
 */
public final class VssIgnoreMatcher {
  private static final VssIgnoreMatcher EMPTY = new VssIgnoreMatcher(List.of());

  private final List<IgnoreRule> rules;

  private VssIgnoreMatcher(@NotNull List<IgnoreRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @NotNull
  public static VssIgnoreMatcher empty() {
    return EMPTY;
  }

  @NotNull
  public static VssIgnoreMatcher parse(@NotNull String content) {
    List<IgnoreRule> rules = new ArrayList<>();
    for (String rawLine : content.split("\n")) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }

      boolean negated = false;
      if (line.startsWith("!")) {
        negated = true;
        line = line.substring(1).trim();
        if (line.isEmpty()) {
          continue;
        }
      }

      boolean directoryOnly = line.endsWith("/");
      if (directoryOnly) {
        line = line.substring(0, line.length() - 1);
      }
      if (line.isEmpty()) {
        continue;
      }

      boolean anchored = line.startsWith("/");
      if (anchored) {
        line = line.substring(1);
      }
      if (line.isEmpty()) {
        continue;
      }

      rules.add(new IgnoreRule(line, anchored, directoryOnly, negated));
    }
    return rules.isEmpty() ? EMPTY : new VssIgnoreMatcher(rules);
  }

  public boolean isIgnored(@NotNull String relativePath) {
    if (rules.isEmpty()) {
      return false;
    }
    String normalized = normalizeRelativePath(relativePath);
    boolean ignored = false;
    for (IgnoreRule rule : rules) {
      if (rule.matches(normalized)) {
        ignored = !rule.negated;
      }
    }
    return ignored;
  }

  @NotNull
  private static String normalizeRelativePath(@NotNull String path) {
    String normalized = path.replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    return normalized.replaceAll("/+", "/");
  }

  private static final class IgnoreRule {
    private final String pattern;
    private final boolean anchored;
    private final boolean directoryOnly;
    private final boolean negated;

    private IgnoreRule(@NotNull String pattern, boolean anchored, boolean directoryOnly, boolean negated) {
      this.pattern = pattern;
      this.anchored = anchored;
      this.directoryOnly = directoryOnly;
      this.negated = negated;
    }

    boolean matches(@NotNull String relativePath) {
      if (directoryOnly) {
        if (matchesPattern(relativePath)) {
          return true;
        }
        String parent = relativePath;
        while (true) {
          int slash = parent.lastIndexOf('/');
          if (slash < 0) {
            break;
          }
          parent = parent.substring(0, slash);
          if (matchesPattern(parent)) {
            return true;
          }
        }
        return false;
      }
      return matchesPattern(relativePath);
    }

    private boolean matchesPattern(@NotNull String relativePath) {
      if (!pattern.contains("/")) {
        if (anchored) {
          if (fnmatch(relativePath, pattern)) {
            return true;
          }
          return relativePath.startsWith(pattern + "/");
        }
        int slash = relativePath.lastIndexOf('/');
        String fileName = slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
        return fnmatch(fileName, pattern);
      }

      if (anchored) {
        if (fnmatch(relativePath, pattern)) {
          return true;
        }
        return relativePath.startsWith(pattern + "/");
      }

      String candidate = relativePath;
      while (true) {
        if (fnmatch(candidate, pattern)) {
          return true;
        }
        int slash = candidate.indexOf('/');
        if (slash < 0) {
          break;
        }
        candidate = candidate.substring(slash + 1);
      }
      return false;
    }

    /**
     * Git-style fnmatch with FNM_PATHNAME: {@code *} and {@code ?} do not match {@code /},
     * while {@code **} matches any sequence including {@code /}.
     */
    private static boolean fnmatch(@NotNull String text, @NotNull String glob) {
      return Pattern.compile(globToRegex(glob), Pattern.CASE_INSENSITIVE).matcher(text).matches();
    }

    @NotNull
    private static String globToRegex(@NotNull String glob) {
      StringBuilder regex = new StringBuilder("^");
      for (int i = 0; i < glob.length(); i++) {
        char c = glob.charAt(i);
        if (c == '*') {
          if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
            regex.append(".*");
            i++;
            if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
              i++;
            }
          }
          else {
            regex.append("[^/]*");
          }
        }
        else if (c == '?') {
          regex.append("[^/]");
        }
        else if (".[]{}()+^$|\\".indexOf(c) >= 0) {
          regex.append('\\').append(c);
        }
        else {
          regex.append(c);
        }
      }
      return regex.append('$').toString();
    }
  }
}
