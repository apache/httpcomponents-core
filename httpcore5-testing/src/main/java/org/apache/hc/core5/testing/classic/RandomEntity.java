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
package org.apache.hc.core5.testing.classic;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;

/**
 * An entity that generates random data.
 * This is an outgoing entity, it supports {@link #writeTo writeTo}
 * but not {@link #getContent getContent}.
 */
public class RandomEntity extends AbstractHttpEntity {

    /**
     * The range from which to generate random data.
     */
    private final static byte[] RANGE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            .getBytes(StandardCharsets.US_ASCII);

    /**
     * The length of the random data to generate.
     */
    protected final long length;

    /**
     * The duration of the delay before sending the response entity.
     */
    protected final int delayMs;

    /**
     * Whether to delay after each chunk sent. If {@code false},
     * will only delay at the start of the response entity.
     */
    protected final boolean drip;

    /**
     * Creates a new entity generating the given amount of data.
     *
     * @param len the number of random bytes to generate,
     *            0 to maxint
     */
    public RandomEntity(final long len) {
        this(len, 0, false);
    }

    /**
     * Creates a new entity generating the given amount of data.
     *
     * @param len     the number of random bytes to generate,
     *                0 to maxint
     * @param delayMs how long to wait before sending the first byte
     * @param drip    whether to repeat the delay after each byte
     */
    public RandomEntity(final long len, final int delayMs, final boolean drip) {
        super(ContentType.DEFAULT_TEXT, null);
        this.length = len;
        this.delayMs = delayMs;
        this.drip = drip;
    }

    /**
     * Tells that this entity is not streaming.
     *
     * @return false
     */
    @Override
    public final boolean isStreaming() {
        return false;
    }

    /**
     * Tells that this entity is repeatable, in a way.
     * Repetitions will generate different random data,
     * unless perchance the same random data is generated twice.
     *
     * @return {@code true}
     */
    @Override
    public boolean isRepeatable() {
        return true;
    }

    /**
     * Obtains the size of the random data.
     *
     * @return the number of random bytes to generate
     */
    @Override
    public long getContentLength() {
        return length;
    }


    /**
     * Not supported.
     * This method throws an exception.
     *
     * @return never anything
     */
    @Override
    public InputStream getContent() {
        throw new UnsupportedOperationException();
    }


    /**
     * Generates the random content.
     *
     * @param out where to write the content to
     */
    @Override
    public void writeTo(final OutputStream out) throws IOException {

        try {
            final int blocksize = 2048;
            int remaining = (int) length; // range checked in constructor
            final byte[] data = new byte[Math.min(remaining, blocksize)];

            out.flush();
            if (!drip) {
                delay();
            }
            while (remaining > 0) {
                final int end = Math.min(remaining, data.length);

                double value = 0.0;
                for (int i = 0; i < end; i++) {
                    // we get 5 random characters out of one random value
                    if (i % 5 == 0) {
                        value = Math.random();
                    }
                    value = value * RANGE.length;
                    final int d = (int) value;
                    value = value - d;
                    data[i] = RANGE[d];
                }
                out.write(data, 0, end);
                out.flush();

                remaining = remaining - end;
                if (drip) {
                    delay();
                }
            }
        } finally {
            out.close();
        }
    }

    private void delay() throws IOException {
        try {
            Thread.sleep(delayMs);
        } catch (final InterruptedException ex) {
            throw new InterruptedIOException();
        }
    }

    @Override
    public void close() throws IOException {
    }

}
