package com.intellij.vssSupport.Configuration;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/** Shared compact, resizable form controls for VSS settings and dialogs. */
final class VssFormUi {
  static final int SETTINGS_FIELD_WIDTH = 120;
  static final int SETTINGS_MAX_WIDTH = 360;

  static final int DIALOG_FIELD_WIDTH = 160;
  static final int DIALOG_MAX_WIDTH = 360;
  static final int DIALOG_MIN_WIDTH = 260;
  static final int COMMENT_WRAP_WIDTH = 200;

  private VssFormUi() {
  }

  static void allowHorizontalResize(JComponent component) {
    Dimension minimum = component.getMinimumSize();
    minimum.width = 0;
    component.setMinimumSize(minimum);
    Dimension maximum = component.getMaximumSize();
    maximum.width = Integer.MAX_VALUE;
    component.setMaximumSize(maximum);
  }

  static void setCompactPreferredWidth(JComponent field, int width) {
    Dimension preferred = field.getPreferredSize();
    preferred.width = JBUI.scale(width);
    field.setPreferredSize(preferred);
    allowHorizontalResize(field);
  }

  static JTextField createCompactTextField(int width) {
    JBTextField field = new JBTextField();
    field.setColumns(0);
    setCompactPreferredWidth(field, width);
    return field;
  }

  static JPasswordField createCompactPasswordField(int width) {
    JPasswordField field = new JPasswordField();
    field.setColumns(0);
    setCompactPreferredWidth(field, width);
    return field;
  }

  static TextFieldWithBrowseButton createCompactBrowseField(int width) {
    JBTextField textField = new JBTextField();
    textField.setColumns(0);
    setCompactPreferredWidth(textField, width);
    TextFieldWithBrowseButton field = new TextFieldWithBrowseButton(textField);
    setCompactPreferredWidth(field, width);
    return field;
  }

  static JPanel boundedPanel(@NotNull JComponent content, int maxWidth) {
    JPanel panel = new BoundedWidthPanel(JBUI.scale(maxWidth));
    panel.add(content, BorderLayout.NORTH);
    allowHorizontalResize(panel);
    lockVerticalSize(panel);
    return panel;
  }

  /**
   * Compact dialog body: limited width, height follows content (no vertical stretch / empty gap).
   */
  static JPanel dialogContentPanel(@NotNull JComponent form, int maxWidth, int minWidth) {
    JPanel inner = boundedPanel(form, maxWidth);
    Dimension minimum = inner.getMinimumSize();
    minimum.width = JBUI.scale(minWidth);
    inner.setMinimumSize(minimum);

    JPanel panel = new JPanel(new BorderLayout(0, 0));
    panel.setOpaque(false);
    panel.add(inner, BorderLayout.NORTH);
    lockVerticalSize(panel);
    return panel;
  }

  /** Keep panel height equal to its content; allow horizontal growth only. */
  private static void lockVerticalSize(JPanel panel) {
    Dimension preferred = panel.getPreferredSize();
    panel.setPreferredSize(preferred);
    Dimension minimum = panel.getMinimumSize();
    minimum.height = preferred.height;
    panel.setMinimumSize(minimum);
    Dimension maximum = panel.getMaximumSize();
    maximum.height = preferred.height;
    maximum.width = Integer.MAX_VALUE;
    panel.setMaximumSize(maximum);
  }

  static JComponent createWrappingComment(@NlsContexts.Label String text, int defaultWrapWidth) {
    return new WrappingComment(text, defaultWrapWidth);
  }

  private static final class BoundedWidthPanel extends JPanel {
    private final int maxContentWidth;

    BoundedWidthPanel(int maxContentWidth) {
      super(new BorderLayout());
      this.maxContentWidth = maxContentWidth;
    }

    @Override
    public Dimension getPreferredSize() {
      return capWidth(super.getPreferredSize());
    }

    @Override
    public Dimension getMinimumSize() {
      return capWidth(super.getMinimumSize());
    }

    private Dimension capWidth(Dimension size) {
      int maxWidth = maxContentWidth + getInsets().left + getInsets().right;
      if (size.width > maxWidth) {
        size.width = maxWidth;
      }
      return size;
    }
  }

  private static final class WrappingComment extends JTextArea {
    private final int defaultWrapWidth;

    WrappingComment(@NlsContexts.Label String text, int defaultWrapWidth) {
      super(text);
      this.defaultWrapWidth = defaultWrapWidth;
      setEditable(false);
      setLineWrap(true);
      setWrapStyleWord(true);
      setOpaque(false);
      setBorder(JBUI.Borders.empty());
      setFont(JBUI.Fonts.smallFont());
      setForeground(UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER));
      setFocusable(false);
      setHighlighter(null);
      allowHorizontalResize(this);

      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          revalidate();
        }
      });
    }

    @Override
    public Dimension getPreferredSize() {
      int width = findContainerWidth();
      if (width <= 0) {
        width = JBUI.scale(defaultWrapWidth);
      }
      setSize(width, Short.MAX_VALUE);
      Dimension size = super.getPreferredSize();
      return new Dimension(width, size.height);
    }

    private int findContainerWidth() {
      Container container = getParent();
      while (container != null) {
        if (container.getWidth() > 0) {
          Insets insets = container.getInsets();
          return container.getWidth() - insets.left - insets.right;
        }
        container = container.getParent();
      }
      return 0;
    }
  }
}
