package com.intellij.vssSupport.checkouts;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User filter for My Checkouts queries (similar to SourceSafe Search for Status).
 */
public record MyCheckoutsUserFilter(boolean matchAllUsers, @NotNull String specifiedUser) {
  public static @NotNull MyCheckoutsUserFilter currentUser(@NotNull String configuredUser) {
    return new MyCheckoutsUserFilter(false, StringUtil.notNullize(configuredUser).trim());
  }

  public static @NotNull MyCheckoutsUserFilter forAllUsers() {
    return new MyCheckoutsUserFilter(true, "");
  }

  public boolean matches(@Nullable String checkoutUser) {
    if (matchAllUsers) {
      return true;
    }
    if (StringUtil.isEmpty(specifiedUser)) {
      return true;
    }
    return specifiedUser.equalsIgnoreCase(StringUtil.notNullize(checkoutUser).trim());
  }
}
