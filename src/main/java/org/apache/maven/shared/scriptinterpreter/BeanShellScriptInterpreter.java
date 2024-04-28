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
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;

import bsh.Capabilities;
import bsh.EvalError;
import bsh.Interpreter;
import bsh.TargetError;

/**
 * Provides a facade to evaluate BeanShell scripts.
 *
 * @author Benjamin Bentmann
 */
class BeanShellScriptInterpreter implements ScriptInterpreter {

    private static class ChildFirstURLClassLoader extends URLClassLoader {
        ChildFirstURLClassLoader() {
            super(new URL[] {}, Thread.currentThread().getContextClassLoader());
        }

        @Override
        public void addURL(URL url) {
            super.addURL(url);
        }

        @Override
        protected synchronized Class<?> loadClass(final String name, final boolean resolve)
                throws ClassNotFoundException {

            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }

            try {
                c = super.findClass(name);
            } catch (ClassNotFoundException e) {
                // ignore
            }

            if (c == null) {
                c = super.loadClass(name, resolve);
            }

            if (resolve) {
                resolveClass(c);
            }

            return c;
        }

        @Override
        public URL getResource(final String name) {
            URL url = findResource(name);
            return url != null ? url : super.getResource(name);
        }
    }

    private final ChildFirstURLClassLoader classLoader = new ChildFirstURLClassLoader();

    @Override
    public void setClassPath(List<String> classPath) {
        if (classPath == null || classPath.isEmpty()) {
            return;
        }

        classPath.stream().map(this::toUrl).forEach(classLoader::addURL);
    }

    private URL toUrl(String path) {
        try {
            return new File(path).toURI().toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Object evaluateScript(String script, Map<String, ?> globalVariables, PrintStream scriptOutput)
            throws ScriptEvaluationException {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;

        try {
            Interpreter engine = new Interpreter();

            if (scriptOutput != null) {
                System.setErr(scriptOutput);
                System.setOut(scriptOutput);
                engine.setErr(scriptOutput);
                engine.setOut(scriptOutput);
            }

            if (!Capabilities.haveAccessibility()) {
                try {
                    Capabilities.setAccessibility(true);
                } catch (Exception e) {
                    if (scriptOutput != null) {
                        e.printStackTrace(scriptOutput);
                    }
                }
            }

            engine.setClassLoader(classLoader);

            if (globalVariables != null) {
                for (Map.Entry<String, ?> entry : globalVariables.entrySet()) {
                    try {
                        engine.set(entry.getKey(), entry.getValue());
                    } catch (EvalError e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            ClassLoader curentClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(classLoader);
                return engine.eval(script);
            } catch (TargetError e) {
                throw new ScriptEvaluationException(e.getTarget());
            } catch (ThreadDeath e) {
                throw e;
            } catch (Throwable e) {
                throw new ScriptEvaluationException(e);
            } finally {
                Thread.currentThread().setContextClassLoader(curentClassLoader);
            }
        } finally {
            System.setErr(origErr);
            System.setOut(origOut);
        }
    }

    @Override
    public void close() throws IOException {
        classLoader.close();
    }
}
