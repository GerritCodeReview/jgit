/*
 * Copyright (C) 2012, Roberto Tyley <roberto.tyley@gmail.com>
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.Mockito;

public class ObjectDirectoryTest extends RepositoryTestCase {

	@Test
	public void testConcurrentInsertionOfBlobsToTheSameNewFanOutDirectory()
			throws Exception {
		ExecutorService e = Executors.newCachedThreadPool();
		for (int i=0; i < 100; ++i) {
			ObjectDirectory dir = createBareRepository().getObjectDatabase();
			for (Future f : e.invokeAll(blobInsertersForTheSameFanOutDir(dir))) {
				f.get();
			}
		}
	}

	/**
	 * Test packfile scanning while a gc is done from the outside (different
	 * process or different Repository instance). This situation occurs e.g. if
	 * a gerrit server is serving fetch requests while native git is doing a
	 * garbage collection. The test shows that when core.trustfolderstat==true
	 * jgit may miss to detect that a new packfile was created. This situation
	 * is persistent until a new full rescan of the pack directory is triggered.
	 *
	 * The test works with two Repository instances working on the same disk
	 * location. One (db) for all write operations (creating commits, doing gc)
	 * and another one (receivingDB) which just reads and which in the end shows
	 * the bug
	 *
	 * @throws Exception
	 */
	@Test
	public void testScanningForPackfiles() throws Exception {
		ObjectId unknownID = ObjectId
				.fromString("c0ffee09d0b63d694bf49bc1e6847473f42d4a8c");
		GC gc = new GC(db);
		gc.setExpireAgeMillis(0);
		gc.setPackExpireAgeMillis(0);

		// the default repo db is used to create the objects. The receivingDB
		// repo is used to trigger gc's
		try (FileRepository receivingDB = new FileRepository(
				db.getDirectory())) {
			// set trustfolderstat to true. If set to false the test always
			// succeeds.
			FileBasedConfig cfg = receivingDB.getConfig();
			cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT, true);
			cfg.save();

			// setup a repo which has at least one pack file and trigger
			// scanning of the packs directory
			ObjectId id = commitFile("file.txt", "test", "master").getId();
			gc.gc().get();
			assertFalse(receivingDB.getObjectDatabase().has(unknownID));
			assertTrue(receivingDB.getObjectDatabase().hasPackedObject(id));

			// preparations
			File packsFolder = receivingDB.getObjectDatabase()
					.getPackDirectory();
			// prepare creation of a temporary file in the pack folder. This
			// simulates that a native git gc is happening starting to write
			// temporary files but has not yet finished
			File tmpFile = new File(packsFolder, "1.tmp");
			RevCommit id2 = commitFile("file.txt", "test2", "master");
			// wait until filesystem timer ticks. This raises probability that
			// the next statements are executed in the same tick as the
			// filesystem timer
			fsTick(null);

			// create a Temp file in the packs folder and trigger a rescan of
			// the packs folder. This lets receivingDB think it has scanned the
			// packs folder at the current fs timestamp t1. The following gc
			// will create new files which have the same timestamp t1 but this
			// will not update the mtime of the packs folder. Because of that
			// JGit will not rescan the packs folder later on and fails to see
			// the pack file created during gc.
			assertTrue(tmpFile.createNewFile());
			assertFalse(receivingDB.getObjectDatabase().has(unknownID));

			// trigger a gc. This will create packfiles which have likely the
			// same mtime than the packfolder
			gc.gc().get();

			// To deal with racy-git situations JGit's Filesnapshot class will
			// report a file/folder potentially dirty if
			// cachedLastReadTime-cachedLastModificationTime < filesystem
			// timestamp resolution. This causes JGit to always rescan a file
			// after modification. But: this was true only if the difference
			// between current system time and cachedLastModification time was
			// less than 2500ms. If the modification is more than 2500ms ago we
			// may have reported a file/folder to be clean although it has not
			// been rescanned. A bug. To show the bug we sleep for more than
			// 2500ms
			Thread.sleep(2600);

			File[] ret = packsFolder.listFiles(
					(File dir, String name) -> name.endsWith(".pack"));
			assertTrue(ret != null && ret.length == 1);
			FS fs = db.getFS();
			Assume.assumeTrue(fs.lastModifiedInstant(tmpFile)
					.equals(fs.lastModifiedInstant(ret[0])));

			// all objects are in a new packfile but we will not detect it
			assertFalse(receivingDB.getObjectDatabase().has(unknownID));
			assertTrue(receivingDB.getObjectDatabase().has(id2));
		}
	}

	@Test
	public void testShallowFile()
			throws Exception {
		FileRepository repository = createBareRepository();
		ObjectDirectory dir = repository.getObjectDatabase();

		String commit = "d3148f9410b071edd4a4c85d2a43d1fa2574b0d2";
		try (PrintWriter writer = new PrintWriter(
				new File(repository.getDirectory(), Constants.SHALLOW),
				UTF_8.name())) {
			writer.println(commit);
		}
		Set<ObjectId> shallowCommits = dir.getShallowCommits();
		assertTrue(shallowCommits.remove(ObjectId.fromString(commit)));
		assertTrue(shallowCommits.isEmpty());
	}

	@Test
	public void testOpenLooseObjectSuppressStaleFileHandleException()
			throws Exception {
		ObjectId id = ObjectId
				.fromString("873fb8d667d05436d728c52b1d7a09528e6eb59b");
		WindowCursor curs = new WindowCursor(db.getObjectDatabase());

		Config config = new Config();
		config.setString("core", null, "trustLooseObjectStat", "ALWAYS");
		LooseObjects spy = Mockito.spy(new LooseObjects(config, trash));
		UnpackedObjectCache unpackedObjectCacheMock = mock(
				UnpackedObjectCache.class);

		doThrow(new IOException("Stale File Handle")).when(spy)
				.getObjectLoader(any(), any(), any());
		doReturn(unpackedObjectCacheMock).when(spy).unpackedObjectCache();

		assertNull(spy.open(curs, id));
		verify(unpackedObjectCacheMock).remove(id);
	}

	@Test(expected = IOException.class)
	public void testOpenLooseObjectPropagatesIOExceptions() throws Exception {
		ObjectId id = ObjectId
				.fromString("873fb8d667d05436d728c52b1d7a09528e6eb59b");
		WindowCursor curs = new WindowCursor(db.getObjectDatabase());

		Config config = new Config();
		config.setString("core", null, "trustLooseObjectStat", "NEVER");
		LooseObjects spy = spy(new LooseObjects(config,
				db.getObjectDatabase().getDirectory()));

		doThrow(new IOException("some IO failure")).when(spy)
				.getObjectLoader(any(), any(), any());

		spy.open(curs, id);
	}

	@Test
	public void testWindowCursorGetCommitGraph() throws Exception {
		db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, true);
		db.getConfig().setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, true);

		try (WindowCursor curs = new WindowCursor(db.getObjectDatabase())) {
			assertTrue(curs.getCommitGraph().isEmpty());
			commitFile("file.txt", "content", "master");
			GC gc = new GC(db);
			gc.gc().get();
			assertTrue(curs.getCommitGraph().isPresent());

			db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_COMMIT_GRAPH, false);

			assertTrue(curs.getCommitGraph().isEmpty());
		}
	}

	@Test
	public void testShallowFileCorrupt() throws Exception {
		FileRepository repository = createBareRepository();
		ObjectDirectory dir = repository.getObjectDatabase();

		String commit = "X3148f9410b071edd4a4c85d2a43d1fa2574b0d2";
		try (PrintWriter writer = new PrintWriter(
				new File(repository.getDirectory(), Constants.SHALLOW),
				UTF_8.name())) {
			writer.println(commit);
		}
		assertThrows(
				MessageFormat.format(JGitText.get().badShallowLine, commit),
				IOException.class, () -> dir.getShallowCommits());
	}

	@Test
	public void testGetCommitGraph() throws Exception {
		db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, true);
		db.getConfig().setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, true);
		db.getConfig().save();

		// no commit-graph
		ObjectDirectory dir = db.getObjectDatabase();
		assertTrue(dir.getCommitGraph().isEmpty());

		// add commit-graph
		commitFile("file.txt", "content", "master");
		GC gc = new GC(db);
		gc.gc().get();
		File file = new File(db.getObjectsDirectory(),
				Constants.INFO_COMMIT_GRAPH);
		assertTrue(file.exists());
		assertTrue(file.isFile());
		assertTrue(dir.getCommitGraph().isPresent());
		assertEquals(1, dir.getCommitGraph().get().getCommitCnt());

		// get commit-graph in a newly created db
		try (FileRepository repo2 = new FileRepository(db.getDirectory())) {
			ObjectDirectory dir2 = repo2.getObjectDatabase();
			assertTrue(dir2.getCommitGraph().isPresent());
			assertEquals(1, dir2.getCommitGraph().get().getCommitCnt());
		}

		// update commit-graph
		commitFile("file2.txt", "content", "master");
		gc.gc().get();
		assertEquals(2, dir.getCommitGraph().get().getCommitCnt());

		// delete commit-graph
		file.delete();
		assertFalse(file.exists());
		assertTrue(dir.getCommitGraph().isEmpty());

		// commit-graph is corrupt
		try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
			writer.println("this is a corrupt commit-graph");
		}
		assertTrue(dir.getCommitGraph().isEmpty());

		// add commit-graph again
		gc.gc().get();
		assertTrue(dir.getCommitGraph().isPresent());
		assertEquals(2, dir.getCommitGraph().get().getCommitCnt());
	}

	private Collection<Callable<ObjectId>> blobInsertersForTheSameFanOutDir(
			final ObjectDirectory dir) {
		Callable<ObjectId> callable = () -> dir.newInserter()
				.insert(Constants.OBJ_BLOB, new byte[0]);
		return Collections.nCopies(4, callable);
	}

}
