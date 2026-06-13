package com.intellij.vssSupport.Configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.JBUI;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssVcs;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Project directory to VSS project mappings, synced with {@link ProjectLevelVcsManager}.
 */
public final class VssDirectoryMappingsPanel extends JPanel {
  private final Project project;
  private final ListTableModel<VssMappingEntry> tableModel;
  private final JBTable table;
  private List<VssMappingEntry> originalEntries = List.of();

  public VssDirectoryMappingsPanel(Project project) {
    this.project = project;
    tableModel = new ListTableModel<>(
      new WorkingDirectoryColumn(),
      new VssProjectColumn()
    );
    table = new JBTable(tableModel);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getColumnModel().getColumn(0).setCellRenderer(new ProjectRootRenderer());

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(table)
      .setAddAction(button -> addMapping())
      .setEditAction(button -> editMapping())
      .setRemoveAction(button -> removeMapping())
      .disableUpAction()
      .disableDownAction();

    JButton checkButton = new JButton(VssBundle.message("button.configuration.check.mapping"));
    checkButton.addActionListener(e -> checkSelectedMapping());

    JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    southPanel.setBorder(JBUI.Borders.emptyTop(4));
    southPanel.add(checkButton);

    setLayout(new BorderLayout());
    setBorder(JBUI.Borders.emptyTop(8));
    add(decorator.createPanel(), BorderLayout.CENTER);
    add(southPanel, BorderLayout.SOUTH);

    table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    table.setPreferredScrollableViewportSize(JBUI.size(80, 120));
    setMinimumSize(JBUI.emptySize());
    Dimension pref = getPreferredSize();
    pref.width = Math.min(pref.width, JBUI.scale(320));
    setPreferredSize(pref);
  }

  private void checkSelectedMapping() {
    int index = table.getSelectedRow();
    if (index < 0) {
      Messages.showInfoMessage(this,
                               VssBundle.message("message.text.configuration.select.mapping.to.check"),
                               VssBundle.message("message.title.check.status"));
      return;
    }
    VssMappingEntry entry = tableModel.getItem(index);
    try {
      VssMappingChecker.checkVssProject(project, entry.getVssProject());
      Messages.showInfoMessage(project,
                               VssBundle.message("message.project.valid"),
                               VssBundle.message("message.title.check.status"));
    }
    catch (VcsException e) {
      Messages.showErrorDialog(project,
                               e.getLocalizedMessage(),
                               VssBundle.message("message.title.check.status"));
    }
  }

  public void reset() {
    List<VssMappingEntry> entries = VssMappingStorage.loadMappingEntries(project);
    originalEntries = copyEntries(entries);
    tableModel.setItems(entries);
  }

  public boolean isModified() {
    return !entriesEqual(originalEntries, tableModel.getItems());
  }

  public void apply() {
    commitMappingsToStorage();
  }

  /** Persists mappings immediately so they survive IDE restart even if Settings is not applied. */
  private void commitMappingsToStorage() {
    VssMappingStorage.syncMappings(project, tableModel.getItems());
    originalEntries = copyEntries(tableModel.getItems());
  }

  private void addMapping() {
    VssMappingEntry entry = new VssMappingEntry("", "$/");
    VssMappingEditDialog dialog = new VssMappingEditDialog(project, entry, true);
    if (!dialog.showAndGet()) {
      return;
    }
    VssMappingEntry newEntry = dialog.getEntry();
    if (hasDuplicateDirectory(newEntry) || hasDuplicateVssProject(newEntry)) {
      return;
    }
    tableModel.addRow(newEntry);
    commitMappingsToStorage();
  }

  private void editMapping() {
    int index = table.getSelectedRow();
    if (index < 0) {
      return;
    }
    VssMappingEntry current = tableModel.getItem(index);
    VssMappingEditDialog dialog = new VssMappingEditDialog(project, current, false);
    if (!dialog.showAndGet()) {
      return;
    }
    VssMappingEntry updated = dialog.getEntry();
    if (hasDuplicateDirectory(updated, index) || hasDuplicateVssProject(updated, index)) {
      return;
    }
    List<VssMappingEntry> items = new ArrayList<>(tableModel.getItems());
    items.set(index, updated);
    tableModel.setItems(items);
    commitMappingsToStorage();
  }

  private void removeMapping() {
    int index = table.getSelectedRow();
    if (index >= 0) {
      tableModel.removeRow(index);
      commitMappingsToStorage();
    }
  }

  private boolean hasDuplicateDirectory(VssMappingEntry entry) {
    return hasDuplicateDirectory(entry, -1);
  }

  private boolean hasDuplicateDirectory(VssMappingEntry entry, int skipIndex) {
    for (int i = 0; i < tableModel.getRowCount(); i++) {
      if (i == skipIndex) {
        continue;
      }
      if (tableModel.getItem(i).getDirectory().equals(entry.getDirectory())) {
        JOptionPane.showMessageDialog(this,
                                      VssBundle.message("message.text.configuration.mapping.with.directory.exists"),
                                      VssBundle.message("message.title.error"),
                                      JOptionPane.ERROR_MESSAGE);
        return true;
      }
    }
    return false;
  }

  private boolean hasDuplicateVssProject(VssMappingEntry entry) {
    return hasDuplicateVssProject(entry, -1);
  }

  private boolean hasDuplicateVssProject(VssMappingEntry entry, int skipIndex) {
    for (int i = 0; i < tableModel.getRowCount(); i++) {
      if (i == skipIndex) {
        continue;
      }
      if (tableModel.getItem(i).getVssProject().equals(entry.getVssProject())) {
        JOptionPane.showMessageDialog(this,
                                      VssBundle.message("message.text.configuration.mapping.with.project.exists"),
                                      VssBundle.message("message.title.error"),
                                      JOptionPane.ERROR_MESSAGE);
        return true;
      }
    }
    return false;
  }

  private static List<VssMappingEntry> copyEntries(List<VssMappingEntry> source) {
    List<VssMappingEntry> copy = new ArrayList<>(source.size());
    for (VssMappingEntry entry : source) {
      copy.add(new VssMappingEntry(entry.getDirectory(), entry.getVssProject()));
    }
    return copy;
  }

  private static boolean entriesEqual(List<VssMappingEntry> left, List<VssMappingEntry> right) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int i = 0; i < left.size(); i++) {
      if (!left.get(i).contentEquals(right.get(i))) {
        return false;
      }
    }
    return true;
  }

  private static final class WorkingDirectoryColumn extends com.intellij.util.ui.ColumnInfo<VssMappingEntry, String> {
    WorkingDirectoryColumn() {
      super(VssBundle.message("column.name.configuration.working.directory"));
    }

    @Override
    public String valueOf(VssMappingEntry entry) {
      return entry.getDirectory();
    }
  }

  private static final class VssProjectColumn extends com.intellij.util.ui.ColumnInfo<VssMappingEntry, String> {
    VssProjectColumn() {
      super(VssBundle.message("column.name.configuration.vss.project"));
    }

    @Override
    public String valueOf(VssMappingEntry entry) {
      return entry.getVssProject();
    }
  }

  private static final class ProjectRootRenderer extends DefaultTableCellRenderer {
    @Override
    protected void setValue(Object value) {
      if (value instanceof String path && path.isEmpty()) {
        setText(VcsDirectoryMapping.PROJECT_CONSTANT.get());
      }
      else {
        setText(value != null ? value.toString() : "");
      }
    }
  }
}
