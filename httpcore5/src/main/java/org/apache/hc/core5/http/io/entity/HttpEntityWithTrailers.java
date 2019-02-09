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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Args;

/**
 * Wrapping entity that also includes trailers.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class HttpEntityWithTrailers implements HttpEntity {

    private final HttpEntity wrappedEntity;
    private final List<Header> trailers;

    /**
     * Creates a new entity wrapper.
     */
    public HttpEntityWithTrailers(final HttpEntity wrappedEntity, final Header... trailers) {
        super();
        this.wrappedEntity = Args.notNull(wrappedEntity, "Wrapped entity");
        this.trailers = Arrays.asList(trailers);
    }

    @Override
    public boolean isRepeatable() {
        return wrappedEntity.isRepeatable();
    }

    @Override
    public boolean isChunked() {
        return true;
    }

    @Override
    public long getContentLength() {
        return wrappedEntity.getContentLength();
    }

    @Override
    public String getContentType() {
        return wrappedEntity.getContentType();
    }

    @Override
    public String getContentEncoding() {
        return wrappedEntity.getContentEncoding();
    }

    @Override
    public InputStream getContent()
        throws IOException {
        return wrappedEntity.getContent();
    }

    @Override
    public void writeTo(final OutputStream outStream)
        throws IOException {
        wrappedEntity.writeTo(outStream);
    }

    @Override
    public boolean isStreaming() {
        return wrappedEntity.isStreaming();
    }

    @Override
    public Supplier<List<? extends Header>> getTrailers() {
        return new Supplier<List<? extends Header>>() {
            @Override
            public List<? extends Header> get() {
                return trailers;
            }
        };
    }

    @Override
    public Set<String> getTrailerNames() {
        final Set<String> names = new LinkedHashSet<>();
        for (final Header trailer: trailers) {
            names.add(trailer.getName());
        }
        return names;
    }

    @Override
    public void close() throws IOException {
        wrappedEntity.close();
    }

}
