/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.debug;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.ObjectWritingException;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefWriter;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * Recreates a repository from another one's commit graph.
 * <p>
 * <b>Do not run this on a repository unless you want to destroy it.</b>
 * <p>
 * To create the input files, in the source repository use:
 *
 * <pre>
 * git for-each-ref &gt;in.refs
 * git log --all '--pretty=format:%H %ct %P' &gt;in.dag
 * </pre>
 * <p>
 * Run the rebuild in either an empty repository, or a clone of the source. Any
 * missing commits (which might be the entire graph) will be created. All refs
 * will be modified to match the input exactly, which means some refs may be
 * deleted from the current repository.
 * <p>
 */
@Command(usage = "usage_RebuildCommitGraph")
class RebuildCommitGraph extends TextBuiltin {
	private static final String REALLY = "--destroy-this-repository"; //$NON-NLS-1$

	@Option(name = REALLY, usage = "usage_approveDestructionOfRepository")
	boolean really;

	@Argument(index = 0, required = true, metaVar = "metaVar_refs", usage = "usage_forEachRefOutput")
	File refList;

	@Argument(index = 1, required = true, metaVar = "metaVar_refs", usage = "usage_logAllPretty")
	File graph;

	private final ProgressMonitor pm = new TextProgressMonitor(errw);

	private Map<ObjectId, ObjectId> rewrites = new HashMap<>();

	@Override
	protected void run() throws Exception {
		if (!really && db.getRefDatabase().hasRefs()) {
			File directory = db.getDirectory();
			String absolutePath = directory == null ? "null" //$NON-NLS-1$
					: directory.getAbsolutePath();
			errw.println(
				MessageFormat.format(CLIText.get().fatalThisProgramWillDestroyTheRepository
					, absolutePath, REALLY));
			throw die(CLIText.get().needApprovalToDestroyCurrentRepository);
		}
		if (!refList.isFile())
			throw die(MessageFormat.format(CLIText.get().noSuchFile, refList.getPath()));
		if (!graph.isFile())
			throw die(MessageFormat.format(CLIText.get().noSuchFile, graph.getPath()));

		recreateCommitGraph();
		detachHead();
		deleteAllRefs();
		recreateRefs();
	}

