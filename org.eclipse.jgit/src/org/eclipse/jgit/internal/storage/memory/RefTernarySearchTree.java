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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

/**
 * A {@link TernarySearchTree} specialised for {@link Ref} values, keyed by ref
 * name, with the ability to look up refs by the {@link ObjectId} they point to.
 *
 * @since 6.5
 */
public class RefTernarySearchTree extends TernarySearchTree<Ref> {

	/**
	 * Find all refs whose leaf object id equals the given id.
	 *
	 * @param objectId
	 *            the object id to search for
	 * @return refs pointing to the given object id; never {@code null}
	 */
	public List<Ref> getByObjectId(AnyObjectId objectId) {
		List<Ref> result = new ArrayList<>();
		if (objectId == null) {
			return result;
		}
		for (Ref ref : getAllValues()) {
			ObjectId id = ref.getLeaf().getObjectId();
			if (id != null && objectId.equals(id)) {
				result.add(ref);
			}
		}
		return result;
	}
}