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
package org.apache.hc.core5.http.impl.nio;

import java.io.IOException;

import org.apache.hc.core5.http.ContentTooLongException;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.IOControl;
import org.apache.hc.core5.http.nio.entity.ContentBufferEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.HeapByteBufferAllocator;

/**
 * Basic implementation of {@link org.apache.hc.core5.http.nio.HttpAsyncRequestConsumer}.
 * Please note that this consumer buffers request content in memory and should be used for
 * relatively small request messages.
 *
 * @since 4.2
 */
public class BasicAsyncRequestConsumer extends AbstractAsyncRequestConsumer<HttpRequest> {

    private volatile HttpRequest request;
    private volatile SimpleInputBuffer buf;

    public BasicAsyncRequestConsumer() {
        super();
    }

    @Override
    protected void onRequestReceived(final HttpRequest request) throws IOException {
        this.request = request;
    }

    @Override
    protected void onEntityEnclosed(
            final HttpEntity entity, final ContentType contentType) throws IOException {
        long len = entity.getContentLength();
        if (len > Integer.MAX_VALUE) {
            throw new ContentTooLongException("Entity content is too long: " + len);
        }
        if (len < 0) {
            len = 4096;
        }
        this.buf = new SimpleInputBuffer((int) len, new HeapByteBufferAllocator());
        this.request.setEntity(new ContentBufferEntity(entity, this.buf));
    }

    @Override
    protected void onContentReceived(
            final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
        Asserts.notNull(this.buf, "Content buffer");
        this.buf.consumeContent(decoder);
    }

    @Override
    protected void releaseResources() {
        this.request = null;
        this.buf = null;
    }

    @Override
    protected HttpRequest buildResult(final HttpContext context) {
        return this.request;
    }

}
