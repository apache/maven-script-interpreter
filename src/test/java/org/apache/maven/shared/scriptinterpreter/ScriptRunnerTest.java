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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Olivier Lamy
 */
class ScriptRunnerTest {

    @TempDir
    private File tempDir;

    @Test
    void beanshell() throws Exception {
        File logFile = new File(tempDir, "build.log");

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.setGlobalVariable("globalVar", "Yeah baby it's rocks");
            scriptRunner.run("test", new File("src/test/resources/bsh-test"), "verify", buildContext(), fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(logContent.contains(new File("src/test/resources/bsh-test/verify.bsh").getPath()));
        assertTrue(logContent.contains("foo=bar"));
        assertTrue(logContent.contains("globalVar=Yeah baby it's rocks"));

        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    void beanshellDeprecationWarnedOncePerScript() throws Exception {
        File logFile = new File(tempDir, "build.log");

        try (FileLogger fileLogger = new FileLogger(logFile);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.run("first", new File("src/test/resources/bsh-test"), "return-null", null, fileLogger);
            scriptRunner.run("second", new File("src/test/resources/bsh-test"), "return-null", null, fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        String warning = "[WARNING] BeanShell scripts are deprecated";
        assertEquals(logContent.indexOf(warning), logContent.lastIndexOf(warning), logContent);
        assertTrue(logContent.contains(warning), logContent);
        assertTrue(logContent.contains("Finished second"), logContent);
    }

    @Test
    void beanshellDeprecationWarningCanBeSwitchedOff() throws Exception {
        File logFile = new File(tempDir, "build.log");

        try (FileLogger fileLogger = new FileLogger(logFile);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.setBeanShellWarning(false);
            scriptRunner.run("test", new File("src/test/resources/bsh-test"), "return-null", null, fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        assertFalse(logContent.contains("BeanShell scripts are deprecated"), logContent);
        assertTrue(logContent.contains("Finished test"), logContent);
    }

    @Test
    void beanshellReturnedNullShouldBeOk() throws Exception {
        File logFile = new File(tempDir, "build.log");

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.run("test", new File("src/test/resources/bsh-test"), "return-null", null, fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(logContent.contains(new File("src/test/resources/bsh-test/return-null.bsh").getPath()));
        assertTrue(logContent.contains("ok with null result"));
        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    void failedBeanshellShouldCreateProperLogsMessage() throws Exception {
        File logFile = new File(tempDir, "build.log");
        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.run("test", new File("src/test/resources/bsh-test"), "failed", buildContext(), fileLogger);
            fail();
        } catch (ScriptEvaluationException expected) {
            String logContent = new String(Files.readAllBytes(logFile.toPath()));
            assertTrue(logContent.contains(new File("src/test/resources/bsh-test/failed.bsh").getPath()));
            assertEquals(logContent, mirrorHandler.getLoggedMessage());
        }
    }

    @Test
    void beanshellReturnedNotTrueShouldThrowException() throws Exception {
        File logFile = new File(tempDir, "build.log");
        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.run("test", new File("src/test/resources/bsh-test"), "return-not-true", null, fileLogger);
            fail();
        } catch (ScriptReturnException expected) {
            assertEquals("Not true value", expected.getResult());
            assertEquals("The test returned Not true value.", expected.getMessage());
            String logContent = new String(Files.readAllBytes(logFile.toPath()));
            assertTrue(logContent.contains(new File("src/test/resources/bsh-test/return-not-true.bsh").getPath()));
            assertEquals(logContent, mirrorHandler.getLoggedMessage());
        }
    }

    @Test
    void beanshellWithFile() throws Exception {
        File logFile = new File(tempDir, "build.log");

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.setGlobalVariable("globalVar", "Yeah baby it's rocks");
            scriptRunner.run("test", new File("src/test/resources/bsh-test/verify.bsh"), buildContext(), fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(logContent.contains(new File("src/test/resources/bsh-test/verify.bsh").getPath()));
        assertTrue(logContent.contains("foo=bar"));

        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    void groovy() throws Exception {
        File logFile = new File(tempDir, "build.log");

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.setGlobalVariable("globalVar", "Yeah baby it's rocks");
            scriptRunner.run("test", new File("src/test/resources/groovy-test"), "verify", buildContext(), fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(logContent.contains(new File("src/test/resources/groovy-test/verify.groovy").getPath()));
        assertTrue(logContent.contains("foo=bar"));
        assertTrue(logContent.contains("globalVar=Yeah baby it's rocks"));

        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    void unknownExtensionRunsAsGroovy() throws Exception {
        File logFile = new File(tempDir, "build.log");

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.setGlobalVariable("globalVar", "Yeah baby it's rocks");
            scriptRunner.run(
                    "test", new File("src/test/resources/groovy-test/verify.script"), buildContext(), fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains("foo=bar"));
        assertTrue(logContent.contains("globalVar=Yeah baby it's rocks"));
    }

    @Test
    void groovyReturnedNullShouldBeOk() throws Exception {
        File logFile = new File(tempDir, "build.log");

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.setGlobalVariable("globalVar", "Yeah baby it's rocks");
            scriptRunner.run("test", new File("src/test/resources/groovy-test"), "return-null", null, fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(logContent.contains(new File("src/test/resources/groovy-test/return-null.groovy").getPath()));
        assertTrue(logContent.contains("ok with null result"));
        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    void failedGroovyShouldCreateProperLogsMessage() throws Exception {
        File logFile = new File(tempDir, "build.log");
        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            assertThrows(
                    ScriptEvaluationException.class,
                    () -> scriptRunner.run(
                            "test", new File("src/test/resources/groovy-test"), "failed", buildContext(), fileLogger));
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains(new File("src/test/resources/groovy-test/failed.groovy").getPath()));
        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @Test
    void groovyReturnedFalseShouldThrowException() throws Exception {
        File logFile = new File(tempDir, "build.log");
        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.run(
                    "test", new File("src/test/resources/groovy-test"), "return-false", buildContext(), fileLogger);
            fail();
        } catch (ScriptReturnException expected) {
            assertFalse((Boolean) expected.getResult());
            assertEquals("The test returned false.", expected.getMessage());
            String logContent = new String(Files.readAllBytes(logFile.toPath()));
            assertTrue(logContent.contains(new File("src/test/resources/groovy-test/return-false.groovy").getPath()));
            assertEquals(logContent, mirrorHandler.getLoggedMessage());
        }
    }

    @Test
    void groovyWithFile() throws Exception {
        File logFile = new File(tempDir, "build.log");

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(logFile, mirrorHandler);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.run(
                    "test", new File("src/test/resources/groovy-test/verify.groovy"), buildContext(), fileLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(logContent.contains(new File("src/test/resources/groovy-test/verify.groovy").getPath()));
        assertTrue(logContent.contains("foo=bar"));

        assertEquals(logContent, mirrorHandler.getLoggedMessage());
    }

    @ValueSource(strings = {"bsh", "groovy"})
    @ParameterizedTest
    void theSameClassloaderShouldBeUsed(String scriptType) throws Exception {

        Map<String, ?> context = buildContext();
        File logFile = new File(tempDir, "build.log");
        File basedir = new File(String.format("target/test-classes/%s-test", scriptType));

        try (FileLogger logger = new FileLogger(logFile);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.setClassPath(Collections.singletonList("target/dependency/wiremock-jre8-standalone.jar"));

            scriptRunner.run("test classpath 1", basedir, "class-path1", context, logger);
            assertNotNull(context.get("wireMockServer"));

            scriptRunner.run("test classpath 2", basedir, "class-path2", context, logger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(logContent.contains("wireMockServer started with port="));
        assertTrue(logContent.contains("wireMockServer stopped"));
    }

    /**
     * A script living in a subdirectory of the project must still see the project directory as <code>basedir</code>,
     * with its own directory available as <code>scriptdir</code>.
     *
     * @see <a href="https://github.com/apache/maven-invoker-plugin/issues/299">maven-invoker-plugin#299</a>
     */
    @ValueSource(strings = {"bsh", "groovy"})
    @ParameterizedTest
    void scriptInSubDirectoryShouldSeeProjectBasedir(String scriptType) throws Exception {
        File logFile = new File(tempDir, "build.log");
        File basedir = new File("src/test/resources/basedir-test");

        Map<String, Object> context = new HashMap<>();

        try (FileLogger logger = new FileLogger(logFile);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.run("test", basedir, "scripts/verify." + scriptType, context, logger);
        }

        assertEquals(basedir, context.get("basedir"));
        assertEquals(new File(basedir, "scripts"), context.get("scriptdir"));
    }

    /**
     * The overload without a project directory has no better candidate, so both variables point at the script's own
     * directory.
     */
    @ValueSource(strings = {"bsh", "groovy"})
    @ParameterizedTest
    void scriptRunWithoutBasedirShouldUseScriptDirectory(String scriptType) throws Exception {
        File logFile = new File(tempDir, "build.log");
        File scriptDir = new File("src/test/resources/basedir-test/scripts");

        Map<String, Object> context = new HashMap<>();

        try (FileLogger logger = new FileLogger(logFile);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.run("test", new File(scriptDir, "verify." + scriptType), context, logger);
        }

        assertEquals(scriptDir, context.get("basedir"));
        assertEquals(scriptDir, context.get("scriptdir"));
    }

    /**
     * Verifies that a Groovy script containing non-ASCII characters (UTF-8 encoded)
     * is decoded correctly when no explicit encoding is set. The script file
     * utf8-test.groovy contains the UTF-8 string literal "café" and asserts it
     * matches the expected value, returning true on success.
     *
     * @see <a href="https://github.com/apache/maven-script-interpreter/issues/206">#206</a>
     */
    @Test
    void groovyUtf8ScriptShouldDecodeCorrectly() throws Exception {
        File logFile = new File(tempDir, "build.log");

        Map<String, Object> context = new HashMap<>();
        context.put("expected", "café");

        try (FileLogger logger = new FileLogger(logFile);
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.run("utf8-test", new File("src/test/resources/groovy-test/utf8-test.groovy"), context, logger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(logContent.contains("expected=café"));
        assertTrue(logContent.contains("actual=café"));
    }

    @Test
    void groovyRunVariablesAreMergedForOneRunOnly() throws Exception {
        File logFile = new File(tempDir, "build.log");
        File otherLogFile = new File(tempDir, "other.log");
        File groovyDir = new File("src/test/resources/groovy-test");

        try (FileLogger fileLogger = new FileLogger(logFile, new TestMirrorHandler());
                FileLogger otherLogger = new FileLogger(otherLogFile, new TestMirrorHandler());
                ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.setGlobalVariable("shared", "global");
            Map<String, Object> variables = new HashMap<>();
            variables.put("runVar", "first");
            variables.put("shared", "run");
            // must not shadow the documented variables
            variables.put("basedir", new File("ignored"));
            scriptRunner.run("test", groovyDir.toPath(), "run-variables", buildContext(), variables, fileLogger);

            // a later run sees only its own variables: the earlier override of "shared" is gone
            Map<String, Object> second = new HashMap<>();
            second.put("runVar", "second");
            scriptRunner.run("test", groovyDir.toPath(), "run-variables", buildContext(), second, otherLogger);
        }

        String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(logContent.contains("runVar=first"));
        assertTrue(logContent.contains("shared=run"));
        assertTrue(logContent.contains("basedirName=groovy-test"));

        String otherContent = new String(Files.readAllBytes(otherLogFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(otherContent.contains("runVar=second"));
        assertTrue(otherContent.contains("shared=global"));
    }

    @Test
    void absoluteScriptPathStaysUnderBasedir() throws Exception {
        Path project = Files.createDirectory(tempDir.toPath().resolve("project"));
        Path script = tempDir.toPath().resolve("outside.groovy").toAbsolutePath();
        Files.write(script, "context.put('ran', 'outside.groovy')\nreturn true\n".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> context = new HashMap<>();
        try (ScriptRunner scriptRunner = new ScriptRunner()) {
            // resolved as basedir + path, so an absolute path names no existing file and nothing runs
            scriptRunner.run("test", project, script.toString(), context, null, null);
        }

        assertTrue(context.isEmpty());
    }

    @Test
    void scriptPathThatIsNoValidPathRunsNothing() throws Exception {
        Map<String, Object> context = new HashMap<>();
        try (ScriptRunner scriptRunner = new ScriptRunner()) {
            // as an absolute path with a drive letter joined to the base directory on Windows
            scriptRunner.run("test", tempDir.toPath(), "a\u0000b", context, null, null);
        }

        assertTrue(context.isEmpty());
    }

    @Test
    void emptyScriptPathRunsNothing() throws Exception {
        Path project = Files.createDirectory(tempDir.toPath().resolve("project"));
        Files.write(
                tempDir.toPath().resolve("project.groovy"),
                "context.put('ran', 'project.groovy')\nreturn true\n".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> context = new HashMap<>();
        try (ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.run("test", project, "", context, null, null);
        }

        assertTrue(context.isEmpty());
    }

    @Test
    void directoryNamedLikeScriptFallsThroughToScriptWithExtension() throws Exception {
        Files.createDirectory(tempDir.toPath().resolve("setup"));
        Files.write(
                tempDir.toPath().resolve("setup.groovy"),
                "context.put('ran', 'setup.groovy')\nreturn true\n".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> context = new HashMap<>();
        try (ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.run("test", tempDir.toPath(), "setup", context, null, null);
        }

        assertEquals("setup.groovy", context.get("ran"));
    }

    @Test
    void directoryNamedLikeScriptWithoutScriptBesideItIsTreatedAsMissing() throws Exception {
        Files.createDirectory(tempDir.toPath().resolve("setup"));

        Map<String, Object> context = new HashMap<>();
        try (ScriptRunner scriptRunner = new ScriptRunner()) {
            scriptRunner.run("test", tempDir.toPath(), "setup", context, null, null);
        }

        assertTrue(context.isEmpty());
    }

    private Map<String, ?> buildContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("foo", "bar");
        return context;
    }
}
