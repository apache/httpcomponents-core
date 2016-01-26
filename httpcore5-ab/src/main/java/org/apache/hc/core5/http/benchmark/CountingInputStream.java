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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

class CountingInputStream extends FilterInputStream {

    private final Stats stats;

    CountingInputStream(final InputStream instream, final Stats stats) {
        super(instream);
        this.stats = stats;
    }

    @Override
    public int read() throws IOException {
        final int b = this.in.read();
        if (b != -1) {
            this.stats.incTotalBytesRecv(1);
        }
        return b;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        final int bytesRead = this.in.read(b);
        if (bytesRead > 0) {
            this.stats.incTotalBytesRecv(bytesRead);
        }
        return bytesRead;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int bytesRead = this.in.read(b, off, len);
        if (bytesRead > 0) {
            this.stats.incTotalBytesRecv(bytesRead);
        }
        return bytesRead;
    }

    @Override
    public long skip(final long n) throws IOException {
        final long bytesRead = this.in.skip(n);
        if (bytesRead > 0) {
            this.stats.incTotalBytesRecv(bytesRead);
        }
        return bytesRead;
    }

}
