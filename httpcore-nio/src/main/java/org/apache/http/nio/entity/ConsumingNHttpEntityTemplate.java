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

package org.apache.http.nio.entity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;

/**
 * A {@link ConsumingNHttpEntity} that forwards available content to a
 * {@link ContentListener}.
 *
 * @since 4.0
 */
public class ConsumingNHttpEntityTemplate
    extends HttpEntityWrapper implements ConsumingNHttpEntity {

    private final ContentListener contentListener;

    public ConsumingNHttpEntityTemplate(
            final HttpEntity httpEntity,
            final ContentListener contentListener) {
        super(httpEntity);
        this.contentListener = contentListener;
    }

    public ContentListener getContentListener() {
        return contentListener;
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        throw new UnsupportedOperationException("Does not support blocking methods");
    }

    @Override
    public boolean isStreaming() {
        return true;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException, UnsupportedOperationException {
        throw new UnsupportedOperationException("Does not support blocking methods");
    }

    /**
     * This method is equivalent to the {@link #finish()} method.
     * <br/>
     * TODO: The name of this method is misnomer. It will be renamed to
     * #finish() in the next major release.
     */
    @Override
    public void consumeContent() throws IOException {
        finish();
    }

    public void consumeContent(
            final ContentDecoder decoder,
            final IOControl ioctrl) throws IOException {
        this.contentListener.contentAvailable(decoder, ioctrl);
    }

    public void finish() {
        this.contentListener.finished();
    }

}
