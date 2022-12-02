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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * <p>FileLogger class.</p>
 *
 */
public class FileLogger implements ExecutionLogger, AutoCloseable {

    /**
     * The path to the log file.
     */
    private File file;

    /**
     * The underlying file stream this logger writes to.
     */
    private PrintStream stream;

    /**
     * Creates a new logger that writes to the specified file.
     *
     * @param outputFile The path to the output file, if null all message will be discarded.
     * @throws java.io.IOException If the output file could not be created.
     */
    public FileLogger(File outputFile) throws IOException {
        this(outputFile, null);
    }

    /**
     * Creates a new logger that writes to the specified file and optionally mirrors messages.
     *
     * @param outputFile The path to the output file, if null all message will be discarded.
     * @param mirrorHandler The class which handle mirrored message, can be <code>null</code>.
     * @throws java.io.IOException If the output file could not be created.
     */
    public FileLogger(File outputFile, FileLoggerMirrorHandler mirrorHandler) throws IOException {
        this.file = outputFile;

        OutputStream outputStream;

        if (outputFile != null) {
            outputFile.getParentFile().mkdirs();
            outputStream = new FileOutputStream(outputFile);
        } else {
            outputStream = new NullOutputStream();
        }

        if (mirrorHandler != null) {
            stream = new PrintStream(new MirrorStreamWrapper(outputStream, mirrorHandler));
        } else {
            stream = new PrintStream(outputStream);
        }
    }

    /**
     * Gets the path to the output file.
     *
     * @return The path to the output file, never <code>null</code>.
     */
    public File getOutputFile() {
        return file;
    }

    /**
     * Gets the underlying stream used to write message to the log file.
     *
     * @return The underlying stream used to write message to the log file, never <code>null</code>.
     */
    @Override
    public PrintStream getPrintStream() {
        return stream;
    }

    /**
     * Writes the specified line to the log file
     * and invoke {@link FileLoggerMirrorHandler#consumeOutput(String)} if is given.
     *
     * @param line The message to log.
     */
    @Override
    public void consumeLine(String line) {
        stream.println(line);
        stream.flush();
    }

    /**
     * Closes the underlying file stream.
     */
    public void close() {
        if (stream != null) {
            stream.flush();
            stream.close();
            stream = null;
        }
    }

    private static class MirrorStreamWrapper extends OutputStream {
        private final OutputStream out;
        private final FileLoggerMirrorHandler mirrorHandler;

        private StringBuilder lineBuffer;

        MirrorStreamWrapper(OutputStream outputStream, FileLoggerMirrorHandler mirrorHandler) {
            this.out = outputStream;
            this.mirrorHandler = mirrorHandler;
            this.lineBuffer = new StringBuilder();
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            lineBuffer.append((char) (b));
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            lineBuffer.append(new String(b, off, len));
        }

        @Override
        public void flush() throws IOException {
            out.flush();

            int len = lineBuffer.length();
            if (len == 0) {
                // nothing to log
                return;
            }

            // remove line end for log
            while (len > 0 && (lineBuffer.charAt(len - 1) == '\n' || lineBuffer.charAt(len - 1) == '\r')) {
                len--;
            }
            lineBuffer.setLength(len);

            mirrorHandler.consumeOutput(lineBuffer.toString());

            // clear buffer
            lineBuffer = new StringBuilder();
        }
    }

    private static class NullOutputStream extends OutputStream {
        @Override
        public void write(int b) {
            // do nothing
        }

        @Override
        public void write(byte[] b, int off, int len) {
            // do nothing
        }
    }
}
