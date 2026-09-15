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

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests the Groovy interpreter facade.
 *
 * @author Benjamin Bentmann
 */
class GroovyScriptInterpreterTest {

    @TempDir
    private File tempDir;

    @Test
    void evaluateScript() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ScriptInterpreter interpreter = new GroovyScriptInterpreter()) {
            assertEquals(
                    Boolean.TRUE,
                    interpreter.evaluateScript("print \"Test\"\nreturn true", null, new PrintStream(out)));
        }
        assertEquals("Test", out.toString());
    }

    @Test
    void evaluateScriptWithDefaultClassPath() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ScriptInterpreter interpreter = new GroovyScriptInterpreter()) {
            assertEquals(
                    Boolean.TRUE,
                    interpreter.evaluateScript(
                            "print getClass().getResource( \"/class-path.txt\" ).getPath().toURI().getPath()\nreturn true",
                            null,
                            new PrintStream(out)));
        }

        String testClassPath =
                new File("target/test-classes/class-path.txt").toURI().getPath();
        assertEquals(testClassPath, out.toString());
    }

    @Test
    void evaluateScriptWithCustomClassPath() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ScriptInterpreter interpreter = new GroovyScriptInterpreter()) {

            List<String> classPath = Collections.singletonList(new File("src/test-class-path").getAbsolutePath());
            interpreter.setClassPath(classPath);

            assertEquals(
                    Boolean.TRUE,
                    interpreter.evaluateScript(
                            "print getClass().getResource( \"/class-path.txt\" ).getPath().toURI().getPath()\nreturn true",
                            null,
                            new PrintStream(out)));
        }

        String testClassPath =
                new File("src/test-class-path/class-path.txt").toURI().getPath();
        assertEquals(testClassPath, out.toString());
    }

    @Test
    void evaluateScriptVars() throws Exception {
        Map<String, Object> vars = new HashMap<>();
        vars.put("testVar", "data");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ScriptInterpreter interpreter = new GroovyScriptInterpreter()) {
            assertEquals(
                    Boolean.TRUE, interpreter.evaluateScript("print testVar\nreturn true", vars, new PrintStream(out)));
        }
        assertEquals("data", out.toString());
    }

    @Test
    void evaluateScriptWithTargetBytecode() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ScriptInterpreter interpreter = new GroovyScriptInterpreter()) {
            interpreter.setTargetBytecode("8");
            assertEquals(
                    Boolean.TRUE,
                    interpreter.evaluateScript("print \"Test\"\nreturn true", null, new PrintStream(out)));
        }
        assertEquals("Test", out.toString());
    }

    /**
     * A Groovy of a different version on the caller-supplied class path must not shadow the Groovy the scripts are
     * compiled with, while every other class stays child-first.
     *
     * @see <a href="https://github.com/apache/maven-invoker-plugin/issues/642">maven-invoker-plugin#642</a>
     */
    @Test
    void groovyClassesAreLoadedParentFirstOtherClassesChildFirst() throws Exception {
        File classesDir = compileShadowClasses();

        try (GroovyScriptInterpreter.GroovyParentFirstRootLoader loader =
                new GroovyScriptInterpreter.GroovyParentFirstRootLoader(
                        getClass().getClassLoader())) {
            loader.addURL(classesDir.toURI().toURL());

            assertSame(groovy.lang.Binding.class, loader.loadClass("groovy.lang.Binding"));

            Class<?> shadowed = loader.loadClass(FilenameUtils.class.getName());
            assertNotSame(FilenameUtils.class, shadowed);
            assertSame(loader, shadowed.getClassLoader());
        }
    }

    /**
     * A script must still compile and run when a stale Groovy sits on the supplied class path.
     */
    @Test
    void evaluateScriptWithShadowedGroovyOnClassPath() throws Exception {
        File classesDir = compileShadowClasses();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ScriptInterpreter interpreter = new GroovyScriptInterpreter()) {
            interpreter.setClassPath(Collections.singletonList(classesDir.getAbsolutePath()));
            assertEquals(
                    Boolean.TRUE,
                    interpreter.evaluateScript("print \"Test\"\nreturn true", null, new PrintStream(out)));
        }
        assertEquals("Test", out.toString());
    }

    /**
     * Compiles stripped-down copies of <code>groovy.lang.Binding</code>, <code>groovy.lang.Script</code> and
     * <code>org.apache.commons.io.FilenameUtils</code>, all of which also exist on the parent class path, into a
     * directory usable as an additional class path entry. This stands in for the incompatible Groovy version that
     * <code>addTestClassPath</code> puts in front of the interpreter's own.
     */
    private File compileShadowClasses() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "JDK compiler is not available");

        File sourceDir = new File(tempDir, "src");
        File classesDir = new File(tempDir, "classes");
        assertTrue(classesDir.mkdirs() || classesDir.isDirectory());

        File binding = writeSource(
                sourceDir, "groovy/lang/Binding.java", "package groovy.lang;\n\npublic class Binding {\n}\n");
        File script =
                writeSource(sourceDir, "groovy/lang/Script.java", "package groovy.lang;\n\npublic class Script {\n}\n");
        File filenameUtils = writeSource(
                sourceDir,
                "org/apache/commons/io/FilenameUtils.java",
                "package org.apache.commons.io;\n\npublic class FilenameUtils {\n}\n");

        assertTrue(
                compiler.run(
                                null,
                                null,
                                null,
                                "-d",
                                classesDir.getAbsolutePath(),
                                binding.getAbsolutePath(),
                                script.getAbsolutePath(),
                                filenameUtils.getAbsolutePath())
                        == 0,
                "compilation of the shadowing classes failed");

        return classesDir;
    }

    private File writeSource(File sourceDir, String relativePath, String content) throws Exception {
        File sourceFile = new File(sourceDir, relativePath);
        assertTrue(sourceFile.getParentFile().mkdirs()
                || sourceFile.getParentFile().isDirectory());
        Files.write(sourceFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return sourceFile;
    }

    @Test
    void normalizeTargetBytecodeMapsOldJdksToDotForm() {
        assertEquals("1.4", GroovyScriptInterpreter.normalizeTargetBytecode("4"));
        assertEquals("1.8", GroovyScriptInterpreter.normalizeTargetBytecode("8"));
        assertEquals("9", GroovyScriptInterpreter.normalizeTargetBytecode("9"));
        assertEquals("17", GroovyScriptInterpreter.normalizeTargetBytecode("17"));
    }
}
