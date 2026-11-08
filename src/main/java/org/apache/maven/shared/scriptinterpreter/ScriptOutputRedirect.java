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

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

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

    private static final InheritableThreadLocal<Context> CURRENT = new InheritableThreadLocal<>();

    private static int active;

    private static PrintStream originalOut;

    private static PrintStream originalErr;

    private final Context context;

    private ScriptOutputRedirect(PrintStream target) {
        synchronized (LOCK) {
            active++;
            if (active == 1) {
                originalOut = System.out;
                originalErr = System.err;
                System.setOut(DispatchingPrintStream.over(originalOut));
                System.setErr(DispatchingPrintStream.over(originalErr));
            }
        }
        context = new Context(resolve(target), CURRENT.get());
        CURRENT.set(context);
    }

    /**
     * Follows a dispatching stream (such as the current {@code System.out}) to the stream it would write to for this
     * thread, so a script handed {@code System.out} as its output cannot dispatch back to itself.
     */
    private static PrintStream resolve(PrintStream stream) {
        PrintStream resolved = stream;
        while (resolved instanceof DispatchingPrintStream) {
            resolved = ((DispatchingPrintStream) resolved).target();
        }
        return resolved;
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
        context.closed = true;
        if (context.parent != null) {
            CURRENT.set(context.parent);
        } else {
            CURRENT.remove();
        }
        synchronized (LOCK) {
            active--;
            if (active == 0) {
                System.setOut(originalOut);
                System.setErr(originalErr);
                originalOut = null;
                originalErr = null;
            }
        }
    }

    /**
     * One redirect. Threads started while it is active inherit it and may outlive it; once it is closed they use the
     * enclosing redirect, or the original stream when there is none.
     */
    private static final class Context {
        final PrintStream target;

        final Context parent;

        volatile boolean closed;

        Context(PrintStream target, Context parent) {
            this.target = target;
            this.parent = parent;
        }
    }

    /**
     * A print stream that hands every byte it produces to the redirect of the current thread, or to the stream it
     * replaced. All of {@code print}, {@code println}, {@code printf}, {@code format} and {@code append} encode their
     * text (as UTF-8, the encoding of {@link FileLogger}) and end in the write methods of the underlying
     * {@link OutputStream}, so overriding those is enough.
     */
    private static final class DispatchingPrintStream extends PrintStream {
        private final Dispatcher dispatcher;

        static DispatchingPrintStream over(PrintStream fallback) {
            try {
                return new DispatchingPrintStream(new Dispatcher(fallback));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e); // UTF-8 is always available
            }
        }

        private DispatchingPrintStream(Dispatcher dispatcher) throws UnsupportedEncodingException {
            super(dispatcher, false, "UTF-8");
            this.dispatcher = dispatcher;
        }

        PrintStream target() {
            return dispatcher.target();
        }

        @Override
        public void close() {
            // the streams behind the dispatch are owned by their creators
        }
    }

    /** The byte sink of a {@link DispatchingPrintStream}: forwards to the stream chosen for the calling thread. */
    private static final class Dispatcher extends OutputStream {
        private final PrintStream fallback;

        Dispatcher(PrintStream fallback) {
            this.fallback = fallback;
        }

        PrintStream target() {
            for (Context c = CURRENT.get(); c != null; c = c.parent) {
                if (!c.closed) {
                    return c.target;
                }
            }
            return fallback;
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
        public void flush() {
            target().flush();
        }
    }
}
