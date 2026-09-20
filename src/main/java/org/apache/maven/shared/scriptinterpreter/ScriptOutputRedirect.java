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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Locale;

/**
 * Routes {@code System.out} and {@code System.err} of the current thread to a script's log while that script runs,
 * without taking the streams away from the rest of the JVM.
 * <p>
 * The process-wide streams are replaced once, while at least one redirect is active, by streams that dispatch each
 * write to the redirect registered for the calling thread (and threads it spawns), or to the original stream when
 * there is none. When the last redirect ends the original streams are put back.
 */
final class ScriptOutputRedirect implements AutoCloseable {

    private static final Object LOCK = new Object();

    private static final InheritableThreadLocal<PrintStream> CURRENT = new InheritableThreadLocal<>();

    private static int active;

    private static PrintStream originalOut;

    private static PrintStream originalErr;

    private final PrintStream previous;

    private ScriptOutputRedirect(PrintStream target) {
        synchronized (LOCK) {
            if (active++ == 0) {
                originalOut = System.out;
                originalErr = System.err;
                System.setOut(new DispatchingPrintStream(originalOut));
                System.setErr(new DispatchingPrintStream(originalErr));
            }
        }
        previous = CURRENT.get();
        CURRENT.set(target);
    }

    /**
     * Starts routing this thread's standard output and error to {@code target}; {@link #close()} ends it.
     *
     * @param target where the script's output goes, must not be {@code null}
     * @return the redirect to close when the script is done
     */
    static ScriptOutputRedirect to(PrintStream target) {
        return new ScriptOutputRedirect(target);
    }

    @Override
    public void close() {
        if (previous != null) {
            CURRENT.set(previous);
        } else {
            CURRENT.remove();
        }
        synchronized (LOCK) {
            if (--active == 0) {
                System.setOut(originalOut);
                System.setErr(originalErr);
                originalOut = null;
                originalErr = null;
            }
        }
    }

    /**
     * A print stream that hands every call to the redirect of the current thread, or to the stream it replaced.
     * Calls are forwarded whole (a {@code println} stays one {@code println}) so a target that mirrors lines on
     * flush, such as {@link FileLogger}, sees the same line boundaries as before.
     */
    private static final class DispatchingPrintStream extends PrintStream {
        private final PrintStream fallback;

        DispatchingPrintStream(PrintStream fallback) {
            super(new OutputStream() {
                @Override
                public void write(int b) {
                    throw new IllegalStateException("all writes are dispatched");
                }
            });
            this.fallback = fallback;
        }

        private PrintStream target() {
            PrintStream current = CURRENT.get();
            return current != null ? current : fallback;
        }

        @Override
        public void write(int b) {
            target().write(b);
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            target().write(buf, off, len);
        }

        @Override
        public void write(byte[] buf) throws IOException {
            target().write(buf);
        }

        @Override
        public void flush() {
            target().flush();
        }

        @Override
        public void close() {
            // the streams behind the dispatch are owned by their creators
        }

        @Override
        public boolean checkError() {
            return target().checkError();
        }

        @Override
        public void print(boolean b) {
            target().print(b);
        }

        @Override
        public void print(char c) {
            target().print(c);
        }

        @Override
        public void print(int i) {
            target().print(i);
        }

        @Override
        public void print(long l) {
            target().print(l);
        }

        @Override
        public void print(float f) {
            target().print(f);
        }

        @Override
        public void print(double d) {
            target().print(d);
        }

        @Override
        public void print(char[] s) {
            target().print(s);
        }

        @Override
        public void print(String s) {
            target().print(s);
        }

        @Override
        public void print(Object obj) {
            target().print(obj);
        }

        @Override
        public void println() {
            target().println();
        }

        @Override
        public void println(boolean x) {
            target().println(x);
        }

        @Override
        public void println(char x) {
            target().println(x);
        }

        @Override
        public void println(int x) {
            target().println(x);
        }

        @Override
        public void println(long x) {
            target().println(x);
        }

        @Override
        public void println(float x) {
            target().println(x);
        }

        @Override
        public void println(double x) {
            target().println(x);
        }

        @Override
        public void println(char[] x) {
            target().println(x);
        }

        @Override
        public void println(String x) {
            target().println(x);
        }

        @Override
        public void println(Object x) {
            target().println(x);
        }

        @Override
        public PrintStream printf(String format, Object... args) {
            target().printf(format, args);
            return this;
        }

        @Override
        public PrintStream printf(Locale l, String format, Object... args) {
            target().printf(l, format, args);
            return this;
        }

        @Override
        public PrintStream format(String format, Object... args) {
            target().format(format, args);
            return this;
        }

        @Override
        public PrintStream format(Locale l, String format, Object... args) {
            target().format(l, format, args);
            return this;
        }

        @Override
        public PrintStream append(CharSequence csq) {
            target().append(csq);
            return this;
        }

        @Override
        public PrintStream append(CharSequence csq, int start, int end) {
            target().append(csq, start, end);
            return this;
        }

        @Override
        public PrintStream append(char c) {
            target().append(c);
            return this;
        }
    }
}
