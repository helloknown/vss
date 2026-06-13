package com.intellij.vssSupport.occupancy;

import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * Parses {@code ss Status} output for checkout occupancy of a single file.
 * <p>
 * Typical line format (columns):
 * {@code FileName(19) User Exc 26/06/11 17:19 D:\working\folder}
 * {@code Exc} = exclusive checkout, {@code Shr} = shared checkout.
 */
public final class VssCheckoutStatusParser {
  @NonNls private static final String NOFILES_SIG = "No checked out files found";
  @NonNls private static final String NOFILES_BY_USER_SIG = "No files found checked out by";
  @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";
  @NonNls private static final String DELETED_MESSAGE = "has been deleted";

  private VssCheckoutStatusParser() {
  }

  public static VssCheckoutInfo parse(@Nullable String output) {
    if (StringUtil.isEmpty(output)) {
      return VssCheckoutInfo.NOT_CHECKED_OUT;
    }
    if (output.contains(NOT_EXISTING_MESSAGE) || output.contains(DELETED_MESSAGE)) {
      return VssCheckoutInfo.NOT_CHECKED_OUT;
    }
    if (output.contains(NOFILES_SIG) || output.contains(NOFILES_BY_USER_SIG)) {
      return VssCheckoutInfo.NOT_CHECKED_OUT;
    }

    String[] lines = LineTokenizer.tokenize(output, false);
    for (String rawLine : lines) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("$") || line.startsWith("!")) {
        continue;
      }
      VssCheckoutInfo parsed = parseCheckoutLine(line);
      if (parsed.isCheckedOut()) {
        return parsed;
      }
    }
    return VssCheckoutInfo.NOT_CHECKED_OUT;
  }

  private static VssCheckoutInfo parseCheckoutLine(String line) {
    String remainder = extractRemainder(line);
    if (remainder == null) {
      return VssCheckoutInfo.NOT_CHECKED_OUT;
    }

    String[] tokens = remainder.split("\\s+");
    if (tokens.length < 2) {
      return VssCheckoutInfo.NOT_CHECKED_OUT;
    }

    String user = tokens[0];
    int index = 1;
    String checkoutType = null;
    if (isCheckoutTypeToken(tokens[index])) {
      checkoutType = tokens[index];
      index++;
    }

    if (tokens.length <= index) {
      return new VssCheckoutInfo(true, user, null, null, checkoutType);
    }

    String dateTime;
    int pathStartIndex;
    if (tokens.length > index + 1 && looksLikeDate(tokens[index]) && looksLikeTime(tokens[index + 1])) {
      dateTime = tokens[index] + " " + tokens[index + 1];
      pathStartIndex = index + 2;
    }
    else if (looksLikeDate(tokens[index])) {
      dateTime = tokens[index];
      pathStartIndex = index + 1;
    }
    else {
      return new VssCheckoutInfo(true, user, null, null, checkoutType);
    }

    String workingFolder = null;
    if (pathStartIndex < tokens.length) {
      workingFolder = String.join(" ", Arrays.copyOfRange(tokens, pathStartIndex, tokens.length));
    }

    return new VssCheckoutInfo(true, user, dateTime, workingFolder, checkoutType);
  }

  @Nullable
  private static String extractRemainder(String line) {
    if (line.length() > 20 && line.charAt(19) == ' ') {
      return line.substring(20).trim();
    }
    if (line.length() > 19 && line.charAt(18) != ' ') {
      // Filename may occupy exactly 19 characters without a trailing space.
      return line.substring(19).trim();
    }
    return line.trim();
  }

  private static boolean isCheckoutTypeToken(String token) {
    if (token.length() > 4) {
      return false;
    }
    String normalized = token.toUpperCase(Locale.ENGLISH);
    return "EXC".equals(normalized) || "SHR".equals(normalized) || "NON".equals(normalized);
  }

  private static boolean looksLikeDate(String token) {
    return token.matches("\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}");
  }

  private static boolean looksLikeTime(String token) {
    return token.matches("\\d{1,2}:\\d{2}[ap]?");
  }
}
