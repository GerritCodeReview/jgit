/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.ObjectId;

/**
 * <p>
 * Reverse index for forward pack index. Provides operations based on offset
 * instead of object id. Such offset-based reverse lookups are performed in
 * O(log n) time.
 * </p>
 *
 * @see PackIndex
 * @see Pack
 */
public abstract class PackReverseIndex {
	/**
	 * Compute an in-memory pack reverse index from the in-memory pack forward
	 * index. This computation uses insertion sort, which has a quadratic
	 * runtime on average.
	 *
	 * @param packIndex the forward index to compute from
	 * @return the reverse index instance
	 */
	public static PackReverseIndex computeFromIndex(PackIndex packIndex) {
		return new ComputedPackReverseIndex(packIndex);
	}

	/**
	 * Search for object id with the specified start offset in this pack
	 * (reverse) index.
	 *
	 * @param offset start offset of object to find.
	 * @return object id for this offset, or null if no object was found.
	 */
	public abstract ObjectId findObject(long offset);

	/**
	 * Search for the next offset to the specified offset in this pack (reverse)
	 * index.
	 *
	 * @param offset
	 *            start offset of previous object (must be valid-existing
	 *            offset).
	 * @param maxOffset
	 *            maximum offset in a pack (returned when there is no next
	 *            offset).
	 * @return offset of the next object in a pack or maxOffset if provided
	 *         offset was the last one.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             when there is no object with the provided offset.
	 */
	public abstract long findNextOffset(long offset, long maxOffset)
			throws CorruptObjectException;

	abstract int findPosition(long offset);

	abstract ObjectId findObjectByPosition(int nthPosition);
}
