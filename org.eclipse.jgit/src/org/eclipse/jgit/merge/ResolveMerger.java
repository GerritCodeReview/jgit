/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>,
 * Copyright (C) 2010-2012, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2012, Research In Motion Limited
 * Copyright (C) 2017, Obeo (mathieu.cartaud@obeo.fr)
 * Copyright (C) 2018, 2022 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.merge;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.EPOCH;
import static org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm.HISTOGRAM;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DIFF_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_ALGORITHM;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.merge.RenameResolver.RenameConflict.RENAME_BOTH_SIDES_CONFLICT;
import static org.eclipse.jgit.merge.RenameResolver.RenameConflict.RENAME_DELETE_CONFLICT;
import static org.eclipse.jgit.merge.RenameResolver.RenameType.RENAME_IN_OURS;
import static org.eclipse.jgit.merge.RenameResolver.RenameType.RENAME_IN_THEIRS;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheCheckout.CheckoutMetadata;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.BinaryBlobException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.IndexWriteException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.RenameResolver.RenameEntry;
import org.eclipse.jgit.merge.RenameResolver.RenameType;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.submodule.SubmoduleConflict;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.RenameProcessingTreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.LfsFactory;
import org.eclipse.jgit.util.LfsFactory.LfsInputStream;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.TemporaryBuffer.LocalFile;
import org.eclipse.jgit.util.io.EolStreamTypeUtil;

/**
 * A three-way merger performing a content-merge if necessary
 */
public class ResolveMerger extends ThreeWayMerger {
	/**
	 * If the merge fails (means: not stopped because of unresolved conflicts)
	 * this enum is used to explain why it failed
	 */
	public enum MergeFailureReason {
		/** the merge failed because of a dirty index */
		DIRTY_INDEX,
		/** the merge failed because of a dirty workingtree */
		DIRTY_WORKTREE,
		/** the merge failed because of a file could not be deleted */
		COULD_NOT_DELETE
	}

	/**
	 * The tree walk which we'll iterate over to merge entries.
	 *
	 * @since 3.4
	 */
	protected TreeWalk tw;

	/**
	 * string versions of a list of commit SHA1s
	 *
	 * @since 3.0
	 */
	protected String[] commitNames;

	/**
	 * Index of the base tree within the {@link #tw tree walk}.
	 *
	 * @since 3.4
	 */
	public static final int T_BASE = 0;

	/**
	 * Index of our tree in withthe {@link #tw tree walk}.
	 *
	 * @since 3.4
	 */
	public static final int T_OURS = 1;

	/**
	 * Index of their tree within the {@link #tw tree walk}.
	 *
	 * @since 3.4
	 */
	public static final int T_THEIRS = 2;

	/**
	 * Index of the index tree within the {@link #tw tree walk}.
	 *
	 * @since 3.4
	 */
	public static final int T_INDEX = 3;

	/**
	 * Index of the working directory tree within the {@link #tw tree walk}.
	 *
	 * @since 3.4
	 */
	public static final int T_FILE = 4;

	/**
	 * Builder to update the cache during this merge.
	 *
	 * @since 3.4
	 */
	protected DirCacheBuilder builder;

	/**
	 * merge result as tree
	 *
	 * @since 3.0
	 */
	protected ObjectId resultTree;

	/**
	 * Paths that could not be merged by this merger because of an unsolvable
	 * conflict.
	 *
	 * @since 3.4
	 */
	protected List<String> unmergedPaths = new ArrayList<>();

	/**
	 * Files modified during this merge operation.
	 *
	 * @since 3.4
	 */
	protected List<String> modifiedFiles = new LinkedList<>();

	/**
	 * If the merger has nothing to do for a file but check it out at the end of
	 * the operation, it can be added here.
	 *
	 * @since 3.4
	 */
	protected Map<String, DirCacheEntry> toBeCheckedOut = new HashMap<>();

	/**
	 * Paths in this list will be deleted from the local copy at the end of the
	 * operation.
	 *
	 * @since 3.4
	 */
	protected List<String> toBeDeleted = new ArrayList<>();

	/**
	 * Low-level textual merge results. Will be passed on to the callers in case
	 * of conflicts.
	 *
	 * @since 3.4
	 */
	protected Map<String, MergeResult<? extends Sequence>> mergeResults = new HashMap<>();

	/**
	 * Paths for which the merge failed altogether.
	 *
	 * @since 3.4
	 */
	protected Map<String, MergeFailureReason> failingPaths = new HashMap<>();

	/**
	 * Updated as we merge entries of the tree walk. Tells us whether we should
	 * recurse into the entry if it is a subtree.
	 *
	 * @since 3.4
	 */
	protected boolean enterSubtree;

	/**
	 * Set to true if this merge should work in-memory. The repos dircache and
	 * workingtree are not touched by this method. Eventually needed files are
	 * created as temporary files and a new empty, in-memory dircache will be
	 * used instead the repo's one. Often used for bare repos where the repo
	 * doesn't even have a workingtree and dircache.
	 * @since 3.0
	 */
	protected boolean inCore;

	/**
	 * Set to true if this merger should use the default dircache of the
	 * repository and should handle locking and unlocking of the dircache. If
	 * this merger should work in-core or if an explicit dircache was specified
	 * during construction then this field is set to false.
	 * @since 3.0
	 */
	protected boolean implicitDirCache;

	/**
	 * Directory cache
	 * @since 3.0
	 */
	protected DirCache dircache;

	/**
	 * The iterator to access the working tree. If set to <code>null</code> this
	 * merger will not touch the working tree.
	 * @since 3.0
	 */
	protected WorkingTreeIterator workingTreeIterator;

	/**
	 * our merge algorithm
	 * @since 3.0
	 */
	protected MergeAlgorithm mergeAlgorithm;

	/**
	 * The config of the repository.
	 */
	protected final Config config;

	/**
	 * If {@link #detectRenames} is true, the instance that holds the renames,
	 * otherwise {@code null}.
	 */
	protected RenameResolver renameResolver;

	/**
	 * Whether the renames should be detected by this merger.
	 */
	protected boolean detectRenames;

	/**
	 * The {@link WorkingTreeOptions} are needed to determine line endings for
	 * merged files.
	 *
	 * @since 4.11
	 */
	protected WorkingTreeOptions workingTreeOptions;

	/**
	 * The size limit (bytes) which controls a file to be stored in {@code Heap}
	 * or {@code LocalFile} during the merge.
	 */
	private int inCoreLimit;

	/**
	 * The {@link ContentMergeStrategy} to use for "resolve" and "recursive"
	 * merges.
	 */
	@NonNull
	private ContentMergeStrategy contentStrategy = ContentMergeStrategy.CONFLICT;

	/**
	 * Keeps {@link CheckoutMetadata} for {@link #checkout()}.
	 */
	private Map<String, CheckoutMetadata> checkoutMetadata;

	/**
	 * Keeps {@link CheckoutMetadata} for {@link #cleanUp()}.
	 */
	private Map<String, CheckoutMetadata> cleanupMetadata;

	private static MergeAlgorithm getMergeAlgorithm(Config config) {
		SupportedAlgorithm diffAlg = config.getEnum(
				CONFIG_DIFF_SECTION, null, CONFIG_KEY_ALGORITHM,
				HISTOGRAM);
		return new MergeAlgorithm(DiffAlgorithm.getAlgorithm(diffAlg));
	}

	private static int getInCoreLimit(Config config) {
		return config.getInt(
				ConfigConstants.CONFIG_MERGE_SECTION, ConfigConstants.CONFIG_KEY_IN_CORE_LIMIT, 10 << 20);
	}

