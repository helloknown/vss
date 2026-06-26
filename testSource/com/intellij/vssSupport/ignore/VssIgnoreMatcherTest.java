package com.intellij.vssSupport.ignore;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VssIgnoreMatcherTest {
  @Test
  public void basenamePatternMatchesInAnyDirectory() {
    VssIgnoreMatcher matcher = VssIgnoreMatcher.parse("*.class\n");
    assertTrue(matcher.isIgnored("Foo.class"));
    assertTrue(matcher.isIgnored("src/Foo.class"));
    assertTrue(matcher.isIgnored("Source/defaultroot/WEB-INF/classes/Foo.class"));
    assertFalse(matcher.isIgnored("Foo.java"));
  }

  @Test
  public void unanchoredPathPatternMatchesAtAnyLevel() {
    VssIgnoreMatcher matcher = VssIgnoreMatcher.parse("WEB-INF/classes/\n");
    assertTrue(matcher.isIgnored("WEB-INF/classes"));
    assertTrue(matcher.isIgnored("WEB-INF/classes/Foo.class"));
    assertTrue(matcher.isIgnored("Source/defaultroot/WEB-INF/classes/Foo.class"));
    assertFalse(matcher.isIgnored("WEB-INF/other/Foo.class"));
  }

  @Test
  public void anchoredPathPatternMatchesFromIgnoreRootOnly() {
    VssIgnoreMatcher matcher = VssIgnoreMatcher.parse("/out/\n");
    assertTrue(matcher.isIgnored("out/build.txt"));
    assertFalse(matcher.isIgnored("module/out/build.txt"));
  }

  @Test
  public void directoryOnlyPatternDoesNotMatchSiblingFile() {
    VssIgnoreMatcher matcher = VssIgnoreMatcher.parse("WEB-INF/classes/\n");
    assertFalse(matcher.isIgnored("WEB-INF/classes.txt"));
  }

  @Test
  public void negationRuleUnignoresEarlierMatch() {
    VssIgnoreMatcher matcher = VssIgnoreMatcher.parse("*.class\n!important/Keep.class\n");
    assertFalse(matcher.isIgnored("important/Keep.class"));
    assertTrue(matcher.isIgnored("important/Other.class"));
  }

  @Test
  public void userProjectIgnoreFileMatchesNestedPaths() {
    VssIgnoreMatcher matcher = VssIgnoreMatcher.parse("""
      # 注释
      .idea/
      WEB-INF/classes/
      back/
      doc/
      target/
      export/
      Function/
      logs/
      MQ/
      NAS/
      upload/
      build/
      UTS3/
      out/
      """);
    assertTrue(matcher.isIgnored("source/MQ/UOB/IMPORT/SEND/20140807"));
    assertTrue(matcher.isIgnored("source/uts_nfs/export/ZZ/20210707"));
    assertTrue(matcher.isIgnored("source/defaultroot/WEB-INF/classes/uts/bo/settlement"));
    assertFalse(matcher.isIgnored("source/defaultroot/WEB-INF/work/org/apache/jsp/jsp/basic"));
  }
}
