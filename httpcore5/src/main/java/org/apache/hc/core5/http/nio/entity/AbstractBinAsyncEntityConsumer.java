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
import java.io.UnsupportedEncodingException;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.util.Args;

/**
 * Abstract binary entity content consumer.
 *
 * @since 5.0
 *
 * @param <T> entity representation.
 */
public abstract class AbstractBinAsyncEntityConsumer<T> extends AbstractBinDataConsumer implements AsyncEntityConsumer<T> {

    private volatile FutureCallback<T> resultCallback;
    private volatile T content;

    /**
     * Triggered to signal beginning of entity content stream.
     *
     * @param contentType the entity content type
     */
    protected abstract void streamStart(ContentType contentType) throws HttpException, IOException;

    /**
     * Triggered to generate entity representation.
     *
     * @return the entity content
     */
    protected abstract T generateContent() throws IOException;

    @Override
    public final void streamStart(
            final EntityDetails entityDetails,
            final FutureCallback<T> resultCallback) throws IOException, HttpException {
        Args.notNull(resultCallback, "Result callback");
        this.resultCallback = resultCallback;
        try {
            final ContentType contentType = entityDetails != null ? ContentType.parse(entityDetails.getContentType()) : null;
            streamStart(contentType);
        } catch (final UnsupportedCharsetException ex) {
            throw new UnsupportedEncodingException(ex.getMessage());
        }
    }

    @Override
    protected final void completed() throws IOException {
        content = generateContent();
        if (resultCallback != null) {
            resultCallback.completed(content);
        }
        releaseResources();
    }

    @Override
    public final void failed(final Exception cause) {
        if (resultCallback != null) {
            resultCallback.failed(cause);
        }
        releaseResources();
    }

    @Override
    public final T getContent() {
        return content;
    }

}
