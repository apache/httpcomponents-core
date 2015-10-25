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
package org.apache.http.benchmark;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.io.SessionOutputBuffer;

class BenchmarkConnection extends DefaultBHttpClientConnection {

    private final Stats stats;

    BenchmarkConnection(final int bufsize, final Stats stats) {
        super(bufsize);
        this.stats = stats;
    }

    @Override
    protected OutputStream createContentOutputStream(final long len,
                                                     final SessionOutputBuffer outbuffer,
                                                     final Map<String, Callable<String>> trailers) {
        return new CountingOutputStream(super.createContentOutputStream(len, outbuffer, trailers), this.stats);
    }

    @Override
    protected InputStream createContentInputStream(final long len, final SessionInputBuffer inbuffer) {
        return new CountingInputStream(super.createContentInputStream(len, inbuffer), this.stats);
    }

}
