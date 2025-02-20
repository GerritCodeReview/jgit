/*
 * Copyright (C) 2025, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.blame;

import static java.lang.String.join;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.blame.cache.CacheRegion;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class BlameGeneratorCacheTest extends RepositoryTestCase {
	private static final String FILE = "file.txt";

	@Test
	public void blame_simple_correctRegions() throws Exception {
		RevCommit c1, c2, c3, c4;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			c1 = commit(r, lines("first", "second", "third"));
			c2 = commit(r, lines("first", "second", "third", "fourth"), c1);
			c3 = commit(r, lines("first", "other2", "other3", "fourth"), c2);
			c4 = commit(r, lines("first", "other22", "other3", "other4"), c3);
		}

		List<EmittedRegion> expectedRegions = Arrays.asList(
				new EmittedRegion(c1, 0, 1), new EmittedRegion(c4, 1, 2),
				new EmittedRegion(c3, 2, 3), new EmittedRegion(c4, 3, 4));

		assertRegions(c4, null, expectedRegions, 4);
		assertRegions(c4, emptyCache(), expectedRegions, 4);
		assertRegions(c4, blameAndCache(c4), expectedRegions, 4);
		assertRegions(c4, blameAndCache(c3), expectedRegions, 4);
		assertRegions(c4, blameAndCache(c2), expectedRegions, 4);
		assertRegions(c4, blameAndCache(c1), expectedRegions, 4);
	}

	@Test
	public void blame_simple_cacheUsage() throws Exception {
		RevCommit c1, c2, c3, c4;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			c1 = commit(r, lines("first", "second", "third"));
			c2 = commit(r, lines("first", "second", "third", "fourth"), c1);
			c3 = commit(r, lines("first", "other2", "other3", "fourth"), c2);
			c4 = commit(r, lines("first", "other22", "other3", "other4"), c3);
		}

		assertCacheUsage(c4, null, false, 4);
		assertCacheUsage(c4, emptyCache(), false, 4);
		assertCacheUsage(c4, blameAndCache(c4), true, 1);
		assertCacheUsage(c4, blameAndCache(c3), true, 2);
		assertCacheUsage(c4, blameAndCache(c2), true, 3);
		assertCacheUsage(c4, blameAndCache(c1), true, 4);
	}

	@Test
	public void blame_ovewrite_correctRegions() throws Exception {
		RevCommit c1, c2, c3;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			c1 = commit(r, lines("first", "second", "third"));
			c2 = commit(r, lines("first", "second", "third", "fourth"), c1);
			c3 = commit(r, lines("a", "b", "c"), c2);
		}

		List<EmittedRegion> expectedRegions = Arrays.asList(
				new EmittedRegion(c3, 0, 3));

		assertRegions(c3, null, expectedRegions, 3);
		assertRegions(c3, emptyCache(), expectedRegions, 3);
		assertRegions(c3, blameAndCache(c3), expectedRegions, 3);
		assertRegions(c3, blameAndCache(c2), expectedRegions, 3);
		assertRegions(c3, blameAndCache(c1), expectedRegions, 3);
	}

	@Test
	public void blame_overwrite_cacheUsage() throws Exception {
		RevCommit c1, c2, c3;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			c1 = commit(r, lines("first", "second", "third"));
			c2 = commit(r, lines("first", "second", "third", "fourth"), c1);
			c3 = commit(r, lines("a", "b", "c"), c2);
		}

		assertCacheUsage(c3, null, false, 1);
		assertCacheUsage(c3, emptyCache(), false, 1);
		assertCacheUsage(c3, blameAndCache(c3), true, 1);
		assertCacheUsage(c3, blameAndCache(c2), false, 1);
		assertCacheUsage(c3, blameAndCache(c1), false, 1);
	}


	@Test
	public void blame_merge_correctRegions() throws Exception {
		RevCommit root, sideA, sideB, mergedTip;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			root = commitAsLines(r, "---");
			sideA = commitAsLines(r, "aaaa---", root);
			sideB = commitAsLines(r, "---bbbb", root);
			mergedTip = commitAsLines(r, "aaaa---bbbb", sideA, sideB);
		}

		List<EmittedRegion> expectedRegions = Arrays.asList(
				new EmittedRegion(sideA, 0, 4), new EmittedRegion(root, 4, 7),
				new EmittedRegion(sideB, 7, 11));

		assertRegions(mergedTip, null, expectedRegions, 11);
		assertRegions(mergedTip, emptyCache(), expectedRegions, 11);
		assertRegions(mergedTip, blameAndCache(root), expectedRegions, 11);
		assertRegions(mergedTip, blameAndCache(sideA), expectedRegions, 11);
		assertRegions(mergedTip, blameAndCache(sideB), expectedRegions, 11);
		assertRegions(mergedTip, blameAndCache(mergedTip), expectedRegions, 11);
	}

	@Test
	public void blame_merge_cacheUsage() throws Exception {
		RevCommit root, sideA, sideB, mergedTip;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			root = commitAsLines(r, "---");
			sideA = commitAsLines(r, "aaaa---", root);
			sideB = commitAsLines(r, "---bbbb", root);
			mergedTip = commitAsLines(r, "aaaa---bbbb", sideA, sideB);
		}

		assertCacheUsage(mergedTip, null, /* cacheUsed */ false,
				/* candidates */ 4);
		assertCacheUsage(mergedTip, emptyCache(), false, 4);
		assertCacheUsage(mergedTip, blameAndCache(mergedTip), true, 1);

		// While splitting unblamed regions to parents, sideA comes first
		// and gets "aaaa----". Processing is by commit time, so sideB is
		// explored first
		assertCacheUsage(mergedTip, blameAndCache(sideA), true, 3);
		assertCacheUsage(mergedTip, blameAndCache(sideB), true, 4);
		assertCacheUsage(mergedTip, blameAndCache(root), true, 4);
	}

	@Test
	public void blame_movingBlock_correctRegions() throws Exception {
		RevCommit c1, c2, c3;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			c1 = commitAsLines(r, "root---");
			c2 = commitAsLines(r, "rootXXX---", c1);
			c3 = commitAsLines(r, "rootYYYXXX---", c2);
		}

		List<EmittedRegion> expectedRegions = Arrays.asList(
				/* root. */ new EmittedRegion(c1, 0, 4),
				/* .YYY. */ new EmittedRegion(c3, 4, 7),
				/* .XXX. */ new EmittedRegion(c2, 7, 10),
				/* .--- */ new EmittedRegion(c1, 10, 13));

		assertRegions(c3, null, expectedRegions, 13);
		assertRegions(c3, emptyCache(), expectedRegions, 13);
		assertRegions(c3, blameAndCache(c3), expectedRegions, 13);
		assertRegions(c3, blameAndCache(c2), expectedRegions, 13);
		assertRegions(c3, blameAndCache(c1), expectedRegions, 13);
	}

	@Test
	public void blame_movingBlock_cacheUsage() throws Exception {
		RevCommit c1, c2, c3;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			c1 = commitAsLines(r, "root---");
			c2 = commitAsLines(r, "rootXXX---", c1);
			c3 = commitAsLines(r, "rootYYYXXX---", c2);
		}

		assertCacheUsage(c3, null, false, 3);
		assertCacheUsage(c3, emptyCache(), false, 3);
		assertCacheUsage(c3, blameAndCache(c3), true, 1);
		assertCacheUsage(c3, blameAndCache(c2), true, 2);
		assertCacheUsage(c3, blameAndCache(c1), true, 3);
	}

	private void assertRegions(RevCommit commit, InMemoryBlameCache cache,
			List<EmittedRegion> expectedRegions, int resultLineCount)
			throws IOException {
		try (BlameGenerator gen = new BlameGenerator(db, FILE, cache)) {
			gen.push(null, db.parseCommit(commit));
			List<EmittedRegion> regions = consume(gen);
			assertRegionsEquals(expectedRegions, regions);
			assertAllLinesCovered(/* lines= */ resultLineCount, regions);
		}
	}

	private void assertCacheUsage(RevCommit commit, InMemoryBlameCache cache,
			boolean useCache, int candidatesVisited) throws IOException {
		try (BlameGenerator gen = new BlameGenerator(db, FILE, cache)) {
			gen.push(null, db.parseCommit(commit));
			consume(gen);
			assertEquals(useCache, gen.getStats().isCacheHit());
			assertEquals(candidatesVisited,
					gen.getStats().getCandidatesVisited());
		}
	}

	private static void assertAllLinesCovered(int lines,
			List<EmittedRegion> regions) {
		Collections.sort(regions);
		assertEquals("Starts in first line", 0, regions.get(0).resultStart());
		for (int i = 1; i < regions.size(); i++) {
			assertEquals("No gaps", regions.get(i).resultStart(),
					regions.get(i - 1).resultEnd());
		}
		assertEquals("Ends in last line", lines,
				regions.get(regions.size() - 1).resultEnd());
	}

	private static void assertRegionsEquals(
			List<EmittedRegion> expected, List<EmittedRegion> actual) {
		assertEquals(expected.size(), actual.size());
		Collections.sort(actual);
		for (int i = 0; i < expected.size(); i++) {
			assertEquals(String.format("List differ in element %d", i),
					expected.get(i), actual.get(i));
		}
	}

	private static InMemoryBlameCache emptyCache() {
		return new InMemoryBlameCache("<empty>");
	}

	private List<EmittedRegion> consume(BlameGenerator generator)
			throws IOException {
		List<EmittedRegion> result = new ArrayList<>();
		while (generator.next()) {
			EmittedRegion genRegion = new EmittedRegion(
					generator.getSourceCommit().toObjectId(),
					generator.getResultStart(), generator.getResultEnd());
			result.add(genRegion);
		}
		return result;
	}

	private InMemoryBlameCache blameAndCache(RevCommit commit)
			throws IOException {
		List<CacheRegion> regions;
		try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
			generator.push(null, commit);
			regions = consume(generator).stream()
					.map(EmittedRegion::asCacheRegion)
					.collect(Collectors.toUnmodifiableList());
		}
		InMemoryBlameCache cache = new InMemoryBlameCache("<x>");
		cache.put(commit, FILE, regions);
		return cache;
	}

	private static RevCommit commitAsLines(TestRepository<?> r,
			String charPerLine, RevCommit... parents) throws Exception {
		return commit(r, charPerLine.replaceAll("\\S", "$0\n"), parents);
	}

	private static RevCommit commit(TestRepository<?> r, String contents,
			RevCommit... parents) throws Exception {
		return r.commit(r.tree(r.file(FILE, r.blob(contents))), parents);
	}

	private static String lines(String... l) {
		return join("\n", l);
	}

	private record EmittedRegion(ObjectId oid, int resultStart, int resultEnd)
			implements Comparable<EmittedRegion> {
		@Override
		public int compareTo(EmittedRegion o) {
			return resultStart - o.resultStart;
		}

		CacheRegion asCacheRegion() {
			return new CacheRegion(FILE, oid, resultStart, resultEnd);
		}
	}
}
