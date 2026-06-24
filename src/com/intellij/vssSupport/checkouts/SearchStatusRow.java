package com.intellij.vssSupport.checkouts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One row in the Search for Status table (folder header or file). */
public record SearchStatusRow(@NotNull String name,
                              @NotNull String user,
                              @NotNull String dateTime,
                              @NotNull String workingFolder,
                              @Nullable VssCheckoutEntry entry) {
}
