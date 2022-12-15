/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.shared.scriptinterpreter;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs pre-/post-build hook scripts.
 *
 * @author Benjamin Bentmann
 */
public class ScriptRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptRunner.class);

    /**
     * The supported script interpreters, indexed by the lower-case file extension of their associated script files,
     * never <code>null</code>.
     */
    private Map<String, ScriptInterpreter> scriptInterpreters;

    /**
     * The common set of global variables to pass into the script interpreter, never <code>null</code>.
     */
    private Map<String, Object> globalVariables;

    /**
     * The additional class path for the script interpreter, never <code>null</code>.
     */
    private List<String> classPath;

    /**
     * The file encoding of the hook scripts or <code>null</code> to use platform encoding.
     */
    private String encoding;

    /**
     * Creates a new script runner with BSH and Groovy interpreters.
     */
    public ScriptRunner() {
        scriptInterpreters = new LinkedHashMap<>();
        scriptInterpreters.put("bsh", new BeanShellScriptInterpreter());
        scriptInterpreters.put("groovy", new GroovyScriptInterpreter());
        globalVariables = new HashMap<>();
        classPath = new ArrayList<>();
    }

    /**
     * Add new script Interpreter
     *
     * @param id The Id of interpreter
     * @param scriptInterpreter the Script Interpreter implementation
     */
    public void addScriptInterpreter(String id, ScriptInterpreter scriptInterpreter) {
        scriptInterpreters.put(id, scriptInterpreter);
    }

    /**
     * Sets a global variable for the script interpreter.
     *
     * @param name The name of the variable, must not be <code>null</code>.
     * @param value The value of the variable, may be <code>null</code>.
     */
    public void setGlobalVariable(String name, Object value) {
        this.globalVariables.put(name, value);
    }

    /**
     * Sets the additional class path for the hook scripts. Note that the provided list is copied, so any later changes
     * will not affect the scripts.
     *
     * @param classPath The additional class path for the script interpreter, may be <code>null</code> or empty if only
     * the plugin realm should be used for the script evaluation. If specified, this class path will precede the
     * artifacts from the plugin class path.
     */
    public void setClassPath(List<String> classPath) {
        this.classPath = (classPath != null) ? new ArrayList<>(classPath) : new ArrayList<>();
    }

    /**
     * Sets the file encoding of the hook scripts.
     *
     * @param encoding The file encoding of the hook scripts, may be <code>null</code> or empty to use the platform's
     *                 default encoding.
     */
    public void setScriptEncoding(String encoding) {
        this.encoding = (encoding != null && encoding.length() > 0) ? encoding : null;
    }

    /**
     * Runs the specified hook script (after resolution).
     *
     * @param scriptDescription The description of the script to use for logging, must not be <code>null</code>.
     * @param basedir The base directory of the project, must not be <code>null</code>.
     * @param relativeScriptPath The path to the script relative to the project base directory, may be <code>null</code>
     *            to skip the script execution and may not have extensions (resolution will search).
     * @param context The key-value storage used to share information between hook scripts, may be <code>null</code>.
     * @param logger The logger to redirect the script output to, may be <code>null</code> to use stdout/stderr.
     * @throws IOException If an I/O error occurred while reading the script file.
     * @throws ScriptException If the script did not return <code>true</code> of threw an exception.
     */
    public void run(
            final String scriptDescription,
            final File basedir,
            final String relativeScriptPath,
            final Map<String, ?> context,
            final ExecutionLogger logger)
            throws IOException, ScriptException {
        if (relativeScriptPath == null) {
            LOG.debug("{}: relativeScriptPath is null, not executing script", scriptDescription);
            return;
        }

        final File scriptFile = resolveScript(new File(basedir, relativeScriptPath));

        if (!scriptFile.exists()) {
            LOG.debug(
                    "{} : no script '{}' found in directory {}",
                    scriptDescription,
                    relativeScriptPath,
                    basedir.getAbsolutePath());
            return;
        }

        LOG.info(
                "run {} {}.{}",
                scriptDescription,
                relativeScriptPath,
                FilenameUtils.getExtension(scriptFile.getAbsolutePath()));

        executeRun(scriptDescription, scriptFile, context, logger);
    }

    /**
     * Runs the specified hook script.
     *
     * @param scriptDescription The description of the script to use for logging, must not be <code>null</code>.
     * @param scriptFile The path to the script, may be <code>null</code> to skip the script execution.
     * @param context The key-value storage used to share information between hook scripts, may be <code>null</code>.
     * @param logger The logger to redirect the script output to, may be <code>null</code> to use stdout/stderr.
     * @throws IOException         If an I/O error occurred while reading the script file.
     * @throws ScriptException If the script did not return <code>true</code> of threw an exception.
     */
    public void run(
            final String scriptDescription, File scriptFile, final Map<String, ?> context, final ExecutionLogger logger)
            throws IOException, ScriptException {

        if (!scriptFile.exists()) {
            LOG.debug("{} : script file not found in directory {}", scriptDescription, scriptFile.getAbsolutePath());
            return;
        }

        LOG.info("run {} {}", scriptDescription, scriptFile.getAbsolutePath());

        executeRun(scriptDescription, scriptFile, context, logger);
    }

    private void executeRun(
            final String scriptDescription, File scriptFile, final Map<String, ?> context, final ExecutionLogger logger)
            throws IOException, ScriptException {
        ScriptInterpreter interpreter = getInterpreter(scriptFile);
        if (LOG.isDebugEnabled()) {
            String name = interpreter.getClass().getName();
            name = name.substring(name.lastIndexOf('.') + 1);
            LOG.debug("Running script with {} :{}", name, scriptFile);
        }

        String script;
        try {
            byte[] bytes = Files.readAllBytes(scriptFile.toPath());
            if (encoding != null) {
                script = new String(bytes, encoding);
            } else {
                script = new String(bytes);
            }
        } catch (IOException e) {
            String errorMessage =
                    "error reading " + scriptDescription + " " + scriptFile.getPath() + ", " + e.getMessage();
            throw new IOException(errorMessage, e);
        }

        Object result;
        try {
            if (logger != null) {
                logger.consumeLine("Running " + scriptDescription + ": " + scriptFile);
            }

            PrintStream out = (logger != null) ? logger.getPrintStream() : null;

            Map<String, Object> scriptVariables = new HashMap<>(this.globalVariables);
            scriptVariables.put("basedir", scriptFile.getParentFile());
            scriptVariables.put("context", context);

            result = interpreter.evaluateScript(script, classPath, scriptVariables, out);
            if (logger != null) {
                logger.consumeLine("Finished " + scriptDescription + ": " + scriptFile);
            }
        } catch (ScriptEvaluationException e) {
            Throwable t = (e.getCause() != null) ? e.getCause() : e;
            if (logger != null) {
                t.printStackTrace(logger.getPrintStream());
            }
            throw e;
        }

        if (!(result == null || Boolean.parseBoolean(String.valueOf(result)))) {
            throw new ScriptReturnException("The " + scriptDescription + " returned " + result + ".", result);
        }
    }

    /**
     * Gets the effective path to the specified script. For convenience, we allow to specify a script path as "verify"
     * and have the plugin auto-append the file extension to search for "verify.bsh" and "verify.groovy".
     *
     * @param scriptFile The script file to resolve, may be <code>null</code>.
     * @return The effective path to the script file or <code>null</code> if the input was <code>null</code>.
     */
    private File resolveScript(File scriptFile) {
        if (scriptFile != null && !scriptFile.exists()) {
            for (String ext : this.scriptInterpreters.keySet()) {
                File candidateFile = new File(scriptFile.getPath() + '.' + ext);
                if (candidateFile.exists()) {
                    scriptFile = candidateFile;
                    break;
                }
            }
        }
        return scriptFile;
    }

    /**
     * Determines the script interpreter for the specified script file by looking at its file extension. In this
     * context, file extensions are considered case-insensitive. For backward compatibility with plugin versions 1.2-,
     * the BeanShell interpreter will be used for any unrecognized extension.
     *
     * @param scriptFile The script file for which to determine an interpreter, must not be <code>null</code>.
     * @return The script interpreter for the file, never <code>null</code>.
     */
    private ScriptInterpreter getInterpreter(File scriptFile) {
        String ext = FilenameUtils.getExtension(scriptFile.getName()).toLowerCase(Locale.ENGLISH);
        ScriptInterpreter interpreter = scriptInterpreters.get(ext);
        if (interpreter == null) {
            interpreter = scriptInterpreters.get("bsh");
        }
        return interpreter;
    }
}
