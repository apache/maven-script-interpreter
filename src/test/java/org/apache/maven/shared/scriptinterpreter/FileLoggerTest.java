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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileLoggerTest
 */
public class FileLoggerTest {

    public static final String EXPECTED_LOG = "Test1\nTest2\n";

    @Test
    void nullOutputFileNoMirror() throws Exception {
        try (FileLogger fileLogger = new FileLogger(null)) {
            fileLogger.consumeLine("Test1");
            fileLogger.getPrintStream().print("Test2");
            fileLogger.getPrintStream().print('\n');
            fileLogger.getPrintStream().flush();

            assertNull(fileLogger.getOutputFile());
        }
    }

    @Test
    void nullOutputFileWithMirror() throws Exception {
        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(null, mirrorHandler)) {
            fileLogger.consumeLine("Test1");
            fileLogger.getPrintStream().print("Test2");
            fileLogger.getPrintStream().print('\n');
            fileLogger.getPrintStream().flush();

            assertNull(fileLogger.getOutputFile());
        }

        assertEquals(EXPECTED_LOG, mirrorHandler.getLoggedMessage());
    }

    @Test
    void nullOutputFileWithMirrorWriteByte() throws Exception {
        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(null, mirrorHandler)) {
            fileLogger.getPrintStream().write('A');
            fileLogger.getPrintStream().flush();

            assertNull(fileLogger.getOutputFile());
        }

        assertEquals("A\n", mirrorHandler.getLoggedMessage());
    }

    @Test
    void outputFileNoMirror(@TempDir File tempDir) throws Exception {
        File outputFile = new File(tempDir, "/target/test.log");

        try (FileLogger fileLogger = new FileLogger(outputFile)) {
            fileLogger.consumeLine("Test1");
            fileLogger.getPrintStream().print("Test2");
            fileLogger.getPrintStream().print('\n');
            fileLogger.getPrintStream().flush();

            assertEquals(outputFile, fileLogger.getOutputFile());
        }

        assertTrue(outputFile.exists());
        assertEquals(EXPECTED_LOG, new String(Files.readAllBytes(outputFile.toPath())));
    }

    @Test
    void outputFileWithMirror(@TempDir File tempDir) throws Exception {
        File outputFile = new File(tempDir, "target/test.log");
        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(outputFile, mirrorHandler)) {
            fileLogger.consumeLine("Test1");
            fileLogger.getPrintStream().print("Test2");
            fileLogger.getPrintStream().print('\n');
            fileLogger.getPrintStream().flush();

            assertEquals(outputFile, fileLogger.getOutputFile());
        }

        assertEquals(EXPECTED_LOG, mirrorHandler.getLoggedMessage());

        assertTrue(outputFile.exists());
        assertEquals(EXPECTED_LOG, new String(Files.readAllBytes(outputFile.toPath())));
    }

    /**
     * Verifies that non-ASCII content written through the logger is encoded and decoded as UTF-8
     * on both the mirror handler and the underlying file, independent of the platform default
     * charset and line separator.
     * "café" in UTF-8: {0x63, 0x61, 0x66, 0xC3, 0xA9, 0x0A}.
     * A platform that decodes these bytes as ISO-8859-1 would produce "cafÃ©" instead of "café".
     */
    @Test
    void mirrorAndFileShouldUseUtf8(@TempDir File tempDir) throws Exception {
        File outputFile = new File(tempDir, "utf8.log");
        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(outputFile, mirrorHandler)) {
            fileLogger.getPrintStream().write("café".getBytes(StandardCharsets.UTF_8));
            fileLogger.getPrintStream().write('\n');
            fileLogger.getPrintStream().flush();
        }

        assertEquals("café\n", mirrorHandler.getLoggedMessage());
        assertTrue(outputFile.exists());
        assertArrayEquals("café\n".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(outputFile.toPath()));
    }
}
