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

package org.apache.http.entity;

import static java.util.Collections.emptyMap;

import java.util.Map;

import org.apache.http.TrailerValueSupplier;
import org.apache.http.annotation.NotThreadSafe;

/**
 * Abstract base class for mutable entities. Provides the commonly used attributes for streamed and
 * self-contained implementations.
 *
 * @since 4.0
 */
@NotThreadSafe
public abstract class AbstractHttpEntity extends AbstractImmutableHttpEntity {

    /**
     * Buffer size for output stream processing.
     *
     * @since 4.3
     */
    static final int OUTPUT_BUFFER_SIZE = 4096;

    private String contentType;
    private String contentEncoding;
    private boolean chunked;
    private Map<String, TrailerValueSupplier> trailers = emptyMap();

    @Override
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public String getContentEncoding() {
        return this.contentEncoding;
    }

    @Override
    public boolean isChunked() {
        return this.chunked;
    }

    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    public void setContentEncoding(final String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    public void setChunked(final boolean b) {
        this.chunked = b;
    }

    public void setTrailers(final Map<String, TrailerValueSupplier> trailers) {
        if (trailers == null) {
            throw new NullPointerException();
        }
        this.trailers = trailers;
    }

    public Map<String, TrailerValueSupplier> getTrailers() {
        return trailers;
    }
}
