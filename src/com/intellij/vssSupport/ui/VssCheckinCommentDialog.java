package com.intellij.vssSupport.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.vssSupport.VssBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class VssCheckinCommentDialog extends DialogWrapper {
  private final JTextArea commentArea;

  public VssCheckinCommentDialog(@NotNull Project project, @NotNull String defaultComment) {
    super(project);
    setTitle(VssBundle.message("dialog.title.checkin.file"));
    commentArea = new JTextArea(8, 50);
    commentArea.setLineWrap(true);
    commentArea.setWrapStyleWord(true);
    commentArea.setText(defaultComment);
    init();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(JBUI.Borders.empty());
    JLabel prompt = new JLabel(VssBundle.message("message.my.checkouts.checkin.prompt"));
    prompt.setBorder(JBUI.Borders.emptyBottom(4));
    panel.add(prompt);
    panel.add(ScrollPaneFactory.createScrollPane(commentArea));
    return panel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return commentArea;
  }

  public @NotNull String getComment() {
    return commentArea.getText();
  }
}
