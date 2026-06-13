package com.intellij.vssSupport.Configuration;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.vssSupport.VssBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * Dialog to add or edit a single VSS directory mapping.
 */
final class VssMappingEditDialog extends DialogWrapper {
  private static final String VSS_PROJECT_PREFIX = "$/";
  /** Bumped to drop previously saved oversized dialog dimensions. */
  private static final String DIMENSION_SERVICE_KEY = "VssMappingEditDialog.v2";

  private final Project project;
  private final VssMappingEntry entry;

  private TextFieldWithBrowseButton directoryField;
  private JTextField vssProjectField;

  VssMappingEditDialog(Project project, VssMappingEntry entry, boolean isAdd) {
    super(project);
    this.project = project;
    this.entry = new VssMappingEntry(entry.getDirectory(), entry.getVssProject());
    setTitle(isAdd ? VssBundle.message("dialog.title.configuration.add.mapping")
                   : VssBundle.message("dialog.title.configuration.edit.mapping"));
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return DIMENSION_SERVICE_KEY;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return directoryField;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    directoryField = VssFormUi.createCompactBrowseField(VssFormUi.DIALOG_FIELD_WIDTH);
    vssProjectField = VssFormUi.createCompactTextField(VssFormUi.DIALOG_FIELD_WIDTH);

    directoryField.addBrowseFolderListener(
      new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project)
    );

    JPanel formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(VssBundle.message("label.configuration.working.directory"), directoryField)
      .addLabeledComponent(VssBundle.message("label.configuration.vss.project"), vssProjectField)
      .addComponent(VssFormUi.createWrappingComment(
        VssBundle.message("message.text.configuration.mapping.hint"), VssFormUi.COMMENT_WRAP_WIDTH))
      .getPanel();

    directoryField.setText(entry.getDirectory());
    vssProjectField.setText(entry.getVssProject());

    return VssFormUi.dialogContentPanel(formPanel, VssFormUi.DIALOG_MAX_WIDTH, VssFormUi.DIALOG_MIN_WIDTH);
  }

  VssMappingEntry getEntry() {
    String directory = directoryField.getText().trim().replace('/', File.separatorChar);
    String vssProject = vssProjectField.getText().trim();
    return new VssMappingEntry(directory, vssProject);
  }

  @Override
  protected ValidationInfo doValidate() {
    String vssProject = vssProjectField.getText().trim();
    if (!vssProject.startsWith(VSS_PROJECT_PREFIX)) {
      return new ValidationInfo(VssBundle.message("message.text.configuration.invalid.project"), vssProjectField);
    }

    String directory = directoryField.getText().trim().replace('/', File.separatorChar);
    if (!directory.isEmpty() && !isUnderProject(directory)) {
      return new ValidationInfo(VssBundle.message("message.text.configuration.mapping.folder.outside"), directoryField);
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    entry.setDirectory(directoryField.getText().trim().replace('/', File.separatorChar));
    entry.setVssProject(vssProjectField.getText().trim());
    super.doOKAction();
  }

  private boolean isUnderProject(String directoryPath) {
    String normalizedDirectory = normalizeMappingPath(directoryPath);
    if (normalizedDirectory.isEmpty()) {
      return true;
    }

    String basePath = project.getBasePath();
    if (basePath != null && isSameOrRelated(normalizeMappingPath(basePath), normalizedDirectory)) {
      return true;
    }

    VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir != null && isSameOrRelated(normalizeMappingPath(projectDir.getPath()), normalizedDirectory)) {
      return true;
    }

    for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
      if (isSameOrRelated(normalizeMappingPath(contentRoot.getPath()), normalizedDirectory)) {
        return true;
      }
    }
    return false;
  }

  private static String normalizeMappingPath(String path) {
    return StringUtil.trimEnd(path.replace('\\', '/'), "/");
  }

  /**
   * Accept mapping when the directory equals, is under, or is a parent of a project/content root.
   */
  private static boolean isSameOrRelated(String first, String second) {
    return FileUtil.pathsEqual(first, second)
           || FileUtil.isAncestor(first, second, false)
           || FileUtil.isAncestor(second, first, false);
  }
}
