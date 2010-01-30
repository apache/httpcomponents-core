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
import java.util.Locale;
import java.util.Random;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

class RandomDataHandler implements HttpRequestHandler  {
    
    public RandomDataHandler() {
        super();
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
            entity.consumeContent();
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
 
    static class RandomEntity extends AbstractHttpEntity {

        private int count;
        
        public RandomEntity(int count) {
            super();
            this.count = count;
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
            Random rnd = new Random(System.currentTimeMillis());
            byte[] tmp = new byte[1024];
            int remaining = this.count;
            while (remaining > 0) {
                int chunk = Math.min(tmp.length, remaining);
                for (int i = 0; i < chunk; i++) {
                    byte ch = (byte) (Math.abs(rnd.nextInt()) % 127);
                    if (ch < 32) {
                        ch = ' ';
                    }
                    tmp[i] = ch;
                }
                outstream.write(tmp, 0, chunk);
                remaining -= chunk;
            }
        }
        
    }
    
}