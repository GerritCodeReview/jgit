/*
 * Copyright (C) 2018-2020, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diffmergetool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectStream;

/**
 * The element used as left or right file for compare.
 *
 * @since 5.9
 *
 */
public class FileElement {

	private final String path;

	private final String id;

	private ObjectStream stream;

	private File tempFile;

	/**
	 * @param path
	 *            the file path
	 * @param id
	 *            the file id
	 */
	public FileElement(final String path, final String id) {
		this(path, id, null);
	}

	/**
	 * @param path
	 *            the file path
	 * @param id
	 *            the file id
	 * @param stream
	 *            the object stream to load instead of file
	 */
	public FileElement(final String path, final String id,
			ObjectStream stream) {
		this.path = path;
		this.id = id;
		this.stream = stream;
	}

	/**
	 * @return the file path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return the file id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param stream
	 *            the object stream
	 */
	public void setStream(ObjectStream stream) {
		this.stream = stream;
	}

	/**
	 * @param workingDir the working directory used if file cannot be found (e.g. /dev/null)
	 * @return the object stream
	 * @throws IOException
	 */
	public File getFile(File workingDir) throws IOException {
		if (tempFile != null) {
			return tempFile;
		}
		File file = new File(path);
		String name = file.getName();
		if (path.equals(DiffEntry.DEV_NULL)) {
			file = new File(workingDir, "nul"); //$NON-NLS-1$
		}
		else if (stream != null) {
			tempFile = File.createTempFile(".__", "__" + name); //$NON-NLS-1$ //$NON-NLS-2$
			try (OutputStream outStream = new FileOutputStream(tempFile)) {
				int read = 0;
				byte[] bytes = new byte[8 * 1024];
				while ((read = stream.read(bytes)) != -1) {
					outStream.write(bytes, 0, read);
				}
			} finally {
				// stream can only be consumed once --> close it
				stream.close();
				stream = null;
			}
			return tempFile;
		}
		return file;
	}

	/**
	 * Returns a real file from work tree or a temporary file with content if
	 * stream is valid or if path is "/dev/null"
	 *
	 * @return the object stream
	 * @throws IOException
	 */
	public File getFile() throws IOException {
		if (tempFile != null) {
			return tempFile;
		}
		File file = new File(path);
		String name = file.getName();
		// if we have a stream or file is missing ("/dev/null") then create
		// temporary file
		if ((stream != null) || path.equals(DiffEntry.DEV_NULL)) {
			// TODO: avoid long random file name (number generated by
			// createTempFile)
			tempFile = File.createTempFile(".__", "__" + name); //$NON-NLS-1$ //$NON-NLS-2$
			if (stream != null) {
				try (OutputStream outStream = new FileOutputStream(tempFile)) {
					int read = 0;
					byte[] bytes = new byte[8 * 1024];
					while ((read = stream.read(bytes)) != -1) {
						outStream.write(bytes, 0, read);
					}
				} finally {
					// stream can only be consumed once --> close it
					stream.close();
					stream = null;
				}
			}
			return tempFile;
		}
		return file;
	}

	/**
	 * Deletes and invalidates temporary file if necessary.
	 */
	public void cleanTemporaries() {
		if (tempFile != null && tempFile.exists())
		tempFile.delete();
		tempFile = null;
	}

}