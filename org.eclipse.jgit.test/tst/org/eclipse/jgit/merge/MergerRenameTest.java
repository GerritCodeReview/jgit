/*
 * Copyright (C) 2012, 2020 Robin Stocker <robin@nibor.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.util.Strings;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.junit.Before;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/**
 * All the test DataPoints below contain the mappings source (original) File -> target (rename) File
 */
@RunWith(Theories.class)
public class MergerRenameTest extends RepositoryTestCase {

  @DataPoints
  public static MergeStrategy[] strategiesUnderTest = new MergeStrategy[] {
      MergeStrategy.RECURSIVE};

  @Before
  public void enableRename() throws IOException, ConfigInvalidException {
    StoredConfig config = db.getConfig();
    config.setString(ConfigConstants.CONFIG_DIFF_SECTION, null, ConfigConstants.CONFIG_KEY_RENAMES, "true");
    config.save();
  }


  private AbstractTreeIterator getTreeIterator(String name) throws IOException {
    final ObjectId id = db.resolve(name);
    if (id == null)
      throw new IllegalArgumentException(name);
    final CanonicalTreeParser p = new CanonicalTreeParser();
    try (ObjectReader or = db.newObjectReader(); RevWalk rw = new RevWalk(db)) {
      p.reset(or, rw.parseTree(id));
      return p;
    }
  }

