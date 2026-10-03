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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Script output goes to the script's own log, other threads keep the real streams, and scripts do not wait for
 * each other.
 */
class ScriptOutputRedirectTest {

    @TempDir
    File tempDir;

    @Test
    void concurrentScriptsKeepTheirOutputApart() throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outsideOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outsideOut, true, "UTF-8"));
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            // the two scripts wait for each other at the barrier, so they are running at the same time
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<String> first = pool.submit(script("first", barrier));
            Future<String> second = pool.submit(script("second", barrier));
            Future<?> bystander = pool.submit(() -> {
                System.out.println("bystander line");
                return null;
            });
            bystander.get();
            String firstLog = first.get();
            String secondLog = second.get();

            assertTrue(firstLog.contains("start first") && firstLog.contains("system first"), firstLog);
            assertTrue(firstLog.contains("error first") && firstLog.contains("end first"), firstLog);
            assertFalse(firstLog.contains("second"), firstLog);
            assertTrue(secondLog.contains("start second") && secondLog.contains("system second"), secondLog);
            assertFalse(secondLog.contains("first"), secondLog);
            assertFalse(
                    firstLog.contains("bystander") || secondLog.contains("bystander"), "scripts captured a bystander");

            String outside = new String(outsideOut.toByteArray(), StandardCharsets.UTF_8);
            assertTrue(outside.contains("bystander line"), outside);
            assertFalse(outside.contains("system first") || outside.contains("system second"), outside);
        } finally {
            pool.shutdownNow();
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        assertSame(originalOut, System.out, "the original stream is back once no script runs");
        assertSame(originalErr, System.err, "the original stream is back once no script runs");
    }

    @Test
    void streamsAreRestoredAfterTheLastRedirect() throws Exception {
        PrintStream out = System.out;
        PrintStream err = System.err;
        PrintStream target = new PrintStream(new ByteArrayOutputStream());
        try (ScriptOutputRedirect outer = ScriptOutputRedirect.to(target)) {
            try (ScriptOutputRedirect inner = ScriptOutputRedirect.to(target)) {
                assertFalse(System.out == out);
            }
            assertFalse(System.out == out, "still redirected while the outer redirect is open");
        }
        assertSame(out, System.out);
        assertSame(err, System.err);
    }

    private Callable<String> script(String name, CyclicBarrier barrier) {
        return () -> {
            File logFile = new File(tempDir, name + ".log");
            Map<String, Object> context = new HashMap<>();
            context.put("barrier", barrier);
            try (FileLogger logger = new FileLogger(logFile);
                    ScriptRunner runner = new ScriptRunner()) {
                runner.setGlobalVariable("name", name);
                runner.run("test", new File("src/test/resources/concurrency"), "verify", context, logger);
            }
            return new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        };
    }
}
