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
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Olivier Lamy
 */
public class ScriptRunnerTest {

    @Test
    public void testBeanshell() throws Exception {
        File logFile = new File("target/build.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler)) {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.setGlobalVariable("globalVar", "Yeah baby it's rocks");
            scriptRunner.run("test", new File("src/test/resources/bsh-test"), "verify", buildContext(), fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains(new File("src/test/resources/bsh-test/verify.bsh").getPath()));
        assertTrue(logContent.contains("foo=bar"));
        assertTrue(logContent.contains("globalVar=Yeah baby it's rocks"));

        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    public void beanshellReturnedNullShouldBeOk() throws Exception {
        File logFile = new File("target/build.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler)) {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.run("test", new File("src/test/resources/bsh-test"), "return-null", null, fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains(new File("src/test/resources/bsh-test/return-null.bsh").getPath()));
        assertTrue(logContent.contains("ok with null result"));
        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    public void failedBeanshellShouldCreateProperLogsMessage() throws Exception {
        File logFile = new File("target/build.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        Exception catchedException = null;

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler)) {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.run("test", new File("src/test/resources/bsh-test"), "failed", buildContext(), fileLogger);
        } catch (ScriptEvaluationException e) {
            catchedException = e;
        }

        assertNotNull(catchedException);
        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains(new File("src/test/resources/bsh-test/failed.bsh").getPath()));
        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    public void beanshellReturnedNotTrueShouldThrowException() throws Exception {
        File logFile = new File("target/build.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        ScriptReturnException catchedException = null;

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler)) {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.run("test", new File("src/test/resources/bsh-test"), "return-not-true", null, fileLogger);
        } catch (ScriptReturnException e) {
            catchedException = e;
        }

        assertEquals("Not true value", catchedException.getResult());
        assertEquals("The test returned Not true value.", catchedException.getMessage());
        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains(new File("src/test/resources/bsh-test/return-not-true.bsh").getPath()));
        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    public void testBeanshellWithFile() throws Exception {
        File logFile = new File("target/build.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler)) {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.setGlobalVariable("globalVar", "Yeah baby it's rocks");
            scriptRunner.run("test", new File("src/test/resources/bsh-test/verify.bsh"), buildContext(), fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains(new File("src/test/resources/bsh-test/verify.bsh").getPath()));
        assertTrue(logContent.contains("foo=bar"));

        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    public void testGroovy() throws Exception {
        File logFile = new File("target/build.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler)) {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.setGlobalVariable("globalVar", "Yeah baby it's rocks");
            scriptRunner.run("test", new File("src/test/resources/groovy-test"), "verify", buildContext(), fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains(new File("src/test/resources/groovy-test/verify.groovy").getPath()));
        assertTrue(logContent.contains("foo=bar"));
        assertTrue(logContent.contains("globalVar=Yeah baby it's rocks"));

        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    public void groovyReturnedNullShouldBeOk() throws Exception {
        File logFile = new File("target/build.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler)) {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.setGlobalVariable("globalVar", "Yeah baby it's rocks");
            scriptRunner.run("test", new File("src/test/resources/groovy-test"), "return-null", null, fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains(new File("src/test/resources/groovy-test/return-null.groovy").getPath()));
        assertTrue(logContent.contains("ok with null result"));
        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    public void failedGroovyShouldCreateProperLogsMessage() throws Exception {
        File logFile = new File("target/build.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        Exception catchedException = null;

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler)) {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.run("test", new File("src/test/resources/groovy-test"), "failed", buildContext(), fileLogger);
        } catch (ScriptEvaluationException e) {
            catchedException = e;
        }

        assertNotNull(catchedException);
        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains(new File("src/test/resources/groovy-test/failed.groovy").getPath()));
        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    public void groovyReturnedFalseShouldThrowException() throws Exception {
        File logFile = new File("target/build.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        ScriptReturnException catchedException = null;

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler)) {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.run(
                    "test", new File("src/test/resources/groovy-test"), "return-false", buildContext(), fileLogger);
        } catch (ScriptReturnException e) {
            catchedException = e;
        }

        assertEquals(false, catchedException.getResult());
        assertEquals("The test returned false.", catchedException.getMessage());
        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains(new File("src/test/resources/groovy-test/return-false.groovy").getPath()));
        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    public void testGroovyWithFile() throws Exception {
        File logFile = new File("target/build.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler)) {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.run(
                    "test", new File("src/test/resources/groovy-test/verify.groovy"), buildContext(), fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains(new File("src/test/resources/groovy-test/verify.groovy").getPath()));
        assertTrue(logContent.contains("foo=bar"));

        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    private Map<String, ?> buildContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("foo", "bar");
        return context;
    }
}
