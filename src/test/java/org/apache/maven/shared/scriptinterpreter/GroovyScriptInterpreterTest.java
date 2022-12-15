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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the Groovy interpreter facade.
 *
 * @author Benjamin Bentmann
 */
public class GroovyScriptInterpreterTest {
    @Test
    public void testEvaluateScript() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ScriptInterpreter interpreter = new GroovyScriptInterpreter();
        assertEquals(
                Boolean.TRUE,
                interpreter.evaluateScript("print \"Test\"\nreturn true", null, null, new PrintStream(out)));
        assertEquals("Test", out.toString());
    }

    @Test
    public void testEvaluateScriptWithDefaultClassPath() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ScriptInterpreter interpreter = new GroovyScriptInterpreter();

        assertEquals(
                Boolean.TRUE,
                interpreter.evaluateScript(
                        "print getClass().getResource( \"/class-path.txt\" ).getPath().toURI().getPath()\nreturn true",
                        null,
                        null,
                        new PrintStream(out)));

        String testClassPath =
                new File("target/test-classes/class-path.txt").toURI().getPath();
        assertEquals(testClassPath, out.toString());
    }

    @Test
    public void testEvaluateScriptWithCustomClassPath() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ScriptInterpreter interpreter = new GroovyScriptInterpreter();

        List<String> classPath = Collections.singletonList(new File("src/test-class-path").getAbsolutePath());

        assertEquals(
                Boolean.TRUE,
                interpreter.evaluateScript(
                        "print getClass().getResource( \"/class-path.txt\" ).getPath().toURI().getPath()\nreturn true",
                        classPath,
                        null,
                        new PrintStream(out)));

        String testClassPath =
                new File("src/test-class-path/class-path.txt").toURI().getPath();
        assertEquals(testClassPath, out.toString());
    }

    @Test
    public void testEvaluateScriptVars() throws Exception {
        Map<String, Object> vars = new HashMap<>();
        vars.put("testVar", "data");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ScriptInterpreter interpreter = new GroovyScriptInterpreter();
        assertEquals(
                Boolean.TRUE,
                interpreter.evaluateScript("print testVar\nreturn true", null, vars, new PrintStream(out)));
        assertEquals("data", out.toString());
    }
}
