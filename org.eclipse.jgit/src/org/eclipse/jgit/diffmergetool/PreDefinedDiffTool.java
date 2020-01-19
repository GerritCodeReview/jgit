/*
 * Copyright (C) 2018-2020, Andre Bossert <andre.bossert@siemens.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diffmergetool;

/**
 * The pre-defined diff tool.
 *
 * @since 5.7
 */
public class PreDefinedDiffTool extends UserDefinedDiffTool {

	/**
	 * Create a pre-defined diff tool
	 *
	 * @param name
	 *            the name
	 * @param path
	 *            the path
	 * @param parameters
	 *            the tool parameters as one string that is used together with
	 *            path as command
	 */
	public PreDefinedDiffTool(String name, String path, String parameters) {
		super(name, path, parameters);
	}

	/**
	 * Creates the pre-defined diff tool
	 *
	 * @param tool
	 *            the command line diff tool
	 *
	 */
	public PreDefinedDiffTool(CommandLineDiffTool tool) {
		this(tool.name(), tool.getPath(), tool.getParameters());
	}

	/**
	 * @param path
	 */
	public void setPath(String path) {
		this.path = path;
	}

	/**
	 * @return the diff tool command
	 */
	@Override
	public String getCommand() {
		return path + " " + super.getCommand(); //$NON-NLS-1$
	}

}