	private static String[] defaultCommitNames() {
		return new String[] { "BASE", "OURS", "THEIRS" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static final Attributes NO_ATTRIBUTES = new Attributes();

	/**
	 * Constructor for ResolveMerger.
	 *
	 * @param local
	 *            the {@link Repository}.
	 * @param inCore
	 *            a boolean.
	 */
	protected ResolveMerger(Repository local, boolean inCore) {
		super(local);
		config = local.getConfig();
		mergeAlgorithm = getMergeAlgorithm(config);
		inCoreLimit = getInCoreLimit(config);
		commitNames = defaultCommitNames();
		this.inCore = inCore;

		if (inCore) {
			implicitDirCache = false;
			dircache = DirCache.newInCore();
		} else {
			implicitDirCache = true;
			workingTreeOptions = local.getConfig().get(WorkingTreeOptions.KEY);
		}
	}

	/**
	 * Constructor for ResolveMerger.
	 *
	 * @param local
	 *            the {@link Repository}.
	 */
	protected ResolveMerger(Repository local) {
		this(local, false);
	}

	/**
	 * Constructor for ResolveMerger.
	 *
	 * @param inserter
	 *            an {@link ObjectInserter} object.
	 * @param config
	 *            the repository configuration
	 * @since 4.8
	 */
	protected ResolveMerger(ObjectInserter inserter, Config config) {
		super(inserter);
		mergeAlgorithm = getMergeAlgorithm(config);
		commitNames = defaultCommitNames();
		inCore = true;
		implicitDirCache = false;
		dircache = DirCache.newInCore();
		this.config = config;
	}

	/**
	 * Retrieves the content merge strategy for content conflicts.
	 *
	 * @return the {@link ContentMergeStrategy} in effect
	 * @since 5.12
	 */
	@NonNull
	public ContentMergeStrategy getContentMergeStrategy() {
		return contentStrategy;
	}

	/**
	 * Sets the content merge strategy for content conflicts.
	 *
	 * @param strategy
	 *            {@link ContentMergeStrategy} to use
	 * @since 5.12
	 */
	public void setContentMergeStrategy(ContentMergeStrategy strategy) {
		contentStrategy = strategy == null ? ContentMergeStrategy.CONFLICT
				: strategy;
	}

	/**
	 * Sets whether the rename detection is on.
	 * 
	 * @param detectRenames
	 *            if true, renames will be detected during merge.
	 */
	public void setDetectRenames(boolean detectRenames) {
		this.detectRenames = detectRenames;
	}

	/** {@inheritDoc} */
	@Override
	protected boolean mergeImpl() throws IOException {
		if (implicitDirCache) {
			dircache = nonNullRepo().lockDirCache();
		}
		if (!inCore) {
			checkoutMetadata = new HashMap<>();
			cleanupMetadata = new HashMap<>();
		}

		try {
			return mergeTrees(mergeBase(), sourceTrees[0], sourceTrees[1],
					false);
		} finally {
			checkoutMetadata = null;
			cleanupMetadata = null;
			if (implicitDirCache) {
				dircache.unlock();
			}
		}
	}

	private void checkout() throws NoWorkTreeException, IOException {
		// Iterate in reverse so that "folder/file" is deleted before
		// "folder". Otherwise this could result in a failing path because
		// of a non-empty directory, for which delete() would fail.
		for (int i = toBeDeleted.size() - 1; i >= 0; i--) {
			String fileName = toBeDeleted.get(i);
			File f = new File(nonNullRepo().getWorkTree(), fileName);
			if (!f.delete())
				if (!f.isDirectory())
					failingPaths.put(fileName,
							MergeFailureReason.COULD_NOT_DELETE);
			modifiedFiles.add(fileName);
		}
		for (Entry<String, DirCacheEntry> entry : toBeCheckedOut
				.entrySet()) {
			DirCacheEntry cacheEntry = entry.getValue();
			if (cacheEntry.getFileMode() == FileMode.GITLINK) {
				new File(nonNullRepo().getWorkTree(), entry.getKey()).mkdirs();
			} else {
				DirCacheCheckout.checkoutEntry(db, cacheEntry, reader, false,
						checkoutMetadata.get(entry.getKey()));
				modifiedFiles.add(entry.getKey());
			}
		}
	}

	/**
	 * Reverts the worktree after an unsuccessful merge. We know that for all
	 * modified files the old content was in the old index and the index
	 * contained only stage 0. In case if inCore operation just clear the
	 * history of modified files.
	 *
	 * @throws IOException
	 * @throws CorruptObjectException
	 * @throws NoWorkTreeException
	 * @since 3.4
	 */
	protected void cleanUp() throws NoWorkTreeException,
			CorruptObjectException,
			IOException {
		if (inCore) {
			modifiedFiles.clear();
			return;
		}

		DirCache dc = nonNullRepo().readDirCache();
		Iterator<String> mpathsIt=modifiedFiles.iterator();
		while(mpathsIt.hasNext()) {
			String mpath = mpathsIt.next();
			DirCacheEntry entry = dc.getEntry(mpath);
			if (entry != null) {
				DirCacheCheckout.checkoutEntry(db, entry, reader, false,
						cleanupMetadata.get(mpath));
			}
			mpathsIt.remove();
		}
	}

	/**
	 * adds a new path with the specified stage to the index builder
	 *
	 * @param path
	 * @param p
	 * @param stage
	 * @param lastMod
	 * @param len
	 * @return the entry which was added to the index
	 */
	private DirCacheEntry add(byte[] path, CanonicalTreeParser p, int stage,
			Instant lastMod, long len) {
		if (p != null && !p.getEntryFileMode().equals(FileMode.TREE) && !p.getEntryFileMode().equals(FileMode.MISSING)) {
			DirCacheEntry e = new DirCacheEntry(path, stage);
			e.setFileMode(p.getEntryFileMode());
			e.setObjectId(p.getEntryObjectId());
			e.setLastModified(lastMod);
			e.setLength(len);
			builder.add(e);
			return e;
		}
		return null;
	}

	/**
	 * adds a entry to the index builder which is a copy of the specified
	 * DirCacheEntry
	 *
	 * @param e
	 *            the entry which should be copied
	 *
	 * @return the entry which was added to the index
	 */
	private DirCacheEntry keep(DirCacheEntry e) {
		DirCacheEntry newEntry = new DirCacheEntry(e.getRawPath(),
				e.getStage());
		newEntry.setFileMode(e.getFileMode());
		newEntry.setObjectId(e.getObjectId());
		newEntry.setLastModified(e.getLastModifiedInstant());
		newEntry.setLength(e.getLength());
		builder.add(newEntry);
		return newEntry;
	}

	/**
	 * Remembers the {@link CheckoutMetadata} for the given path; it may be
	 * needed in {@link #checkout()} or in {@link #cleanUp()}.
	 *
	 * @param map
	 *            to add the metadata to
	 * @param path
	 *            of the current node
	 * @param attributes
	 *            to use for determining the metadata
	 * @throws IOException
	 *             if the smudge filter cannot be determined
	 * @since 6.1
	 */
	protected void addCheckoutMetadata(Map<String, CheckoutMetadata> map,
			String path, Attributes attributes)
			throws IOException {
		if (map != null) {
			EolStreamType eol = EolStreamTypeUtil.detectStreamType(
					OperationType.CHECKOUT_OP, workingTreeOptions,
					attributes);
			CheckoutMetadata data = new CheckoutMetadata(eol,
					tw.getSmudgeCommand(attributes));
			map.put(path, data);
		}
	}

	/**
	 * Adds a {@link DirCacheEntry} for direct checkout and remembers its
	 * {@link CheckoutMetadata}.
	 *
	 * @param path
	 *            of the entry
	 * @param entry
	 *            to add
	 * @param attributes
	 *            the {@link Attributes} of the trees
	 * @throws IOException
	 *             if the {@link CheckoutMetadata} cannot be determined
	 * @since 6.1
	 */
	protected void addToCheckout(String path, DirCacheEntry entry,
			Attributes[] attributes)
			throws IOException {
		toBeCheckedOut.put(path, entry);
		addCheckoutMetadata(cleanupMetadata, path, attributes[T_OURS]);
		addCheckoutMetadata(checkoutMetadata, path, attributes[T_THEIRS]);
	}

	/**
	 * Remember a path for deletion, and remember its {@link CheckoutMetadata}
	 * in case it has to be restored in {@link #cleanUp()}.
	 *
	 * @param path
	 *            of the entry
	 * @param isFile
	 *            whether it is a file
	 * @param attributes
	 *            to use for determining the {@link CheckoutMetadata}
	 * @throws IOException
	 *             if the {@link CheckoutMetadata} cannot be determined
	 * @since 5.1
	 */
	protected void addDeletion(String path, boolean isFile,
			Attributes attributes) throws IOException {
		toBeDeleted.add(path);
		if (isFile) {
			addCheckoutMetadata(cleanupMetadata, path, attributes);
		}
	}

	/**
	 * Processes one path and tries to merge taking git attributes in account.
	 * This method will do all trivial (not content) merges and will also detect
	 * if a merge will fail. The merge will fail when one of the following is
	 * true
	 * <ul>
	 * <li>the index entry does not match the entry in ours. When merging one
	 * branch into the current HEAD, ours will point to HEAD and theirs will
	 * point to the other branch. It is assumed that the index matches the HEAD
	 * because it will only not match HEAD if it was populated before the merge
	 * operation. But the merge commit should not accidentally contain
	 * modifications done before the merge. Check the <a href=
	 * "http://www.kernel.org/pub/software/scm/git/docs/git-read-tree.html#_3_way_merge"
	 * >git read-tree</a> documentation for further explanations.</li>
	 * <li>A conflict was detected and the working-tree file is dirty. When a
	 * conflict is detected the content-merge algorithm will try to write a
	 * merged version into the working-tree. If the file is dirty we would
	 * override unsaved data.</li>
	 * </ul>
	 *
	 * @param base
	 *            the common base for ours and theirs
	 * @param ours
	 *            the ours side of the merge. When merging a branch into the
	 *            HEAD ours will point to HEAD
	 * @param theirs
	 *            the theirs side of the merge. When merging a branch into the
	 *            current HEAD theirs will point to the branch which is merged
	 *            into HEAD.
	 * @param index
	 *            the index entry
	 * @param work
	 *            the file in the working tree
	 * @param ignoreConflicts
	 *            see
	 *            {@link ResolveMerger#mergeTrees(RevTree, RevTree, RevTree, boolean)}
	 * @param attributes
	 *            the {@link Attributes} for the three trees
	 * @param isRenameProcessing
	 *            if the path being processed is a rename.
	 * @return <code>false</code> if the merge will fail because the index entry
	 *         didn't match ours or the working-dir file was dirty and a
	 *         conflict occurred
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws CorruptObjectException
	 * @throws IOException
	 * @since 6.1
	 */
	protected boolean processEntry(CanonicalTreeParser base,
			CanonicalTreeParser ours, CanonicalTreeParser theirs,
			DirCacheBuildIterator index, WorkingTreeIterator work,
			boolean ignoreConflicts, Attributes[] attributes,
			boolean isRenameProcessing) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		enterSubtree = true;
		final int modeO = tw.getRawMode(T_OURS);
		final int modeT = tw.getRawMode(T_THEIRS);
		final int modeB = tw.getRawMode(T_BASE);
		boolean gitLinkMerging = isGitLink(modeO) || isGitLink(modeT)
				|| isGitLink(modeB);
		if (modeO == 0 && modeT == 0 && modeB == 0)
			// File is either untracked or new, staged but uncommitted
			return true;

		if (isIndexDirty())
			return false;

		DirCacheEntry ourDce = oursDce(index);

		if (nonTree(modeO) && nonTree(modeT) && tw.idEqual(T_OURS, T_THEIRS)) {
			// OURS and THEIRS have equal content. Check the file mode
			if (modeO == modeT) {
				// content and mode of OURS and THEIRS are equal: it doesn't
				// matter which one we choose. OURS is chosen. Since the index
				// is clean (the index matches already OURS) we can keep the existing one
				addOursToIndex(ourDce, ours, attributes, isRenameProcessing);
				// no checkout needed!
				return true;
			}
			// same content but different mode on OURS and THEIRS.
			// Try to merge the mode and report an error if this is
			// not possible.
			int newMode = mergeFileModes(modeB, modeO, modeT);
			if (newMode != FileMode.MISSING.getBits()) {
				if (newMode == modeO) {
					// ours version is preferred
					addOursToIndex(ourDce, ours, attributes,
							isRenameProcessing);
				} else {
					// the preferred version THEIRS has a different mode
					// than ours. Check it out!
					if (isWorktreeDirty(work, ourDce)) {
						return false;
					}
					addToIndexAndCheckout(theirs, attributes);
				}
				return true;
			}
			if (!ignoreConflicts) {
				// FileModes are not mergeable. We found a conflict on modes.
				// For conflicting entries we don't know lastModified and
				// length.
				// This path can be skipped on ignoreConflicts, so the caller
				// could use virtual commit.
				add(tw.getRawPath(), base, DirCacheEntry.STAGE_1, EPOCH, 0);
				add(tw.getRawPath(), ours, DirCacheEntry.STAGE_2, EPOCH, 0);
				add(tw.getRawPath(), theirs, DirCacheEntry.STAGE_3, EPOCH, 0);
				unmergedPaths.add(tw.getPathString());
				mergeResults.put(tw.getPathString(),
						new MergeResult<>(Collections.emptyList()));
			}
			return true;
		}

		if (modeB == modeT && tw.idEqual(T_BASE, T_THEIRS)) {
			// THEIRS was not changed compared to BASE. All changes must be in
			// OURS. OURS is chosen. We can keep the existing entry.
			if (ourDce != null)
				addOursToIndex(ourDce, ours, attributes, isRenameProcessing);
			// no checkout needed!
			return true;
		}

		if (modeB == modeO && tw.idEqual(T_BASE, T_OURS)) {
			// OURS was not changed compared to BASE. All changes must be in
			// THEIRS. THEIRS is chosen.

			// Check worktree before checking out THEIRS
			if (isWorktreeDirty(work, ourDce))
				return false;
			if (nonTree(modeT)) {
				addToIndexAndCheckout(theirs, attributes);
				return true;
			}
			// we want THEIRS ... but THEIRS contains a folder or the
			// deletion of the path. Delete what's in the working tree,
			// which we know to be clean.
			if (tw.getTreeCount() > T_FILE && tw.getRawMode(T_FILE) == 0) {
				// Not present in working tree, so nothing to delete
				return true;
			}
			if (modeT != 0 && modeT == modeB) {
				// Base, ours, and theirs all contain a folder: don't delete
				return true;
			}
			addDeletion(tw.getPathString(), nonTree(modeO), attributes[T_OURS]);
			return true;
		}

		if (tw.isSubtree()) {
			// file/folder conflicts: here I want to detect only file/folder
			// conflict between ours and theirs. file/folder conflicts between
			// base/index/workingTree and something else are not relevant or
			// detected later
			if (nonTree(modeO) != nonTree(modeT)) {
				if (ignoreConflicts) {
					// In case of merge failures, ignore this path instead of reporting unmerged, so
					// a caller can use virtual commit. This will not result in files with conflict
					// markers in the index/working tree. The actual diff on the path will be
					// computed directly on children.
					enterSubtree = false;
					return true;
				}
				if (nonTree(modeB))
					add(tw.getRawPath(), base, DirCacheEntry.STAGE_1, EPOCH, 0);
				if (nonTree(modeO))
					add(tw.getRawPath(), ours, DirCacheEntry.STAGE_2, EPOCH, 0);
				if (nonTree(modeT))
					add(tw.getRawPath(), theirs, DirCacheEntry.STAGE_3, EPOCH, 0);
				unmergedPaths.add(tw.getPathString());
				enterSubtree = false;
				return true;
			}

			// ours and theirs are both folders or both files (and treewalk
			// tells us we are in a subtree because of index or working-dir).
			// If they are both folders no content-merge is required - we can
			// return here.
			if (!nonTree(modeO))
				return true;

			// ours and theirs are both files, just fall out of the if block
			// and do the content merge
		}

		if (nonTree(modeO) && nonTree(modeT)) {
			// Check worktree before modifying files
			boolean worktreeDirty = isWorktreeDirty(work, ourDce);
			if (!attributes[T_OURS].canBeContentMerged() && worktreeDirty) {
				return false;
			}

			if (gitLinkMerging && ignoreConflicts) {
				// Always select 'ours' in case of GITLINK merge failures so
				// a caller can use virtual commit.
				add(tw.getRawPath(), ours, DirCacheEntry.STAGE_0, EPOCH, 0);
				return true;
			} else if (gitLinkMerging) {
				add(tw.getRawPath(), base, DirCacheEntry.STAGE_1, EPOCH, 0);
				add(tw.getRawPath(), ours, DirCacheEntry.STAGE_2, EPOCH, 0);
				add(tw.getRawPath(), theirs, DirCacheEntry.STAGE_3, EPOCH, 0);
				MergeResult<SubmoduleConflict> result = createGitLinksMergeResult(
						base, ours, theirs);
				result.setContainsConflicts(true);
				mergeResults.put(tw.getPathString(), result);
				unmergedPaths.add(tw.getPathString());
				return true;
			} else if (!attributes[T_OURS].canBeContentMerged()) {
				// File marked as binary
				switch (getContentMergeStrategy()) {
				case OURS:
					keep(ourDce);
					return true;
				case THEIRS:
					DirCacheEntry theirEntry = add(tw.getRawPath(), theirs,
							DirCacheEntry.STAGE_0, EPOCH, 0);
					addToCheckout(tw.getPathString(), theirEntry, attributes);
					return true;
				default:
					break;
				}
				add(tw.getRawPath(), base, DirCacheEntry.STAGE_1, EPOCH, 0);
				add(tw.getRawPath(), ours, DirCacheEntry.STAGE_2, EPOCH, 0);
				add(tw.getRawPath(), theirs, DirCacheEntry.STAGE_3, EPOCH, 0);

				// attribute merge issues are conflicts but not failures
				unmergedPaths.add(tw.getPathString());
				return true;
			}

			// Check worktree before modifying files
			if (worktreeDirty) {
				return false;
			}

			MergeResult<RawText> result = null;
			try {
				result = contentMerge(base, ours, theirs, attributes,
						getContentMergeStrategy());
			} catch (BinaryBlobException e) {
				switch (getContentMergeStrategy()) {
				case OURS:
					keep(ourDce);
					return true;
				case THEIRS:
					addToIndexAndCheckout(theirs, attributes);
					return true;
				default:
					result = new MergeResult<>(Collections.emptyList());
					result.setContainsConflicts(true);
					break;
				}
			}
			if (ignoreConflicts) {
				result.setContainsConflicts(false);
			}
			updateIndex(base, ours, theirs, result, attributes[T_OURS]);
			String currentPath = tw.getPathString();
			if (result.containsConflicts() && !ignoreConflicts) {
				unmergedPaths.add(currentPath);
			}
			modifiedFiles.add(currentPath);
			addCheckoutMetadata(cleanupMetadata, currentPath,
					attributes[T_OURS]);
			addCheckoutMetadata(checkoutMetadata, currentPath,
					attributes[T_THEIRS]);
		} else if (modeO != modeT) {
			// OURS or THEIRS has been deleted
			if (((modeO != 0 && !tw.idEqual(T_BASE, T_OURS)) || (modeT != 0 && !tw
					.idEqual(T_BASE, T_THEIRS)))) {
				if (gitLinkMerging && ignoreConflicts) {
					add(tw.getRawPath(), ours, DirCacheEntry.STAGE_0, EPOCH, 0);
				} else if (gitLinkMerging) {
					add(tw.getRawPath(), base, DirCacheEntry.STAGE_1, EPOCH, 0);
					add(tw.getRawPath(), ours, DirCacheEntry.STAGE_2, EPOCH, 0);
					add(tw.getRawPath(), theirs, DirCacheEntry.STAGE_3, EPOCH, 0);
					MergeResult<SubmoduleConflict> result = createGitLinksMergeResult(
							base, ours, theirs);
					result.setContainsConflicts(true);
					mergeResults.put(tw.getPathString(), result);
					unmergedPaths.add(tw.getPathString());
				} else {
					// Content merge strategy does not apply to delete-modify
					// conflicts!
					MergeResult<RawText> result;
					try {
						result = contentMerge(base, ours, theirs, attributes,
								ContentMergeStrategy.CONFLICT);
					} catch (BinaryBlobException e) {
						result = new MergeResult<>(Collections.emptyList());
						result.setContainsConflicts(true);
					}
					if (ignoreConflicts) {
						// In case a conflict is detected the working tree file
						// is again filled with new content (containing conflict
						// markers). But also stage 0 of the index is filled
						// with that content.
						result.setContainsConflicts(false);
						updateIndex(base, ours, theirs, result,
								attributes[T_OURS]);
					} else {
						add(tw.getRawPath(), base, DirCacheEntry.STAGE_1, EPOCH,
								0);
						add(tw.getRawPath(), ours, DirCacheEntry.STAGE_2, EPOCH,
								0);
						DirCacheEntry e = add(tw.getRawPath(), theirs,
								DirCacheEntry.STAGE_3, EPOCH, 0);

						// OURS was deleted checkout THEIRS
						if (modeO == 0) {
							// Check worktree before checking out THEIRS
							if (isWorktreeDirty(work, ourDce)) {
								return false;
							}
							if (nonTree(modeT) && e != null) {
								addToCheckout(tw.getPathString(), e,
										attributes);
							}
						}

						unmergedPaths.add(tw.getPathString());

						// generate a MergeResult for the deleted file
						// TODO: index is not populated with the merge result
						// anyway, why is it needed?
						mergeResults.put(tw.getPathString(), result);
					}
				}
			}
		}
		return true;
	}

	private DirCacheEntry oursDce(DirCacheBuildIterator index) {
		DirCacheEntry ourDce = null;
		if (index == null || index.getDirCacheEntry() == null) {
			// create a fake DCE, but only if ours is valid. ours is kept only
			// in case it is valid, so a null ourDce is ok in all other cases.
			if (nonTree(tw.getRawMode(T_OURS))) {
				ourDce = new DirCacheEntry(tw.getRawPath(T_OURS));
				ourDce.setObjectId(tw.getObjectId(T_OURS));
				ourDce.setFileMode(tw.getFileMode(T_OURS));
			}
		} else {
			ourDce = index.getDirCacheEntry();
		}
		return ourDce;
	}

	private static MergeResult<SubmoduleConflict> createGitLinksMergeResult(
			CanonicalTreeParser base, CanonicalTreeParser ours,
			CanonicalTreeParser theirs) {
		return new MergeResult<>(Arrays.asList(
				new SubmoduleConflict(
						base == null ? null : base.getEntryObjectId()),
				new SubmoduleConflict(
						ours == null ? null : ours.getEntryObjectId()),
				new SubmoduleConflict(
						theirs == null ? null : theirs.getEntryObjectId())));
	}

	/**
	 * Does the content merge. The three texts base, ours and theirs are
	 * specified with {@link CanonicalTreeParser}. If any of the parsers is
	 * specified as <code>null</code> then an empty text will be used instead.
	 *
	 * @param base
	 * @param ours
	 * @param theirs
	 * @param attributes
	 * @param strategy
	 *
	 * @return the result of the content merge
	 * @throws BinaryBlobException
	 *             if any of the blobs looks like a binary blob
	 * @throws IOException
	 */
	private MergeResult<RawText> contentMerge(CanonicalTreeParser base,
			CanonicalTreeParser ours, CanonicalTreeParser theirs,
			Attributes[] attributes, ContentMergeStrategy strategy)
			throws BinaryBlobException, IOException {
		// TW: The attributes here are used to determine the LFS smudge filter.
		// Is doing a content merge on LFS items really a good idea??
		RawText baseText = base == null ? RawText.EMPTY_TEXT
				: getRawText(base.getEntryObjectId(), attributes[T_BASE]);
		RawText ourText = ours == null ? RawText.EMPTY_TEXT
				: getRawText(ours.getEntryObjectId(), attributes[T_OURS]);
		RawText theirsText = theirs == null ? RawText.EMPTY_TEXT
				: getRawText(theirs.getEntryObjectId(), attributes[T_THEIRS]);
		mergeAlgorithm.setContentMergeStrategy(strategy);
		return mergeAlgorithm.merge(RawTextComparator.DEFAULT, baseText,
				ourText, theirsText);
	}

	private boolean isIndexDirty() {
		if (inCore)
			return false;

		final int modeI = tw.getRawMode(T_INDEX);
		final int modeO = tw.getRawMode(T_OURS);

		// Index entry has to match ours to be considered clean
		final boolean isDirty = nonTree(modeI)
				&& !(modeO == modeI && tw.idEqual(T_INDEX, T_OURS));
		if (isDirty)
			failingPaths
					.put(tw.getPathString(), MergeFailureReason.DIRTY_INDEX);
		return isDirty;
	}

	private boolean isWorktreeDirty() throws IOException {
		boolean hasWorkingTree = tw.getTreeCount() > T_FILE;
		WorkingTreeIterator work = tw.getTree(T_FILE,
				WorkingTreeIterator.class);
		DirCacheBuildIterator index = tw.getTree(T_INDEX,
				DirCacheBuildIterator.class);
		if (!hasWorkingTree) {
			return false;
		}
		DirCacheEntry oursDce = oursDce(index);
		return isWorktreeDirty(work, oursDce);

	}

	private boolean isWorktreeDirty(WorkingTreeIterator work,
			DirCacheEntry ourDce) throws IOException {
		if (work == null)
			return false;

		final int modeF = tw.getRawMode(T_FILE);
		final int modeO = tw.getRawMode(T_OURS);

		// Worktree entry has to match ours to be considered clean
		boolean isDirty;
		if (ourDce != null)
			isDirty = work.isModified(ourDce, true, reader);
		else {
			isDirty = work.isModeDifferent(modeO);
			if (!isDirty && nonTree(modeF))
				isDirty = !tw.idEqual(T_FILE, T_OURS);
		}

		// Ignore existing empty directories
		if (isDirty && modeF == FileMode.TYPE_TREE
				&& modeO == FileMode.TYPE_MISSING)
			isDirty = false;
		if (isDirty)
			failingPaths.put(tw.getPathString(),
					MergeFailureReason.DIRTY_WORKTREE);
		return isDirty;
	}

	/**
	 * Updates the index after a content merge has happened. If no conflict has
	 * occurred this includes persisting the merged content to the object
	 * database. In case of conflicts this method takes care to write the
	 * correct stages to the index.
	 *
	 * @param base
	 * @param ours
	 * @param theirs
	 * @param result
	 * @param attributes
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void updateIndex(CanonicalTreeParser base,
			CanonicalTreeParser ours, CanonicalTreeParser theirs,
			MergeResult<RawText> result, Attributes attributes)
			throws FileNotFoundException,
			IOException {
		TemporaryBuffer rawMerged = null;
		try {
			rawMerged = doMerge(result);
			File mergedFile = inCore ? null
					: writeMergedFile(rawMerged, attributes);
			if (result.containsConflicts()) {
				// A conflict occurred, the file will contain conflict markers
				// the index will be populated with the three stages and the
				// workdir (if used) contains the halfway merged content.
				add(tw.getRawPath(), base, DirCacheEntry.STAGE_1, EPOCH, 0);
				add(tw.getRawPath(), ours, DirCacheEntry.STAGE_2, EPOCH, 0);
				add(tw.getRawPath(), theirs, DirCacheEntry.STAGE_3, EPOCH, 0);
				mergeResults.put(tw.getPathString(), result);
				return;
			}

			// No conflict occurred, the file will contain fully merged content.
			// The index will be populated with the new merged version.
			DirCacheEntry dce = new DirCacheEntry(tw.getPathString());

			// Set the mode for the new content. Fall back to REGULAR_FILE if
			// we can't merge modes of OURS and THEIRS.
			int newMode = mergeFileModes(tw.getRawMode(0), tw.getRawMode(1),
					tw.getRawMode(2));
			dce.setFileMode(newMode == FileMode.MISSING.getBits()
					? FileMode.REGULAR_FILE : FileMode.fromBits(newMode));
			if (mergedFile != null) {
				dce.setLastModified(
						nonNullRepo().getFS().lastModifiedInstant(mergedFile));
				dce.setLength((int) mergedFile.length());
			}
			dce.setObjectId(insertMergeResult(rawMerged, attributes));
			builder.add(dce);
		} finally {
			if (rawMerged != null) {
				rawMerged.destroy();
			}
		}
	}

	/**
	 * Writes merged file content to the working tree.
	 *
	 * @param rawMerged
	 *            the raw merged content
	 * @param attributes
	 *            the files .gitattributes entries
	 * @return the working tree file to which the merged content was written.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private File writeMergedFile(TemporaryBuffer rawMerged,
			Attributes attributes)
			throws FileNotFoundException, IOException {
		File workTree = nonNullRepo().getWorkTree();
		FS fs = nonNullRepo().getFS();
		File of = new File(workTree, tw.getPathString());
		File parentFolder = of.getParentFile();
		if (!fs.exists(parentFolder)) {
			parentFolder.mkdirs();
		}
		EolStreamType streamType = EolStreamTypeUtil.detectStreamType(
				OperationType.CHECKOUT_OP, workingTreeOptions,
				attributes);
		try (OutputStream os = EolStreamTypeUtil.wrapOutputStream(
				new BufferedOutputStream(new FileOutputStream(of)),
				streamType)) {
			rawMerged.writeTo(os, null);
		}
		return of;
	}

	private TemporaryBuffer doMerge(MergeResult<RawText> result)
			throws IOException {
		LocalFile buf = new LocalFile(
				db != null ? nonNullRepo().getDirectory() : null, inCoreLimit);
		boolean success = false;
		try {
			new MergeFormatter().formatMerge(buf, result,
					Arrays.asList(commitNames), UTF_8);
			buf.close();
			success = true;
		} finally {
			if (!success) {
				buf.destroy();
			}
		}
		return buf;
	}

	private ObjectId insertMergeResult(TemporaryBuffer buf,
			Attributes attributes) throws IOException {
		InputStream in = buf.openInputStream();
		try (LfsInputStream is = LfsFactory.getInstance().applyCleanFilter(
				getRepository(), in,
				buf.length(), attributes.get(Constants.ATTR_MERGE))) {
			return getObjectInserter().insert(OBJ_BLOB, is.getLength(), is);
		}
	}

	/**
	 * Try to merge filemodes. If only ours or theirs have changed the mode
	 * (compared to base) we choose that one. If ours and theirs have equal
	 * modes return that one. If also that is not the case the modes are not
	 * mergeable. Return {@link FileMode#MISSING} int that case.
	 *
	 * @param modeB
	 *            filemode found in BASE
	 * @param modeO
	 *            filemode found in OURS
	 * @param modeT
	 *            filemode found in THEIRS
	 *
	 * @return the merged filemode or {@link FileMode#MISSING} in case of a
	 *         conflict
	 */
	private int mergeFileModes(int modeB, int modeO, int modeT) {
		if (modeO == modeT)
			return modeO;
		if (modeB == modeO)
			// Base equal to Ours -> chooses Theirs if that is not missing
			return (modeT == FileMode.MISSING.getBits()) ? modeO : modeT;
		if (modeB == modeT)
			// Base equal to Theirs -> chooses Ours if that is not missing
			return (modeO == FileMode.MISSING.getBits()) ? modeT : modeO;
		return FileMode.MISSING.getBits();
	}

	private RawText getRawText(ObjectId id,
			Attributes attributes)
			throws IOException, BinaryBlobException {
		if (id.equals(ObjectId.zeroId()))
			return new RawText(new byte[] {});

		ObjectLoader loader = LfsFactory.getInstance().applySmudgeFilter(
				getRepository(), reader.open(id, OBJ_BLOB),
				attributes.get(Constants.ATTR_MERGE));
		int threshold = PackConfig.DEFAULT_BIG_FILE_THRESHOLD;
		return RawText.load(loader, threshold);
	}

	private static boolean nonTree(int mode) {
		return mode != 0 && !FileMode.TREE.equals(mode);
	}

	private static boolean isGitLink(int mode) {
		return FileMode.GITLINK.equals(mode);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId getResultTreeId() {
		return (resultTree == null) ? null : resultTree.toObjectId();
	}

	/**
	 * Set the names of the commits as they would appear in conflict markers
	 *
	 * @param commitNames
	 *            the names of the commits as they would appear in conflict
	 *            markers
	 */
	public void setCommitNames(String[] commitNames) {
		this.commitNames = commitNames;
	}

	/**
	 * Get the names of the commits as they would appear in conflict markers.
	 *
	 * @return the names of the commits as they would appear in conflict
	 *         markers.
	 */
	public String[] getCommitNames() {
		return commitNames;
	}

	/**
	 * Get the paths with conflicts. This is a subset of the files listed by
	 * {@link #getModifiedFiles()}
	 *
	 * @return the paths with conflicts. This is a subset of the files listed by
	 *         {@link #getModifiedFiles()}
	 */
	public List<String> getUnmergedPaths() {
		return unmergedPaths;
	}

	/**
	 * Get the paths of files which have been modified by this merge.
	 *
	 * @return the paths of files which have been modified by this merge. A file
	 *         will be modified if a content-merge works on this path or if the
	 *         merge algorithm decides to take the theirs-version. This is a
	 *         superset of the files listed by {@link #getUnmergedPaths()}.
	 */
	public List<String> getModifiedFiles() {
		return modifiedFiles;
	}

	/**
	 * Get a map which maps the paths of files which have to be checked out
	 * because the merge created new fully-merged content for this file into the
	 * index.
	 *
	 * @return a map which maps the paths of files which have to be checked out
	 *         because the merge created new fully-merged content for this file
	 *         into the index. This means: the merge wrote a new stage 0 entry
	 *         for this path.
	 */
	public Map<String, DirCacheEntry> getToBeCheckedOut() {
		return toBeCheckedOut;
	}

	/**
	 * Get the mergeResults
	 *
	 * @return the mergeResults
	 */
	public Map<String, MergeResult<? extends Sequence>> getMergeResults() {
		return mergeResults;
	}

	/**
	 * Get list of paths causing this merge to fail (not stopped because of a
	 * conflict).
	 *
	 * @return lists paths causing this merge to fail (not stopped because of a
	 *         conflict). <code>null</code> is returned if this merge didn't
	 *         fail.
	 */
	public Map<String, MergeFailureReason> getFailingPaths() {
		return failingPaths.isEmpty() ? null : failingPaths;
	}

	/**
	 * Returns whether this merge failed (i.e. not stopped because of a
	 * conflict)
	 *
	 * @return <code>true</code> if a failure occurred, <code>false</code>
	 *         otherwise
	 */
	public boolean failed() {
		return !failingPaths.isEmpty();
	}

	/**
	 * Sets the DirCache which shall be used by this merger. If the DirCache is
	 * not set explicitly and if this merger doesn't work in-core, this merger
	 * will implicitly get and lock a default DirCache. If the DirCache is
	 * explicitly set the caller is responsible to lock it in advance. Finally
	 * the merger will call {@link DirCache#commit()}
	 * which requires that the DirCache is locked. If the {@link #mergeImpl()}
	 * returns without throwing an exception the lock will be released. In case
	 * of exceptions the caller is responsible to release the lock.
	 *
	 * @param dc
	 *            the DirCache to set
	 */
	public void setDirCache(DirCache dc) {
		this.dircache = dc;
		implicitDirCache = false;
	}

	/**
	 * Sets the WorkingTreeIterator to be used by this merger. If no
	 * WorkingTreeIterator is set this merger will ignore the working tree and
	 * fail if a content merge is necessary.
	 * <p>
	 * TODO: enhance WorkingTreeIterator to support write operations. Then this
	 * merger will be able to merge with a different working tree abstraction.
	 *
	 * @param workingTreeIterator
	 *            the workingTreeIt to set
	 */
	public void setWorkingTreeIterator(WorkingTreeIterator workingTreeIterator) {
		this.workingTreeIterator = workingTreeIterator;
	}


	/**
	 * The resolve conflict way of three way merging
	 *
	 * @param baseTree
	 *            an optional {@link RevTree} object.
	 * @param headTree
	 *            a {@link RevTree} object.
	 * @param mergeTree
	 *            a {@link RevTree} object.
	 * @param ignoreConflicts
	 *            Controls what to do in case a content-merge is done and a
	 *            conflict is detected. The default setting for this should be
	 *            <code>false</code>. In this case the working tree file is
	 *            filled with new content (containing conflict markers) and the
	 *            index is filled with multiple stages containing BASE, OURS and
	 *            THEIRS content. Having such non-0 stages is the sign to git
	 *            tools that there are still conflicts for that path.
	 *            <p>
	 *            If <code>true</code> is specified the behavior is different.
	 *            In case a conflict is detected the working tree file is again
	 *            filled with new content (containing conflict markers). But
	 *            also stage 0 of the index is filled with that content. No
	 *            other stages are filled. Means: there is no conflict on that
	 *            path but the new content (including conflict markers) is
	 *            stored as successful merge result. This is needed in the
	 *            context of {@link RecursiveMerger} where when determining
	 *            merge bases we don't want to deal with content-merge
	 *            conflicts.
	 * @return whether the trees merged cleanly
	 * @throws IOException
	 * @since 3.5
	 */
	protected boolean mergeTrees(@Nullable RevTree baseTree, RevTree headTree,
			RevTree mergeTree, boolean ignoreConflicts) throws IOException {

		builder = dircache.builder();

		if (detectRenames && baseTree != null) {
			// rename detection only works for tree-way merges
			renameResolver = new RenameResolver(db, reader, config, baseTree,
					headTree, mergeTree);
			tw = new RenameProcessingTreeWalk(db, reader, renameResolver,
					() -> isIndexDirty(), () -> isWorktreeDirty(),
					/* skipRenames= */true);
		} else {
			tw = new NameConflictTreeWalk(db, reader);
		}
		DirCacheBuildIterator buildIt = new DirCacheBuildIterator(builder);
		setUpMergeWalk(tw, baseTree, headTree, mergeTree, buildIt);
		if (!mergeTreeWalk(baseTree, headTree, mergeTree, tw,
				ignoreConflicts)) {
			return false;
		}

		if (!inCore) {
			// No problem found. The only thing left to be done is to
			// checkout all files from "theirs" which have been selected to
			// go into the new index.
			checkout();

			// All content-merges are successfully done. If we can now write the
			// new index we are on quite safe ground. Even if the checkout of
			// files coming from "theirs" fails the user can work around such
			// failures by checking out the index again.
			if (!builder.commit()) {
				cleanUp();
				throw new IndexWriteException();
			}
			builder = null;

		} else {
			builder.finish();
			builder = null;
		}

		if (getUnmergedPaths().isEmpty() && !failed()) {
			resultTree = dircache.writeTree(getObjectInserter());
			return true;
		}
		resultTree = null;
		return false;
	}

	/**
	 * Process the given TreeWalk's entries.
	 *
	 * @param baseTree
	 *            {@link #T_BASE} of the three-way merge.
	 * @param headTree
	 *            {@link #T_OURS} of the three-way merge.
	 * @param mergeTree
	 *            {@link #T_THEIRS} of the three-way merge.
	 * @param treeWalk
	 *            The walk to iterate over.
	 * @param ignoreConflicts
	 *            see
	 *            {@link ResolveMerger#mergeTrees(RevTree, RevTree, RevTree, boolean)}
	 * @return Whether the trees merged cleanly.
	 * @throws IOException
	 * @since 3.5
	 */
	protected boolean mergeTreeWalk(@Nullable RevTree baseTree, RevTree headTree,
			RevTree mergeTree, TreeWalk treeWalk, boolean ignoreConflicts)
			throws IOException {
		boolean hasWorkingTreeIterator = tw.getTreeCount() > T_FILE;
		boolean hasAttributeNodeProvider = treeWalk.hasAttributeNodeProvider();
		while (treeWalk.next()) {
			Attributes[] attributes = { NO_ATTRIBUTES, NO_ATTRIBUTES,
					NO_ATTRIBUTES };
			if (hasAttributeNodeProvider) {
				attributes[T_BASE] = treeWalk.getAttributes(T_BASE);
				attributes[T_OURS] = treeWalk.getAttributes(T_OURS);
				attributes[T_THEIRS] = treeWalk.getAttributes(T_THEIRS);
			}
			if (!processEntry(
					treeWalk.getTree(T_BASE, CanonicalTreeParser.class),
					treeWalk.getTree(T_OURS, CanonicalTreeParser.class),
					treeWalk.getTree(T_THEIRS, CanonicalTreeParser.class),
					treeWalk.getTree(T_INDEX, DirCacheBuildIterator.class),
					hasWorkingTreeIterator
							? treeWalk.getTree(T_FILE,
									WorkingTreeIterator.class)
							: null,
					ignoreConflicts, attributes,
					/* isRenameProcessing= */ false)) {
				cleanUp();
				return false;
			}
			if (treeWalk.isSubtree() && enterSubtree)
				treeWalk.enterSubtree();
		}
		if (tw instanceof RenameProcessingTreeWalk
				&& ((RenameProcessingTreeWalk) tw).isTerminated()) {
			cleanUp();
			return false;
		}

		if (detectRenames && baseTree != null) {
			// Renames were skipped by treeWalk, process them separately
			boolean success = processRenames(baseTree, headTree, mergeTree,
					ignoreConflicts);
			if (!success) {
				cleanUp();
				return false;
			}
		}

		return true;
	}

	private void setUpMergeWalk(TreeWalk treeWalk, @Nullable RevTree baseTree,
			RevTree headTree, RevTree mergeTree, DirCacheBuildIterator buildIt)
			throws IOException {
		treeWalk.addTree(baseTree == null ? new EmptyTreeIterator()
				: openTree(baseTree));
		treeWalk.setHead(treeWalk.addTree(headTree));
		treeWalk.addTree(mergeTree);
		int dciPos = treeWalk.addTree(buildIt);
		if (workingTreeIterator != null) {
			if (workingTreeIterator.eof()) {
				workingTreeIterator.reset();
			}
			treeWalk.addTree(workingTreeIterator);
			workingTreeIterator.setDirCacheIterator(treeWalk, dciPos);
		} else {
			treeWalk.setFilter(TreeFilter.ANY_DIFF);
		}
	}

	private DirCacheEntry addToIndexAndCheckout(CanonicalTreeParser p,
			Attributes attributes[]) throws IOException {
		// we know about length and lastMod only after we have
		// written the new content.
		// This will happen later. Set these values to 0 for know.
		// The entries, that do not exist in working tree need to be checked out
		// explicitly
		DirCacheEntry e = add(tw.getRawPath(), p, DirCacheEntry.STAGE_0, EPOCH,
				0);
		if (e != null) {
			addToCheckout(tw.getPathString(), e, attributes);
		}
		return e;
	}

	private DirCacheEntry addOursToIndex(DirCacheEntry oursEntry,
			CanonicalTreeParser ours, Attributes attributes[],
			boolean isRenameProcessing) throws IOException {

		if (!isRenameProcessing) {
			// Index is clean so we can just keep the entry in index
			return keep(oursEntry);
		}
		// this is a rename, but we want to keep ours, since the content was not
		// changed. Add the same entry with the new path to index and checkout.
		// The entry is also present in the working tree under the old name, so
		// we will need to remove it later on.

		return addToIndexAndCheckout(ours, attributes);
	}

	// Returns the walk positioned at the path with the correct attributes
	private TreeWalk getRenameWalk(Collection<RevTree> renameTrees, String path)
			throws IOException {
		TreeWalk renameWalk = new TreeWalk(db, reader);
		renameWalk.setAttributesNodeProvider(tw.getAttributesNodeProvider());
		for (RevTree tree : renameTrees) {
			renameWalk.addTree(tree);
		}
		// needed to retrieve the correct attributes?
		renameWalk.addTree(new DirCacheBuildIterator(builder, false));
		TreeWalk.walkToPath(renameWalk, path);
		return renameWalk;
	}

	private TreeWalk getRenameWalk(RevTree renameTree, String path)
			throws IOException {
		return getRenameWalk(List.of(renameTree), path);
	}

	/**
	 * Sets up the {@link RenameProcessingTreeWalk} for merging rename entries,
	 * by force-matching the entries at different path.
	 */
	/**
	 * Sets up the {@link RenameProcessingTreeWalk} for merging rename entries,
	 * by force-matching the entries at different path.
	 * 
	 * @param walk
	 *            {@link RenameProcessingTreeWalk}
	 * @param walkPath
	 *            path to position {@code walk} to.
	 * @param swapTreeNth
	 *            trees that need to be positioned to {@code swapPath} with
	 *            their indexes in {@code walk}.
	 * @param swapPath
	 *            path to position {@code swapTreeNth} to.
	 * @param attributes
	 *            {@link Attributes}
	 * @throws IOException
	 */
	private void setUpRenameWalk(RenameProcessingTreeWalk walk, String walkPath,
			LinkedHashMap<Integer, RevTree> swapTreeNth, String swapPath,
			Attributes[] attributes) throws IOException {

		// position to the matching path, then swap non-matching tree
		TreeWalk.walkToPath(walk, walkPath);

		boolean hasAttributeNodeProvider = walk.hasAttributeNodeProvider();
		if (hasAttributeNodeProvider) {
			attributes[T_BASE] = walk.getAttributes(T_BASE);
			attributes[T_OURS] = walk.getAttributes(T_OURS);
			attributes[T_THEIRS] = walk.getAttributes(T_THEIRS);
		}
		// Trees in swap walk has the same order as swapTreeNth.
		TreeWalk swapWalk = getRenameWalk(swapTreeNth.values(), swapPath);
		int i = 0;
		for (int swapNth : swapTreeNth.keySet()) {
			walk.swapTree(swapNth,
					swapWalk.getTree(i, CanonicalTreeParser.class));
			if (hasAttributeNodeProvider) {
				attributes[swapNth] = swapWalk.getAttributes(i);
			}
			i++;
		}
	}

	/**
	 * Process the entries, that belong to the renames.
	 *
	 * <p>
	 * Source and target rename paths are skipped during the first-phase of the
	 * merge execution.
	 * <p>
	 * They are merged by this method in the following way:
	 * <ul>
	 * <li>For each {@link RenameEntry}, the base and head/merge side entries
	 * are found in the corresponding trees</li>
	 * <li>The entries are content merged under the target rename path, using
	 * {@link #processEntry}
	 * <li>The old, "source" path is deleted, unless it was added in some other
	 * diff, unrelated to rename.</li>
	 * </ul>
	 *
	 * <p>
	 * This method currently creates a new {@link TreeWalk} for each
	 * {@link RenameEntry} and re-parses the trees, which is not optimal, but
	 * tolerable if the number of renames is not large.
	 * <p>
	 * This can be optimized with the directory rename detection implementation.
	 * @param baseTree
	 *            {@link #T_BASE} of the three-way merge.
	 * @param headTree
	 *            {@link #T_OURS} of the three-way merge.
	 * @param mergeTree
	 *            {@link #T_THEIRS} of the three-way merge.
	 * @param ignoreConflicts
	 *            see
	 *            {@link ResolveMerger#mergeTrees(RevTree, RevTree, RevTree, boolean)}
	 * @return Whether the trees merged cleanly.
	 * @throws IOException
	 */
	private boolean processRenames(RevTree baseTree, RevTree headTree,
			RevTree mergeTree, boolean ignoreConflicts) throws IOException {

		if (checkWorkTreeDirty(baseTree, headTree, mergeTree)) {
			return false;
		}
		for (Entry<String, RenameEntry> sourceRenameConflict : renameResolver
				.getSourceConflicts()) {
			reportRenameConflict(sourceRenameConflict.getValue(), baseTree,
					headTree, mergeTree, ignoreConflicts);
		}

		for (Entry<String, RenameEntry> baseRename : renameResolver
				.getRenamesNoConflicts()) {
			RenameType renameType = baseRename.getValue().getRenameType();
			RenameProcessingTreeWalk renameTw = new RenameProcessingTreeWalk(db,
					reader, renameResolver, () -> false, () -> false, false);
			// Reuse the already used dirCacheBuildIterator, since otherwise
			// entries are copied to DirCacheBuilder every time they are seen by
			// the treeWalk
			// We already walked the entire tree. We do not need to copy all
			// entries to builder again.
			DirCacheBuildIterator buildIt = new DirCacheBuildIterator(builder,
					false);
			setUpMergeWalk(renameTw, baseTree, headTree, mergeTree, buildIt);

			renameTw.setAttributesNodeProvider(tw.getAttributesNodeProvider());

			Attributes[] attributes = { NO_ATTRIBUTES, NO_ATTRIBUTES,
					NO_ATTRIBUTES };

			// Always position at our path
			if (renameType.equals(RenameType.RENAME_BOTH_NO_CONFLICT)) {
				// position at rename path, position base at the original path
				setUpRenameWalk(renameTw, baseRename.getValue().getTargetPath(),
						new LinkedHashMap<>() {
							{
								put(T_BASE, baseTree);
							}
						}, baseRename.getKey(), attributes);
			} else if (renameType.equals(RENAME_IN_OURS)) {
				// position at ours, swap theirs & base
				setUpRenameWalk(renameTw, baseRename.getValue().getTargetPath(),
						new LinkedHashMap<>() {
							{
								put(T_BASE, baseTree);
								put(T_THEIRS, mergeTree);
							}
						}, baseRename.getKey(), attributes);
			} else if (renameType.equals(RENAME_IN_THEIRS)) {
				// position at ours (=base), the rename side (theirs) will be
				// swapped
				setUpRenameWalk(renameTw, baseRename.getKey(),
						new LinkedHashMap<>() {
							{
								put(T_THEIRS, mergeTree);
							}
						}, baseRename.getValue().getTargetPath(), attributes);
			}

			renameTw.setPathName(baseRename.getValue().getTargetPath());
			tw = renameTw;
			boolean success = processEntry(
					renameTw.getTree(T_BASE, CanonicalTreeParser.class),
					renameTw.getTree(T_OURS, CanonicalTreeParser.class),
					renameTw.getTree(T_THEIRS, CanonicalTreeParser.class),
					renameTw.getTree(T_INDEX, DirCacheBuildIterator.class),
					renameTw.getTreeCount() > T_FILE
							? renameTw.getTree(T_FILE,
									WorkingTreeIterator.class)
							: null,
					ignoreConflicts, attributes,
					/* isRenameProcessing= */ true);
			if (!success) {
				return false;
			}
			updateWorkingTree(
					renameTw.getTree(T_OURS, CanonicalTreeParser.class), tw,
					attributes);
		}
		return true;
	}

	/**
	 * Updates the working three after the rename content merge happened. The
	 * new content was added under the target rename path to the builder, but
	 * the old path might still exist in working tree. It needs to be deleted
	 * explicitly.
	 * <p>
	 * We already checked the working tree is clean in
	 * {@link #checkWorkTreeDirty}
	 *
	 * @param ours
	 *            ours entry that might need to be deleted
	 * @param treeWalk
	 *            a TreeWalk used by the merger
	 * @param attributes
	 *            {@link Attributes}
	 * @throws IOException
	 */
	private void updateWorkingTree(AbstractTreeIterator ours, TreeWalk treeWalk,
			Attributes[] attributes) throws IOException {
		boolean hasWorkingTreeEntry = treeWalk.getTreeCount() > T_FILE
				&& treeWalk.getRawMode(T_FILE) != 0;
		if (!hasWorkingTreeEntry || ours == null) {
			return;
		}
		String oldPath = ours.getEntryPathString();
		if (!oldPath.equals(treeWalk.getPathString())
				&& !renameResolver.isTargetRename(oldPath)
				&& nonTree(ours.getEntryRawMode())) {
			addDeletion(ours.getEntryPathString(),
					nonTree(ours.getEntryRawMode()), attributes[T_OURS]);
		}
	}

	private void reportRenameConflict(RenameEntry renameEntry, RevTree base,
			RevTree ours, RevTree theirs, boolean ignoreConflicts)
			throws IOException {
		// TODO: this will always result in merge conflicts on criss-cross.
		// Consider if we can do anything better.
		if (ignoreConflicts) {
			return;
		}
		// Those are the only conflict that we process for now.
		// For all other types of conflicts we just switch off the rename
		// detection.
		if (renameEntry.getRenameConflict()
				.equals(RENAME_BOTH_SIDES_CONFLICT)) {
			reportRenameRenameConflict(renameEntry, base, ours, theirs);
		} else if (renameEntry.getRenameConflict()
				.equals(RENAME_DELETE_CONFLICT)) {
			reportRenameDeleteConflict(renameEntry, base, ours, theirs);
		}
	}

	/**
	 * Check that the paths, touched by renames, are clean before processing
	 * each rename.
	 * 
	 * @param baseTree
	 *            {@link #T_BASE} of the three-way merge.
	 * @param headTree
	 *            {@link #T_OURS} of the three-way merge.
	 * @param mergeTree
	 *            {@link #T_THEIRS} of the three-way merge.
	 * @return true if any of the path, that the merger will write to as a
	 *         result of the rename processing are dirty.
	 * @throws IOException
	 */
	private boolean checkWorkTreeDirty(RevTree baseTree, RevTree headTree,
			RevTree mergeTree) throws IOException {
		Set<String> renamePaths = new HashSet<>();
		for (Entry<String, RenameEntry> rename : renameResolver
				.getRenamesNoConflicts()) {
			if (rename.getValue().getRenameType().equals(RENAME_IN_THEIRS)) {
				// we will clean up this path in #updateWorkingTree
				renamePaths.add(rename.getKey());
			}
			// We will be writing to this path
			renamePaths.add(rename.getValue().getTargetPath());
		}
		for (Entry<String, RenameEntry> rename : renameResolver
				.getSourceConflicts()) {
			// we might write to the target paths when reporting the conflict,
			// they have to be clean.
			renamePaths.addAll(rename.getValue().getTargetPaths());
		}
		if (renamePaths.isEmpty()) {
			return false;
		}
		TreeFilter renamePathsFilter = PathFilterGroup
				.createFromStrings(renamePaths);
		TreeWalk mergeTreeWalk = new TreeWalk(db, reader);

		DirCacheBuildIterator buildIt = new DirCacheBuildIterator(builder,
				false);
		setUpMergeWalk(mergeTreeWalk, baseTree, headTree, mergeTree, buildIt);
		mergeTreeWalk.setFilter(renamePathsFilter);
		mergeTreeWalk.setAttributesNodeProvider(tw.getAttributesNodeProvider());

		tw = mergeTreeWalk;
		while (mergeTreeWalk.next()) {
			if (!checkIndexAndWorkTree(
					mergeTreeWalk.getTree(T_INDEX, DirCacheBuildIterator.class),
					workingTreeIterator != null
							? mergeTreeWalk.getTree(T_FILE,
									WorkingTreeIterator.class)
							: null)) {
				return true;
			}
		}
		return false;
	}

	private boolean checkIndexAndWorkTree(DirCacheBuildIterator index,
			WorkingTreeIterator work) throws IOException {
		DirCacheEntry ourDce = oursDce(index);
		return !isIndexDirty() && !isWorktreeDirty(work, ourDce);
	}

	private void reportRenameRenameConflict(RenameEntry renameEntry,
			RevTree base, RevTree ours, RevTree theirs) throws IOException {
		// the working tree will contain target paths and the index will contain
		// non-merged stages with the corresponding paths.
		MergeResult conflictResult = new MergeResult<>(Collections.emptyList());
		conflictResult.setContainsConflicts(true);
		TreeWalk baseWalk = getRenameWalk(base, renameEntry.getSourcePath());
		TreeWalk oursWalk = getRenameWalk(ours,
				renameEntry.getTargetPath(T_OURS));
		TreeWalk theirsWalk = getRenameWalk(theirs,
				renameEntry.getTargetPath(T_THEIRS));
		add(baseWalk.getRawPath(), baseWalk.getTree(CanonicalTreeParser.class),
				DirCacheEntry.STAGE_1, EPOCH, 0);
		add(oursWalk.getRawPath(), oursWalk.getTree(CanonicalTreeParser.class),
				DirCacheEntry.STAGE_2, EPOCH, 0);
		DirCacheEntry theirsEntry = add(theirsWalk.getRawPath(),
				theirsWalk.getTree(CanonicalTreeParser.class),
				DirCacheEntry.STAGE_3, EPOCH, 0);
		// TODO: It seems like C-GIT attempts content merge on rename files,
		// with both files populated with contents with conflict markers.
		// Consider if we need this behavior (e.g. so that the user does not
		// need to resolve content merge manually).
		if (theirsEntry != null && nonTree(theirsEntry.getRawMode())) {
			Attributes[] attributes = { NO_ATTRIBUTES, NO_ATTRIBUTES,
					NO_ATTRIBUTES };
			attributes[T_THEIRS] = theirsWalk.hasAttributeNodeProvider()
					? theirsWalk.getAttributes()
					: NO_ATTRIBUTES;
			// checkout is needed to make theirs available in the working tree
			addToCheckout(theirsEntry.getPathString(), theirsEntry, attributes);
		}
		unmergedPaths.add(renameEntry.getSourcePath());
		unmergedPaths.add(renameEntry.getTargetPath(T_OURS));
		unmergedPaths.add(renameEntry.getTargetPath(T_THEIRS));
		mergeResults.put(renameEntry.getSourcePath(), conflictResult);
		mergeResults.put(renameEntry.getTargetPath(T_OURS), conflictResult);
		mergeResults.put(renameEntry.getTargetPath(T_THEIRS), conflictResult);

	}

	private void reportRenameDeleteConflict(RenameEntry renameEntry,
			RevTree base, RevTree ours, RevTree theirs) throws IOException {
		// similar to delete/modify conflict, the rename/delete conflict: the
		// working tree is populated with the present file and the index with
		// the present stages, all under the target rename path
		int renameSide = renameEntry.getRenameType().equals(RENAME_IN_OURS)
				? T_OURS
				: T_THEIRS;
		MergeResult conflictResult = new MergeResult<>(Collections.emptyList());
		conflictResult.setContainsConflicts(true);
		TreeWalk baseWalk = getRenameWalk(base, renameEntry.getSourcePath());
		TreeWalk oursWalk = renameSide == T_OURS
				? getRenameWalk(ours, renameEntry.getTargetPath())
				: null;
		TreeWalk theirsWalk = renameSide == T_THEIRS
				? getRenameWalk(theirs, renameEntry.getTargetPath())
				: null;
		Attributes[] attributes = { NO_ATTRIBUTES, NO_ATTRIBUTES,
				NO_ATTRIBUTES };
		if (tw.hasAttributeNodeProvider()) {
			attributes[T_BASE] = baseWalk.getAttributes();
			attributes[T_OURS] = oursWalk != null ? oursWalk.getAttributes()
					: NO_ATTRIBUTES;
			attributes[T_THEIRS] = attributes[T_THEIRS] = theirsWalk != null
					? theirsWalk.getAttributes()
					: NO_ATTRIBUTES;
		}
		byte[] rawTargetPath = oursWalk != null ? oursWalk.getRawPath()
				: theirsWalk != null ? theirsWalk.getRawPath() : null;
		if (rawTargetPath == null) {
			// will never happen but needed to bypass the style check
			return;
		}
		add(rawTargetPath, baseWalk.getTree(CanonicalTreeParser.class),
				DirCacheEntry.STAGE_1, EPOCH, 0);
		if (oursWalk != null) {
			add(rawTargetPath, oursWalk.getTree(CanonicalTreeParser.class),
					DirCacheEntry.STAGE_2, EPOCH, 0);
		}
		if (theirsWalk != null) {
			DirCacheEntry theirsEntry = add(rawTargetPath,
					theirsWalk.getTree(CanonicalTreeParser.class),
					DirCacheEntry.STAGE_3, EPOCH, 0);
			if (theirsEntry != null && nonTree(theirsEntry.getRawMode())) {
				// checkout is needed to make theirs available in the working
				// tree
				addToCheckout(renameEntry.getTargetPath(), theirsEntry,
						attributes);
			}
		}
		unmergedPaths.add(renameEntry.getTargetPath());
		mergeResults.put(renameEntry.getTargetPath(), conflictResult);
	}

}
