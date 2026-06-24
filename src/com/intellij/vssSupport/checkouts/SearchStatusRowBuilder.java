package com.intellij.vssSupport.checkouts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vssSupport.VssTruncatedFileNameUtil;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SearchStatusRowBuilder {
  private SearchStatusRowBuilder() {
  }

  @NotNull
  static List<SearchStatusRow> build(@NotNull Project project, @NotNull List<VssCheckoutEntry> entries) {
    Map<String, List<VssCheckoutEntry>> byVssFolder = new LinkedHashMap<>();
    List<VssCheckoutEntry> sorted = entries.stream()
      .sorted(Comparator.comparing(VssCheckoutEntry::localPath))
      .toList();

    for (VssCheckoutEntry entry : sorted) {
      String vssFolder = resolveVssFolderPath(project, entry);
      byVssFolder.computeIfAbsent(vssFolder, k -> new ArrayList<>()).add(entry);
    }

    List<SearchStatusRow> rows = new ArrayList<>();
    for (Map.Entry<String, List<VssCheckoutEntry>> group : byVssFolder.entrySet()) {
      rows.add(new SearchStatusRow(group.getKey(), "", "", "", null));
      for (VssCheckoutEntry entry : group.getValue()) {
        rows.add(new SearchStatusRow(
          "    " + VssTruncatedFileNameUtil.displayFileName(entry.localPath(), entry.fileName()),
          entry.checkoutUser(),
          entry.checkoutDate(),
          entry.workingFolder(),
          entry
        ));
      }
    }
    return rows;
  }

  @NotNull
  private static String resolveVssFolderPath(@NotNull Project project, @NotNull VssCheckoutEntry entry) {
    String vssPath = VssUtil.getVssPath(entry.localPath(), false, project);
    if (StringUtil.isEmpty(vssPath)) {
      return entry.presentablePath();
    }
    int slash = vssPath.lastIndexOf('/');
    if (slash <= 0) {
      return vssPath;
    }
    return vssPath.substring(0, slash);
  }
}
