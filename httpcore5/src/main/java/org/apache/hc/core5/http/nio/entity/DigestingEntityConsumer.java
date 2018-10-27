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
import java.util.List;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Args;

/**
 * {@link AsyncEntityConsumer} decorator that calculates a digest hash from
 * the data stream content and keeps the list of trailers received with
 * the data stream.
 *
 * @since 5.0
 */
public class DigestingEntityConsumer<T> implements AsyncEntityConsumer<T> {

    private final AsyncEntityConsumer<T> wrapped;
    private final List<Header> trailers;
    private final MessageDigest digester;

    private volatile byte[] digest;

    public DigestingEntityConsumer(
            final String algo,
            final AsyncEntityConsumer<T> wrapped) throws NoSuchAlgorithmException {
        this.wrapped = Args.notNull(wrapped, "Entity consumer");
        this.trailers = new ArrayList<>();
        this.digester = MessageDigest.getInstance(algo);
    }

    @Override
    public void streamStart(
            final EntityDetails entityDetails,
            final FutureCallback<T> resultCallback) throws IOException, HttpException {
        wrapped.streamStart(entityDetails, resultCallback);
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        wrapped.updateCapacity(capacityChannel);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        src.mark();
        digester.update(src);
        src.reset();
        wrapped.consume(src);
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (trailers != null) {
            this.trailers.addAll(trailers);
        }
        digest = digester.digest();
        wrapped.streamEnd(trailers);
    }

    @Override
    public void failed(final Exception cause) {
        wrapped.failed(cause);
    }

    @Override
    public T getContent() {
        return wrapped.getContent();
    }

    @Override
    public void releaseResources() {
        wrapped.releaseResources();
    }

    /**
     * List of trailers sent with the data stream.
     *
     * @return the list of trailers sent with the data stream
     */
    public List<Header> getTrailers() {
        return trailers != null ? new ArrayList<>(trailers) : null;
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
