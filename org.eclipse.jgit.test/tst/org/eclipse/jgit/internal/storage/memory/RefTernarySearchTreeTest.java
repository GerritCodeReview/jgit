/*
 * Copyright (C) 2021, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.memory;

import static java.util.Map.entry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.junit.Before;
import org.junit.Test;

public class RefTernarySearchTreeTest {

	private static final ObjectId ID_A = ObjectId
			.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

	private static final ObjectId ID_B = ObjectId
			.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

	private static final ObjectId ID_C = ObjectId
			.fromString("cccccccccccccccccccccccccccccccccccccccc");

	private static final ObjectId ID_UNKNOWN = ObjectId
			.fromString("1234567890123456789012345678901234567890");

	private RefTernarySearchTree tree;

	@Before
	public void setup() {
		tree = new RefTernarySearchTree();
		tree.insert("refs/heads/master", ref("refs/heads/master", ID_A));
		tree.insert("refs/heads/stable", ref("refs/heads/stable", ID_B));
		tree.insert("refs/tags/v1.0", ref("refs/tags/v1.0", ID_A));
	}

	@Test
	public void testGetByObjectIdNull() {
		assertTrue(tree.getByObjectId(null).isEmpty());
	}

	@Test
	public void testGetByObjectIdUnknown() {
		assertTrue(tree.getByObjectId(ID_UNKNOWN).isEmpty());
	}

	@Test
	public void testGetByObjectIdSingleMatch() {
		List<Ref> result = tree.getByObjectId(ID_B);
		assertEquals(1, result.size());
		assertEquals("refs/heads/stable", result.get(0).getName());
	}

	@Test
	public void testGetByObjectIdMultipleMatches() {
		List<Ref> result = tree.getByObjectId(ID_A);
		assertEquals(2, result.size());
		assertTrue(result.stream()
				.anyMatch(r -> r.getName().equals("refs/heads/master")));
		assertTrue(result.stream()
				.anyMatch(r -> r.getName().equals("refs/tags/v1.0")));
	}

	@Test
	public void testGetByObjectIdAfterInsert() {
		tree.insert("refs/heads/feature", ref("refs/heads/feature", ID_C));
		List<Ref> result = tree.getByObjectId(ID_C);
		assertEquals(1, result.size());
		assertEquals("refs/heads/feature", result.get(0).getName());
	}

	@Test
	public void testGetByObjectIdAfterOverwrite() {
		// Overwrite refs/heads/master to point to ID_B instead of ID_A.
		tree.insert("refs/heads/master", ref("refs/heads/master", ID_B));
		// ID_A should now only match refs/tags/v1.0.
		List<Ref> byA = tree.getByObjectId(ID_A);
		assertEquals(1, byA.size());
		assertEquals("refs/tags/v1.0", byA.get(0).getName());
		// ID_B should now match both refs/heads/stable and refs/heads/master.
		assertEquals(2, tree.getByObjectId(ID_B).size());
	}

	@Test
	public void testGetByObjectIdAfterDelete() {
		tree.delete("refs/heads/master");
		// ID_A should now only match refs/tags/v1.0.
		List<Ref> result = tree.getByObjectId(ID_A);
		assertEquals(1, result.size());
		assertEquals("refs/tags/v1.0", result.get(0).getName());
	}

	@Test
	public void testGetByObjectIdAfterDeleteAll() {
		tree.delete("refs/heads/master");
		tree.delete("refs/tags/v1.0");
		assertTrue(tree.getByObjectId(ID_A).isEmpty());
	}

	@Test
	public void testGetByObjectIdAfterReplace() {
		tree.replace(Map.ofEntries(
				entry("refs/heads/next", ref("refs/heads/next", ID_C)))
				.entrySet());
		// Old entries are gone.
		assertTrue(tree.getByObjectId(ID_A).isEmpty());
		assertTrue(tree.getByObjectId(ID_B).isEmpty());
		// New entry is present.
		List<Ref> result = tree.getByObjectId(ID_C);
		assertEquals(1, result.size());
		assertEquals("refs/heads/next", result.get(0).getName());
	}

	@Test
	public void testGetByObjectIdAfterReload() {
		tree.reload(Map.ofEntries(
				entry("refs/heads/master", ref("refs/heads/master", ID_C)))
				.entrySet());
		// refs/heads/master now points to ID_C, not ID_A.
		List<Ref> byA = tree.getByObjectId(ID_A);
		assertEquals(1, byA.size());
		assertEquals("refs/tags/v1.0", byA.get(0).getName());
		List<Ref> byC = tree.getByObjectId(ID_C);
		assertEquals(1, byC.size());
		assertEquals("refs/heads/master", byC.get(0).getName());
	}

	@Test
	public void testGetByObjectIdAfterClear() {
		tree.clear();
		assertTrue(tree.getByObjectId(ID_A).isEmpty());
		assertTrue(tree.getByObjectId(ID_B).isEmpty());
	}

	private static Ref ref(String name, ObjectId id) {
		return new ObjectIdRef.Unpeeled(Storage.LOOSE, name, id);
	}
}
