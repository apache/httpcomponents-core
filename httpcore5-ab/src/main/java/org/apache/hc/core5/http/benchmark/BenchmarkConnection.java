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
package org.apache.hc.core5.http.benchmark;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnection;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.SessionOutputBuffer;

class BenchmarkConnection extends DefaultBHttpClientConnection {

    private final Stats stats;

    BenchmarkConnection(final H1Config h1Config, final Stats stats) {
        super(h1Config);
        this.stats = stats;
    }

    @Override
    protected OutputStream createContentOutputStream(final long len,
                                                     final SessionOutputBuffer outbuffer,
                                                     final OutputStream outputStream,
                                                     final Supplier<List<? extends Header>> trailers) {
        return new CountingOutputStream(
                super.createContentOutputStream(len, outbuffer, outputStream, trailers),
                this.stats);
    }

    @Override
    protected InputStream createContentInputStream(final long len,
                                                   final SessionInputBuffer inbuffer,
                                                   final InputStream inputStream) {
        return new CountingInputStream(super.createContentInputStream(len, inbuffer, inputStream), this.stats);
    }

}
