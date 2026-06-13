package com.intellij.vssSupport;

import com.intellij.openapi.vcs.VcsShowSettingOption;

public class VssShowSettingOption implements VcsShowSettingOption {
  private boolean showDialog = true;

  @Override
  public boolean getValue() {
    return showDialog;
  }

  @Override
  public void setValue(boolean value) {
    showDialog = value;
  }

}
