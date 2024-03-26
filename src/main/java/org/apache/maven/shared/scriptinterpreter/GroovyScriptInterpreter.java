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

    @Override
    public void setClassPath(List<String> classPath) {
        if (classPath == null || classPath.isEmpty()) {
            return;
        }

        classPath.stream().map(this::toUrl).forEach(childFirstLoader::addURL);
    }

    private URL toUrl(String path) {
        try {
            return new File(path).toURI().toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
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

        try {

            if (scriptOutput != null) {
                System.setErr(scriptOutput);
                System.setOut(scriptOutput);
            }

            GroovyShell interpreter = new GroovyShell(
                    childFirstLoader,
                    new Binding(globalVariables),
                    new CompilerConfiguration(CompilerConfiguration.DEFAULT));

            return interpreter.evaluate(script);
        } catch (Throwable e) {
            throw new ScriptEvaluationException(e);
        } finally {
            System.setErr(origErr);
            System.setOut(origOut);
        }
    }

    @Override
    public void close() throws IOException {
        childFirstLoader.close();
    }
}