  public void testRename_merged(MergeStrategy strategy, String originalName, String originalContent, String oursName, String oursContent, String theirsName, String theirsContent, Map<String, String> expectedFileContents) throws Exception {
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalName, originalContent);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(oursName, oursContent);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(theirsName, theirsContent);
    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFileContents);
  }

  public void testRename_merged(MergeStrategy strategy, Map<String, String> originalFilesToContents,  Map<String, String> oursFilesToContents,  Map<String, String> theirsFilesToContents, Map<String, String> expectedFileContents) throws Exception {
    if (!strategy.equals(MergeStrategy.RECURSIVE)) {
      return;
    }
    MergeResult mergeResult = mergeRename(strategy, originalFilesToContents, oursFilesToContents, theirsFilesToContents);
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.MERGED);
    Set<String> expectedIndexContent = new HashSet<>();
    for (Entry<String, String> expectedFile : expectedFileContents.entrySet()) {
      if(expectedFile.getValue() != null) {
        assertEquals(expectedFile.getValue(), read(expectedFile.getKey()));
        expectedIndexContent.add(String.format("%s, mode:100644, content:%s", expectedFile.getKey(),
            expectedFile.getValue()));
      } else {
        assertFalse(check(expectedFile.getKey()));
      }
    }
    // index contains only the expected files. Everything was merged.
    Set<String> stagedFiles = Arrays.asList(indexState(CONTENT).split("\\[|\\]")).stream()
        .filter(s ->
            !Strings.isNullOrEmpty(s)).collect(Collectors.toSet());
    assertEquals(stagedFiles, expectedIndexContent);
  }

  public void testRename_withConflict(MergeStrategy strategy, Map<String, String> originalFilesToContents,  Map<String, String> oursFilesToContents,  Map<String, String> theirsFilesToContents, Map<String, String> expectedFileContents, Set<String> expectedConflicts) throws Exception {
    if (!strategy.equals(MergeStrategy.RECURSIVE)) {
      return;
    }
    MergeResult mergeResult = mergeRename(strategy, originalFilesToContents, oursFilesToContents, theirsFilesToContents);
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.CONFLICTING);
    assertTrue(mergeResult.getFailingPaths() == null);
    assertEquals(mergeResult.getConflicts().keySet(), expectedConflicts);
    for (Entry<String, String> expectedFile : expectedFileContents.entrySet()) {
      if (expectedFile.getValue() == null) {
        assertFalse(check(expectedFile.getKey()));
      } else if (expectedFile.getValue().startsWith("<<<<<<<")) {
        assertTrue(read(expectedFile.getKey()).contains(expectedFile.getValue()));
        //assertEquals(read(expectedFile.getKey()), expectedFile.getValue());
      } else {
        assertEquals(expectedFile.getValue(), read(expectedFile.getKey()));
      }
    }
  }

  public void testRename_withConflict(MergeStrategy strategy, Map<String, String> originalFilesToContents,  Map<String, String> oursFilesToContents,  Map<String, String> theirsFilesToContents, Map<String, String> expectedFileContents, Set<String> expectedConflicts, Set<String> expectedIndexContent) throws Exception {

    testRename_withConflict(strategy, originalFilesToContents, oursFilesToContents,
        theirsFilesToContents, expectedFileContents, expectedConflicts);

    Set<String> stagedFiles = Arrays.asList(indexState(CONTENT).split("\\[|\\]")).stream()
        .filter(s ->
            !Strings.isNullOrEmpty(s)).collect(Collectors.toSet());
    assertEquals(stagedFiles, expectedIndexContent);
  }

  private MergeResult mergeRename(MergeStrategy strategy, Map<String, String> originalFilesToContents,  Map<String, String> oursFilesToContents,  Map<String, String> theirsFilesToContents) throws Exception {
    Git git = Git.wrap(db);
    // master
    for (Entry<String, String> originalFile : originalFilesToContents.entrySet()) {
      writeTrashFile(originalFile.getKey(), originalFile.getValue());
      git.add().addFilepattern(originalFile.getKey()).call();
    }
    RevCommit commitI = git.commit().setMessage("Initial commit").call();

    git.checkout().setCreateBranch(true).setStartPoint(commitI).setName("second-branch").call();
    for (Entry<String, String> originalFile : originalFilesToContents.entrySet()) {
      git.rm().addFilepattern(originalFile.getKey()).call();
    }
    for (Entry<String, String> theirsFile : theirsFilesToContents.entrySet()) {
      writeTrashFile(theirsFile.getKey(), theirsFile.getValue());
      git.add().addFilepattern(theirsFile.getKey()).call();
    }
    RevCommit mergeCommit = git.commit().setMessage("Commit on second-branch").call();

    git.checkout().setName("master").call();

    for (Entry<String, String> originalFile : originalFilesToContents.entrySet()) {
      git.rm().addFilepattern(originalFile.getKey()).call();
    }
    for (Entry<String, String> oursFile : oursFilesToContents.entrySet()) {
      writeTrashFile(oursFile.getKey(), oursFile.getValue());
      git.add().addFilepattern(oursFile.getKey()).call();
    }

    RevCommit headCommit = git.commit().setMessage("Commit on master")
        .call();

    // Merge master into second-branch
    return git.merge().include(mergeCommit).setStrategy(strategy).call();
  }

  /**
   * SECTION 1
   * Basic rename cases
   */

  /**
   * Test cases for that support multiple file modifications per commit
   *
   * TODO: extend
   */
  @DataPoints("renameLists")
  public static final List<List<Entry<String, String>>> renameListsData  = List.of(
      // Direct rename of file
      List.of(Map.entry("test/file2","test/file1")),
      List.of(Map.entry("test/file1","test/file2")),
      List.of(Map.entry("test/a/file1","test/z/file2")),
      List.of(Map.entry("test/z/file1","test/a/file2")),
      // Move file to subdir
      List.of(Map.entry("test/file1", "test/w/file1")),
      List.of(Map.entry("test/file1", "test/a/file1")),
      // Move file to subdir and rename
      List.of(Map.entry("test/file1", "test/w/other")),
      List.of(Map.entry("test/file1", "test/a/other")),
      // Move to parent dir
      List.of(Map.entry("test/w/file1", "test/file1")),
      List.of(Map.entry("test/a/file1", "test/file1")),
      // Move to parent dir and rename
      List.of(Map.entry("test/w/file1", "test/other")),
      List.of(Map.entry("test/a/file1", "test/other"))
  );


  /**
   * Testcase 1.1
   *
   * <p>Test classic conflict, one side renamed, other modified.
   * <p>This should be merged cleanly.
   *
   * <p>Examines various source -> target rename scenarios that have different sorting in TreeWalk
   * <p>E.g.:
   * <pre>B: test/file1, X_1
   * O: test/file1, X_2
   * T: test/file2, X_1
   *
   * Expected:
   * M: test/file2, X_2
   * </pre>
   */
  public void testRenameModify_merged(MergeStrategy strategy, List<Entry<String, String>> renamePairs, boolean isRenameInOurs) throws Exception {

    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    Map<String, String> originalFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      originalFiles.put(renamePair.getKey(), originalContent + renamePair.getKey());
    }
    Map<String, String> noRenameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      noRenameFiles.put(renamePair.getKey(), slightlyModifiedContent + renamePair.getKey());
    }
    Map<String, String> renameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      renameFiles.put(renamePair.getValue(), originalContent + renamePair.getKey());
    }
    Map<String, String> expectedFiles = new HashMap<>();

    for (Entry<String, String> renamePair : renamePairs) {
      expectedFiles.put(renamePair.getValue(), slightlyModifiedContent + renamePair.getKey());
    }
    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : noRenameFiles,
        isRenameInOurs ? noRenameFiles : renameFiles, expectedFiles);
  }

  @Theory
  public void checkRenameModifyFile_merged(MergeStrategy strategy, @FromDataPoints("renameLists") List<Entry<String, String>> renamePairs, boolean isRenameInOurs) throws Exception {
    testRenameModify_merged(strategy, renamePairs, isRenameInOurs);

  }

  /**
   * Test cases for directory split renames
   *
   * <p>TODO: extend when dir rename detection is implemented
   */
  @DataPoints("renameListsSplit")
  public static final List<List<Entry<String, String>>> renameListsSplitData  = List.of(
      // Directory split evenly
      List.of(Map.entry("test/a/file1","test/a1/file1"),
          Map.entry("test/a/file2","test/a2/file2")),
      // Directory split, but also filenames swapped
      List.of(Map.entry("test/a/file1","test/a1/file2"),
          Map.entry("test/a/file2","test/a2/file1")),
      // Directory split & file renamed
      List.of(Map.entry("test/a/file1","test/a1/other"),
          Map.entry("test/a/file2","test/a2/other")),

      // Some files moved to subdir, other remain in the original folder
      List.of(Map.entry("test/a/file1","test/a/file1"),
          Map.entry("test/a/file2","test/a/w/file2")),
      // Some files moved to parent dir, other remain in the original folder
      List.of(Map.entry("test/a/file1","test/a/file1"),
          Map.entry("test/a/file2","test/file2"))
  );

  @Theory
  public void checkRenameSplitDir_merged(MergeStrategy strategy, @FromDataPoints("renameListsSplit") List<Entry<String, String>> renamePairs, boolean isRenameInOurs) throws Exception {
    testRenameModify_merged(strategy, renamePairs, isRenameInOurs);

  }


  /**
   * Testcase 1.2
   *
   * <p>Classic rename, with one side renamed and various modifications on either sides.
   * <p>This should be merged cleanly, regardless of modifications on either side, if content merge does not have conflicts.
   * <pre>
   *   B: test/file1 X_1
   *   O: test/file1 X_1(2)
   *   T: test/file2 X_1(3)
   *
   *  Expected:
   *  M: test/file2 X_1(2,3,4)
   *</pre>
   */
  @Theory
  public void checkRenameModify_merged(MergeStrategy strategy, boolean isRenameInOurs, boolean modifyInRename, boolean modifyInOther) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent1 = "z\na\nb\nc";

    String slightlyModifiedContent2 = "a\nb\nc\nd";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> renameFiles = new HashMap<>();
    renameFiles.put(renameFilename, modifyInRename ? slightlyModifiedContent1 : originalContent);
    Map<String, String> otherFiles = new HashMap<>();
    otherFiles.put(originalFilename, modifyInOther ? slightlyModifiedContent2 : originalContent);

    String expectedRenameContent = modifyInRename && modifyInOther ? "z\na\nb\nc\nd"
        : modifyInRename ? slightlyModifiedContent1
            : modifyInOther ? slightlyModifiedContent2 : originalContent;
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, expectedRenameContent);
    expectedFiles.put(originalFilename, null);

    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : otherFiles,
        isRenameInOurs ? otherFiles : renameFiles, expectedFiles);
  }

  /**
   * Testcase 1.3
   *
   * <p> Classic rename, with one side renamed and modifications, causing content merge conflict.
   * <p> TODO: similar test is needed for git link merging, mode conflicts and file/dir conflicts.
   *
   * <pre>
   *   B: test/file1 X_1
   *   O: test/file1 X_2
   *   T: test/file2 X_3
   *
   *  Expected:
   *  Work tree:
   *      test/file2 (X_2 vs X_3) conflict
   *  Index:
   *   B: test/file2 X_1
   *   O: test/file2 X_2
   *   T: test/file2 X_3
   *</pre>
   * <p>The rename conflict was resolved, but there is a merge conflict on the content.
   * <p> Since rename was detected, index/work tree do not contain the original file
   */
  @Theory
  public void checkRenameModify_conflict(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String oursContent = "a\nb\nc\nx";

    String theirsContent = "a\nb\nc\nd";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> renameFiles = new HashMap<>();
    renameFiles.put(renameFilename, isRenameInOurs ? oursContent : theirsContent);
    Map<String, String> otherFiles = new HashMap<>();
    otherFiles.put(originalFilename, isRenameInOurs ? theirsContent : oursContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, "<<<<<<< HEAD\n"
        + "x\n"
        + "=======\n"
        + "d\n");
    expectedFiles.put(originalFilename, null);

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", renameFilename, originalContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s",
        renameFilename, oursContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s",
        renameFilename, theirsContent));

    testRename_withConflict(strategy, originalFiles, isRenameInOurs ? renameFiles : otherFiles,
        isRenameInOurs ? otherFiles : renameFiles, expectedFiles, expectedConflicts, expectedIndex);
  }

  /**
   * Testcase 1.4
   *
   * <p>Same as Testcase 1.2, but both sides renamed to the same name
   * <pre>
   *   B: test/file1 X_1
   *   O: test/file2 X_1(2)
   *   T: test/file2 X_1(3)
   *
   *  Expected:
   *  M: test/file2 X_1(2,3,4)
   *</pre>
   */
  @Theory
  public void checkRenameBothModify_merged(MergeStrategy strategy, boolean isRenameInOurs, boolean modifyInRename, boolean modifyInOther) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent1 = "z\na\nb\nc";

    String slightlyModifiedContent2 = "a\nb\nc\nd";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> renameFiles = new HashMap<>();
    renameFiles.put(renameFilename, modifyInRename ? slightlyModifiedContent1 : originalContent);
    Map<String, String> otherFiles = new HashMap<>();
    otherFiles.put(renameFilename, modifyInOther ? slightlyModifiedContent2 : originalContent);

    String expectedRenameContent = modifyInRename && modifyInOther ? "z\na\nb\nc\nd"
        : modifyInRename ? slightlyModifiedContent1
            : modifyInOther ? slightlyModifiedContent2 : originalContent;
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, expectedRenameContent);
    expectedFiles.put(originalFilename, null);

    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : otherFiles,
        isRenameInOurs ? otherFiles : renameFiles, expectedFiles);
  }



  /**
   *  Testcase 1.5
   *
   *  <p>Same as Testcase 1.3, but both sides renamed to the same name
   *  <pre>
   *  B: test/file1 X_1
   *  O: test/file2 X(2)
   *  T: test/file2 X(3)
   *</pre>
   */
  @Theory
  public void checkRenameBothModify_conflict(MergeStrategy strategy) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String oursContent = "a\nb\nc\nx";

    String theirsContent = "a\nb\nc\nd";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> oursFiles= new HashMap<>();
    oursFiles.put(renameFilename, oursContent);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(renameFilename, theirsContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, "<<<<<<< HEAD\n"
        + "x\n"
        + "=======\n"
        + "d\n");
    expectedFiles.put(originalFilename, null);

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", renameFilename, originalContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s",
        renameFilename, oursContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s",
        renameFilename, theirsContent));

    testRename_withConflict(strategy, originalFiles, oursFiles,
        theirsFiles, expectedFiles, expectedConflicts, expectedIndex);
  }

  /**
   *  Testcase 1.6
   *  <p>Cross-side renames are no supported. Both files are present in the merge commit.
   *  <pre>
   *    B: test/file1 X
   *    O: test/file2 Z
   *    T: test/file3 Z
   *  Expected:
   *    M: test/file2 Z test/file3 Z
   *  </pre>
   */
  @Theory
  public void checkCrossSidesRename_notDetected_merged(MergeStrategy strategy) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "Unrelated base file";
    String renameFileContent = "Identical merge-side-file";
    String oursFilename = "test/file2";
    String theirsFilename = "test/file3";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(oursFilename, renameFileContent);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(theirsFilename, renameFileContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(oursFilename, renameFileContent);
    expectedFiles.put(theirsFilename, renameFileContent);
    expectedFiles.put(originalFilename, null);

    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);
  }


  /**
   * Testcase 1.7
   * <p> File was completely replaced on non-rename side.
   * <p> Since we do not compute cross-side similarity, this does not result in rename/delete conflict.
   * <p> The rename is detected and the files are content-merged.
   * <p> This might seem obvious, however see Testcase 3.5 why this behaviour can be problematic.
   * <p> Note: Without rename detection, we would have modify/delete conflict.
   *  <pre>
   *    B: test/file1 X_1
   *    O: test/file1 Y
   *    T: test/file2 X_1;
   *  Expected:
   *    M: test/file2 Y
   *  </pre>
   */
  @Theory
  public void checkRenameWithReplace_merged(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {

    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename, "Unrelated content");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, originalContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, "Unrelated content");
    expectedFiles.put(originalFilename, null);

    testRename_merged(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles);
  }

  /**
   * Testcase 1.8
   * <p> Same as above, but the rename side modified the content, which results in content conflict.
   *  <pre>
   *    B: test/file1 X_1
   *    O: test/file1 Y
   *    T: test/file2 X_2
   *  Expected:
   *    Work tree:
   *       test/file2 X_2 vs Y conflict
   *    Index:
   *    B: test/file2 X_1
   *    O: test/file2 Y
   *    T: test/file2 X_2
   *  </pre>
   */
  @Theory
  public void checkRenameWithReplace_conflict(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename, "Unrelated content");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, slightlyModifiedContent);

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);

    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int modifyStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", renameFilename, originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage,
            slightlyModifiedContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, modifyStage,
            "Unrelated content"));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, String.format("<<<<<<< HEAD\n%s\n"
            + "=======" +
            "\n%s\n>>>>>>>", isRenameInOurs ? slightlyModifiedContent : "Unrelated content",
        isRenameInOurs ? "Unrelated content" : slightlyModifiedContent));
    expectedFiles.put(originalFilename, null);

    testRename_withConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles, expectedConflicts,
        expectedIndex);
  }

  /**
   * SECTION 2
   *
   * <p> This section covers various rename conflicts
   */

  /**
   *  Testcase 2.1
   *  <p> Renamed on both sides to different names. Conflict with all files present in the index.
   *  <pre>
   *    B: test/file1 X_1
   *    O: test/file2 X_1(2)
   *    T: test/file3 X_1(3)
   *    Expected:
   *    Work tree:
   *       test/file2 X_1(2),  test/file3 X_1(3)
   *    Index:
   *    B: test/file1 X_1
   *    O: test/file2 X_1(2)
   *    T: test/file3 X_1(3)
   *  Conflict with both file path present in the index.
   *  </pre>
   */
  @Theory
  public void checkRenameRename_conflict(MergeStrategy strategy, boolean modifyInOurs, boolean modifyInTheirs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String oursFilename = "test/file2";
    String oursContent = modifyInOurs ? slightlyModifiedContent : originalContent;
    String theirsFilename = "test/file3";
    String theirsContent = modifyInTheirs ? slightlyModifiedContent : originalContent;

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(oursFilename, oursContent);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(theirsFilename, theirsContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename, null);
    expectedFiles.put(oursFilename, oursContent);
    expectedFiles.put(theirsFilename, theirsContent);

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(originalFilename);
    expectedConflicts.add(oursFilename);
    expectedConflicts.add(theirsFilename);

    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:2, content:%s", oursFilename, oursContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:3, content:%s", theirsFilename, theirsContent));
    testRename_withConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles,
        expectedConflicts, expectedIndex);
  }

  /**
   *  Testcase 2.2
   *
   *  <p> Rename/delete conflict
   *  <pre>
   *    B: test/file1 X_1
   *    O:
   *    T: test/file2 X_1
   *    Expected:
   *    Work tree:
   *       test/file2 X_1(2)
   *    Index:
   *    B: test/file1 X_1
   *    O:
   *    T: test/file3 X_1(2)
   *  </pre>
   *  <p>Note: index is always populated with the contents of the rename file, regardless of the pull side
   */
  @Theory
  public void checkRenameDelete_conflict(MergeStrategy strategy, boolean isRenameInOurs, boolean shouldModify) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithDelete = new HashMap<>();
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int deleteStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", renameFilename, originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage,
            shouldModify ? slightlyModifiedContent : originalContent));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    expectedFiles.put(originalFilename, null);

    testRename_withConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithDelete,
        isRenameInOurs ? filesWithDelete : filesWithRename, expectedFiles, expectedConflicts,
        expectedIndex);
  }

  @Theory
  public void checkDelete_conflict(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {

    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithDelete = new HashMap<>();
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(originalFilename, slightlyModifiedContent);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(originalFilename);
    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int deleteStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", originalFilename, renameStage,slightlyModifiedContent));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename, slightlyModifiedContent);

    testRename_withConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithDelete,
        isRenameInOurs ? filesWithDelete : filesWithRename, expectedFiles, expectedConflicts,
        expectedIndex);
  }

  /**
   *  Testcase 2.3
   *  <p> Related to Testcase 1.7, 1.8
   *  <p>Rename/Add conflict: one side added the file that the other side renamed to.
   *  <p>Rename processing is switched off for this path, which results in content conflict on target file and delete/modify on source file.
   *  <p>NOTE: This is handled differently in c-git, see below.
   * <pre>
   *  B: test/file1 X_1
   *  O: test/file1 X_2 ; test/file2 Y
   *  T: test/file2 X_1;
   *  Expected:
   *  Content merge conflict on test/file2 Y vs test/file2 X_1
   *  Delete/modify conflict on test/file1
   *  Work tree:
   *      test/file2 (Y vs X_1) conflict
   *      test/file1 X_2
   *  Index:
   *   B: test/file1 X_1
   *   O: test/file1 X_2
   *   O: test/file2 Y
   *   T: test/file2 X_1
   * </pre>
   *  TODO: c-git creates an X_1 vs X_2 merge which is than merged with Y (results in nested conflict markers), and only reports file2 as unmerged.
   */
  @Theory
  public void checkRenameAddCollision_conflict(MergeStrategy strategy,  boolean isRenameInOurs, boolean shouldModifyRename, boolean shouldModifyOriginal) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(renameFilename, "Unrelated file");
    filesWithModify.put(originalFilename,
        shouldModifyOriginal ? slightlyModifiedContent : originalContent);
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename,
        shouldModifyRename ? slightlyModifiedContent : originalContent);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    if (shouldModifyOriginal) {
      expectedConflicts.add(originalFilename);
    }
    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int modifyStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    if (shouldModifyOriginal) {
      expectedIndex.add(
          String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
      expectedIndex.add(
          String.format("%s, mode:100644, stage:%s, content:%s", originalFilename, modifyStage,
              filesWithModify.get(originalFilename)));
    }
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, modifyStage,
            "Unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage,
            filesWithRename.get(renameFilename)));

    Map<String, String> expectedFiles = new HashMap<>();
    String oursContent = isRenameInOurs ? filesWithRename.get(renameFilename) : "Unrelated file";
    String theirsContent =
        isRenameInOurs ? "Unrelated file" : filesWithRename.get(renameFilename);
    expectedFiles.put(renameFilename,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s", oursContent, theirsContent));
    if(shouldModifyOriginal) {
      expectedFiles.put(originalFilename,
          shouldModifyOriginal ? slightlyModifiedContent : originalContent);
    }

    testRename_withConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles, expectedConflicts,
        expectedIndex);
  }

  /**
   *  Testcase 2.4
   *  <p> Related to Testcase 1.7, 1.8
   *  <p> The target rename exists in the base and is kept by one of the sides.
   *  <p> Since it was not touched, and other side deleted it, the file is deleted.
   * <pre>
   *   B: test/file1 X_1 test/file2 Y
   *   O: test/file1 X_2 test/file2 Y
   *   T: test/file2 X_1
   *  Expected:
   *   M: test/file2 X_2
   * </pre>
   *  TODO: c-git treats it as delete/modify on file1
   */
  @Theory
  public void checkRenameModifyCollision_collisionUnchanged_merged(MergeStrategy strategy,  boolean isRenameInOurs, boolean shouldModifyRename, boolean shouldModifyOriginal) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    originalFiles.put(renameFilename, "Unrelated file");
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename,
        shouldModifyOriginal ? slightlyModifiedContent : originalContent);
    filesWithModify.put(renameFilename, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename,
        shouldModifyRename ? slightlyModifiedContent : originalContent);
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, shouldModifyRename || shouldModifyOriginal? slightlyModifiedContent: originalContent);
    testRename_merged(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles);
  }

  /**
   *  Testcase 2.5
   *  <p> Same as Testcase 2.4, but the side that kept the target also modified it.
   *  <p> Makes it identical to Testcase 2.3.
   * <pre>
   *   B: test/file1 X_1 test/file2 Y
   *   O: test/file1 X_2 test/file2 Y_1
   *   T: test/file2 X_1
   *  Expected:
   *  Content merge conflict on test/file2 Y_1 vs test/file2 X_1
   *  Delete/modify conflict on test/file1
   *  Work tree:
   *      test/file2 (Y vs X_1) conflict
   *      test/file1 X_2
   * </pre>
   *  TODO: c-git treats it as delete/modify on file1
   */
  @Theory
  public void checkRenameModifyCollision_collisionUnchanged_conflicting(MergeStrategy strategy,  boolean isRenameInOurs, boolean shouldModifyRename, boolean shouldModifyOriginal) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    originalFiles.put(renameFilename, "Original unrelated file");
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(renameFilename, "Unrelated file");
    filesWithModify.put(originalFilename,
        shouldModifyOriginal ? slightlyModifiedContent : originalContent);
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename,
        shouldModifyRename ? slightlyModifiedContent : originalContent);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    if (shouldModifyOriginal) {
      expectedConflicts.add(originalFilename);
    }
    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int modifyStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    if (shouldModifyOriginal) {
      expectedIndex.add(
          String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
      expectedIndex.add(
          String.format("%s, mode:100644, stage:%s, content:%s", originalFilename, modifyStage,
              filesWithModify.get(originalFilename)));
    }
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", renameFilename,
            "Original unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, modifyStage,
            "Unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage,
            filesWithRename.get(renameFilename)));

    Map<String, String> expectedFiles = new HashMap<>();
    String oursContent = isRenameInOurs ? filesWithRename.get(renameFilename) : "Unrelated file";
    String theirsContent =
        isRenameInOurs ? "Unrelated file" : filesWithRename.get(renameFilename);
    expectedFiles.put(renameFilename,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s", oursContent, theirsContent));
    if(shouldModifyOriginal) {
      expectedFiles.put(originalFilename,
          shouldModifyOriginal ? slightlyModifiedContent : originalContent);
    }

    testRename_withConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles, expectedConflicts,
        expectedIndex);
  }


  /**
   *  Testcase 2.6
   *  Same as Testcase 2.3, but the side with add collision also removed the source file.
   *  Since source file is missing on both sides, it is not present on merge, not reported as a conflict.
   *  <pre>
   *    B: test/file1 X_1
   *    O: test/file2 Y
   *    T: test/file2 X_1(2)
   *    Expected:
   *    Content merge conflict
   *    Work tree:
   *      test/file2 (Y vs X_1(2)) conflict
   *    Index:
   *     O: test/file2 Y
   *     T: test/file2 X_1(2)
   *  </pre>
   */
  @Theory
  public void checkRenameAddCollision_withRenameDelete_conflict(MergeStrategy strategy,  boolean isRenameInOurs, boolean shouldModifyRename) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(renameFilename, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename,
        shouldModifyRename ? slightlyModifiedContent : originalContent);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int modifyStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;

    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, modifyStage,
            "Unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage,
            shouldModifyRename ? slightlyModifiedContent : originalContent));

    Map<String, String> expectedFiles = new HashMap<>();

    String oursContent = isRenameInOurs? filesWithRename.get(renameFilename): filesWithModify.get(renameFilename);
    String theirsContent = isRenameInOurs? filesWithModify.get(renameFilename): filesWithRename.get(renameFilename);
    expectedFiles.put(renameFilename,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s", oursContent, theirsContent));
    expectedFiles.put(originalFilename,null);

    testRename_withConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles, expectedConflicts,
        expectedIndex);
  }


  /**
   *  Testcase 2.5
   *  <p> Both sides added the file but under different names, with one side colliding with the rename.
   *  <p> Because of add/rename collision, the rename detection is off which results in content merge resolution.
   *  <pre>
   *    B: test/file1 X_1 test/file2 Z
   *    O: test/file1 X_2 test/file2 Y
   *    T: test/file2 X_1 test/file1 Y
   *  Expected:
   *    Content conflict: test/file2 X_1 vs Y, test/file1 X_2 vs Y
   *  </pre>
   */
  @Theory
  public void checkFilesNamesSwappedOnSides_conflict(MergeStrategy strategy, boolean isRenameInOurs, boolean shouldModifyRename,boolean shouldModifyOriginal) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    originalFiles.put(renameFilename, "Unrelated base content");
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename,
        shouldModifyOriginal ? slightlyModifiedContent : originalContent);
    filesWithModify.put(renameFilename, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename,
        shouldModifyRename ? slightlyModifiedContent : originalContent);
    filesWithRename.put(originalFilename, "Unrelated file");

    Map<String, String> oursFiles = isRenameInOurs ? filesWithRename : filesWithModify;
    Map<String, String> theirsFiles = isRenameInOurs ? filesWithModify : filesWithRename;
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    if (shouldModifyOriginal) {
      expectedConflicts.add(originalFilename);
    }
    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int modifyStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    if (shouldModifyOriginal) {
      expectedIndex.add(
          String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
      expectedIndex.add(
          String.format("%s, mode:100644, stage:%s, content:%s", originalFilename, modifyStage,
              shouldModifyOriginal ? slightlyModifiedContent : originalContent));
      expectedIndex.add(
          String.format("%s, mode:100644, stage:%s, content:%s", originalFilename, renameStage,
              "Unrelated file"));
    } else {
      expectedIndex.add(
          String.format("%s, mode:100644, content:%s", originalFilename, "Unrelated file"));
    }
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", renameFilename, "Unrelated base content"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, modifyStage,
            "Unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage,
            shouldModifyRename? slightlyModifiedContent: originalContent));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s", oursFiles.get(renameFilename),
            theirsFiles.get(renameFilename)));
    if(shouldModifyOriginal) {
      expectedFiles.put(originalFilename,
          String.format("<<<<<<< HEAD\n%s\n=======\n%s", oursFiles.get(originalFilename),
              theirsFiles.get(originalFilename)));
    } else {
      expectedFiles.put(originalFilename, "Unrelated file");
    }

    testRename_withConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles,
        expectedConflicts, expectedIndex);
  }


  /**
   *  Testcase 2.6
   *  <pre>
   *    B: test/file1 A_1, test/file2 X_1
   *    O: test/file3 A_1
   *    T: test/file3 X_1
   *  Expected:
   *  Content merge conflict
   *    Work tree:
   *      test/file3 (A_1 vs X_1) conflict
   *    Index:
   *     O: test/file3 A_1
   *     T: test/file3 X_1
   *  </pre>
   *  <p>>The rename detection is off for this path. This will always result in test/file3 A_1 vs X_1 content merge conflict.
   */
  @Theory
  public void checkRenameSameTarget_differentSource_conflict(MergeStrategy strategy, boolean keepOriginalContent) throws Exception {
    String originalFilename1 = "test/file1";
    String originalFilename2 = "test/file2";
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\ny\ny";
    String renameFilename = "test/file3";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename1, originalContent1);
    originalFiles.put(originalFilename2, originalContent2);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(renameFilename,
        keepOriginalContent ? originalContent1 : slightlyModifiedContent1);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(renameFilename,
        keepOriginalContent ? originalContent2 : slightlyModifiedContent2);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s", renameFilename,
        oursFiles.get(renameFilename)));
    expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s", renameFilename,
        theirsFiles.get(renameFilename)));
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename1, null);
    expectedFiles.put(originalFilename2, null);
    expectedFiles.put(renameFilename,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s\n>>>>>>>", oursFiles.get(renameFilename),
            theirsFiles.get(renameFilename)));

    testRename_withConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles,
        expectedConflicts, expectedIndex);
  }

  /**
   * SECTION 3
   *
   * <p> This section covers edge-cases related to file name correlation
   */

  /**
   *  Testcase 3.1
   *  <p> This test checks that we do not accidentally drop added files, that are involved as a source of rename, and it does not result in collision.
   *  <pre>
   *    B: test/file1 X_1
   *    O: test/file1 X_2
   *    T: test/file2 X_1, test/file1 Y
   *  Expected:
   *    M: test/file2 X_2, test/file1 Y
   * </pre>
   */
  @Theory
  public void checkSourceAddedOnRenameSide_merged(MergeStrategy strategy, boolean isRenameInOurs, boolean modifyInRename, boolean modifyInOther) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent1 = "z\na\nb\nc";

    String slightlyModifiedContent2 = "a\nb\nc\nd";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename,
        modifyInRename ? slightlyModifiedContent1 : originalContent);
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, modifyInOther? slightlyModifiedContent2: originalContent);
    filesWithRename.put(originalFilename, "Unrelated file");

    String expectedRenameContent = modifyInRename && modifyInOther ? "z\na\nb\nc\nd"
        : modifyInRename ? slightlyModifiedContent1
            : modifyInOther ? slightlyModifiedContent2 : originalContent;
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, expectedRenameContent);
    expectedFiles.put(originalFilename, "Unrelated file");
    testRename_merged(strategy, originalFiles, isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles);
  }

  /**
   *  Testcase 3.2
   *  <p> Same as Testcase 3.1, but the source was renamed on both sides.
   *  <pre>
   *    B: test/file1 X_1
   *    O: test/file2 X_2
   *    T: test/file2 X_1 test/file1 Y
   *  Expected:
   *    test/file2 X_2 test/file1 Y
   *  </pre>
   */
  @Theory
  public void checkSourceAdded_renameOnBothSides_merged(MergeStrategy strategy, boolean isAddInOurs, boolean shouldModify) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithAdd = new HashMap<>();
    filesWithAdd.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    filesWithAdd.put(originalFilename, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    expectedFiles.put(originalFilename, "Unrelated file");
    testRename_merged(strategy, originalFiles, isAddInOurs ? filesWithAdd : filesWithRename,
        isAddInOurs ? filesWithRename : filesWithAdd, expectedFiles);
  }

  /**
   *  Testcase 3.3
   *  <p> Same as Testcase 3.2, but the content merge on added file is required.
   *  <pre>
   *    B: test/file1 X_1
   *    O: test/file2 X_2 test/file1 Y_1
   *    T: test/file2 X_1 test/file1 Y_2
   *  Expected:
   *    test/file2 X_2 test/file1 Y_3
   *  </pre>
   */
  @Theory
  public void checkSourceAddedOnBothSides_renameOnBothSides_merged(MergeStrategy strategy, boolean shouldModify) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String mergeContent1 = "Content 1";

    String mergeContent2 = "Content 2";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    oursFiles.put(originalFilename, mergeContent1);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    theirsFiles.put(originalFilename, mergeContent2);
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    expectedFiles.put(originalFilename,  String.format("<<<<<<< HEAD\n%s\n=======\n%s", mergeContent1, mergeContent2));
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(originalFilename);
    testRename_withConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles, expectedConflicts);
  }

  /**
   *  Testcase 3.4
   *  <p> Ideally, this should be detected as rename/delete conflict, but since we do not compute cross-side similarity, this just results in content merge.
   *  <p> This might not be a problem, however, see Testcase 3.5.
   *  <pre>
   *    B: test/file1 X_1
   *    O: test/file1 Y
   *    T: test/file2 X_2 test/file1 Y
   *  Expected:
   *   test/file2 X_2 ; test/file1 Y
   *  </pre>
   *  <p>With the current implementation, we always assume the non-rename side modified the file, while it is possible it completely replaced it.
   *  <p>Detecting it would require either:
   *  <p> - content test/file1 OvsT content comparison, since they do not have a common matching base.
   *  <p> - similarity {@code org.eclipse.jgit.diff.DiffEntry#score} calculations for {@code ChangeType#MODIFY}
   */
  @Theory
  public void checkSourceAddedOnBothSides_withModification_conflict(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, slightlyModifiedContent);
    filesWithRename.put(originalFilename, "Unrelated file");

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);

    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int modifyStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, content:%s", originalFilename, "Unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", renameFilename, originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage,
            slightlyModifiedContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, modifyStage,
            "Unrelated file"));

    String oursContent = isRenameInOurs ? filesWithRename.get(renameFilename) : "Unrelated file";
    String theirsContent =
        isRenameInOurs ? "Unrelated file" : filesWithRename.get(renameFilename);
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s", oursContent, theirsContent));
    expectedFiles.put(originalFilename, "Unrelated file");
    testRename_withConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles, expectedConflicts,
        expectedIndex);
  }

  /**
   *  Testcase 3.5
   *  <p> See Testcase 3.4. The results of this merge are meaningless.
   *  <p> Since rename/delete conflict was not detected, the unrelated file was chosen as a content for rename file, since the rename file was not modified comparing to base.
   *  <pre>
   *    B: test/file1 X_1
   *    O: test/file1 Y
   *    T: test/file2 X_1, test/file1 Y
   *  Expected:
   *   test/file2 Y, test/file1 Y
   *  </pre>
   *  <p> Note: without the rename detection, this would be content merged as test/file2 X_1, test/file1 Y
   */
  @Theory
  public void checkSourceAddedOnBothSides_noModification_merged(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, originalContent);
    filesWithRename.put(originalFilename, "Unrelated file");

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, "Unrelated file");
    expectedFiles.put(originalFilename, "Unrelated file");
    testRename_merged(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles);
  }

  /**
   * Testcase 3.6
   * <p>It does not matter that test/file2 Z is present in base. It is missing in both commits, so missing in merge.
   * <pre>
   *   B: test/file1 X_1 test/file2 Z
   *   O: test/file1 X_2
   *   T: test/file2 X_1
   *  Expected:
   *  test/file2 X_2
   * </pre>
   */
  @Theory
  public void checkRenameModify_withRenameExistedInBase_merged(MergeStrategy strategy, boolean isRenameInOurs, boolean shouldModifyOurs, boolean shouldModifyTheirs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    originalFiles.put(renameFilename, "Unrelated base content");
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename,
        shouldModifyOurs ? slightlyModifiedContent : originalContent);
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename,
        shouldModifyTheirs ? slightlyModifiedContent : originalContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, shouldModifyOurs || shouldModifyTheirs? slightlyModifiedContent: originalContent);

    testRename_merged(strategy, originalFiles, isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles);
  }

  /**
   * Testcase 3.7
   * <p> Same as Testcase 3.7, but renamed on both sides.
   * <pre>
   *   B: test/file1 X_1 test/file2 Z
   *   O: test/file2 X_2
   *   T: test/file2 X_1
   *  Expected:
   *  test/file2 X_2
   * </pre>
   */
  @Theory
  public void checkRenameBoth_withRenameExistedInBase_merged(MergeStrategy strategy, boolean shouldModifyOurs, boolean shouldModifyTheirs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    originalFiles.put(renameFilename, "Unrelated base content");
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(renameFilename,
        shouldModifyOurs ? slightlyModifiedContent : originalContent);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(renameFilename,
        shouldModifyTheirs ? slightlyModifiedContent : originalContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, shouldModifyOurs || shouldModifyTheirs? slightlyModifiedContent: originalContent);

    testRename_merged(strategy, originalFiles, oursFiles,
        theirsFiles, expectedFiles);
  }


  /**
   *  Testcase 3.8
   *  <p> Rename/delete conflict, but index is updated with the added file.
   *  <pre>
   *    B: test/file1 X_1
   *    O:
   *    T: test/file2 X_1, test/file1 Y
   *  Expected:
   *    M: test/file2 X_2, test/file1 Y
   * </pre>
   */
  @Theory
  public void checkSourceAddedOnRenameSide_withRenameDeleteConflict_conflicting(MergeStrategy strategy, boolean isRenameInOurs, boolean shouldModify) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithDelete = new HashMap<>();
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    filesWithRename.put(originalFilename, "Unrelated file");
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int deleteStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, content:%s", originalFilename, "Unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", renameFilename, originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage,
            shouldModify ? slightlyModifiedContent : originalContent));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    expectedFiles.put(originalFilename, "Unrelated file");

    testRename_withConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithDelete,
        isRenameInOurs ? filesWithDelete : filesWithRename, expectedFiles, expectedConflicts,
        expectedIndex);
  }

  /**
   *  Testcase 3.9
   *  <pre>
   *    B: test/file1 X_1 ; test/file2 Y
   *    O: test/file2 X_2 ;
   *    T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  test/file2 X_2, Delete/modify conflict on test/file1 Y.
   *  </pre>
   */
  @Theory
  public void checkMultipleRenames_correlationOnSource_renameModify_renameDelete_conflict(MergeStrategy strategy, boolean isSwapInOurs, boolean shouldModify, boolean shouldRenameBothSides) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    originalFiles.put(renameFilename, "Unrelated file");
    Map<String, String> filesWithRenameSwap = new HashMap<>();
    filesWithRenameSwap.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    filesWithRenameSwap.put(originalFilename, "Unrelated file");
    Map<String, String> filesWithRenameDelete = new HashMap<>();
    filesWithRenameDelete.put(shouldRenameBothSides? renameFilename :originalFilename, shouldModify ? slightlyModifiedContent : originalContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    expectedFiles.put(originalFilename, "Unrelated file");

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(originalFilename);
    Set<String> expectedIndex = new HashSet<>();
    int renameSwapStage = isSwapInOurs? DirCacheEntry.STAGE_2: DirCacheEntry.STAGE_3;
    expectedIndex.add(
        String.format("%s, mode:100644, content:%s", renameFilename,  shouldModify ? slightlyModifiedContent : originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", originalFilename, "Unrelated file"));
    expectedIndex.add(String.format("%s, mode:100644, stage:%s, content:%s",
        originalFilename, renameSwapStage, "Unrelated file"));

    testRename_withConflict(strategy, originalFiles, isSwapInOurs ? filesWithRenameSwap : filesWithRenameDelete,
        isSwapInOurs ? filesWithRenameDelete : filesWithRenameSwap, expectedFiles, expectedConflicts, expectedIndex);
  }

  /**
   *  Testcase 3.10
   *  <p> There are 2 renames with swapped contents.
   *  <pre>
   *    B: test/file1 X_1 ; test/file2 Y
   *    O: test/file2 X_2 ; test/file1 Y_1
   *    T: test/file2 X_1; test/file1 Y_1
   *  Expected:
   *   test/file2 X_2 ; test/file1 Y_1
   *  </pre>
   */
  @Theory
  public void checkMultipleRenames_swapRename_merged(MergeStrategy strategy, boolean shouldModifyRename1, boolean shouldModifyRename2) throws Exception {
    String renameFilename1 = "test/file1";
    String originalContent1 = "a\nb\nc";
    String slightlyModifiedContent1 = "a\nb\nb";
    String renameFilename2 = "test/file2";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent2 = "x\ny\ny";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(renameFilename1, originalContent1);
    originalFiles.put(renameFilename2, originalContent2);
    Map<String, String> oursFilesWithRename = new HashMap<>();
    oursFilesWithRename.put(renameFilename2,
        shouldModifyRename1 ? slightlyModifiedContent1 : originalContent1);
    oursFilesWithRename.put(renameFilename1,
        shouldModifyRename2 ? slightlyModifiedContent2 : originalContent2);
    Map<String, String> theirsFilesWithRename = new HashMap<>();
    theirsFilesWithRename.put(renameFilename2,
        shouldModifyRename1 ? slightlyModifiedContent1 : originalContent1);
    theirsFilesWithRename.put(renameFilename1,
        shouldModifyRename2 ? slightlyModifiedContent2 : originalContent2);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename2,
        shouldModifyRename1 ? slightlyModifiedContent1 : originalContent1);
    expectedFiles.put(renameFilename1,
        shouldModifyRename2 ? slightlyModifiedContent2 : originalContent2);
    testRename_merged(strategy, originalFiles, oursFilesWithRename, theirsFilesWithRename,
        expectedFiles);
  }

  /**
   *  Testcase 3.11
   *  <p> Same as Testcase 2.6, but the side keeps the rename files of opposite side.
   *  <pre>
   *    B: test/file1 A_1, test/file2 X_1
   *    O: test/file3 A_1, test/file2 X_1(2)
   *    T: test/file3 X_1; test/file1 A_1(2)
   *  Expected:
   *    The rename detection is off for test/file3 This will always result in
   *    test/file3 A_1 vs test/file3 X_1 conflict.
   * </pre>
   * Additionaly, we have a modify/delete conflict on test/file1, test/file2
   */
  @Theory
  public void  checkRenameSameTarget_differentSource_wihSourcePresentOnSides_conflict(MergeStrategy strategy, boolean keepOriginalContent) throws Exception {
    String originalFilename1 = "test/file1";
    String originalFilename2 = "test/file2";
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\ny\ny";
    String renameFilename = "test/file3";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename1, originalContent1);
    originalFiles.put(originalFilename2, originalContent2);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(renameFilename,
        keepOriginalContent ? originalContent1 : slightlyModifiedContent1);
    oursFiles.put(originalFilename2,
        keepOriginalContent ? originalContent2 : slightlyModifiedContent2);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(renameFilename,
        keepOriginalContent ? originalContent2 : slightlyModifiedContent2);
    theirsFiles.put(originalFilename1,
        keepOriginalContent ? originalContent1 : slightlyModifiedContent1);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    if(!keepOriginalContent) {
      // we also get a modify/deite conflict in this case.
      expectedConflicts.add(originalFilename1);
      expectedConflicts.add(originalFilename2);
    }
    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s", renameFilename,
        oursFiles.get(renameFilename)));
    expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s", renameFilename,
        theirsFiles.get(renameFilename)));
    if(!keepOriginalContent) {
      expectedIndex.add(
          String.format("%s, mode:100644, stage:1, content:%s", originalFilename1,
              originalContent1));
      expectedIndex.add(
          String.format("%s, mode:100644, stage:1, content:%s", originalFilename2,
              originalContent2));
      expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s", originalFilename2,
          oursFiles.get(originalFilename2)));
      expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s", originalFilename1,
          theirsFiles.get(originalFilename1)));
    }

    Map<String, String> expectedFiles = new HashMap<>();
    if(!keepOriginalContent) {
      expectedFiles.put(originalFilename1, slightlyModifiedContent1);
      expectedFiles.put(originalFilename2, slightlyModifiedContent2);
    } else {
      expectedFiles.put(originalFilename1, null);
      expectedFiles.put(originalFilename2, null);
    }
    expectedFiles.put(renameFilename,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s\n>>>>>>>", oursFiles.get(renameFilename),
            theirsFiles.get(renameFilename)));

    testRename_withConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles,
        expectedConflicts, expectedIndex);
  }

  /**
   *  Testcase 3.12
   *  <p> See Testcase 2.6. The conflicting rename is switched off, but the correlating rename is not affected.
   *  <pre>
   *    B: test/file1 A_1, test/file2 X_1, test/file3 B
   *    O: test/file3 A_1, test/file1 B
   *    T: test/file3 X_1; test/file1 B
   *  Expected:
   *    - test/file3 A_1 vs test/file3 X_1 content merge conflict.
   *    - test/file1 B
   * </pre>
   */
  @Theory
  public void  checkMultipleRenames_renameSameTarget_differentSource_conflict(MergeStrategy strategy, boolean keepOriginalContent) throws Exception {
    String originalFilename1 = "test/file1";
    String originalFilename2 = "test/file2";
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\ny\ny";
    String renameFilename = "test/file3";
    String correlatingRenameContent = "Correlating rename";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename1, originalContent1);
    originalFiles.put(originalFilename2, originalContent2);
    originalFiles.put(renameFilename, correlatingRenameContent);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(renameFilename,
        keepOriginalContent ? originalContent1 : slightlyModifiedContent1);
    oursFiles.put(originalFilename1, correlatingRenameContent);
    Map<String, String> theirsFiles = new HashMap<>();

    theirsFiles.put(renameFilename,
        keepOriginalContent ? originalContent2 : slightlyModifiedContent2);
    theirsFiles.put(originalFilename1, correlatingRenameContent);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(
        String.format("%s, mode:100644, content:%s", originalFilename1, correlatingRenameContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s", renameFilename,
        oursFiles.get(renameFilename)));
    expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s", renameFilename,
        theirsFiles.get(renameFilename)));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s\n>>>>>>>", oursFiles.get(renameFilename),
            theirsFiles.get(renameFilename)));
    expectedFiles.put(originalFilename1, correlatingRenameContent);

    testRename_withConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles,
        expectedConflicts, expectedIndex);
  }

  /**
   *  Testcase 3.13
   *  <p> See Testcase 2.1, but also added file with source name.
   *  <p> This conflict would be extremely hard to represent in index, hence we have to resolve it.
   *  <pre>
   *    B: test/file1 X_1
   *    O: test/file2 X_2, test/file1 Z
   *    T: test/file3 X_1
   *    Expected:
   *    M: test/file3 Z vs X_1, test/file2 X_2
   *  </pre>
   */
  @Theory
  public void checkRenameRename_withSourceAdded_conflict(MergeStrategy strategy, boolean addInOurs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename1 = "test/file2";
    String renameFilename2 = "test/file3";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithAdd = new HashMap<>();
    filesWithAdd.put(renameFilename1, slightlyModifiedContent);
    filesWithAdd.put(originalFilename, "Unrelated content");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename2, originalContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename, null);
    expectedFiles.put(renameFilename1, slightlyModifiedContent);
    // Content merge result with O: file1 Z
    expectedFiles.put(renameFilename2, "Unrelated content");
    testRename_merged(strategy, originalFiles, addInOurs? filesWithAdd:filesWithRename, addInOurs? filesWithRename: filesWithAdd, expectedFiles);
  }

  /**
   *  Testcase 3.14
   *  <p> Same as 3.13, but source is present on both sides.
   *  <p> Since we can not represent it in index without dropping the source, switch off the rename.
   *  <pre>
   *    B: test/file1 X_1
   *    O: test/file2 X_2, test/file1 Z
   *    T: test/file3 X_3, test/file1 Z
   *    Expected:
   *    M: test/file1 Z, test/file2 X_2, test/file3 X_3
   *  </pre>
   */
  @Theory
  public void checkRenameRename_withSourceAddedOnBothSides_merged(MergeStrategy strategy) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "a\nb\nc\nd";
    String renameFilename1 = "test/file2";
    String renameFilename2 = "test/file3";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(originalFilename, "Unrelated content");
    oursFiles.put(renameFilename1, slightlyModifiedContent1);
    Map<String, String> theirsFile = new HashMap<>();
    theirsFile.put(originalFilename, "Unrelated content");
    theirsFile.put(renameFilename2, slightlyModifiedContent2);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename, "Unrelated content");
    expectedFiles.put(renameFilename1, slightlyModifiedContent1);
    expectedFiles.put(renameFilename2, slightlyModifiedContent2);
    testRename_merged(strategy, originalFiles, oursFiles, theirsFile, expectedFiles);
  }

  /**
   *  Testcase 3.15
   *  <p> See Testcase 2.1, but also added file with target name.
   *  <p> Since we can not represent it in index without dropping the source, switch off the rename.
   *  <pre>
   *    B: test/file1 X_1
   *    O: test/file2 X_2, test/file3 Z
   *    T: test/file3 X_3
   *    Expected:
   *    Conflict:
   *    test/file2 X_2, test/file3 X_3 vs Z
   *  </pre>
   */
  @Theory
  public void checkRenameRename_withRenameAddCollision_conflicting(MergeStrategy strategy, boolean isAddInOurs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "a\nb\nc\nd";
    String renameFilename1 = "test/file2";
    String renameFilename2 = "test/file3";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithAdd = new HashMap<>();
    filesWithAdd.put(renameFilename1, slightlyModifiedContent1);
    filesWithAdd.put(renameFilename2, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename2, slightlyModifiedContent2);

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename2);
    Set<String> expectedIndex = new HashSet<>();
    int modifyStage = isAddInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int renameStage = isAddInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, content:%s", renameFilename1,
            slightlyModifiedContent1));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename2, modifyStage,
            "Unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename2, renameStage,
            slightlyModifiedContent2));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename, null);
    expectedFiles.put(renameFilename1, slightlyModifiedContent1);
    String oursContent = isAddInOurs ? "Unrelated file": slightlyModifiedContent2;
    String theirsContent =
        isAddInOurs ? slightlyModifiedContent2 : "Unrelated file";
    expectedFiles.put(renameFilename2,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s", oursContent, theirsContent));
    testRename_withConflict(strategy, originalFiles, isAddInOurs ? filesWithAdd : filesWithRename,
        isAddInOurs ? filesWithRename : filesWithAdd, expectedFiles, expectedConflicts,
        expectedIndex);
  }

  /**
   *  Testcase 3.16
   *  <p> Same as Testcase 3.15, but with rename/modify collision.
   *  <pre>
   *    B: test/file1 X_1, test/file3 Z
   *    O: test/file2 X_2, test/file3 Z_1
   *    T: test/file3 X_3
   *  Expected:
   *    Conflict:
   *    test/file2 X_2, test/file3 X_3 vs Z_1
   *  </pre>
   */
  @Theory
  public void checkRenameRename_withRenameModifyCollision_conflicting(MergeStrategy strategy, boolean isAddInOurs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "a\nb\nc\nd";
    String renameFilename1 = "test/file2";
    String renameFilename2 = "test/file3";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    originalFiles.put(renameFilename2, "Base unrelated file");

    Map<String, String> filesWithAdd = new HashMap<>();
    filesWithAdd.put(renameFilename1, slightlyModifiedContent1);
    filesWithAdd.put(renameFilename2, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename2, slightlyModifiedContent2);

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename2);
    Set<String> expectedIndex = new HashSet<>();
    int modifyStage = isAddInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int renameStage = isAddInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, content:%s", renameFilename1,
            slightlyModifiedContent1));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", renameFilename2,
            "Base unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename2, modifyStage,
            "Unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename2, renameStage,
            slightlyModifiedContent2));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename, null);
    expectedFiles.put(renameFilename1, slightlyModifiedContent1);
    String oursContent = isAddInOurs ? "Unrelated file": slightlyModifiedContent2;
    String theirsContent =
        isAddInOurs ? slightlyModifiedContent2 : "Unrelated file";
    expectedFiles.put(renameFilename2,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s", oursContent, theirsContent));
    testRename_withConflict(strategy, originalFiles, isAddInOurs ? filesWithAdd : filesWithRename,
        isAddInOurs ? filesWithRename : filesWithAdd, expectedFiles, expectedConflicts,
        expectedIndex);
  }

  /**
   *  Testcase 3.17
   *  <p> Same as Testcase 3.15, but the collision was not modified.
   *  <p> Since it is untouched in one side and deleted in other, it is not present on merge and the regular rename/rename conflict is reported.
   *  <pre>
   *    B: test/file1 X_1, test/file3 Z
   *    O: test/file2 X_2, test/file3 Z
   *    T: test/file3 X_3
   *  Expected:
   *    Conflict:
   *    test/file2 X_2,
   *  </pre>
   */
  @Theory
  public void checkRenameRename_withRenameCollision_conflicting(MergeStrategy strategy, boolean isAddInOurs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "a\nb\nc\nd";
    String renameFilename1 = "test/file2";
    String renameFilename2 = "test/file3";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    originalFiles.put(renameFilename2, "Unrelated file");

    Map<String, String> filesWithAdd = new HashMap<>();
    filesWithAdd.put(renameFilename1, slightlyModifiedContent1);
    filesWithAdd.put(renameFilename2, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename2, slightlyModifiedContent2);

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(originalFilename);
    expectedConflicts.add(renameFilename1);
    expectedConflicts.add(renameFilename2);
    Set<String> expectedIndex = new HashSet<>();
    int modifyStage = isAddInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int renameStage = isAddInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename1, modifyStage,
            slightlyModifiedContent1));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename2, renameStage,
            slightlyModifiedContent2));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename, null);
    expectedFiles.put(renameFilename1, slightlyModifiedContent1);
    expectedFiles.put(renameFilename2, slightlyModifiedContent2);
    testRename_withConflict(strategy, originalFiles, isAddInOurs ? filesWithAdd : filesWithRename,
        isAddInOurs ? filesWithRename : filesWithAdd, expectedFiles, expectedConflicts,
        expectedIndex);
  }



  /**
   * SECTION 4
   * <p>Same as Testcase 1.1, but also tests that files, adjacent to rename are not dropped.
   */

  /**
   * Various files, that are present in the directories, containing the renames.
   * <p>This test cases make sure files are not disappearing on rename detection
   *
   * <p>TODO: extend when directory rename detection is implemented
   */
  @DataPoints("fileAdditions")
  public static final List<String> fileAdditionsData = List.of("file1.1", "file3","file0","a/file", "z/file", "a/sub/file", "z/sub/file");

  /**
   * Single file renames
   */
  @DataPoints("singleRenamePairs")
  public static final List<Entry<String, String>> singleRenamePairsData = List.of(
      // Direct rename of file
      Map.entry("test/file2","test/file1"),
      Map.entry("test/file1","test/file2"),
      Map.entry("test/a/file1","test/z/file2"),
      Map.entry("test/z/file1","test/a/file2"),
      // Move file to subdir
      Map.entry("test/file1", "test/w/file1"),
      Map.entry("test/file1", "test/a/file1"),
      // Move file to subdir and rename
      Map.entry("test/file1", "test/w/other"),
      Map.entry("test/file1", "test/a/other"),
      // Move to parent dir
      Map.entry("test/w/file1", "test/file1"),
      Map.entry("test/a/file1", "test/file1"),
      // Move to parent dir and rename
      Map.entry("test/w/file1", "test/other"),
      Map.entry("test/a/file1", "test/other"));

  /**
   * Testcase 4.1
   * <p>Note: this test does not take into account directory renames, since it is not implemented (see Testcase 2.2, Testcase 2.3).
   * <p>Examples:
   * <pre>
   * B: test/file1
   * O: test/file1, test/file1.1
   * T: test/file2
   *
   * Expected:
   * M: test/file2, test/file1.1
   * </pre>
   */
  @Theory
  public void checkRenameModify_withFilesInSameDir_merged(MergeStrategy strategy, @FromDataPoints("singleRenamePairs") Entry<String, String> renamePair,  @FromDataPoints("fileAdditions") String fileAddition, boolean isRenameInOurs) throws Exception {
    String originalContent = "a\nb\nc";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(renamePair.getKey(), originalContent);
    Map<String, String> nonRenameFiles = new HashMap<>();
    nonRenameFiles.put(renamePair.getKey(), originalContent);
    String addedFile = getDir(renamePair.getKey()) + "/" + fileAddition;
    nonRenameFiles.put(addedFile, "One file was added in ours");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renamePair.getValue(), originalContent);
    Map<String, String> expectedFiles = new HashMap<>();

    expectedFiles.put(renamePair.getValue(), originalContent);
    expectedFiles.put(addedFile, "One file was added in ours");
    testRename_merged(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : nonRenameFiles,
        isRenameInOurs ? nonRenameFiles : filesWithRename, expectedFiles);
  }

  /**
   * Testcase 4.2
   * Explicit test that directory renames are not detected, moving to parent dir
   * <pre>
   * B: test/a/file1
   * O: test/a/file1, test/a/file1.1
   * T: test/file1
   *
   * Expected:
   * M: test/file1, test/a/file1.1
   *
   * With the rename detection, expected:
   * M: test/file1, test/file1.1
   * </pre>
   */
  @Theory
  public void checkRenameMoveToParent_withFilesInSameDir_merged(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    String originalContent = "a\nb\nc";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/a/file1", originalContent);
    Map<String, String> nonRenameFiles = new HashMap<>();
    nonRenameFiles.put("test/a/file1", originalContent);
    nonRenameFiles.put("test/a/file1.1", "One file was added in ours");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put("test/file1", originalContent);
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put("test/file1", originalContent);
    expectedFiles.put("test/a/file1.1", "One file was added in ours");
    testRename_merged(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : nonRenameFiles,
        isRenameInOurs ? nonRenameFiles : filesWithRename, expectedFiles);
  }

  /**
   * Testcase 4.3
   *
   * Explicit test that directory renames are not detected, moving to sub dir
   * <pre>
   * B: test/file1
   * O: test/file1, test/file1.1
   * T: test/a/file1
   *
   * Expected:
   * M: test/a/file1, test/file1.1
   *
   * With the rename detection, expected:
   * M: test/a/file1, test/a/file1.1
   * </pre>
   */
  @Theory
  public void checkRenameToSubDir_withFilesInSameDir_merged(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    String originalContent = "a\nb\nc";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/file1", originalContent);
    Map<String, String> nonRenameFiles = new HashMap<>();
    nonRenameFiles.put("test/file1", originalContent);
    nonRenameFiles.put("test/file1.1", "One file was added in ours");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put("test/a/file1", originalContent);
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put("test/a/file1", originalContent);
    expectedFiles.put("test/file1.1", "One file was added in ours");
    testRename_merged(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : nonRenameFiles,
        isRenameInOurs ? nonRenameFiles : filesWithRename, expectedFiles);

  }

  @Theory
  public void checkRenameDir_AllFilesMoved_modifyConflict(MergeStrategy strategy) throws Exception {
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\nz\nz";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/a/file1", originalContent1);
    originalFiles.put("test/a/file2", originalContent2);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put("test/a/file1", slightlyModifiedContent1);
    oursFiles.put("test/a/file2", slightlyModifiedContent2);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put("test/sub/file1", originalContent1);
    theirsFiles.put("test/sub/file2", originalContent2);
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put("test/sub/file1", slightlyModifiedContent1);
    expectedFiles.put("test/sub/file2", slightlyModifiedContent2);
    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);

  }

  @Theory
  public void checkRenameSubDir_AllFilesMoved_SomeFilesAddedOnRenameSide_modifyConflict(MergeStrategy strategy, @FromDataPoints("renameLists") List<Entry<String, String>> renamePairs, boolean isRenameInOurs) throws Exception {
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    Map<String, String> originalFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      originalFiles.put(renamePair.getKey(), originalContent + renamePair.getKey());
    }
    Map<String, String> noRenameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      noRenameFiles.put(renamePair.getKey(), slightlyModifiedContent + renamePair.getKey());
    }
    Map<String, String> renameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      renameFiles.put(renamePair.getValue(), originalContent + renamePair.getKey());
    }
    String renameDir = getDir(renamePairs.get(0).getValue());
    for (String fileAddition : fileAdditionsData) {
      renameFiles.put(String.format("%s/%s",renameDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under rename dir?
    for (Entry<String, String> renamePair : renamePairs) {
      expectedFiles.put(renamePair.getValue(), slightlyModifiedContent + renamePair.getKey());
    }
    for (String fileAddition : fileAdditionsData) {
      expectedFiles.put(String.format("%s/%s",renameDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : noRenameFiles,
        isRenameInOurs ? noRenameFiles : renameFiles, expectedFiles);
  }

  @Theory
  public void checkRenameSubDir_AllFilesMoved_SomeFilesAddedOnModifySide_modifyConflict(MergeStrategy strategy, @FromDataPoints("renameLists") List<Entry<String, String>> renamePairs, boolean isRenameInOurs) throws Exception {
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    Map<String, String> originalFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      originalFiles.put(renamePair.getKey(), originalContent + renamePair.getKey());
    }
    Map<String, String> noRenameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      noRenameFiles.put(renamePair.getKey(), slightlyModifiedContent + renamePair.getKey());
    }
    String originalDir = getDir(renamePairs.get(0).getKey());
    for (String fileAddition : fileAdditionsData) {
      noRenameFiles.put(String.format("%s/%s", originalDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    Map<String, String> renameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      renameFiles.put(renamePair.getValue(), originalContent + renamePair.getKey());
    }
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under rename dir?
    for (Entry<String, String> renamePair : renamePairs) {
      expectedFiles.put(renamePair.getValue(), slightlyModifiedContent + renamePair.getKey());
    }
    for (String fileAddition : fileAdditionsData) {
      expectedFiles.put(String.format("%s/%s",originalDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : noRenameFiles,
        isRenameInOurs ? noRenameFiles : renameFiles, expectedFiles);
  }


  @Theory
  public void checkRenameSubDir_AllFilesMoved_SomeFilesAddedOnBothSides_modifyConflict(MergeStrategy strategy, @FromDataPoints("renameLists") List<Entry<String, String>> renamePairs, boolean isRenameInOurs) throws Exception {
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    Map<String, String> originalFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      originalFiles.put(renamePair.getKey(), originalContent + renamePair.getKey());
    }
    Map<String, String> noRenameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      noRenameFiles.put(renamePair.getKey(), slightlyModifiedContent + renamePair.getKey());
    }
    String originalDir = getDir(renamePairs.get(0).getKey());
    for (String fileAddition : fileAdditionsData) {
      noRenameFiles.put(String.format("%s/%s", originalDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    Map<String, String> renameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      renameFiles.put(renamePair.getValue(), originalContent + renamePair.getKey());
    }
    String renameDir = getDir(renamePairs.get(0).getValue());
    for (String fileAddition : fileAdditionsData) {
      renameFiles.put(String.format("%s/%s",renameDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under rename dir?
    for (Entry<String, String> renamePair : renamePairs) {
      expectedFiles.put(renamePair.getValue(), slightlyModifiedContent + renamePair.getKey());
    }
    for (String fileAddition : fileAdditionsData) {
      expectedFiles.put(String.format("%s/%s",originalDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    for (String fileAddition : fileAdditionsData) {
      expectedFiles.put(String.format("%s/%s",renameDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : noRenameFiles,
        isRenameInOurs ? noRenameFiles : renameFiles, expectedFiles);
  }

  @Theory
  public void checkRenameSubDir_AllFilesMoved_SomeFilesAddedInOurs_modifyConflict(MergeStrategy strategy) throws Exception {
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\nz\nz";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/a/file1", originalContent1);
    originalFiles.put("test/a/file2", originalContent2);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put("test/a/file1", slightlyModifiedContent1);
    oursFiles.put("test/a/file2", slightlyModifiedContent2);
    oursFiles.put("test/a/file3", "Added file");
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put("test/sub/file1", originalContent1);
    theirsFiles.put("test/sub/file2", originalContent2);
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under test/sub/
    expectedFiles.put("test/sub/file1", slightlyModifiedContent1);
    expectedFiles.put("test/sub/file2", slightlyModifiedContent2);
    expectedFiles.put("test/a/file3", "Added file");
    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);
  }

  @Theory
  public void checkRenameSubDir_AllFilesMoved_SomeFilesAddedInBoth_modifyConflict(MergeStrategy strategy) throws Exception {
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\nz\nz";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/a/file1", originalContent1);
    originalFiles.put("test/a/file2", originalContent2);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put("test/a/file1", slightlyModifiedContent1);
    oursFiles.put("test/a/file2", slightlyModifiedContent2);
    oursFiles.put("test/a/file1.1","One file was added in ours");
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put("test/sub/file1", originalContent1);
    theirsFiles.put("test/sub/file2", originalContent2);
    theirsFiles.put("test/sub/file3", "Another file was added in thiers");
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under test/sub/
    expectedFiles.put("test/sub/file1", slightlyModifiedContent1);
    expectedFiles.put("test/sub/file2", slightlyModifiedContent2);
    expectedFiles.put("test/a/file1.1", "One file was added in ours");
    expectedFiles.put("test/sub/file3", "Another file was added in thiers");
    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);
  }

  @Theory
  public void checkRename_dirSplit_modifyConflict(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\nz\nz";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/a/file1", originalContent1);
    originalFiles.put("test/a/file2", originalContent2);
    Map<String, String> noRenameFiles = new HashMap<>();
    noRenameFiles.put("test/a/file1", slightlyModifiedContent1);
    noRenameFiles.put("test/a/file2", slightlyModifiedContent2);
    Map<String, String> renameFiles = new HashMap<>();
    renameFiles.put("test/a1/file1", originalContent1);
    renameFiles.put("test/a2/file2", originalContent2);
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put("test/a1/file1", slightlyModifiedContent1);
    expectedFiles.put("test/a2/file2", slightlyModifiedContent2);
    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : noRenameFiles,
        isRenameInOurs ? noRenameFiles : renameFiles, expectedFiles);
  }

  @Theory
  public void checkRename_subDirSplit_modifyConflict(MergeStrategy strategy) throws Exception {
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\nz\nz";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/file1", originalContent1);
    originalFiles.put("test/file2", originalContent2);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put("test/file1", slightlyModifiedContent1);
    oursFiles.put("test/file2", slightlyModifiedContent2);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put("test/a1/file1", originalContent1);
    theirsFiles.put("test/a2/file2", originalContent2);
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put("test/a1/file1", slightlyModifiedContent1);
    expectedFiles.put("test/a2/file2", slightlyModifiedContent2);
    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);

  }

  private String getDir(String path){
    int endDir = path.lastIndexOf("/");
    return path.substring(0, endDir);
  }

}
