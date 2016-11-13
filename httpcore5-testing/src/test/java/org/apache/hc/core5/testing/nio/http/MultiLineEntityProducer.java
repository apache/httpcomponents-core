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
package org.apache.hc.core5.testing.nio.http;

import java.io.IOException;
import java.nio.CharBuffer;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.apache.hc.core5.http.nio.entity.AbstractCharAsyncEntityProducer;

public class MultiLineEntityProducer extends AbstractCharAsyncEntityProducer {

    private final String text;
    private final int total;
    private final CharBuffer charbuf;

    private int count;

    public MultiLineEntityProducer(final String text, final int total) {
        super(1024, -1, ContentType.TEXT_PLAIN);
        this.text = text;
        this.total = total;
        this.charbuf = CharBuffer.allocate(4096);
        this.count = 0;
    }

    @Override
    public long getContentLength() {
        return -1;
    }

    @Override
    public int available() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected void produceData(final StreamChannel<CharBuffer> channel) throws IOException {
        while (charbuf.remaining() > text.length() + 2 && count < total) {
            charbuf.put(text + "\r\n");
            count++;
        }
        if (charbuf.position() > 0) {
            charbuf.flip();
            channel.write(charbuf);
            charbuf.compact();
        }
        if (count >= total && charbuf.position() == 0) {
            channel.endStream();
        }
    }

    @Override
    public void failed(final Exception cause) {
    }

    @Override
    public void releaseResources() {
    }

}
