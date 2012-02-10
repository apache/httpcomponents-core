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
package org.apache.http.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.entity.EntityDeserializer;
import org.apache.http.impl.entity.EntitySerializer;
import org.apache.http.impl.entity.LaxContentLengthStrategy;
import org.apache.http.impl.entity.StrictContentLengthStrategy;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.io.SessionOutputBuffer;

class BenchmarkConnection extends DefaultHttpClientConnection {

    private final Stats stats;
    
    BenchmarkConnection(final Stats stats) {
        super();
        this.stats = stats;
    }

    @Override
    protected EntityDeserializer createEntityDeserializer() {
        return new EntityDeserializer(new LaxContentLengthStrategy()) {

            @Override
            protected BasicHttpEntity doDeserialize(
                    final SessionInputBuffer inbuffer, 
                    final HttpMessage message) throws HttpException, IOException {
                BasicHttpEntity entity = super.doDeserialize(inbuffer, message);
                InputStream instream = entity.getContent();
                entity.setContent(new CountingInputStream(instream, stats));
                return entity;
            }
            
        };
    }

    @Override
    protected EntitySerializer createEntitySerializer() {
        return new EntitySerializer(new StrictContentLengthStrategy()) {

            @Override
            protected OutputStream doSerialize(
                    final SessionOutputBuffer outbuffer, 
                    final HttpMessage message) throws HttpException, IOException {
                return new CountingOutputStream(super.doSerialize(outbuffer, message), stats);
            }
            
        };
    }
    
}
