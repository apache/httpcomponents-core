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
package org.apache.hc.core5.http.nio;

import java.io.IOException;
import java.net.URI;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;

public class BasicRequestProducer implements AsyncRequestProducer {

    private final HttpRequest request;
    private final AsyncEntityProducer dataProducer;

    public BasicRequestProducer(final HttpRequest request, final AsyncEntityProducer dataProducer) {
        this.request = request;
        this.dataProducer = dataProducer;
    }

    public BasicRequestProducer(final String method, final HttpHost host, final String path, final AsyncEntityProducer dataProducer) {
        this(new BasicHttpRequest(method, host, path), dataProducer);
    }

    public BasicRequestProducer(final String method, final HttpHost host, final String path) {
        this(method, host, path, null);
    }

    public BasicRequestProducer(final String method, final URI requestUri, final AsyncEntityProducer dataProducer) {
        this(new BasicHttpRequest(method, requestUri), dataProducer);
    }

    public BasicRequestProducer(final String method, final URI requestUri) {
        this(method, requestUri, null);
    }

    @Override
    public HttpRequest produceRequest() {
        return request;
    }

    @Override
    public EntityDetails getEntityDetails() {
        return dataProducer;
    }

    @Override
    public int available() {
        return dataProducer != null ? dataProducer.available() : 0;
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        if (dataProducer != null) {
            dataProducer.produce(channel);
        }
    }

    @Override
    public void failed(final Exception cause) {
        releaseResources();
    }

    @Override
    public void releaseResources() {
        if (dataProducer != null) {
            dataProducer.releaseResources();
        }
    }
}
