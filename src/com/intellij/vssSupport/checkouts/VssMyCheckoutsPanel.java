package com.intellij.vssSupport.checkouts;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssTruncatedFileNameUtil;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.actions.VssDirectCheckinAction;
import com.intellij.vssSupport.commands.DiffFileCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class VssMyCheckoutsPanel extends JPanel implements com.intellij.openapi.Disposable {
  private static final int NAME_WIDTH = 280;
  private static final int USER_WIDTH = 100;
  private static final int DATE_WIDTH = 140;
  private static final int FOLDER_WIDTH = 320;
  private static final int ICON_BUTTON_SIZE = 22;

  private final Project project;
  private final VssMyCheckoutsService service;
  private final ListTableModel<SearchStatusRow> tableModel;
  private final JBTable table;
  private final JTextField userFilterField;
  private final JCheckBox allUsersCheckBox;
  private final JCheckBox copyFilenameOnly;
  private final Runnable refreshListener = this::reloadTable;
  private int[] lastSelectedRows = new int[0];

  public VssMyCheckoutsPanel(@NotNull Project project) {
    super(new BorderLayout());
    this.project = project;
    service = VssMyCheckoutsService.getInstance(project);
    tableModel = new ListTableModel<>(
      new NameColumn(),
      new UserColumn(),
      new DateColumn(),
      new WorkingFolderColumn()
    );
    table = new JBTable(tableModel);
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    table.getTableHeader().setReorderingAllowed(false);
    table.setDefaultRenderer(Object.class, new SearchStatusRowRenderer(project, tableModel));
    installColumnWidths(table);
    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
          openSelectedFile();
        }
      }
    });
    table.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        lastSelectedRows = table.getSelectedRows();
      }
    });

    userFilterField = new JTextField(VssConfiguration.getInstance(project).USER_NAME, 8);
    allUsersCheckBox = new JCheckBox(VssBundle.message("checkbox.my.checkouts.all.users"));
    copyFilenameOnly = new JCheckBox(VssBundle.message("checkbox.my.checkouts.copy.filename.only"));
    copyFilenameOnly.setSelected(true);
    copyFilenameOnly.setBorder(JBUI.Borders.emptyLeft(2));

    allUsersCheckBox.addActionListener(e -> {
      userFilterField.setEnabled(!allUsersCheckBox.isSelected());
      applyUserFilterFromUi();
    });

    JPanel content = new JPanel(new BorderLayout());
    content.add(createTopBar(), BorderLayout.NORTH);
    content.add(createLeftToolbar(), BorderLayout.WEST);
    content.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);
    add(content, BorderLayout.CENTER);

    service.addListener(refreshListener);
    service.setBeforeSearchRunnable(this::applyUserFilterFromUi);
    VssMyCheckoutsCommitHelper.removeLegacyChangeList(project);
    applyUserFilterFromUi();
    reloadTable();
  }

  @Override
  public void dispose() {
    service.removeListener(refreshListener);
    service.setBeforeSearchRunnable(null);
    service.cancelActiveScan();
  }

  private JPanel createTopBar() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(2), JBUI.scale(0)));
    row.setBorder(JBUI.Borders.empty(2, 4));

    row.add(createIconButton(AllIcons.Actions.Copy,
                             VssBundle.message("action.Vss.MyCheckouts.Copy.text"),
                             this::copySelection));
    row.add(copyFilenameOnly);

    row.add(createVerticalSeparator());

    row.add(new JLabel(VssBundle.message("label.my.checkouts.user.filter")));
    row.add(userFilterField);
    row.add(allUsersCheckBox);
    return row;
  }

  private JComponent createLeftToolbar() {
    JPanel toolbar = new JPanel();
    toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
    toolbar.setBorder(JBUI.Borders.empty(2, 2, 2, 2));

    toolbar.add(createIconButton(AllIcons.Actions.Refresh,
                                 VssBundle.message("action.Vss.MyCheckouts.Refresh.description"),
                                 this::refreshFromButton));
    toolbar.add(Box.createVerticalStrut(JBUI.scale(2)));
    toolbar.add(createIconButton(AllIcons.Vcs.Push,
                                 VssBundle.message("action.Vss.MyCheckouts.Checkin.text"),
                                 this::checkIn));
    toolbar.add(Box.createVerticalStrut(JBUI.scale(2)));
    toolbar.add(createIconButton(AllIcons.Actions.Rollback,
                                 VssBundle.message("action.Vss.MyCheckouts.Undocheckout.text"),
                                 this::undoCheckout));
    toolbar.add(Box.createVerticalStrut(JBUI.scale(2)));
    toolbar.add(createIconButton(AllIcons.Actions.Diff,
                                 VssBundle.message("action.Vss.Diff.description"),
                                 this::compareWithVss));

    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(toolbar, BorderLayout.NORTH);
    wrapper.setBorder(JBUI.Borders.customLine(UIUtil.getPanelBackground(), 0, 0, 0, 1));
    return wrapper;
  }

  private static JButton createIconButton(@NotNull Icon icon,
                                          @NotNull String tooltip,
                                          @NotNull Runnable action) {
    return createIconButton(icon, tooltip, e -> action.run());
  }

  private static JButton createIconButton(@NotNull Icon icon,
                                          @NotNull String tooltip,
                                          @NotNull Consumer<ActionEvent> action) {
    JButton button = new JButton(icon);
    button.setToolTipText(tooltip);
    button.setMargin(JBUI.emptyInsets());
    int size = JBUI.scale(ICON_BUTTON_SIZE);
    button.setPreferredSize(new Dimension(size, size));
    button.setMinimumSize(new Dimension(size, size));
    button.setMaximumSize(new Dimension(size, size));
    button.setBorder(JBUI.Borders.empty(2));
    button.setBorderPainted(false);
    button.setContentAreaFilled(false);
    button.setFocusable(false);
    button.addActionListener(action::accept);
    button.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        button.setContentAreaFilled(true);
        button.setBackground(JBUI.CurrentTheme.ActionButton.hoverBackground());
      }

      @Override
      public void mouseExited(MouseEvent e) {
        button.setContentAreaFilled(false);
      }
    });
    return button;
  }

  private static JComponent createVerticalSeparator() {
    JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
    separator.setPreferredSize(new Dimension(JBUI.scale(4), JBUI.scale(18)));
    return separator;
  }

  private void refreshFromButton(@NotNull ActionEvent event) {
    applyUserFilterFromUi();
    boolean fullScan = (event.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
    service.cancelActiveScan();
    service.refreshOnUserRequest(fullScan);
  }

  private void applyUserFilterFromUi() {
    if (allUsersCheckBox.isSelected()) {
      service.setUserFilter(MyCheckoutsUserFilter.forAllUsers());
    }
    else {
      String user = userFilterField.getText().trim();
      if (StringUtil.isEmpty(user)) {
        user = VssConfiguration.getInstance(project).USER_NAME.trim();
      }
      service.setUserFilter(MyCheckoutsUserFilter.currentUser(user));
    }
  }

  private void reloadTable() {
    syncUserFilterUiFromService();
    List<VssCheckoutEntry> entries = service.getEntries();
    tableModel.setItems(SearchStatusRowBuilder.build(project, entries));
    installColumnWidths(table);
    if (entries.isEmpty()) {
      table.getEmptyText().setText(VssBundle.message("message.my.checkouts.empty.hint"));
    }
  }

  private void syncUserFilterUiFromService() {
    MyCheckoutsUserFilter filter = service.getUserFilter();
    allUsersCheckBox.setSelected(filter.matchAllUsers());
    userFilterField.setEnabled(!filter.matchAllUsers());
    if (!filter.matchAllUsers() && !filter.specifiedUser().equals(userFilterField.getText().trim())) {
      userFilterField.setText(filter.specifiedUser());
    }
  }

  @NotNull
  private List<VssCheckoutEntry> getSelectedEntries() {
    int[] rows = table.getSelectedRows();
    if (rows.length == 0 && lastSelectedRows.length > 0) {
      rows = lastSelectedRows;
    }
    List<VssCheckoutEntry> selected = new ArrayList<>();
    for (int row : rows) {
      if (row < 0 || row >= tableModel.getRowCount()) {
        continue;
      }
      SearchStatusRow item = tableModel.getItem(row);
      if (item.entry() != null) {
        selected.add(item.entry());
      }
    }
    return selected;
  }

  @Nullable
  private VirtualFile resolveSelectedFile(@NotNull VssCheckoutEntry entry) {
    return VssMyCheckoutsCommitHelper.resolveEntryFile(entry);
  }

  private void copySelection() {
    List<VssCheckoutEntry> selected = getSelectedEntries();
    if (selected.isEmpty()) {
      Messages.showInfoMessage(project, VssBundle.message("message.my.checkouts.select.files"),
                               VssBundle.message("toolwindow.vss.search.for.status"));
      return;
    }
    boolean filenameOnly = copyFilenameOnly.isSelected();
    String text = selected.stream()
      .map(entry -> filenameOnly
                    ? VssTruncatedFileNameUtil.displayFileName(entry.localPath(), entry.fileName())
                    : entry.presentablePath())
      .collect(Collectors.joining(System.lineSeparator()));
    CopyPasteManager.getInstance().setContents(new StringSelection(text));
  }

  private void openSelectedFile() {
    List<VssCheckoutEntry> selected = getSelectedEntries();
    if (selected.size() != 1) {
      return;
    }
    VirtualFile file = resolveSelectedFile(selected.get(0));
    if (file != null && !file.isDirectory()) {
      new OpenFileDescriptor(project, file).navigate(true);
    }
  }

  private void checkIn() {
    List<VssCheckoutEntry> selected = getSelectedEntries();
    if (selected.isEmpty()) {
      Messages.showInfoMessage(project, VssBundle.message("message.my.checkouts.select.files"),
                               VssBundle.message("toolwindow.vss.search.for.status"));
      return;
    }
    List<VirtualFile> files = VssMyCheckoutsCommitHelper.resolveEntryFiles(selected);
    if (files.isEmpty()) {
      Messages.showInfoMessage(project, VssBundle.message("message.my.checkouts.files.not.found"),
                               VssBundle.message("toolwindow.vss.search.for.status"));
      return;
    }
    VssDirectCheckinAction.performCheckin(project, files.toArray(VirtualFile[]::new));
  }

  private void compareWithVss() {
    List<VssCheckoutEntry> selected = getSelectedEntries();
    if (selected.size() != 1) {
      Messages.showInfoMessage(project, VssBundle.message("message.my.checkouts.select.one.file"),
                               VssBundle.message("toolwindow.vss.search.for.status"));
      return;
    }
    VirtualFile file = resolveSelectedFile(selected.get(0));
    if (file == null || file.isDirectory()) {
      Messages.showInfoMessage(project, VssBundle.message("message.my.checkouts.files.not.found"),
                               VssBundle.message("toolwindow.vss.search.for.status"));
      return;
    }
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> {
        List<VcsException> errors = new ArrayList<>();
        new DiffFileCommand(project, file, errors).execute();
        if (!errors.isEmpty()) {
          Messages.showErrorDialog(project, errors.get(0).getLocalizedMessage(), VssBundle.message("message.title.error"));
        }
      },
      VssBundle.message("action.Vss.Diff.description"),
      true,
      project
    );
  }

  private void undoCheckout() {
    List<VssCheckoutEntry> selected = getSelectedEntries();
    if (selected.isEmpty()) {
      Messages.showInfoMessage(project, VssBundle.message("message.my.checkouts.select.files"),
                               VssBundle.message("toolwindow.vss.search.for.status"));
      return;
    }
    VirtualFile[] files = selected.stream()
      .map(this::resolveSelectedFile)
      .filter(f -> f != null && !f.isDirectory())
      .toArray(VirtualFile[]::new);
    if (files.length == 0) {
      return;
    }
    VssUndocheckoutHelper.undoCheckout(project, files, false);
  }

  private static void installColumnWidths(@NotNull JTable table) {
    TableColumnModel columnModel = table.getColumnModel();
    if (columnModel.getColumnCount() < 4) {
      return;
    }
    setPreferredWidth(columnModel.getColumn(0), NAME_WIDTH);
    setPreferredWidth(columnModel.getColumn(1), USER_WIDTH);
    setPreferredWidth(columnModel.getColumn(2), DATE_WIDTH);
    setPreferredWidth(columnModel.getColumn(3), FOLDER_WIDTH);
    table.revalidate();
  }

  private static void setPreferredWidth(@NotNull TableColumn column, int width) {
    int scaled = JBUI.scale(width);
    column.setPreferredWidth(scaled);
    column.setMinWidth(scaled / 2);
  }

  private static final class SearchStatusRowRenderer extends DefaultTableCellRenderer {
    private final Project project;
    private final ListTableModel<SearchStatusRow> model;

    private SearchStatusRowRenderer(@NotNull Project project, @NotNull ListTableModel<SearchStatusRow> model) {
      this.project = project;
      this.model = model;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
      JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      SearchStatusRow statusRow = model.getItem(row);
      label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

      if (statusRow.entry() == null) {
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
        return label;
      }

      Color statusColor = resolveVcsStatusColor(statusRow);
      if (statusColor != null) {
        label.setForeground(statusColor);
      }
      else {
        label.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
      }
      return label;
    }

    @Nullable
    private Color resolveVcsStatusColor(@NotNull SearchStatusRow row) {
      if (row.entry() == null) {
        return null;
      }
      VirtualFile file = VssUtil.getVirtualFile(
        VssTruncatedFileNameUtil.completeTruncatedLocalPath(row.entry().localPath()));
      FileStatus status = file != null
                          ? FileStatusManager.getInstance(project).getStatus(file)
                          : FileStatus.NOT_CHANGED;
      if (status == FileStatus.NOT_CHANGED) {
        return FileStatus.MODIFIED.getColor();
      }
      return status.getColor();
    }
  }

  private static final class NameColumn extends ColumnInfo<SearchStatusRow, String> {
    NameColumn() {
      super(VssBundle.message("column.search.status.name"));
    }

    @Override
    public String valueOf(SearchStatusRow row) {
      return row.name();
    }
  }

  private static final class UserColumn extends ColumnInfo<SearchStatusRow, String> {
    UserColumn() {
      super(VssBundle.message("column.search.status.user"));
    }

    @Override
    public String valueOf(SearchStatusRow row) {
      return row.user();
    }
  }

  private static final class DateColumn extends ColumnInfo<SearchStatusRow, String> {
    DateColumn() {
      super(VssBundle.message("column.search.status.date"));
    }

    @Override
    public String valueOf(SearchStatusRow row) {
      return row.dateTime();
    }
  }

  private static final class WorkingFolderColumn extends ColumnInfo<SearchStatusRow, String> {
    WorkingFolderColumn() {
      super(VssBundle.message("column.search.status.folder"));
    }

    @Override
    public String valueOf(SearchStatusRow row) {
      return row.workingFolder();
    }
  }
}
