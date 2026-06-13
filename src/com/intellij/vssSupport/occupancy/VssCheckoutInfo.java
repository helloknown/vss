package com.intellij.vssSupport.occupancy;

import org.jetbrains.annotations.Nullable;

/**
 * Checkout occupancy parsed from {@code ss Status} output for a single file.
 */
public final class VssCheckoutInfo {
  public static final VssCheckoutInfo NOT_CHECKED_OUT = new VssCheckoutInfo(false, null, null, null, null);

  private final boolean checkedOut;
  @Nullable private final String user;
  @Nullable private final String dateTime;
  @Nullable private final String workingFolder;
  /** Checkout type from {@code ss Status}, e.g. {@code Exc} (exclusive) or {@code Shr} (shared). */
  @Nullable private final String checkoutType;

  public VssCheckoutInfo(boolean checkedOut,
                         @Nullable String user,
                         @Nullable String dateTime,
                         @Nullable String workingFolder,
                         @Nullable String checkoutType) {
    this.checkedOut = checkedOut;
    this.user = user;
    this.dateTime = dateTime;
    this.workingFolder = workingFolder;
    this.checkoutType = checkoutType;
  }

  public boolean isCheckedOut() {
    return checkedOut;
  }

  @Nullable
  public String getUser() {
    return user;
  }

  @Nullable
  public String getDateTime() {
    return dateTime;
  }

  @Nullable
  public String getWorkingFolder() {
    return workingFolder;
  }

  @Nullable
  public String getCheckoutType() {
    return checkoutType;
  }

  @Nullable
  public String getCheckoutTypeDisplay() {
    if (checkoutType == null) {
      return null;
    }
    if ("Exc".equalsIgnoreCase(checkoutType)) {
      return "Exclusive";
    }
    if ("Shr".equalsIgnoreCase(checkoutType)) {
      return "Shared";
    }
    if ("Non".equalsIgnoreCase(checkoutType)) {
      return "Not checked out";
    }
    return checkoutType;
  }
}
