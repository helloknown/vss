package com.intellij.vssSupport;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.ui.EditorTextField;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler;
import com.intellij.vcs.commit.CommitMessageUi;

import java.util.List;

/**
 * Allows empty commit messages for VSS-only commits. The platform commit UI requires a non-blank message;
 * we keep an invisible placeholder in the editor so the Commit button stays enabled, then strip it before check-in.
 */
public final class VssEmptyCommitMessageSupport implements ChangesViewWorkflowManager.ChangesViewWorkflowListener {
  private final Project project;
  private Document hookedDocument;

  public VssEmptyCommitMessageSupport(Project project) {
    this.project = project;
  }

  public void install(MessageBusConnection connection) {
    connection.subscribe(ChangesViewWorkflowManager.TOPIC, this);
    commitWorkflowChanged();
  }

  @Override
  public void commitWorkflowChanged() {
    if (VssVcs.getInstance(project) == null) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(this::hookCommitMessageEditor);
  }

  private void hookCommitMessageEditor() {
    ChangesViewCommitWorkflowHandler handler = ChangesViewWorkflowManager.getInstance(project).getCommitWorkflowHandler();
    if (handler == null) {
      return;
    }

    CommitMessageUi messageUi = handler.getUi().getCommitMessageUi();
    if (!(messageUi instanceof CommitMessage commitMessage)) {
      return;
    }

    EditorTextField editorField = commitMessage.getEditorField();
    Document document = editorField.getDocument();
    if (document == hookedDocument) {
      return;
    }
    hookedDocument = document;

    document.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent e) {
        ensurePlaceholderIfNeeded(handler, document);
      }
    });

    handler.getUi().addInclusionListener(() -> {
      ensurePlaceholderIfNeeded(handler, document);
      handler.updateDefaultCommitActionEnabled();
    }, handler.getUi());

    ensurePlaceholderIfNeeded(handler, document);
  }

  private void ensurePlaceholderIfNeeded(ChangesViewCommitWorkflowHandler handler, Document document) {
    if (!shouldAllowEmptyCommitMessage(handler)) {
      return;
    }
    if (!document.getText().isEmpty()) {
      return;
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      if (document.getText().isEmpty()) {
        document.insertString(0, VssCommitMessageUtil.EMPTY_MESSAGE_PLACEHOLDER);
      }
    });
    handler.updateDefaultCommitActionEnabled();
  }

  private boolean shouldAllowEmptyCommitMessage(ChangesViewCommitWorkflowHandler handler) {
    VssVcs vss = VssVcs.getInstance(project);
    if (vss == null) {
      return false;
    }
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    List<Change> included = handler.getUi().getIncludedChanges();
    if (included.isEmpty()) {
      for (com.intellij.openapi.vcs.AbstractVcs activeVcs : vcsManager.getAllActiveVcss()) {
        if (activeVcs == vss) {
          return true;
        }
      }
      return false;
    }
    for (Change change : included) {
      FilePath path = ChangesUtil.getFilePath(change);
      if (vcsManager.getVcsFor(path) != vss) {
        return false;
      }
    }
    return true;
  }
}
