package com.intellij.vssSupport;

import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

/** Applies default widths to SourceSafe file history tables without changing cell content. */
final class VssHistoryTableColumns {
  @NonNls private static final String DATE_HEADER = "Date";
  @NonNls private static final String ACTION_HEADER = "Action";
  @NonNls private static final String LABEL_HEADER = "Label";

  private static final int DATE_WIDTH = 240;
  private static final int ACTION_WIDTH = 200;
  private static final int LABEL_WIDTH = 320;

  private VssHistoryTableColumns() {
  }

  static void installWidthHints(JComponent historyPanel) {
    Runnable adjust = () -> apply(historyPanel);
    historyPanel.addAncestorListener(new AncestorListener() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        adjust.run();
        SwingUtilities.invokeLater(adjust);
      }

      @Override
      public void ancestorRemoved(AncestorEvent event) {
      }

      @Override
      public void ancestorMoved(AncestorEvent event) {
      }
    });
    historyPanel.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        adjust.run();
      }
    });
    SwingUtilities.invokeLater(adjust);
  }

  private static void apply(JComponent root) {
    List<JTable> tables = UIUtil.findComponentsOfType(root, JTable.class);
    for (JTable table : tables) {
      applyToTable(table);
    }
  }

  private static void applyToTable(JTable table) {
    TableColumnModel columnModel = table.getColumnModel();
    for (int i = 0; i < columnModel.getColumnCount(); i++) {
      TableColumn column = columnModel.getColumn(i);
      Object header = column.getHeaderValue();
      if (header == null) {
        continue;
      }
      int width = widthForHeader(header.toString());
      if (width > 0) {
        column.setPreferredWidth(width);
        column.setMinWidth(width / 2);
      }
    }
    table.revalidate();
  }

  private static int widthForHeader(String header) {
    if (DATE_HEADER.equals(header)) {
      return JBUI.scale(DATE_WIDTH);
    }
    if (ACTION_HEADER.equals(header)) {
      return JBUI.scale(ACTION_WIDTH);
    }
    if (LABEL_HEADER.equals(header)) {
      return JBUI.scale(LABEL_WIDTH);
    }
    return -1;
  }
}
