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
 * The user-defined diff tool.
 *
 * @since 5.7
 */
public class UserDefinedDiffTool implements ExternalDiffTool {

	private boolean available;

	/**
	 * the diff tool name
	 */
	private final String name;

	/**
	 * the diff tool path
	 */
	protected String path;

	/**
	 * the diff tool command
	 */
	private final String cmd;

	/**
	 * Creates the diff tool
	 *
	 * @param name
	 *            the name
	 * @param path
	 *            the path
	 * @param cmd
	 *            the command
	 */
	public UserDefinedDiffTool(String name, String path, String cmd) {
		this.name = name;
		this.path = path;
		this.cmd = cmd;
	}

	/**
	 * @return the diff tool name
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * @return the diff tool path
	 */
	@Override
	public String getPath() {
		return path;
	}

	/**
	 * @return the diff tool command
	 */
	@Override
	public String getCommand() {
		return cmd;
	}

	/**
	 * @return availability of the tool: true if tool can be executed and false
	 *         if not
	 */
	@Override
	public boolean isAvailable() {
		return available;
	}

	/**
	 * @param available
	 *            true if tool can be found and false if not
	 */
	public void setAvailable(boolean available) {
		this.available = available;
	}

}
