package com.intellij.vssSupport.occupancy;

import org.jetbrains.annotations.Nullable;

/**
 * Combined occupancy and properties snapshot for a versioned file.
 */
public final class VssFileOccupancy {
  private final String localPath;
  @Nullable private final VssPropertiesInfo properties;
  private final VssCheckoutInfo checkout;
  private final boolean checkedOutByCurrentUser;

  public VssFileOccupancy(String localPath,
                          @Nullable VssPropertiesInfo properties,
                          VssCheckoutInfo checkout,
                          boolean checkedOutByCurrentUser) {
    this.localPath = localPath;
    this.properties = properties;
    this.checkout = checkout;
    this.checkedOutByCurrentUser = checkedOutByCurrentUser;
  }

  public String getLocalPath() {
    return localPath;
  }

  @Nullable
  public VssPropertiesInfo getProperties() {
    return properties;
  }

  public VssCheckoutInfo getCheckout() {
    return checkout;
  }

  public boolean isCheckedOut() {
    return checkout.isCheckedOut();
  }

  public boolean isCheckedOutByCurrentUser() {
    return checkedOutByCurrentUser;
  }

  public boolean isCheckedOutByOtherUser() {
    return checkout.isCheckedOut() && !checkedOutByCurrentUser;
  }

  @Nullable
  public String getCheckoutUser() {
    return checkout.getUser();
  }

  @Nullable
  public String getLatestVersion() {
    return properties != null ? properties.getLatestVersion() : null;
  }

  @Nullable
  public String getVssPath() {
    return properties != null ? properties.getVssPath() : null;
  }

  public String getStatusSummary() {
    if (!checkout.isCheckedOut()) {
      return "Not checked out";
    }
    String user = checkout.getUser();
    if (checkedOutByCurrentUser) {
      return user != null ? "Checked out by you (" + user + ")" : "Checked out by you";
    }
    return user != null ? "Checked out by " + user : "Checked out";
  }
}
