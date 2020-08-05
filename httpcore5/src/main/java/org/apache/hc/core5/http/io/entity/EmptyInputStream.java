/*
 * ====================================================================
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
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.core5.http.io.entity;

import java.io.InputStream;

/**
 * @since 5.1
 */
public final class EmptyInputStream extends InputStream {

    public static final EmptyInputStream INSTANCE = new EmptyInputStream();

    private EmptyInputStream() {
        // noop.
    }

    /**
     * Returns {@code 0}.
     */
    @Override
    public int available() {
        return 0;
    }

    /**
     * Noop.
     */
    @Override
    public void close() {
        // noop.
    }

    /**
     * Noop.
     */
    @SuppressWarnings("sync-override")
    @Override
    public void mark(final int readLimit) {
        // noop.
    }

    /**
     * Returns {@code true}.
     */
    @Override
    public boolean markSupported() {
        return true;
    }

    /**
     * Returns {@code -1}.
     */
    @Override
    public int read() {
        return -1;
    }

    /**
     * Returns {@code -1}.
     */
    @Override
    public int read(final byte[] buf) {
        return -1;
    }

    /**
     * Returns {@code -1}.
     */
    @Override
    public int read(final byte[] buf, final int off, final int len) {
        return -1;
    }

    /**
     * Noop.
     */
    @SuppressWarnings("sync-override")
    @Override
    public void reset() {
        // noop
    }

    /**
     * Returns {@code 0}.
     */
    @Override
    public long skip(final long n) {
        return 0L;
    }
}

