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
package org.apache.hc.core5.http.nio.entity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

/**
 * {@link AsyncEntityProducer} decorator that calculates a digest hash from
 * the data stream content and appends its value to the list of trailers.
 *
 * @since 5.0
 */
public class DigestingEntityProducer implements AsyncEntityProducer {

    private final AsyncEntityProducer wrapped;
    private final MessageDigest digester;

    private volatile byte[] digest;

    public DigestingEntityProducer(
            final String algo,
            final AsyncEntityProducer wrapped) {
        this.wrapped = Args.notNull(wrapped, "Entity consumer");
        try {
            this.digester = MessageDigest.getInstance(algo);
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalArgumentException("Unsupported digest algorithm: " + algo);
        }
    }

    @Override
    public boolean isRepeatable() {
        return wrapped.isRepeatable();
    }

    @Override
    public long getContentLength() {
        return wrapped.getContentLength();
    }

    @Override
    public String getContentType() {
        return wrapped.getContentType();
    }

    @Override
    public String getContentEncoding() {
        return wrapped.getContentEncoding();
    }

    @Override
    public boolean isChunked() {
        return wrapped.isChunked();
    }

    @Override
    public Set<String> getTrailerNames() {
        final Set<String> allNames = new LinkedHashSet<>();
        final Set<String> names = wrapped.getTrailerNames();
        if (names != null) {
            allNames.addAll(names);
        }
        allNames.add("digest-algo");
        allNames.add("digest");
        return allNames;
    }

    @Override
    public int available() {
        return wrapped.available();
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        wrapped.produce(new DataStreamChannel() {

            @Override
            public void requestOutput() {
                channel.requestOutput();
            }

            @Override
            public int write(final ByteBuffer src) throws IOException {
                final ByteBuffer dup = src.duplicate();
                final int writtenBytes = channel.write(src);
                if (writtenBytes > 0) {
                    dup.limit(dup.position() + writtenBytes);
                    digester.update(dup);
                }
                return writtenBytes;
            }

            @Override
            public void endStream(final List<? extends Header> trailers) throws IOException {
                digest = digester.digest();
                final List<Header> allTrailers = new ArrayList<>();
                if (trailers != null) {
                    allTrailers.addAll(trailers);
                }
                allTrailers.add(new BasicHeader("digest-algo", digester.getAlgorithm()));
                allTrailers.add(new BasicHeader("digest", TextUtils.toHexString(digest)));
                channel.endStream(allTrailers);
            }

            @Override
            public void endStream() throws IOException {
                endStream(null);
            }

        });
    }

    @Override
    public void failed(final Exception cause) {
        wrapped.failed(cause);
    }

    @Override
    public void releaseResources() {
        wrapped.releaseResources();
    }

    /**
     * Returns digest hash.
     *
     * @return the digest hash value.
     */
    public byte[] getDigest() {
        return digest;
    }

}
