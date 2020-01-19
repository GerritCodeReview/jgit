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

import java.util.TreeMap;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS.ExecutionResult;

/**
 * Manages diff tools.
 *
 * @since 5.7
 */
public class DiffTools {

	private final Repository db;

	private final DiffToolConfig config;

	private final Map<String, ExternalDiffTool> predefinedTools;

	private final Map<String, ExternalDiffTool> userDefinedTools;

	/**
	 * Creates the external diff-tools manager for given repository.
	 *
	 * @param db
	 *            the repository database
	 */
	public DiffTools(Repository db) {
		this.db = db;
		config = db.getConfig().get(DiffToolConfig.KEY);
		predefinedTools = setupPredefinedTools();
		userDefinedTools = setupUserDefinedTools(config, predefinedTools);
	}

	/**
	 * @param localFile
	 *            the local file element
	 * @param remoteFile
	 *            the remote file element
	 * @param mergedFile
	 *            the merged file element, it's path equals local or remote
	 *            element path
	 * @param toolName
	 *            the selected tool name (can be null)
	 * @param prompt
	 *            the prompt option
	 * @param gui
	 *            the GUI option
	 * @param trustExitCode
	 *            the "trust exit code" option
	 * @return the execution result from tool
	 * @throws ToolException
	 */
	public ExecutionResult compare(FileElement localFile,
			FileElement remoteFile, FileElement mergedFile,
			String toolName, BooleanOption prompt,
			BooleanOption gui, BooleanOption trustExitCode)
			throws ToolException {
		try {
			// prepare the command (replace the file paths)
			String command = ExternalToolUtils.prepareCommand(
					guessTool(toolName, gui).getCommand(), localFile,
					remoteFile, mergedFile, null);
			// prepare the environment
			Map<String, String> env = ExternalToolUtils.prepareEnvironment(db,
					localFile, remoteFile, mergedFile, null);
			boolean trust = config.isTrustExitCode();
			if (trustExitCode.isConfigured()) {
				trust = trustExitCode.toBoolean();
			}
			// execute the tool
			CommandExecutor cmdExec = new CommandExecutor(db.getFS(), trust);
			return cmdExec.run(command, db.getWorkTree(), env);
		} catch (IOException | InterruptedException e) {
			throw new ToolException(e);
		} finally {
			localFile.cleanTemporaries();
			remoteFile.cleanTemporaries();
			mergedFile.cleanTemporaries();
		}
	}

	/**
	 * @return the tool names
	 */
	public Set<String> getToolNames() {
		return config.getToolNames();
	}

	/**
	 * @return the user defined tools
	 */
	public Map<String, ExternalDiffTool> getUserDefinedTools() {
		return userDefinedTools;
	}

	/**
	 * @return the available predefined tools
	 */
	public Map<String, ExternalDiffTool> getAvailableTools() {
		return predefinedTools;
	}

	/**
	 * @return the NOT available predefined tools
	 */
	public Map<String, ExternalDiffTool> getNotAvailableTools() {
		return new TreeMap<>();
	}

	/**
	 * @param gui
	 *            use the diff.guitool setting ?
	 * @return the default tool name
	 */
	public String getDefaultToolName(BooleanOption gui) {
		return gui.toBoolean() ? config.getDefaultGuiToolName()
				: config.getDefaultToolName();
	}

	/**
	 * @return id prompt enabled?
	 */
	public boolean isPrompt() {
		return config.isPrompt();
	}

	private ExternalDiffTool guessTool(String toolName, BooleanOption gui)
			throws ToolException {
		if ((toolName == null) || toolName.isEmpty()) {
			toolName = getDefaultToolName(gui);
		}
		ExternalDiffTool tool = getTool(toolName);
		if (tool == null) {
			throw new ToolException("Unknown diff tool " + toolName); //$NON-NLS-1$
		}
		return tool;
	}

	private ExternalDiffTool getTool(final String name) {
		ExternalDiffTool tool = userDefinedTools.get(name);
		if (tool == null) {
			tool = predefinedTools.get(name);
		}
		return tool;
	}

	private Map<String, ExternalDiffTool> setupPredefinedTools() {
		Map<String, ExternalDiffTool> tools = new TreeMap<>();
		for (CommandLineDiffTool tool : CommandLineDiffTool.values()) {
			tools.put(tool.name(), new PreDefinedDiffTool(tool));
		}
		return tools;
	}

	private Map<String, ExternalDiffTool> setupUserDefinedTools(
			DiffToolConfig cfg, Map<String, ExternalDiffTool> predefTools) {
		Map<String, ExternalDiffTool> tools = new TreeMap<>();
		Map<String, ExternalDiffTool> userTools = cfg.getTools();
		for (String name : userTools.keySet()) {
			ExternalDiffTool userTool = userTools.get(name);
			// if difftool.<name>.cmd is defined we have user defined tool
			if (userTool.getCommand() != null) {
				tools.put(name, userTool);
			} else if (userTool.getPath() != null) {
				// if difftool.<name>.path is defined we just overload the path
				// of predefined tool
				PreDefinedDiffTool predefTool = (PreDefinedDiffTool) predefTools
						.get(name);
				if (predefTool != null) {
					predefTool.setPath(userTool.getPath());
				}
			}
		}
		return tools;
	}

}
