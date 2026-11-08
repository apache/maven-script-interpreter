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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void currentStreamAsTargetDoesNotRecurse() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream target = new PrintStream(captured, true, "UTF-8");
        try (ScriptOutputRedirect outer = ScriptOutputRedirect.to(target)) {
            // System.out is the dispatching stream here, handing it to a script as its output must not loop
            try (ScriptOutputRedirect inner = ScriptOutputRedirect.to(System.out)) {
                System.out.println("through the dispatcher");
            }
        }
        assertEquals("through the dispatcher" + System.lineSeparator(), captured.toString("UTF-8"));
        assertSame(originalOut, System.out);
    }

    @Test
    void threadOutlivingTheScriptFallsBackToTheEnclosingTargetOrTheRealStream() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream real = new ByteArrayOutputStream();
        ByteArrayOutputStream outerLog = new ByteArrayOutputStream();
        ByteArrayOutputStream innerLog = new ByteArrayOutputStream();
        System.setOut(new PrintStream(real, true, "UTF-8"));
        try {
            CountDownLatch printedWhileInner = new CountDownLatch(1);
            CountDownLatch innerClosed = new CountDownLatch(1);
            CountDownLatch outerClosed = new CountDownLatch(1);
            CountDownLatch printedAfterInner = new CountDownLatch(1);
            Thread child;
            try (ScriptOutputRedirect outer = ScriptOutputRedirect.to(new PrintStream(outerLog, true, "UTF-8"))) {
                try (ScriptOutputRedirect inner = ScriptOutputRedirect.to(new PrintStream(innerLog, true, "UTF-8"))) {
                    PrintStream dispatcher = System.out;
                    // started inside the inner script, so it inherits the inner script's redirect
                    child = new Thread(() -> {
                        try {
                            dispatcher.println("while inner runs");
                            printedWhileInner.countDown();
                            innerClosed.await();
                            dispatcher.println("after inner closed");
                            printedAfterInner.countDown();
                            outerClosed.await();
                            dispatcher.println("after all closed");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    child.start();
                    printedWhileInner.await();
                }
                innerClosed.countDown();
                printedAfterInner.await();
            }
            outerClosed.countDown();
            child.join();

            assertEquals("while inner runs" + System.lineSeparator(), innerLog.toString("UTF-8"));
            assertEquals("after inner closed" + System.lineSeparator(), outerLog.toString("UTF-8"));
            assertEquals("after all closed" + System.lineSeparator(), real.toString("UTF-8"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void everyPrintMethodOfBothStreamsLandsInTheScriptLog() throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream realOut = new ByteArrayOutputStream();
        ByteArrayOutputStream realErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(realOut, true, "UTF-8"));
        System.setErr(new PrintStream(realErr, true, "UTF-8"));
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        try {
            PrintStream reference = new PrintStream(expected, true, "UTF-8");
            exerciseEveryMethod(reference);
            exerciseEveryMethod(reference);
            try (ScriptOutputRedirect redirect = ScriptOutputRedirect.to(new PrintStream(log, true, "UTF-8"))) {
                exerciseEveryMethod(System.out);
                exerciseEveryMethod(System.err);
            }
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        assertEquals(expected.toString("UTF-8"), log.toString("UTF-8"));
        assertEquals(0, realOut.size());
        assertEquals(0, realErr.size());
    }

    private static void exerciseEveryMethod(PrintStream stream) throws Exception {
        stream.print(true);
        stream.print('c');
        stream.print(42);
        stream.print(43L);
        stream.print(1.5f);
        stream.print(2.5d);
        stream.print(new char[] {'a', 'b'});
        stream.print("s\u00e9\u20ac");
        stream.print((Object) "o");
        stream.println();
        stream.println(false);
        stream.println('d');
        stream.println(7);
        stream.println(8L);
        stream.println(3.5f);
        stream.println(4.5d);
        stream.println(new char[] {'e', 'f'});
        stream.println("line\u00e9");
        stream.println((Object) "obj");
        stream.printf("%s-%d%n", "p", 1);
        stream.printf(Locale.GERMANY, "%.2f%n", 1.5d);
        stream.format("%s%n", "f");
        stream.format(Locale.FRANCE, "%.1f%n", 2.5d);
        stream.append("cs").append("xyz", 1, 2).append('c');
        stream.write('w');
        stream.write(new byte[] {'b', 'y'});
        stream.write(new byte[] {'0', 'b', 'z', '1'}, 1, 2);
        stream.println();
        stream.flush();
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
