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
package org.apache.hc.core5.jackson2.http.examples;

import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.jackson2.bulk.JsonBulkArrayReader;
import org.apache.hc.core5.jackson2.http.RequestData;

public class JsonBulkArrayReaderExample {

    public static void main(final String... args) throws Exception {
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);

        final URL resource = JsonBulkArrayReaderExample.class.getResource("/sample6.json");

        final JsonBulkArrayReader bulkArrayReader = new JsonBulkArrayReader(objectMapper);
        bulkArrayReader.initialize(new TypeReference<RequestData>() {
        }, entry -> System.out.println(entry));
        try (final InputStream inputStream = resource.openStream()) {
            int l;
            final byte[] tmp = new byte[4096];
            while ((l = inputStream.read(tmp)) != -1) {
                bulkArrayReader.consume(ByteBuffer.wrap(tmp, 0, l));
            }
            bulkArrayReader.streamEnd();
        }
    }

}
