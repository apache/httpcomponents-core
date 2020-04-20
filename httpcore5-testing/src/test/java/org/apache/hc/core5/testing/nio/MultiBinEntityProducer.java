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
package org.apache.hc.core5.testing.nio;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityProducer;

public class MultiBinEntityProducer extends AbstractBinAsyncEntityProducer {

    private final byte[] bin;
    private final int total;
    private final ByteBuffer bytebuf;

    private int count;

    public MultiBinEntityProducer(final byte[] bin, final int total, final ContentType contentType) {
        super(-1, contentType);
        this.bin = bin;
        this.total = total;
        this.bytebuf = ByteBuffer.allocate(4096);
        this.count = 0;
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    protected int availableData() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected void produceData(final StreamChannel<ByteBuffer> channel) throws IOException {
        while (bytebuf.remaining() > bin.length + 2 && count < total) {
            bytebuf.put(bin);
            count++;
        }
        if (bytebuf.position() > 0) {
            bytebuf.flip();
            channel.write(bytebuf);
            bytebuf.compact();
        }
        if (count >= total && bytebuf.position() == 0) {
            channel.endStream();
        }
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public long getContentLength() {
        return bin.length * total;
    }

    @Override
    public void failed(final Exception cause) {
    }

    @Override
    public void releaseResources() {
        count = 0;
        bytebuf.clear();
        super.releaseResources();
    }

}
