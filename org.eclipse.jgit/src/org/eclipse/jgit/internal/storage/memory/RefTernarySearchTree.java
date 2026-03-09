/*
 * Copyright (C) 2026 GerritForge, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

/**
 * A {@link TernarySearchTree} specialised for {@link Ref} values, keyed by ref
 * name, with an ObjectId secondary index for O(1) lookup by object id.
 *
 * <p>All mutating operations keep the secondary index in sync atomically under
 * the same {@link java.util.concurrent.locks.ReadWriteLock} used by the parent
 * tree. {@code ReentrantReadWriteLock} permits both write-lock reentrancy and
 * read-lock acquisition while holding the write lock, so the nesting in the
 * overridden methods is safe.
 *
 * @since 6.5
 */
public class RefTernarySearchTree extends TernarySearchTree<Ref> {

	/** Secondary index: objectId → refs pointing to that objectId. */
	private final Map<ObjectId, List<Ref>> objectIdIndex = new HashMap<>();

	// -----------------------------------------------------------------------
	// Public query API
	// -----------------------------------------------------------------------

	/**
	 * Find all refs whose leaf object id equals the given id.
	 *
	 * @param objectId
	 *            the object id to search for
	 * @return refs pointing to the given object id; never {@code null}
	 */
	public List<Ref> getByObjectId(AnyObjectId objectId) {
		if (objectId == null) {
			return Collections.emptyList();
		}
		getLock().readLock().lock();
		try {
			List<Ref> refs = objectIdIndex.get(objectId);
			return refs != null ? new ArrayList<>(refs)
					: Collections.emptyList();
		} finally {
			getLock().readLock().unlock();
		}
	}

	// -----------------------------------------------------------------------
	// Mutating method overrides — keep objectIdIndex in sync
	// -----------------------------------------------------------------------

	@Override
	public int insert(String key, Ref value) {
		getLock().writeLock().lock();
		try {
			removeOldFromIndex(key);
			int result = super.insert(key, value);
			addToIndex(value);
			return result;
		} finally {
			getLock().writeLock().unlock();
		}
	}

	@Override
	public int insert(Map<String, Ref> map) {
		getLock().writeLock().lock();
		try {
			for (String key : map.keySet()) {
				removeOldFromIndex(key);
			}
			int result = super.insert(map);
			for (Ref ref : map.values()) {
				addToIndex(ref);
			}
			return result;
		} finally {
			getLock().writeLock().unlock();
		}
	}

	@Override
	public int replace(Iterable<Entry<String, Ref>> loader) {
		// Buffer so the iterable can be passed to super and also used to
		// populate the index in the same lock acquisition.
		List<Entry<String, Ref>> entries = new ArrayList<>();
		for (Entry<String, Ref> e : loader) {
			entries.add(e);
		}
		getLock().writeLock().lock();
		try {
			objectIdIndex.clear();
			int result = super.replace(entries);
			for (Entry<String, Ref> e : entries) {
				addToIndex(e.getValue());
			}
			return result;
		} finally {
			getLock().writeLock().unlock();
		}
	}

	@Override
	public int reload(Iterable<Entry<String, Ref>> loader) {
		List<Entry<String, Ref>> entries = new ArrayList<>();
		for (Entry<String, Ref> e : loader) {
			entries.add(e);
		}
		getLock().writeLock().lock();
		try {
			for (Entry<String, Ref> e : entries) {
				removeOldFromIndex(e.getKey());
			}
			int result = super.reload(entries);
			for (Entry<String, Ref> e : entries) {
				addToIndex(e.getValue());
			}
			return result;
		} finally {
			getLock().writeLock().unlock();
		}
	}

	@Override
	public int delete(String key) {
		getLock().writeLock().lock();
		try {
			removeOldFromIndex(key);
			return super.delete(key);
		} finally {
			getLock().writeLock().unlock();
		}
	}

	@Override
	public int delete(Iterable<String> delete) {
		List<String> keys = new ArrayList<>();
		for (String key : delete) {
			keys.add(key);
		}
		getLock().writeLock().lock();
		try {
			for (String key : keys) {
				removeOldFromIndex(key);
			}
			return super.delete(keys);
		} finally {
			getLock().writeLock().unlock();
		}
	}

	@Override
	public void clear() {
		getLock().writeLock().lock();
		try {
			objectIdIndex.clear();
			super.clear();
		} finally {
			getLock().writeLock().unlock();
		}
	}

	// -----------------------------------------------------------------------
	// Index helpers (must be called while holding the write lock)
	// -----------------------------------------------------------------------

	private void addToIndex(Ref ref) {
		ObjectId id = ref.getLeaf().getObjectId();
		if (id != null) {
			objectIdIndex.computeIfAbsent(id.copy(), k -> new ArrayList<>())
					.add(ref);
		}
	}

	/**
	 * Remove the current tree entry for {@code key} from the secondary index.
	 * Must be called before the new value is written into the tree.
	 * <p>
	 * Read-lock acquisition while holding the write lock is permitted by
	 * {@code ReentrantReadWriteLock} (write-to-read downgrade).
	 */
	private void removeOldFromIndex(String key) {
		Ref old = get(key);
		if (old == null) {
			return;
		}
		ObjectId id = old.getLeaf().getObjectId();
		if (id == null) {
			return;
		}
		List<Ref> refs = objectIdIndex.get(id);
		if (refs == null) {
			return;
		}
		refs.removeIf(r -> r.getName().equals(old.getName()));
		if (refs.isEmpty()) {
			objectIdIndex.remove(id);
		}
	}
}
