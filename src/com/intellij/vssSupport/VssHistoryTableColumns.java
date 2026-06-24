package com.intellij.vssSupport;



import com.intellij.util.concurrency.AppExecutorUtil;

import com.intellij.util.ui.JBUI;

import com.intellij.util.ui.UIUtil;

import org.jetbrains.annotations.NotNull;



import javax.swing.*;

import javax.swing.event.AncestorEvent;

import javax.swing.event.AncestorListener;

import javax.swing.table.TableColumn;

import javax.swing.table.TableColumnModel;

import java.awt.*;

import java.awt.event.ComponentAdapter;

import java.awt.event.ComponentEvent;

import java.beans.PropertyChangeEvent;

import java.util.ArrayList;

import java.util.LinkedHashSet;

import java.util.List;

import java.util.Set;

import java.util.concurrent.TimeUnit;



/** Ensures SourceSafe file history tables stretch columns across the panel width. */

final class VssHistoryTableColumns {

  private static final int[] RETRY_DELAYS_MS = {0, 50, 150, 400, 1000, 2000};



  private VssHistoryTableColumns() {

  }



  static void installWidthHints(@NotNull JComponent historyPanel) {

    Runnable adjust = () -> apply(findSearchRoots(historyPanel));

    historyPanel.addAncestorListener(new AncestorListener() {

      @Override

      public void ancestorAdded(AncestorEvent event) {

        adjust.run();

        scheduleRetries(historyPanel);

      }



      @Override

      public void ancestorRemoved(AncestorEvent event) {

      }



      @Override

      public void ancestorMoved(AncestorEvent event) {

        adjust.run();

      }

    });

    historyPanel.addComponentListener(new ComponentAdapter() {

      @Override

      public void componentShown(ComponentEvent e) {

        adjust.run();

        scheduleRetries(historyPanel);

      }



      @Override

      public void componentResized(ComponentEvent e) {

        adjust.run();

      }

    });

    SwingUtilities.invokeLater(adjust);

    scheduleRetries(historyPanel);

  }



  private static void scheduleRetries(@NotNull JComponent historyPanel) {

    for (int delay : RETRY_DELAYS_MS) {

      AppExecutorUtil.getAppScheduledExecutorService().schedule(

        () -> SwingUtilities.invokeLater(() -> apply(findSearchRoots(historyPanel))),

        delay,

        TimeUnit.MILLISECONDS

      );

    }

  }



  @NotNull

  private static List<JComponent> findSearchRoots(@NotNull JComponent anchor) {

    Set<JComponent> roots = new LinkedHashSet<>();

    roots.add(anchor);



    Container current = anchor;

    for (int depth = 0; depth < 10 && current != null; depth++) {

      if (current instanceof JComponent jc) {

        roots.add(jc);

      }

      current = current.getParent();

    }



    Window window = SwingUtilities.getWindowAncestor(anchor);
    if (window instanceof RootPaneContainer rootPaneContainer
        && rootPaneContainer.getRootPane() != null) {
      roots.add(rootPaneContainer.getRootPane());
    }

    return new ArrayList<>(roots);

  }



  private static void apply(@NotNull List<JComponent> roots) {

    Set<JTable> tables = new LinkedHashSet<>();

    for (JComponent root : roots) {

      tables.addAll(UIUtil.findComponentsOfType(root, JTable.class));

    }

    for (JTable table : tables) {

      applyToTable(table);

    }

  }



  private static void applyToTable(@NotNull JTable table) {

    TableColumnModel columnModel = table.getColumnModel();

    int columnCount = columnModel.getColumnCount();

    if (columnCount == 0) {

      return;

    }



    table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

    var header = table.getTableHeader();

    if (header != null) {

      header.setReorderingAllowed(false);

    }



    int tableWidth = Math.max(table.getWidth(), table.getParent() != null ? table.getParent().getWidth() : 0);

    if (tableWidth <= 0) {

      tableWidth = JBUI.scale(800);

    }

    int defaultWidth = Math.max(JBUI.scale(80), tableWidth / columnCount);



    for (int i = 0; i < columnCount; i++) {

      TableColumn column = columnModel.getColumn(i);

      column.setMinWidth(JBUI.scale(48));

      column.setMaxWidth(Integer.MAX_VALUE);

      column.setPreferredWidth(defaultWidth);

    }



    if (!Boolean.TRUE.equals(table.getClientProperty("vss.history.columns.hooked"))) {

      table.putClientProperty("vss.history.columns.hooked", Boolean.TRUE);

      table.addPropertyChangeListener("columnModel", (PropertyChangeEvent e) -> applyToTable(table));

      table.getModel().addTableModelListener(e -> SwingUtilities.invokeLater(() -> applyToTable(table)));

      table.addComponentListener(new ComponentAdapter() {

        @Override

        public void componentResized(ComponentEvent e) {

          applyToTable(table);

        }

      });

    }



    table.doLayout();

    table.revalidate();

    table.repaint();

  }

}

