package com.intellij.vssSupport;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.vcsUtil.VcsImplUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles VSS error reporting during background change-list scans so expected
 * messages (e.g. paths not yet in the repository) do not spam modal dialogs.
 */
@Service(Service.Level.PROJECT)
public final class VssScanErrorReporter {
  private static final Logger LOG = Logger.getInstance(VssScanErrorReporter.class);
  private static final long SUPPRESS_MS = 120_000L;
  private static final String NOTIFICATION_GROUP = "VSS";

  private final Project project;
  private final Set<String> reportedThisScan = ConcurrentHashMap.newKeySet();
  private final Map<String, Long> reportedRecently = new ConcurrentHashMap<>();

  public VssScanErrorReporter(@NotNull Project project) {
    this.project = project;
  }

  public static VssScanErrorReporter getInstance(@NotNull Project project) {
    return project.getService(VssScanErrorReporter.class);
  }

  public void beginChangeScan() {
    reportedThisScan.clear();
  }

  public static boolean isBenignScanMessage(@NotNull String message) {
    String lower = message.toLowerCase();
    return lower.contains("is not an existing")
           || lower.contains("has been deleted")
           || lower.contains("no items found")
           || lower.contains("no checked out files found")
           || lower.contains("no files found checked out by");
  }

  /**
   * Reports a scan-time problem at most once per refresh pass and once per two minutes globally.
   * Benign messages are logged only (no UI).
   */
  public void reportChangeScanError(@NotNull String message) {
    if (project.isDisposed()) {
      return;
    }
    if (isBenignScanMessage(message)) {
      LOG.debug("VSS change scan (benign, suppressed UI): " + message);
      return;
    }

    if (!reportedThisScan.add(message)) {
      return;
    }
    long now = System.currentTimeMillis();
    Long last = reportedRecently.get(message);
    if (last != null && now - last < SUPPRESS_MS) {
      return;
    }
    reportedRecently.put(message, now);
    LOG.warn("VSS change scan: " + message);

    String friendly = toFriendlyMessage(message);
    Notification notification = new Notification(
      NOTIFICATION_GROUP,
      VssBundle.message("message.title.check.status"),
      friendly,
      NotificationType.WARNING
    );
    Notifications.Bus.notify(notification, project);
  }

  /**
   * User-initiated actions still use a modal dialog.
   */
  public static void showActionError(@NotNull Project project, @NotNull String message) {
    VcsImplUtil.showErrorMessage(project, message, VssBundle.message("message.title.error"));
  }

  @NotNull
  private static String toFriendlyMessage(@NotNull String message) {
    if (message.contains("is not an existing")) {
      return VssBundle.message("message.text.scan.path.not.in.vss", message);
    }
    return message;
  }
}
