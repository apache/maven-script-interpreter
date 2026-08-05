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
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.tools.RootLoader;

/**
 * Provides a facade to evaluate Groovy scripts.
 *
 * @author Benjamin Bentmann
 */
class GroovyScriptInterpreter implements ScriptInterpreter {

    private final RootLoader childFirstLoader =
            new RootLoader(new URL[] {}, Thread.currentThread().getContextClassLoader());

    private String targetBytecode;

    @Override
    public void setClassPath(List<String> classPath) {
        if (classPath == null || classPath.isEmpty()) {
            return;
        }

        classPath.stream().map(this::toUrl).forEach(childFirstLoader::addURL);
    }

    @Override
    public void setTargetBytecode(String version) {
        this.targetBytecode = version;
    }

    private URL toUrl(String path) {
        try {
            return new File(path).toURI().toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Maps a Maven-style release value (for example <code>"8"</code>) to the version string Groovy's
     * {@link CompilerConfiguration#setTargetBytecode(String)} expects (for example <code>"1.8"</code>). Groovy uses
     * the <code>"1.x"</code> form for JDK 4 through 8, and bare version numbers from JDK 9 onward.
     *
     * @param version The Maven-style release value, must not be <code>null</code>.
     * @return The Groovy-compatible bytecode version string.
     */
    static String normalizeTargetBytecode(String version) {
        switch (version) {
            case "4":
            case "5":
            case "6":
            case "7":
            case "8":
                return "1." + version;
            default:
                return version;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object evaluateScript(String script, Map<String, ?> globalVariables, PrintStream scriptOutput)
            throws ScriptEvaluationException {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;

        ClassLoader curentClassLoader = Thread.currentThread().getContextClassLoader();
        try {

            if (scriptOutput != null) {
                System.setErr(scriptOutput);
                System.setOut(scriptOutput);
            }

            CompilerConfiguration compilerConfiguration = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
            if (targetBytecode != null) {
                compilerConfiguration.setTargetBytecode(normalizeTargetBytecode(targetBytecode));
            }

            GroovyShell interpreter =
                    new GroovyShell(childFirstLoader, new Binding(globalVariables), compilerConfiguration);

            Thread.currentThread().setContextClassLoader(childFirstLoader);
            return interpreter.evaluate(script);
        } catch (Throwable e) {
            throw new ScriptEvaluationException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(curentClassLoader);
            System.setErr(origErr);
            System.setOut(origOut);
        }
    }

    @Override
    public void close() throws IOException {
        childFirstLoader.close();
    }
}
