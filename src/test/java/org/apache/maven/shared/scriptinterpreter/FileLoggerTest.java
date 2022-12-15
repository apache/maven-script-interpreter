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
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileLoggerTest
 */
public class FileLoggerTest {

    public static final String EXPECTED_LOG = "Test1" + System.lineSeparator() + "Test2" + System.lineSeparator();

    @Test
    public void nullOutputFileNoMirror() throws IOException {
        try (FileLogger fileLogger = new FileLogger(null)) {
            fileLogger.consumeLine("Test1");
            fileLogger.getPrintStream().println("Test2");
            fileLogger.getPrintStream().flush();

            assertNull(fileLogger.getOutputFile());
        }
    }

    @Test
    public void nullOutputFileWithMirror() throws IOException {
        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(null, mirrorHandler)) {
            fileLogger.consumeLine("Test1");
            fileLogger.getPrintStream().println("Test2");
            fileLogger.getPrintStream().flush();

            assertNull(fileLogger.getOutputFile());
        }

        assertEquals(EXPECTED_LOG, mirrorHandler.getLoggedMessage());
    }

    @Test
    public void nullOutputFileWithMirrorWriteByte() throws IOException {
        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(null, mirrorHandler)) {
            fileLogger.getPrintStream().write('A');
            fileLogger.getPrintStream().flush();

            assertNull(fileLogger.getOutputFile());
        }

        assertEquals("A" + System.lineSeparator(), mirrorHandler.getLoggedMessage());
    }

    @Test
    public void outputFileNoMirror() throws IOException {
        File outputFile = new File("target/test.log");
        if (outputFile.exists()) {
            outputFile.delete();
        }

        try (FileLogger fileLogger = new FileLogger(outputFile)) {
            fileLogger.consumeLine("Test1");
            fileLogger.getPrintStream().println("Test2");
            fileLogger.getPrintStream().flush();

            assertEquals(outputFile, fileLogger.getOutputFile());
        }

        assertTrue(outputFile.exists());
        assertEquals(EXPECTED_LOG, new String(Files.readAllBytes(outputFile.toPath())));
    }

    @Test
    public void outputFileWithMirror() throws IOException {
        File outputFile = new File("target/test.log");
        if (outputFile.exists()) {
            outputFile.delete();
        }
        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try (FileLogger fileLogger = new FileLogger(outputFile, mirrorHandler)) {
            fileLogger.consumeLine("Test1");
            fileLogger.getPrintStream().println("Test2");
            fileLogger.getPrintStream().flush();

            assertEquals(outputFile, fileLogger.getOutputFile());
        }

        assertEquals(EXPECTED_LOG, mirrorHandler.getLoggedMessage());

        assertTrue(outputFile.exists());
        assertEquals(EXPECTED_LOG, new String(Files.readAllBytes(outputFile.toPath())));
    }
}