	private void recreateCommitGraph() throws IOException {
		final Map<ObjectId, ToRewrite> toRewrite = new HashMap<>();
		List<ToRewrite> queue = new ArrayList<>();
		try (RevWalk rw = new RevWalk(db);
				final BufferedReader br = new BufferedReader(
						new InputStreamReader(new FileInputStream(graph),
								UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				final String[] parts = line.split("[ \t]{1,}"); //$NON-NLS-1$
				final ObjectId oldId = ObjectId.fromString(parts[0]);
				try {
					rw.parseCommit(oldId);
					// We have it already. Don't rewrite it.
					continue;
				} catch (MissingObjectException mue) {
					// Fall through and rewrite it.
				}

				final long time = Long.parseLong(parts[1]) * 1000L;
				final ObjectId[] parents = new ObjectId[parts.length - 2];
				for (int i = 0; i < parents.length; i++) {
					parents[i] = ObjectId.fromString(parts[2 + i]);
				}

				final ToRewrite t = new ToRewrite(oldId, time, parents);
				toRewrite.put(oldId, t);
				queue.add(t);
			}
		}

		pm.beginTask("Rewriting commits", queue.size()); //$NON-NLS-1$
		try (ObjectInserter oi = db.newObjectInserter()) {
			final ObjectId emptyTree = oi.insert(Constants.OBJ_TREE,
					new byte[] {});
			final PersonIdent me = new PersonIdent("jgit rebuild-commitgraph", //$NON-NLS-1$
					"rebuild-commitgraph@localhost"); //$NON-NLS-1$
			while (!queue.isEmpty()) {
				final ListIterator<ToRewrite> itr = queue
						.listIterator(queue.size());
				queue = new ArrayList<>();
				REWRITE: while (itr.hasPrevious()) {
					final ToRewrite t = itr.previous();
					final ObjectId[] newParents = new ObjectId[t.oldParents.length];
					for (int k = 0; k < t.oldParents.length; k++) {
						final ToRewrite p = toRewrite.get(t.oldParents[k]);
						if (p != null) {
							if (p.newId == null) {
								// Must defer until after the parent is
								// rewritten.
								queue.add(t);
								continue REWRITE;
							}
							newParents[k] = p.newId;
						} else {
							// We have the old parent object. Use it.
							//
							newParents[k] = t.oldParents[k];
						}
					}

					final CommitBuilder newc = new CommitBuilder();
					newc.setTreeId(emptyTree);
					newc.setAuthor(new PersonIdent(me,
							Instant.ofEpochSecond(t.commitTime)));
					newc.setCommitter(newc.getAuthor());
					newc.setParentIds(newParents);
					newc.setMessage("ORIGINAL " + t.oldId.name() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
					t.newId = oi.insert(newc);
					rewrites.put(t.oldId, t.newId);
					pm.update(1);
				}
			}
			oi.flush();
		}
		pm.endTask();
	}

	private static class ToRewrite {
		final ObjectId oldId;

		final long commitTime;

		final ObjectId[] oldParents;

		ObjectId newId;

		ToRewrite(ObjectId o, long t, ObjectId[] p) {
			oldId = o;
			commitTime = t;
			oldParents = p;
		}
	}

	private void detachHead() throws IOException {
		final String head = db.getFullBranch();
		final ObjectId id = db.resolve(Constants.HEAD);
		if (!ObjectId.isId(head) && id != null) {
			final LockFile lf;
			lf = new LockFile(new File(db.getDirectory(), Constants.HEAD));
			if (!lf.lock())
				throw new IOException(MessageFormat.format(CLIText.get().cannotLock, Constants.HEAD));
			lf.write(id);
			if (!lf.commit())
				throw new IOException(CLIText.get().cannotDeatchHEAD);
		}
	}

	private void deleteAllRefs() throws Exception {
		final RevWalk rw = new RevWalk(db);
		for (Ref r : db.getRefDatabase().getRefs()) {
			if (Constants.HEAD.equals(r.getName()))
				continue;
			final RefUpdate u = db.updateRef(r.getName());
			u.setForceUpdate(true);
			u.delete(rw);
		}
	}

	private void recreateRefs() throws Exception {
		final Map<String, Ref> refs = computeNewRefs();
		new RefWriter(refs.values()) {
			@Override
			protected void writeFile(String name, byte[] content)
					throws IOException {
				final File file = new File(db.getDirectory(), name);
				final LockFile lck = new LockFile(file);
				if (!lck.lock())
					throw new ObjectWritingException(MessageFormat.format(CLIText.get().cantWrite, file));
				try {
					lck.write(content);
				} catch (IOException ioe) {
					throw new ObjectWritingException(
							MessageFormat.format(CLIText.get().cantWrite, file),
							ioe);
				}
				if (!lck.commit())
					throw new ObjectWritingException(MessageFormat.format(CLIText.get().cantWrite, file));
			}
		}.writePackedRefs();
	}

	private Map<String, Ref> computeNewRefs() throws IOException {
		final Map<String, Ref> refs = new HashMap<>();
		try (RevWalk rw = new RevWalk(db);
				BufferedReader br = new BufferedReader(
						new InputStreamReader(new FileInputStream(refList),
								UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				final String[] parts = line.split("[ \t]{1,}"); //$NON-NLS-1$
				final ObjectId origId = ObjectId.fromString(parts[0]);
				final String type = parts[1];
				final String name = parts[2];

				ObjectId id = rewrites.get(origId);
				if (id == null)
					id = origId;
				try {
					rw.parseAny(id);
				} catch (MissingObjectException mue) {
					if (!Constants.TYPE_COMMIT.equals(type)) {
						errw.println(MessageFormat.format(CLIText.get().skippingObject, type, name));
						continue;
					}
					MissingObjectException mue1 = new MissingObjectException(id, type);
					mue1.initCause(mue);
					throw mue1;
				}
				refs.put(name, new ObjectIdRef.Unpeeled(Ref.Storage.PACKED,
						name, id));
			}
		}
		return refs;
	}
}
