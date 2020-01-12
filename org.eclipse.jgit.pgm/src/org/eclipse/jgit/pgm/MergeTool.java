/*
 * Copyright (C) 2018-2020, Andre Bossert <andre.bossert@siemens.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import static org.eclipse.jgit.treewalk.TreeWalk.OperationType.CHECKOUT_OP;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.ContentSource;
import org.eclipse.jgit.diffmergetool.BooleanOption;
import org.eclipse.jgit.diffmergetool.FileElement;
import org.eclipse.jgit.diffmergetool.FileElement.Type;
import org.eclipse.jgit.diffmergetool.ExternalMergeTool;
import org.eclipse.jgit.diffmergetool.MergeToolManager;
import org.eclipse.jgit.diffmergetool.ToolException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.dircache.DirCacheCheckout.CheckoutMetadata;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.IndexDiff.StageState;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.RestOfArgumentsHandler;

@Command(name = "mergetool", common = true, usage = "usage_MergeTool")
class MergeTool extends TextBuiltin {
	private MergeToolManager mergeToolMgr;

	@Option(name = "--tool", aliases = {
			"-t" }, metaVar = "metaVar_tool", usage = "usage_ToolForMerge")
	private String toolName;

	private BooleanOption prompt = BooleanOption.DEFAULT_FALSE;

	@Option(name = "--prompt", usage = "usage_prompt")
	void setPrompt(@SuppressWarnings("unused") boolean on) {
		prompt = BooleanOption.TRUE;
	}

	@Option(name = "--no-prompt", aliases = { "-y" }, usage = "usage_noPrompt")
	void noPrompt(@SuppressWarnings("unused") boolean on) {
		prompt = BooleanOption.FALSE;
	}

	@Option(name = "--tool-help", usage = "usage_toolHelp")
	private boolean toolHelp;

	private BooleanOption gui = BooleanOption.DEFAULT_FALSE;

	@Option(name = "--gui", aliases = { "-g" }, usage = "usage_MergeGuiTool")
	void setGui(@SuppressWarnings("unused") boolean on) {
		gui = BooleanOption.TRUE;
	}

	@Option(name = "--no-gui", usage = "usage_noGui")
	void noGui(@SuppressWarnings("unused") boolean on) {
		gui = BooleanOption.FALSE;
	}

	@Argument(required = false, index = 0, metaVar = "metaVar_paths")
	@Option(name = "--", metaVar = "metaVar_paths", handler = RestOfArgumentsHandler.class)
	protected List<String> filterPaths;

	@Override
	protected void init(Repository repository, String gitDir) {
		super.init(repository, gitDir);
		mergeToolMgr = new MergeToolManager(repository);
	}

	enum MergeResult {
		SUCCESSFUL, FAILED, ABORT
	}

	@Override
	protected void run() {
		try {
			if (toolHelp) {
				showToolHelp();
			} else {
				// get prompt
				boolean showPrompt = mergeToolMgr.isPrompt();
				if (prompt.isConfigured()) {
					showPrompt = prompt.toBoolean();
				}
				// get passed or default tool name
				String toolNameSelected = toolName;
				if ((toolNameSelected == null) || toolNameSelected.isEmpty()) {
					toolNameSelected = mergeToolMgr.getDefaultToolName(gui);
				}
				// get the changed files
				Map<String, StageState> files = getFiles();
				if (files.size() > 0) {
					merge(files, showPrompt, toolNameSelected);
				} else {
					outw.println("No files need merging"); //$NON-NLS-1$
				}
			}
			outw.flush();
		} catch (Exception e) {
			throw die(e.getMessage(), e);
		}
	}

	private void merge(Map<String, StageState> files, boolean showPrompt,
			String toolNamePrompt) throws Exception {
		// sort file names
		List<String> mergedFilePaths = new ArrayList<>(files.keySet());
		Collections.sort(mergedFilePaths);
		// show the files
		outw.println("Merging:"); //$NON-NLS-1$
		for (String mergedFilePath : mergedFilePaths) {
			outw.println(mergedFilePath);
		}
		outw.flush();
		// merge the files
		MergeResult mergeResult = MergeResult.SUCCESSFUL;
		for (String mergedFilePath : mergedFilePaths) {
			// if last merge failed...
			if (mergeResult == MergeResult.FAILED) {
				// check if user wants to continue
				if (!isContinueUnresolvedPaths()) {
					mergeResult = MergeResult.ABORT;
				}
			}
			// aborted ?
			if (mergeResult == MergeResult.ABORT) {
				break;
			}
			// get file stage state and merge
			StageState fileState = files.get(mergedFilePath);
			if (fileState == StageState.BOTH_MODIFIED) {
				mergeResult = mergeModified(mergedFilePath, showPrompt,
						toolNamePrompt);
			} else if ((fileState == StageState.DELETED_BY_US) || (fileState == StageState.DELETED_BY_THEM)) {
				mergeResult = mergeDeleted(mergedFilePath,
						fileState == StageState.DELETED_BY_US);
			} else {
				outw.println(
						"\nUnknown merge conflict for '" + mergedFilePath //$NON-NLS-1$
								+ "':"); //$NON-NLS-1$
				mergeResult = MergeResult.ABORT;
			}
		}
	}

	private MergeResult mergeModified(final String mergedFilePath,
			final boolean showPrompt, final String toolNamePrompt)
			throws Exception {
		outw.println("\nNormal merge conflict for '" + mergedFilePath //$NON-NLS-1$
				+ "':"); //$NON-NLS-1$
		outw.println("  {local}: modified file"); //$NON-NLS-1$
		outw.println("  {remote}: modified file"); //$NON-NLS-1$
		outw.flush();
		// check if user wants to launch merge resolution tool
		boolean launch = true;
		if (showPrompt) {
			launch = isLaunch(toolNamePrompt);
		}
		if (!launch) {
			return MergeResult.ABORT; // abort
		}
		boolean isMergeSuccessful = true;
		ContentSource baseSource = ContentSource.create(db.newObjectReader());
		ContentSource localSource = ContentSource.create(db.newObjectReader());
		ContentSource remoteSource = ContentSource.create(db.newObjectReader());
		// temporary directory if mergetool.writeToTemp == true
		File tempDir = mergeToolMgr.createTempDirectory();
		// the parent directory for temp files (can be same as tempDir or just
		// the worktree dir)
		File tempFilesParent = tempDir != null ? tempDir : db.getWorkTree();
		try {
			FileElement base = null;
			FileElement local = null;
			FileElement remote = null;
			FileElement merged = new FileElement(mergedFilePath,
					Type.MERGED);
			DirCache cache = db.readDirCache();
			try (RevWalk revWalk = new RevWalk(db);
					TreeWalk treeWalk = new TreeWalk(db,
							revWalk.getObjectReader())) {
				treeWalk.setFilter(
						PathFilterGroup.createFromStrings(mergedFilePath));
				DirCacheIterator cacheIter = new DirCacheIterator(cache);
				treeWalk.addTree(cacheIter);
				while (treeWalk.next()) {
					final EolStreamType eolStreamType = treeWalk
							.getEolStreamType(CHECKOUT_OP);
					final String filterCommand = treeWalk.getFilterCommand(
							Constants.ATTR_FILTER_TYPE_SMUDGE);
					WorkingTreeOptions opt = db.getConfig()
							.get(WorkingTreeOptions.KEY);
					CheckoutMetadata checkoutMetadata = new CheckoutMetadata(
							eolStreamType, filterCommand);
					DirCacheEntry entry = treeWalk.getTree(DirCacheIterator.class).getDirCacheEntry();
					ObjectId id = entry.getObjectId();
					switch (entry.getStage()) {
					case DirCacheEntry.STAGE_1:
						base = new FileElement(mergedFilePath, Type.BASE);
						DirCacheCheckout.checkoutToFile(db, mergedFilePath,
								checkoutMetadata,
								baseSource.open(mergedFilePath, id), db.getFS(),
								opt, base.createTempFile(tempFilesParent));
						break;
					case DirCacheEntry.STAGE_2:
						local = new FileElement(mergedFilePath, Type.LOCAL);
						DirCacheCheckout.checkoutToFile(db, mergedFilePath,
								checkoutMetadata,
								localSource.open(mergedFilePath, id),
								db.getFS(), opt,
								local.createTempFile(tempFilesParent));
						break;
					case DirCacheEntry.STAGE_3:
						remote = new FileElement(mergedFilePath, Type.REMOTE);
						DirCacheCheckout.checkoutToFile(db, mergedFilePath,
								checkoutMetadata,
								remoteSource.open(mergedFilePath, id),
								db.getFS(), opt,
								remote.createTempFile(tempFilesParent));
						break;
					}
				}
			}
			if ((local == null) || (remote == null)) {
				throw die(
						"local or remote cannot be found in cache, stopping at " //$NON-NLS-1$
								+ mergedFilePath);
			}
			long modifiedBefore = merged.getFile().lastModified();
			try {
				// TODO: check how to return the exit-code of the
				// tool to jgit / java runtime ?
				// int rc =...
				ExecutionResult executionResult = mergeToolMgr.merge(local,
						remote, merged, base, tempDir, toolName, prompt, gui);
				outw.println(
						new String(executionResult.getStdout().toByteArray()));
				outw.flush();
				errw.println(
						new String(executionResult.getStderr().toByteArray()));
				errw.flush();
			} catch (ToolException e) {
				isMergeSuccessful = false;
				outw.println(e.getResultStdout());
				outw.flush();
				errw.println("merge of " + mergedFilePath + " failed"); //$NON-NLS-1$ //$NON-NLS-2$
				errw.flush();
				if (e.isCommandExecutionError()) {
					errw.println(e.getMessage());
					throw die("excution error", //$NON-NLS-1$
							e);
				}
			}
			// if merge was successful check file modified
			if (isMergeSuccessful) {
				long modifiedAfter = merged.getFile().lastModified();
				if (modifiedBefore == modifiedAfter) {
					outw.println(mergedFilePath + " seems unchanged."); //$NON-NLS-1$
					isMergeSuccessful = isMergeSuccessful();
				}
			}
			// if automatically or manually successful
			// -> add the file to the index
			if (isMergeSuccessful) {
				addFile(mergedFilePath);
			}
		} finally {
			baseSource.close();
			localSource.close();
			remoteSource.close();
		}
		return isMergeSuccessful ? MergeResult.SUCCESSFUL : MergeResult.FAILED;
	}

	private MergeResult mergeDeleted(final String mergedFilePath,
			final boolean deletedByUs) throws Exception {
		outw.println("\nDeleted merge conflict for '" + mergedFilePath //$NON-NLS-1$
				+ "':"); //$NON-NLS-1$
		if (deletedByUs) {
			outw.println("  {local}: deleted"); //$NON-NLS-1$
			outw.println("  {remote}: modified file"); //$NON-NLS-1$
		} else {
			outw.println("  {local}: modified file"); //$NON-NLS-1$
			outw.println("  {remote}: deleted"); //$NON-NLS-1$
		}
		int mergeDecision = getDeletedMergeDecision();
		if (mergeDecision == 1) {
			// add modified file
			addFile(mergedFilePath);
		} else if (mergeDecision == -1) {
			// remove deleted file
			rmFile(mergedFilePath);
		} else {
			return MergeResult.ABORT;
		}
		return MergeResult.SUCCESSFUL;
	}

	private void addFile(String fileName) throws Exception {
		try (Git git = new Git(db)) {
			git.add().addFilepattern(fileName).call();
		}
	}

	private void rmFile(String fileName) throws Exception {
		try (Git git = new Git(db)) {
			git.rm().addFilepattern(fileName).call();
		}
	}

	private boolean hasUserAccepted(final String message)
			throws IOException {
		boolean yes = true;
		outw.print(message);
		outw.flush();
		BufferedReader br = new BufferedReader(new InputStreamReader(ins));
		String line = null;
		while ((line = br.readLine()) != null) {
			if (line.equalsIgnoreCase("y")) { //$NON-NLS-1$
				yes = true;
				break;
			} else if (line.equalsIgnoreCase("n")) { //$NON-NLS-1$
				yes = false;
				break;
			}
			outw.print(message);
			outw.flush();
		}
		return yes;
	}

	private boolean isContinueUnresolvedPaths() throws IOException {
		return hasUserAccepted(
				"Continue merging other unresolved paths [y/n]? "); //$NON-NLS-1$
	}

	private boolean isMergeSuccessful() throws IOException {
		return hasUserAccepted("Was the merge successful [y/n]? "); //$NON-NLS-1$
	}

	private boolean isLaunch(String toolNamePrompt)
			throws IOException {
		boolean launch = true;
		final String message = "Hit return to start merge resolution tool (" //$NON-NLS-1$
				+ toolNamePrompt + "): "; //$NON-NLS-1$
		outw.print(message);
		outw.flush();
		BufferedReader br = new BufferedReader(new InputStreamReader(ins));
		String line = null;
		if ((line = br.readLine()) != null) {
			if (!line.equalsIgnoreCase("y") && !line.equalsIgnoreCase("")) { //$NON-NLS-1$ //$NON-NLS-2$
				launch = false;
			}
		}
		return launch;
	}

	private int getDeletedMergeDecision()
			throws IOException {
		int ret = 0; // abort
		final String message = "Use (m)odified or (d)eleted file, or (a)bort? "; //$NON-NLS-1$
		outw.print(message);
		outw.flush();
		BufferedReader br = new BufferedReader(new InputStreamReader(ins));
		String line = null;
		while ((line = br.readLine()) != null) {
			if (line.equalsIgnoreCase("m")) { //$NON-NLS-1$
				ret = 1; // modified
				break;
			} else if (line.equalsIgnoreCase("d")) { //$NON-NLS-1$
				ret = -1; // deleted
				break;
			} else if (line.equalsIgnoreCase("a")) { //$NON-NLS-1$
				break;
			}
			outw.print(message);
			outw.flush();
		}
		return ret;
	}

	private void showToolHelp() throws IOException {
		outw.println(
				"'git mergetool --tool=<tool>' may be set to one of the following:"); //$NON-NLS-1$
		for (String name : mergeToolMgr.getAvailableTools().keySet()) {
			outw.println("\t\t" + name); //$NON-NLS-1$
		}
		outw.println(""); //$NON-NLS-1$
		outw.println("\tuser-defined:"); //$NON-NLS-1$
		Map<String, ExternalMergeTool> userTools = mergeToolMgr
				.getUserDefinedTools();
		for (String name : userTools.keySet()) {
			outw.println("\t\t" + name + ".cmd " //$NON-NLS-1$ //$NON-NLS-2$
					+ userTools.get(name).getCommand());
		}
		outw.println(""); //$NON-NLS-1$
		outw.println(
				"The following tools are valid, but not currently available:"); //$NON-NLS-1$
		for (String name : mergeToolMgr.getNotAvailableTools().keySet()) {
			outw.println("\t\t" + name); //$NON-NLS-1$
		}
		outw.println(""); //$NON-NLS-1$
		outw.println("Some of the tools listed above only work in a windowed"); //$NON-NLS-1$
		outw.println(
				"environment. If run in a terminal-only session, they will fail."); //$NON-NLS-1$
		return;
	}

	private Map<String, StageState> getFiles()
			throws RevisionSyntaxException, NoWorkTreeException,
			GitAPIException {
		Map<String, StageState> files = new TreeMap<>();
		try (Git git = new Git(db)) {
			StatusCommand statusCommand = git.status();
			if (filterPaths != null && filterPaths.size() > 0) {
				for (String path : filterPaths) {
					statusCommand.addPath(path);
				}
			}
			org.eclipse.jgit.api.Status status = statusCommand.call();
			files = status.getConflictingStageState();
		}
		return files;
	}

}
