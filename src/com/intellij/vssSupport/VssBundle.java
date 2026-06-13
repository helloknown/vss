/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.vssSupport;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

public class VssBundle {

  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  @NonNls private static final String BUNDLE = "com.intellij.vssSupport.VssBundle";
  private static ResourceBundle ourBundle;

  private VssBundle() {
  }

  private static ResourceBundle getBundle() {
    if (ourBundle == null) {
      ourBundle = ResourceBundle.getBundle(BUNDLE);
    }
    return ourBundle;
  }
}
