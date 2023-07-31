/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.util.RawParseUtils;

/**
 * Single line at the end of a message, such as a "Signed-off-by: someone".
 * <p>
 * These footer lines tend to be used to represent additional information about
 * a commit, like the path it followed through reviewers before finally being
 * accepted into the project's main repository as an immutable commit.
 *
 * @see RevCommit#getFooterLines()
 */
public final class FooterLine {
	private final byte[] buffer;

	private final Charset enc;

	private final int keyStart;

	private final int keyEnd;

	private final int valStart;

	private final int valEnd;

	FooterLine(final byte[] b, final Charset e, final int ks, final int ke,
			final int vs, final int ve) {
		buffer = b;
		enc = e;
		keyStart = ks;
		keyEnd = ke;
		valStart = vs;
		valEnd = ve;
	}

	/**
	 * Extract the footer lines from the given message.
	 * <p>
	 * See {@link RevCommit#getFooterLines()} for further details.
	 *
	 * @param str
	 *            the message to extract footers from.
	 * @return ordered list of footer lines; empty list if no footers found.
	 */
	public static List<FooterLine> fromMessage(
			String str) {
		return fromMessage(str.getBytes());
	}

	/**
	 * Extract the footer lines from the given message.
	 * <p>
	 * See {@link RevCommit#getFooterLines()} for further details.
	 *
	 * @param raw
	 *            the raw message to extract footers from.
	 * @return ordered list of footer lines; empty list if no footers found.
	 */
	public static List<FooterLine> fromMessage(
			byte[] raw) {
		int ptr = raw.length - 1;
		while (raw[ptr] == '\n') // trim any trailing LFs, not interesting
			ptr--;

		final int msgB = RawParseUtils.commitMessage(raw, 0);
		final ArrayList<FooterLine> r = new ArrayList<>(4);
		final Charset enc = RawParseUtils.guessEncoding(raw);
		for (;;) {
			ptr = RawParseUtils.prevLF(raw, ptr);
			if (ptr <= msgB)
				break; // Don't parse commit headers as footer lines.

			final int keyStart = ptr + 2;
			if (raw[keyStart] == '\n')
				break; // Stop at first paragraph break, no footers above it.

			final int keyEnd = RawParseUtils.endOfFooterLineKey(raw, keyStart);
			if (keyEnd < 0)
				continue; // Not a well formed footer line, skip it.

			// Skip over the ': *' at the end of the key before the value.
			//
			int valStart = keyEnd + 1;
			while (valStart < raw.length && raw[valStart] == ' ')
				valStart++;

			// Value ends at the LF, and does not include it.
			//
			int valEnd = RawParseUtils.nextLF(raw, valStart);
			if (raw[valEnd - 1] == '\n')
				valEnd--;

			r.add(new FooterLine(raw, enc, keyStart, keyEnd, valStart, valEnd));
		}
		Collections.reverse(r);
		return r;
	}

	/**
	 * Whether keys match
	 *
	 * @param key
	 *            key to test this line's key name against.
	 * @return true if {@code key.getName().equalsIgnorecase(getKey())}.
	 */
	public boolean matches(FooterKey key) {
		final byte[] kRaw = key.raw;
		final int len = kRaw.length;
		int bPtr = keyStart;
		if (keyEnd - bPtr != len)
			return false;
		for (int kPtr = 0; kPtr < len;) {
			byte b = buffer[bPtr++];
			if ('A' <= b && b <= 'Z')
				b += (byte) ('a' - 'A');
			if (b != kRaw[kPtr++])
				return false;
		}
		return true;
	}

	/**
	 * Get key name of this footer.
	 *
	 * @return key name of this footer; that is the text before the ":" on the
	 *         line footer's line. The text is decoded according to the commit's
	 *         specified (or assumed) character encoding.
	 */
	public String getKey() {
		return RawParseUtils.decode(enc, buffer, keyStart, keyEnd);
	}

	/**
	 * Get value of this footer.
	 *
	 * @return value of this footer; that is the text after the ":" and any
	 *         leading whitespace has been skipped. May be the empty string if
	 *         the footer has no value (line ended with ":"). The text is
	 *         decoded according to the commit's specified (or assumed)
	 *         character encoding.
	 */
	public String getValue() {
		return RawParseUtils.decode(enc, buffer, valStart, valEnd);
	}

	/**
	 * Extract the email address (if present) from the footer.
	 * <p>
	 * If there is an email address looking string inside of angle brackets
	 * (e.g. "&lt;a@b&gt;"), the return value is the part extracted from inside the
	 * brackets. If no brackets are found, then {@link #getValue()} is returned
	 * if the value contains an '@' sign. Otherwise, null.
	 *
	 * @return email address appearing in the value of this footer, or null.
	 */
	public String getEmailAddress() {
		final int lt = RawParseUtils.nextLF(buffer, valStart, '<');
		if (valEnd <= lt) {
			final int at = RawParseUtils.nextLF(buffer, valStart, '@');
			if (valStart < at && at < valEnd)
				return getValue();
			return null;
		}
		final int gt = RawParseUtils.nextLF(buffer, lt, '>');
		if (valEnd < gt)
			return null;
		return RawParseUtils.decode(enc, buffer, lt, gt - 1);
	}

	@Override
	public String toString() {
		return getKey() + ": " + getValue(); //$NON-NLS-1$
	}
}
