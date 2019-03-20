/*
 * Copyright (C) 2019, Google LLC.
 * and other copyright owners as documented in the project's IP log.
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

package org.eclipse.jgit.transport;

/**
 * Represents either a filter specified in a protocol "filter" line, or a
 * placeholder to indicate no filtering.
 */
public final class FilterSpec {

	private final long blobLimit;

	private final long treeDepthLimit;

	private FilterSpec(long blobLimit, long treeDepthLimit) {
		this.blobLimit = blobLimit;
		this.treeDepthLimit = treeDepthLimit;
	}

	/**
	 * @param blobLimit
	 *            the blob limit in a "blob:[limit]" or "blob:none" filter line
	 * @return a filter spec which filters blobs above a certain size
	 */
	public static FilterSpec blobFilter(long blobLimit) {
		if (blobLimit < 0) {
			throw new IllegalArgumentException(
					"blobLimit cannot be negative: " + blobLimit); //$NON-NLS-1$
		}
		return new FilterSpec(blobLimit, -1);
	}

	/**
	 * @param treeDepthLimit
	 *            the tree depth limit in a "tree:[depth]" filter line
	 * @return a filter spec which filters blobs and trees beyond a certain tree
	 *         depth
	 */
	public static FilterSpec treeDepthFilter(long treeDepthLimit) {
		if (treeDepthLimit < 0) {
			throw new IllegalArgumentException(
					"treeDepthLimit cannot be negative: " + treeDepthLimit); //$NON-NLS-1$
		}
		return new FilterSpec(-1, treeDepthLimit);
	}

	/**
	 * A placeholder that indicates no filtering.
	 */
	public static final FilterSpec NO_OP_FILTER = new FilterSpec(-1, -1);

	/**
	 * @return -1 if this filter does not filter blobs based on size, or a
	 *         non-negative integer representing the max size of blobs to allow
	 */
	public long getBlobLimit() {
		return blobLimit;
	}

	/**
	 * @return -1 if this filter does not filter blobs and trees based on depth,
	 *         or a non-negative integer representing the max tree depth of
	 *         blobs and trees to fetch
	 */
	public long getTreeDepthLimit() {
		return treeDepthLimit;
	}

	/**
	 * @return true if this filter doesn't filter out anything
	 */
	public boolean isNoOp() {
		return blobLimit == -1 && treeDepthLimit == -1;
	}

	/**
	 * @return the filter line which describes this spec, e.g. "filter blob:limit=42"
	 */
	public String filterLine() {
		if (blobLimit == 0)
			return GitProtocolConstants.OPTION_FILTER + " blob:none"; //$NON-NLS-1$

		if (blobLimit > 0)
			return GitProtocolConstants.OPTION_FILTER + " blob:limit=" + blobLimit; //$NON-NLS-1$

		throw new UnsupportedOperationException();
	}
}
