package com.intellij.vssSupport.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.occupancy.VssCheckoutInfo;
import com.intellij.vssSupport.occupancy.VssFileOccupancy;
import com.intellij.vssSupport.occupancy.VssPropertiesInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class VssPropertiesDialog extends DialogWrapper {
  private final VssFileOccupancy occupancy;

  public VssPropertiesDialog(Project project, VssFileOccupancy occupancy) {
    super(project, true);
    this.occupancy = occupancy;
    setTitle(VssBundle.message("dialog.title.vss.properties"));
    setOKButtonText(VssBundle.message("button.close"));
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setPreferredSize(JBUI.size(560, 380));

    JPanel form = new JPanel(new GridBagLayout());
    GridBagConstraints labelConstraints = new GridBagConstraints();
    labelConstraints.gridx = 0;
    labelConstraints.anchor = GridBagConstraints.NORTHWEST;
    labelConstraints.insets = JBUI.insets(2, 0, 2, 12);

    GridBagConstraints valueConstraints = new GridBagConstraints();
    valueConstraints.gridx = 1;
    valueConstraints.anchor = GridBagConstraints.NORTHWEST;
    valueConstraints.insets = JBUI.insets(2, 0, 2, 0);
    valueConstraints.weightx = 1.0;
    valueConstraints.fill = GridBagConstraints.HORIZONTAL;

    int row = 0;
    VssPropertiesInfo properties = occupancy.getProperties();
    VssCheckoutInfo checkout = occupancy.getCheckout();

    row = addRow(form, row, labelConstraints, valueConstraints,
                 VssBundle.message("label.vss.properties.local.path"), occupancy.getLocalPath());
    row = addRow(form, row, labelConstraints, valueConstraints,
                 VssBundle.message("label.vss.properties.vss.path"),
                 properties != null ? properties.getVssPath() : null);
    row = addRow(form, row, labelConstraints, valueConstraints,
                 VssBundle.message("label.vss.properties.latest.version"),
                 properties != null ? properties.getLatestVersion() : null);
    row = addRow(form, row, labelConstraints, valueConstraints,
                 VssBundle.message("label.vss.properties.latest.date"),
                 properties != null ? properties.getLatestDate() : null);
    row = addRow(form, row, labelConstraints, valueConstraints,
                 VssBundle.message("label.vss.properties.type"),
                 properties != null ? properties.getFileType() : null);
    row = addRow(form, row, labelConstraints, valueConstraints,
                 VssBundle.message("label.vss.properties.size"),
                 properties != null ? properties.getSize() : null);
    row = addRow(form, row, labelConstraints, valueConstraints,
                 VssBundle.message("label.vss.properties.checkout.status"), occupancy.getStatusSummary());
    row = addRow(form, row, labelConstraints, valueConstraints,
                 VssBundle.message("label.vss.properties.checkout.user"), checkout.getUser());
    row = addRow(form, row, labelConstraints, valueConstraints,
                 VssBundle.message("label.vss.properties.checkout.type"), checkout.getCheckoutTypeDisplay());
    row = addRow(form, row, labelConstraints, valueConstraints,
                 VssBundle.message("label.vss.properties.checkout.date"), checkout.getDateTime());
    row = addRow(form, row, labelConstraints, valueConstraints,
                 VssBundle.message("label.vss.properties.checkout.folder"), checkout.getWorkingFolder());

    String comment = properties != null ? properties.getLatestComment() : null;
    if (StringUtil.isNotEmpty(comment)) {
      labelConstraints.gridy = row;
      valueConstraints.gridy = row;
      valueConstraints.weighty = 1.0;
      form.add(new JBLabel(VssBundle.message("label.vss.properties.comment")), labelConstraints);
      JTextArea area = new JTextArea(comment);
      area.setEditable(false);
      area.setLineWrap(true);
      area.setWrapStyleWord(true);
      area.setBackground(form.getBackground());
      form.add(new JBScrollPane(area), valueConstraints);
    }

    panel.add(form, BorderLayout.CENTER);
    return panel;
  }

  private static int addRow(JPanel form,
                            int row,
                            GridBagConstraints labelConstraints,
                            GridBagConstraints valueConstraints,
                            String label,
                            @Nullable String value) {
    if (StringUtil.isEmpty(value)) {
      value = VssBundle.message("label.vss.properties.not.available");
    }
    labelConstraints.gridy = row;
    valueConstraints.gridy = row;
    valueConstraints.weighty = 0.0;
    form.add(new JBLabel(label), labelConstraints);
    form.add(new JBLabel(value), valueConstraints);
    return row + 1;
  }

  @Override
  protected Action @Nullable [] createActions() {
    return new Action[]{getOKAction()};
  }
}
