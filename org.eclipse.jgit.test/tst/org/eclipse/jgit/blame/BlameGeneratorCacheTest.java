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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.blame.cache.BlameCache;
import org.eclipse.jgit.blame.cache.CacheRegion;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class BlameGeneratorCacheTest extends RepositoryTestCase {
	private static final String FILE = "file.txt";

	/**
	 * Simple history:
	 *
	 * <pre>
	 *          C1    C2    C3    C4   C4 blame
	 * lines ----------------------------------
	 * L1    |  C1    C1    C1    C1     C1
	 * L2    |  C1    C1   *C3   *C4     C4
	 * L3    |  C1    C1   *C3    C3     C3
	 * L4    |       *C2    C2   *C4     C4
	 * </pre>
	 *
	 * @throws Exception
	 *             any error
	 */
	@Test
	public void blame_simple_correctRegions() throws Exception {
		RevCommit c1, c2, c3, c4;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			c1 = commit(r, lines("L1C1", "L2C1", "L3C1"));
			c2 = commit(r, lines("L1C1", "L2C1", "L3C1", "L4C2"), c1);
			c3 = commit(r, lines("L1C1", "L2C3", "L3C3", "L4C2"), c2);
			c4 = commit(r, lines("L1C1", "L2C4", "L3C3", "L4C4"), c3);
		}

		List<EmittedRegion> expectedRegions = Arrays.asList(
				new EmittedRegion(c1, 0, 1),
				new EmittedRegion(c4, 1, 2),
				new EmittedRegion(c3, 2, 3),
				new EmittedRegion(c4, 3, 4));

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
			c1 = commit(r, lines("L1C1", "L2C1", "L3C1"));
			c2 = commit(r, lines("L1C1", "L2C1", "L3C1", "L4C2"), c1);
			c3 = commit(r, lines("L1C1", "L2C3", "L3C3", "L4C2"), c2);
			c4 = commit(r, lines("L1C1", "L2C4", "L3C3", "L4C4"), c3);
		}

		assertCacheUsage(c4, null, false, 4);
		assertCacheUsage(c4, emptyCache(), false, 4);
		assertCacheUsage(c4, blameAndCache(c4), true, 1);
		assertCacheUsage(c4, blameAndCache(c3), true, 2);
		assertCacheUsage(c4, blameAndCache(c2), true, 3);
		// Cache not needed because c1 doesn't have parents
		assertCacheUsage(c4, blameAndCache(c1), false, 4);
	}

	@Test
	public void blame_simple_endBeforeRoot_correctRegions() throws Exception {
		RevCommit c0, c1, c2, c3, c4;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			c0 = commit(r, Map.of("otherfile", "contents"));
			c1 = commit(r, lines("L1C1", "L2C1", "L3C1"), c0);
			c2 = commit(r, lines("L1C1", "L2C1", "L3C1", "L4C2"), c1);
			c3 = commit(r, lines("L1C1", "L2C3", "L3C3", "L4C2"), c2);
			c4 = commit(r, lines("L1C1", "L2C4", "L3C3", "L4C4"), c3);
		}

		List<EmittedRegion> expectedRegions = Arrays.asList(
				new EmittedRegion(c1, 0, 1),
				new EmittedRegion(c4, 1, 2),
				new EmittedRegion(c3, 2, 3),
				new EmittedRegion(c4, 3, 4));

		assertRegions(c4, null, expectedRegions, 4);
		assertRegions(c4, emptyCache(), expectedRegions, 4);
		assertRegions(c4, blameAndCache(c4), expectedRegions, 4);
		assertRegions(c4, blameAndCache(c3), expectedRegions, 4);
		assertRegions(c4, blameAndCache(c2), expectedRegions, 4);
		assertRegions(c4, blameAndCache(c1), expectedRegions, 4);
		assertRegions(c4, blameAndCache(c0), expectedRegions, 4);
	}

	@Test
	public void blame_simple_endBeforeRoot_cacheUsage() throws Exception {
		RevCommit c0, c1, c2, c3, c4;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			c0 = commit(r, Map.of("otherfile", "contents"));
			c1 = commit(r, lines("L1C1", "L2C1", "L3C1"), c0);
			c2 = commit(r, lines("L1C1", "L2C1", "L3C1", "L4C2"), c1);
			c3 = commit(r, lines("L1C1", "L2C3", "L3C3", "L4C2"), c2);
			c4 = commit(r, lines("L1C1", "L2C4", "L3C3", "L4C4"), c3);
		}

		assertCacheUsage(c4, null, false, 4);
		assertCacheUsage(c4, emptyCache(), false, 4);
		assertCacheUsage(c4, blameAndCache(c4), true, 1);
		assertCacheUsage(c4, blameAndCache(c3), true, 2);
		assertCacheUsage(c4, blameAndCache(c2), true, 3);
		// Cache not needed because c1 created the file
		assertCacheUsage(c4, blameAndCache(c1), false, 4);
	}

	/**
	 * Overwrite:
	 *
	 * <pre>
	 *          C1    C2    C3    C3 blame
	 * lines ----------------------------------
	 * L1    |  C1    C1   *C3      C3
	 * L2    |  C1    C1   *C3      C3
	 * L3    |  C1    C1   *C3      C3
	 * L4    |       *C2
	 * </pre>
	 *
	 * @throws Exception
	 *             any error
	 */
	@Test
	public void blame_ovewrite_correctRegions() throws Exception {
		RevCommit c1, c2, c3;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			c1 = commit(r, lines("L1C1", "L2C1", "L3C1"));
			c2 = commit(r, lines("L1C1", "L2C1", "L3C1", "L4C2"), c1);
			c3 = commit(r, lines("L1C3", "L2C3", "L3C3"), c2);
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
			c1 = commit(r, lines("L1C1", "L2C1", "L3C1"));
			c2 = commit(r, lines("L1C1", "L2C1", "L3C1", "L4C2"), c1);
			c3 = commit(r, lines("L1C3", "L2C3", "L3C3"), c2);
		}

		assertCacheUsage(c3, null, false, 1);
		assertCacheUsage(c3, emptyCache(), false, 1);
		assertCacheUsage(c3, blameAndCache(c3), true, 1);
		assertCacheUsage(c3, blameAndCache(c2), false, 1);
		assertCacheUsage(c3, blameAndCache(c1), false, 1);
	}

	/**
	 * Merge:
	 *
	 * <pre>
	 *                 root
	 *                 ----
	 *                 L1  -
	 *                 L2  -
	 *                 L3  -
	 *               /     \
	 *           sideA     sideB
	 *           -----     -----
	 *           *L1 a      L1 -
	 *           *L2 a      L2 -
	 *           *L3 a      L3 -
	 *           *L4 a     *L4 b
	 *            L5 -     *L5 b
	 *            L6 -     *L6 b
	 *            L7 -     *L7 b
	 *              \       /
	 *                merge
	 *                -----
	 *              L1-L4 a (from sideA)
	 *              L5-L7 - (common, from root)
	 *              L8-L11 b (from sideB)
	 * </pre>
	 *
	 * @throws Exception
	 *             any error
	 */
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
				new EmittedRegion(sideA, 0, 4),
				new EmittedRegion(root, 4, 7),
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

		// While splitting unblamed regions to parents, sideA comes first
		// and gets "aaaa----". Processing is by commit time, so sideB is
		// explored first
		assertCacheUsage(mergedTip, blameAndCache(sideA), true, 3);
		assertCacheUsage(mergedTip, blameAndCache(sideB), true, 4);
		assertCacheUsage(mergedTip, blameAndCache(root), false, 4);
	}

	/**
	 * Moving block (insertion)
	 *
	 * <pre>
	 *          C1    C2    C3    C3 blame
	 * lines ----------------------------------
	 * L1    |  C1    C1    C1      C1
	 * L2    |  C1   *C2    C2      C2
	 * L3    |        C1   *C3      C3
	 * L4    |              C1      C1
	 * </pre>
	 *
	 * @throws Exception
	 *             any error
	 */
	@Test
	public void blame_movingBlock_correctRegions() throws Exception {
		RevCommit c1, c2, c3;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			c1 = commit(r, lines("L1C1", "L2C1"));
			c2 = commit(r, lines("L1C1", "middle", "L2C1"), c1);
			c3 = commit(r, lines("L1C1", "middle", "extra", "L2C1"), c2);
		}

		List<EmittedRegion> expectedRegions = Arrays.asList(
				new EmittedRegion(c1, 0, 1),
				new EmittedRegion(c2, 1, 2),
				new EmittedRegion(c3, 2, 3),
				new EmittedRegion(c1, 3, 4));

		assertRegions(c3, null, expectedRegions, 4);
		assertRegions(c3, emptyCache(), expectedRegions, 4);
		assertRegions(c3, blameAndCache(c3), expectedRegions, 4);
		assertRegions(c3, blameAndCache(c2), expectedRegions, 4);
		assertRegions(c3, blameAndCache(c1), expectedRegions, 4);
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
		assertCacheUsage(c3, blameAndCache(c1), false, 3);
	}

	@Test
	public void blame_longHistory_queryCacheOnChange() throws Exception {
		String README = "README";
		RevCommit c1, c2, c3, c4;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			c1 = commit(r, lines("L1C1", "L2C1", "L3C1"));
			String c2Content = lines("L1C1", "L2C1", "L3C1", "L4C2");
			c2 = commit(r, c2Content, c1);
			// Keep FILE and edit 100 times README
			RevCommit ni = c2;
			for (int i = 0; i < 100; i++) {
				ni = commit(r, Map.of(FILE, c2Content, README, lines("whatever ", Integer.toString(i))), ni);
			}
			c3 = commit(r, lines("L1C1", "L2C3", "L3C3", "L4C2"), ni);
			c4 = commit(r, lines("L1C1", "L2C4", "L3C3", "L4C4"), c3);
			r.branch("refs/heads/master").update(c4);
		}

		InMemoryBlameCache testCache = new InMemoryBlameCache("<empty>");
		int candidatesEmitted = 0;
		try (BlameGenerator gen = new BlameGenerator(db, FILE, testCache)) {
			gen.push(null, c4);
			while (gen.next()) {
				candidatesEmitted++;
			}
			assertEquals(104, gen.getStats().getCandidatesVisited());;
		}
		assertEquals(4, candidatesEmitted);
		assertEquals(3, testCache.callCount);
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

	private static void assertRegionsEquals(List<EmittedRegion> expected,
			List<EmittedRegion> actual) {
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

	private static RevCommit commit(TestRepository<?> r,
			Map<String, String> fileContents, RevCommit... parents)
			throws Exception {
		DirCacheEntry[] dirCacheEntries = fileContents.entrySet().stream()
				.map(e -> {
					try {
						return r.file(e.getKey(), r.blob(e.getValue()));
					} catch (Exception ex) {
						throw new RuntimeException(ex);
					}
				}).toList().toArray(new DirCacheEntry[0]);
		return r.commit(r.tree(dirCacheEntries), parents);
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

	private static class InMemoryBlameCache implements BlameCache {

		private final Map<Key, List<CacheRegion>> cache = new HashMap<>();

		private final String description;

		private int callCount;

		public InMemoryBlameCache(String description) {
			this.description = description;
		}

		@Override
		public List<CacheRegion> get(Repository repo, ObjectId commitId,
				String path) throws IOException {
			callCount++;
			return cache.get(new Key(commitId.name(), path));
		}

		public void put(ObjectId commitId, String path,
				List<CacheRegion> cachedRegions) {
			cache.put(new Key(commitId.name(), path), cachedRegions);
		}

		@Override
		public String toString() {
			return "InMemoryCache: " + description;
		}

		record Key(String commitId, String path) {
		}
	}
}
