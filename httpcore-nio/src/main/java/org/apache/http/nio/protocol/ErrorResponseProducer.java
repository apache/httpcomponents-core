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

package org.apache.http.nio.protocol;

import java.io.IOException;
import java.util.Locale;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.ProducingNHttpEntity;
import org.apache.http.protocol.HTTP;

class ErrorResponseProducer implements HttpAsyncResponseProducer {

    private final HttpVersion version;
    private final int status;
    private final ProducingNHttpEntity entity;
    private final boolean keepAlive;

    ErrorResponseProducer(
            final HttpVersion version,
            final int status,
            final ProducingNHttpEntity entity,
            final boolean keepAlive) {
        super();
        this.version = version;
        this.status = status;
        this.entity = entity;
        this.keepAlive = keepAlive;
    }

    public HttpResponse generateResponse() {
        BasicHttpResponse response = new BasicHttpResponse(this.version, this.status,
                EnglishReasonPhraseCatalog.INSTANCE.getReason(this.status, Locale.US));
        if (this.keepAlive) {
            response.addHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE);
        } else {
            response.addHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
        }
        response.setEntity(this.entity);
        return response;
    }

    public void produceContent(
            final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
        this.entity.produceContent(encoder, ioctrl);
    }

    public void close() throws IOException {
        this.entity.finish();
    }

}