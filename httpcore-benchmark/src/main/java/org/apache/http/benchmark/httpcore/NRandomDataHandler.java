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
package org.apache.http.benchmark.httpcore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.BufferingNHttpEntity;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.entity.ProducingNHttpEntity;
import org.apache.http.nio.protocol.NHttpRequestHandler;
import org.apache.http.nio.protocol.NHttpResponseTrigger;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

class NRandomDataHandler implements NHttpRequestHandler  {

    public NRandomDataHandler() {
        super();
    }

    public ConsumingNHttpEntity entityRequest(
            final HttpEntityEnclosingRequest request,
            final HttpContext context) throws HttpException, IOException {
        // Use buffering entity for simplicity
        return new BufferingNHttpEntity(request.getEntity(), new HeapByteBufferAllocator());
    }

    public void handle(
            final HttpRequest request,
            final HttpResponse response,
            final NHttpResponseTrigger trigger,
            final HttpContext context) throws HttpException, IOException {
        String method = request.getRequestLine().getMethod().toUpperCase(Locale.ENGLISH);
        if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
            throw new MethodNotSupportedException(method + " method not supported");
        }
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            EntityUtils.consume(entity);
        }
        String target = request.getRequestLine().getUri();

        int count = 100;

        int idx = target.indexOf('?');
        if (idx != -1) {
            String s = target.substring(idx + 1);
            if (s.startsWith("c=")) {
                s = s.substring(2);
                try {
                    count = Integer.parseInt(s);
                } catch (NumberFormatException ex) {
                    response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                    response.setEntity(new StringEntity("Invalid query format: " + s,
                            "text/plain", "ASCII"));
                    return;
                }
            }
        }
        response.setStatusCode(HttpStatus.SC_OK);
        RandomEntity body = new RandomEntity(count);
        response.setEntity(body);
        trigger.submitResponse(response);
    }



    public void handle(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws HttpException, IOException {
        String method = request.getRequestLine().getMethod().toUpperCase(Locale.ENGLISH);
        if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
            throw new MethodNotSupportedException(method + " method not supported");
        }
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            EntityUtils.consume(entity);
        }
        String target = request.getRequestLine().getUri();

        int count = 100;

        int idx = target.indexOf('?');
        if (idx != -1) {
            String s = target.substring(idx + 1);
            if (s.startsWith("c=")) {
                s = s.substring(2);
                try {
                    count = Integer.parseInt(s);
                } catch (NumberFormatException ex) {
                    response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                    response.setEntity(new StringEntity("Invalid query format: " + s,
                            "text/plain", "ASCII"));
                    return;
                }
            }
        }
        response.setStatusCode(HttpStatus.SC_OK);
        RandomEntity body = new RandomEntity(count);
        response.setEntity(body);
    }

    static class RandomEntity extends AbstractHttpEntity implements ProducingNHttpEntity {

        private final int count;
        private final ByteBuffer buf;

        private int remaining;

        public RandomEntity(int count) {
            super();
            this.count = count;
            this.remaining = count;
            this.buf = ByteBuffer.allocate(1024);
            setContentType("text/plain");
        }

        public InputStream getContent() throws IOException, IllegalStateException {
            throw new IllegalStateException("Method not supported");
        }

        public long getContentLength() {
            return this.count;
        }

        public boolean isRepeatable() {
            return true;
        }

        public boolean isStreaming() {
            return false;
        }

        public void writeTo(final OutputStream outstream) throws IOException {
            throw new IllegalStateException("Method not supported");
        }

        public void produceContent(
                final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
            int r = Math.abs(this.buf.hashCode());
            int chunk = Math.min(this.buf.remaining(), this.remaining);
            if (chunk > 0) {
                for (int i = 0; i < chunk; i++) {
                    byte b = (byte) ((r + i) % 96 + 32);
                    this.buf.put(b);
                }
            }
            this.buf.flip();
            int bytesWritten = encoder.write(this.buf);
            this.remaining -= bytesWritten;
            if (this.remaining == 0 && this.buf.remaining() == 0) {
                encoder.complete();
            }
            this.buf.compact();
        }

        public void finish() throws IOException {
            this.remaining = this.count;
        }

    }

}