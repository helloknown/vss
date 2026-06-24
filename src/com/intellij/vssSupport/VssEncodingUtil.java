package com.intellij.vssSupport;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/**
 * Charset used to decode {@code ss.exe} console output (typically GBK on Chinese Windows).
 */
public final class VssEncodingUtil {
  private VssEncodingUtil() {
  }

  @NotNull
  public static String getDefaultCharsetName() {
    String os = System.getProperty("os.name", "");
    if (os.toLowerCase().contains("windows")) {
      return "GBK";
    }
    return Charset.defaultCharset().name();
  }

  @NotNull
  public static String getCharsetName(@NotNull Project project) {
    String configured = VssConfiguration.getInstance(project).OUTPUT_CHARSET;
    if (StringUtil.isNotEmpty(configured)) {
      return configured.trim();
    }
    return getDefaultCharsetName();
  }
}
