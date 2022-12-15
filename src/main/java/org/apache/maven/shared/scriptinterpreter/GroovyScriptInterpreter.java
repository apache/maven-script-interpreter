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
import java.io.PrintStream;
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

    /** {@inheritDoc} */
    @Override
    public Object evaluateScript(
            String script,
            List<String> classPath,
            Map<String, ? extends Object> globalVariables,
            PrintStream scriptOutput)
            throws ScriptEvaluationException {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;

        try (RootLoader childFirstLoader =
                new RootLoader(new URL[] {}, getClass().getClassLoader())) {

            if (scriptOutput != null) {
                System.setErr(scriptOutput);
                System.setOut(scriptOutput);
            }

            if (classPath != null && !classPath.isEmpty()) {
                for (String path : classPath) {
                    childFirstLoader.addURL(new File(path).toURI().toURL());
                }
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
}
