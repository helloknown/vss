package com.intellij.vssSupport.Configuration;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.vssSupport.VssBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.BorderLayout;
import java.io.File;

/**
 * Standard FormBuilder layout: label immediately left of field; hints wrap to panel width.
 */
public class VssConfigurable implements Configurable {

  private final VssConfiguration myConfig;
  private final Project myProject;

  private JPanel myPanel;
  private TextFieldWithBrowseButton myClientPath;
  private TextFieldWithBrowseButton mySrcsafeIni;
  private JTextField myTextFieldUserName;
  private JPasswordField myPasswordField;
  private VssDirectoryMappingsPanel myMappingsPanel;

  @NonNls public static final String PATH_TO_SS_EXE = "ss.exe";
  @NonNls public static final String PATH_TO_SS_INI = "srcsafe.ini";

  public VssConfigurable(Project project) {
    myProject = project;
    myConfig = VssConfiguration.getInstance(myProject);
  }

  @NlsContexts.ConfigurableName
  @Override
  public String getDisplayName() {
    return "SourceSafe";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    ensurePanelCreated();
    reset();
    return myPanel;
  }

  private void ensurePanelCreated() {
    if (myPanel != null && myMappingsPanel != null) {
      return;
    }
    disposeUIResources();

    myClientPath = VssFormUi.createCompactBrowseField(VssFormUi.SETTINGS_FIELD_WIDTH);
    mySrcsafeIni = VssFormUi.createCompactBrowseField(VssFormUi.SETTINGS_FIELD_WIDTH);
    myTextFieldUserName = VssFormUi.createCompactTextField(VssFormUi.SETTINGS_FIELD_WIDTH);
    myPasswordField = VssFormUi.createCompactPasswordField(VssFormUi.SETTINGS_FIELD_WIDTH);
    myMappingsPanel = new VssDirectoryMappingsPanel(myProject);
    VssFormUi.allowHorizontalResize(myMappingsPanel);

    setupBrowseButton(myClientPath, PATH_TO_SS_EXE,
                      VssBundle.message("dialog.description.configuration.path.to.ss.exe"));
    setupBrowseButton(mySrcsafeIni, PATH_TO_SS_INI,
                      VssBundle.message("dialog.description.configuration.path.to.srcsafe.ini"));

    JPanel mappingsWrapper = new JPanel(new BorderLayout());
    mappingsWrapper.setBorder(IdeBorderFactory.createTitledBorder(
      VssBundle.message("border.confugyration.working.directories.group"), false));
    VssFormUi.allowHorizontalResize(mappingsWrapper);
    mappingsWrapper.add(myMappingsPanel, BorderLayout.CENTER);
    mappingsWrapper.add(
      VssFormUi.createWrappingComment(
        VssBundle.message("message.text.configuration.mappings.managed.here"), VssFormUi.COMMENT_WRAP_WIDTH),
      BorderLayout.SOUTH);

    JPanel formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(VssBundle.message("label.configuration.path.to.exe"), myClientPath)
      .addLabeledComponent(VssBundle.message("label.configuration.path.to.ini"), mySrcsafeIni)
      .addLabeledComponent(VssBundle.message("label.configuration.user.name"), myTextFieldUserName)
      .addLabeledComponent(VssBundle.message("label.configuration.password"), myPasswordField)
      .addComponent(mappingsWrapper)
      .addComponent(VssFormUi.createWrappingComment(
        "Please check out particular restrictions for VSS repository in our Help!", VssFormUi.COMMENT_WRAP_WIDTH))
      .addComponentFillVertically(new JPanel(), 0)
      .getPanel();

    myPanel = VssFormUi.boundedPanel(formPanel, VssFormUi.SETTINGS_MAX_WIDTH);
    myPanel.setBorder(JBUI.Borders.empty(5));
  }

  private void setupBrowseButton(TextFieldWithBrowseButton field, String fileName, String description) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(com.intellij.openapi.vfs.VirtualFile file) {
        return file.isDirectory() || fileName.equalsIgnoreCase(file.getName());
      }
    };
    field.addBrowseFolderListener(new TextBrowseFolderListener(descriptor, myProject));
  }

  @Override
  public boolean isModified() {
    if (!isUiReady()) {
      return false;
    }
    return !myClientPath.getText().replace('/', File.separatorChar).equals(myConfig.CLIENT_PATH)
           || !mySrcsafeIni.getText().replace('/', File.separatorChar).equals(myConfig.SRCSAFEINI_PATH)
           || !myTextFieldUserName.getText().trim().equals(myConfig.USER_NAME)
           || !(new String(myPasswordField.getPassword())).equals(myConfig.getPassword())
           || myMappingsPanel.isModified();
  }

  @Override
  public void apply() {
    if (!isUiReady()) {
      return;
    }
    myConfig.CLIENT_PATH = myClientPath.getText().replace('/', File.separatorChar);
    myConfig.SRCSAFEINI_PATH = mySrcsafeIni.getText().replace('/', File.separatorChar);
    myConfig.USER_NAME = myTextFieldUserName.getText().trim();
    myConfig.setPassword(new String(myPasswordField.getPassword()));
    myMappingsPanel.apply();
    VssMappingStorage.enrichDirectoryMappings(myProject);
  }

  @Override
  public void reset() {
    if (!isUiReady()) {
      return;
    }
    myClientPath.setText(myConfig.CLIENT_PATH);
    mySrcsafeIni.setText(myConfig.SRCSAFEINI_PATH);
    myTextFieldUserName.setText(myConfig.USER_NAME);
    myPasswordField.setText(myConfig.getPassword());
    myMappingsPanel.reset();
  }

  private boolean isUiReady() {
    return myClientPath != null && myMappingsPanel != null;
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
    myClientPath = null;
    mySrcsafeIni = null;
    myTextFieldUserName = null;
    myPasswordField = null;
    myMappingsPanel = null;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "project.propVSS";
  }
}
