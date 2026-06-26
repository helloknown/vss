
package com.intellij.vssSupport.Checkin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.util.PairConsumer;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.AddOptions;
import com.intellij.vssSupport.VssCommitMessageUtil;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.CheckinOptions;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtil.map2Array;

public class VssCheckinEnvironment implements CheckinEnvironment
{
  public static final Key<Boolean> RENAME_ROLLBACK = new Key<>("RENAME_ROLLBACK");

  private final Project project;
  private final VssVcs host;
  private double fraction;

  public VssCheckinEnvironment( Project project, VssVcs host )
  {
    this.project = project;
    this.host = host;
  }

  public String getCheckinOperationName()  {  return VssBundle.message("action.name.checkin");  }

  public String getHelpId() {  return null;   }

  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                             PairConsumer<Object, Object> additionalDataConsumer)
  {
    VssConfiguration config = VssConfiguration.getInstance( project );
    final CheckinOptions checkinOptions = config.getCheckinOptions();
    final AddOptions addOptions = config.getAddOptions();

    final JPanel additionalPanel = new JPanel();
    additionalPanel.setLayout( new BoxLayout( additionalPanel, BoxLayout.PAGE_AXIS ));
    final JCheckBox keepCheckedOut = new JCheckBox( VssBundle.message("checkbox.option.keep.checked.out") );
    additionalPanel.add( keepCheckedOut );

    //  Add "Store only latest version" is shown only in the case if there is
    //  at least one file with status "ADDED".
    final JCheckBox storeOnlyLatestVersion = new JCheckBox( VssBundle.message("checkbox.option.store.only.latest.version") );
    if( isAnyNewFile( panel.getVirtualFiles() ) )
    {
      additionalPanel.add( storeOnlyLatestVersion );
    }

    return new RefreshableOnComponent()
    {
      public JComponent getComponent() {   return additionalPanel;    }

      public void saveState() {
        addOptions.STORE_ONLY_LATEST_VERSION = storeOnlyLatestVersion.isSelected();
        checkinOptions.KEEP_CHECKED_OUT = addOptions.CHECK_OUT_IMMEDIATELY = keepCheckedOut.isSelected();
      }

      public void restoreState() {  refresh();   }

      public void refresh() {
        storeOnlyLatestVersion.setSelected(addOptions.STORE_ONLY_LATEST_VERSION);
        keepCheckedOut.setSelected(checkinOptions.KEEP_CHECKED_OUT);
      }
    };
  }

  private boolean isAnyNewFile( final Collection<VirtualFile> files )
  {
    FileStatusManager mgr = FileStatusManager.getInstance(project);
    for( VirtualFile file : files )
    {
      if( mgr.getStatus( file ) == FileStatus.ADDED )
        return true;
    }
    return false;
  }
  /**
   * Force to reuse the last checkout's comment for the checkin.
   */
  public String getDefaultMessageFor( FilePath[] filesToCheckin )
  {
    VssConfiguration config = VssConfiguration.getInstance( project );

    String changeListComment = null;
    if( filesToCheckin.length > 0 )
    {
      ChangeListManager mgr = ChangeListManager.getInstance( project );
      Change change = mgr.getChange( filesToCheckin[ 0 ] );
      LocalChangeList list = mgr.getChangeList( change );
      changeListComment = list.getComment();
    }

    //  If Checkout comment is null, <caller> will inherit last commit's
    //  message for this commit.
    return StringUtil.isNotEmpty( changeListComment ) ? changeListComment :
                                                        config.getCheckoutOptions().COMMENT;
  }

  @Override
  public List<VcsException> commit(List<? extends Change> changes,
                                   String comment,
                                   CommitContext commitContext,
                                   Set<? super String> feedback)
  {
    List<Change> changeList = new ArrayList<>(changes);
    changeList.removeIf(change -> {
      VirtualFile file = change.getVirtualFile();
      return file != null && host.isFileIgnored(file);
    });
    if (changeList.isEmpty()) {
      return List.of();
    }
    List<VcsException> errors = new ArrayList<>();
    HashSet<FilePath> processedFiles = new HashSet<>();

    if (comment != null) {
      comment = VssCommitMessageUtil.normalize(comment);
    }
    VssConfiguration.getInstance(project).getCheckinOptions().COMMENT = comment != null ? comment : "";

    //  Keep track of the fact that we deal with renamed fodlers. This will
    //  help us to suppress undesirable warning messages of type
    //  "X was checkout from Y folder, continue?" which are invevitable when
    //  we will checkin changed files under the renamed fodlers.
    boolean isAnyAddedFolder = adjustChangesWithRenamedParentFolders(changeList);

    try
    {
      initProgress(changeList.size());

      //  Committing of renamed folders must be performed first since they
      //  affect all other checkings under them (except those having status
      //  "ADDED") since:
      //  - if modified file is checked in before renamed folder checkin then
      //    we need to checkin from (yet) nonexisting file into (already) non-
      //    existing space. It is too tricky to recreate the old folders
      //    structure and commit from out of there.
      //  - if modified file is checked AFTER the renamed folder has been
      //    checked in, we just have to checkin in into the necessary place,
      //    just get the warning that we checking in file which was checked out
      //    from another location. Supress it.

      commitRenamedFolders(changeList, errors);

      commitDeleted(changeList, errors);

      //  IMPORTANT!
      //  Committment of the changed files must be performed first because of
      //  specially processed exceptions described in the ChangeProvider.
      commitChanged(changeList, processedFiles, errors, isAnyAddedFolder);
      commitNew(changeList, processedFiles, errors);
    }
    catch( ProcessCanceledException e )
    {
      //  Nothing to do, just refresh the files which are already committed.
    }

    VfsUtil.markDirtyAndRefresh(true, true, false, map2Array(processedFiles, VirtualFile.class, FilePath::getVirtualFile));
    VcsDirtyScopeManager.getInstance(project).filesDirty(map(processedFiles, FilePath::getVirtualFile), null);

    return errors;
  }

  @Override
  public List<VcsException> commit(List<? extends Change> changes, String preparedComment) {
    return commit(changes, preparedComment, new CommitContext(), null);
  }

  private boolean adjustChangesWithRenamedParentFolders( List<Change> changes )
  {
    Set<VirtualFile> renamedFolders = new HashSet<>();
    boolean isAnyAddedFolder = getNecessaryRenamedFoldersForList( changes, renamedFolders );
    if( isAnyAddedFolder )
    {
      for( VirtualFile folder : renamedFolders )
        changes.add( ChangeListManager.getInstance( project ).getChange( folder ) );
    }
    return isAnyAddedFolder;
  }

  private void commitRenamedFolders( List<Change> changes, List<VcsException> errors )
  {
    for( Change change : changes )
    {
      if( VssUtil.isRenameChange( change ) && VssUtil.isChangeForFolder( change ) )
      {
        FilePath newFile = change.getAfterRevision().getFile();
        FilePath oldFile = change.getBeforeRevision().getFile();

        host.renameDirectory(oldFile.getPath(), newFile.getName(), errors);
        host.renamedFolders.remove(newFile.getPath());
        incrementProgress(newFile.getPath());
      }
    }
  }

  /**
   *  Add all folders first, then add all files into these folders.
   *  Difference between added and modified files is that added file
   *  has no "before" revision.
   */
  private void commitNew( List<Change> changes, HashSet<FilePath> processedFiles, List<VcsException> errors )
  {
    HashSet<FilePath> folders = new HashSet<>();
    HashSet<FilePath> files = new HashSet<>();

    collectNewChanges( changes, folders, files, processedFiles );

    //  Sort folders in ascending order - from the most outer folder
    //  to the inner one.
    FilePath[] foldersSorted = folders.toArray( new FilePath[ folders.size() ] );
    foldersSorted = VssUtil.sortPathsFromOutermost(foldersSorted);

    for( FilePath folder : foldersSorted )
      host.addFolder( folder.getVirtualFile(), errors );

    for( FilePath file : files )
    {
      host.addFile( file.getVirtualFile(), errors );
      incrementProgress( file.getPath() );
    }
  }

  private void collectNewChanges( List<Change> changes, HashSet<FilePath> folders,
                                  HashSet<FilePath> files, HashSet<FilePath> processedFiles )
  {
    for( Change change : changes )
    {
      if( VssUtil.isChangeForNew( change ) )
      {
        FilePath filePath = change.getAfterRevision().getFile();
        if( filePath.isDirectory() )
          folders.add( filePath );
        else
        {
          files.add( filePath );
          analyzeParent( filePath, folders, processedFiles );
        }
        processedFiles.add( filePath );
      }
    }
  }

  /**
   * If the parent of the file has status New or Unversioned - add it
   * to the list of folders OBLIGATORY for addition into the repository -
   * no file can be added into VSS without all higher folders are already
   * presented there.
   * Process with the parent's parent recursively.
   */
  private void analyzeParent( FilePath file, HashSet<FilePath> folders,
                              HashSet<FilePath> processedFiles )
  {
    VirtualFile parent = file.getVirtualFileParent();
    FileStatus status = FileStatusManager.getInstance( project ).getStatus( parent );
    if( status == FileStatus.ADDED || status == FileStatus.UNKNOWN )
    {
      FilePath parentPath = file.getParentPath();

      folders.add( parentPath );
      processedFiles.add( parentPath );
      analyzeParent( parentPath, folders, processedFiles );
    }
  }

  private void commitDeleted( List<Change> changes, List<VcsException> errors )
  {
    for( Change change : changes )
    {
      if( VssUtil.isChangeForDeleted( change ) )
      {
        final FilePath fp = change.getBeforeRevision().getFile();
        String path = VssUtil.getCanonicalLocalPath( fp.getPath() );
        host.removeFile( path, errors );

        host.deletedFiles.remove( path );
        host.deletedFolders.remove( path );
        ApplicationManager.getApplication().invokeLater(() -> VcsDirtyScopeManager.getInstance(project ).fileDirty(fp ));
      }
    }
  }

  private void commitChanged( List<Change> changes, HashSet<FilePath> processedFiles,
                              List<VcsException> errors, boolean suppressWarns )
  {
    for( Change change : changes )
    {
      if( !VssUtil.isChangeForNew( change ) &&
          !VssUtil.isChangeForDeleted( change ) &&
          !VssUtil.isChangeForFolder( change ) )
      {
        FilePath file = change.getAfterRevision().getFile();
        ContentRevision before = change.getBeforeRevision();
        String newPath = VssUtil.getCanonicalLocalPath( file.getPath() );
        String oldPath = host.renamedFiles.get( newPath );
        if( oldPath != null )
        {
          FilePath oldFile = before.getFile();
          String prevPath = VssUtil.getCanonicalLocalPath( oldFile.getPath() );

          //  If parent folders' names of the revisions coinside, then we
          //  deal with the simle rename, otherwise we process full-scaled
          //  file movement across folders (packages).

          if( oldFile.getVirtualFileParent().getPath().equals( file.getVirtualFileParent().getPath() ))
          {
            host.renameAndCheckInFile( prevPath, file.getName(), errors );
          }
          else
          {
            String newFolder = VssUtil.getCanonicalLocalPath( file.getVirtualFileParent().getPath() );
            host.moveRenameAndCheckInFile( prevPath, newFolder, file.getName(), errors );
          }
          host.renamedFiles.remove( newPath );
        }
        else
        {
          host.checkinFile( file.getVirtualFile(), errors, suppressWarns );
        }

        incrementProgress( file.getPath() );
        processedFiles.add( file );
      }
    }
  }

  @Override
  public List<VcsException> scheduleMissingFileForDeletion(List<? extends FilePath> paths)
  {
    List<VcsException> errors = new ArrayList<>();
    for( FilePath file : paths )
    {
      String path = file.getPath();
      host.removeFile( path, errors );

      host.removedFiles.remove( VssUtil.getCanonicalLocalPath( path ) );
      host.removedFolders.remove( VssUtil.getCanonicalLocalPath( path ) );
    }
    return errors;
  }

  @Override
  public List<VcsException> scheduleUnversionedFilesForAddition(List<? extends VirtualFile> files) {
    for (VirtualFile file : files) {
      host.add2NewFile(file);
      VcsDirtyScopeManager.getInstance(project).fileDirty(file);
      extendStatus(file);
    }
    return new ArrayList<>();
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return true;
  }

  private void extendStatus( VirtualFile file )
  {
    FileStatusManager mgr = FileStatusManager.getInstance( project );
    VirtualFile parent = file.getParent();

    if( mgr.getStatus( parent ) == FileStatus.UNKNOWN )
    {
      host.add2NewFile( parent );
      VcsDirtyScopeManager.getInstance(project).fileDirty(parent);

      extendStatus( parent );
    }
  }

  private boolean getNecessaryRenamedFoldersForList( List<Change> changes, Set<VirtualFile> set )
  {
    boolean isAnyRenamedFolderForFiles;
    for( Change change : changes )
    {
      if( !VssUtil.isChangeForDeleted( change ))
      {
        ContentRevision rev = change.getAfterRevision();
        for( String newFolderName : host.renamedFolders.keySet() )
        {
          if( rev.getFile().getPath().startsWith( newFolderName ) )
          {
            VirtualFile parent = VssUtil.getVirtualFile(newFolderName);
            set.add( parent );
          }
        }
      }
    }
    isAnyRenamedFolderForFiles = set.size() > 0;

    for( Change change : changes )
    {
      if( !VssUtil.isChangeForDeleted( change ))
      {
        ContentRevision rev = change.getAfterRevision();
        VirtualFile submittedParent = rev.getFile().getVirtualFile();
        if( submittedParent != null )
          set.remove( submittedParent );
      }
    }
    
    return isAnyRenamedFolderForFiles;
  }

  private void initProgress( int total )
  {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if( progress != null )
    {
      fraction = 1.0 / (double) total;
      progress.setIndeterminate( false );
      progress.setFraction( 0.0 );
    }
  }

  private void incrementProgress( String text ) throws ProcessCanceledException
  {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if( progress != null )
    {
      double newFraction = progress.getFraction();
      newFraction += fraction;
      progress.setFraction( newFraction );
      progress.setText( text );

      if( progress.isCanceled() )
        throw new ProcessCanceledException();
    }
  }
}
