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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link AsyncEntityProducers}.
 */
public class TestAsyncEntityProducers {

    @Test
    public void testPathEntityProducer() throws IOException {
        final Path path = Paths.get("src/test/resources/test-ssl.txt");
        final AsyncEntityProducer producer = AsyncEntityProducers.create(path, ContentType.APPLICATION_OCTET_STREAM,
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try {
            Assertions.assertFalse(producer.isChunked());
            Assertions.assertEquals(Files.size(path), producer.getContentLength());
            Assertions.assertEquals(ContentType.APPLICATION_OCTET_STREAM.toString(), producer.getContentType());
        } finally {
            producer.releaseResources();
        }
    }

    @Test
    public void testPathEntityProducerWithTrailers() throws IOException {
        final Path path = Paths.get("src/test/resources/test-ssl.txt");
        final Header header1 = new BasicHeader("Tailer1", "Value1");
        final Header header2 = new BasicHeader("Tailer2", "Value2");
        final AsyncEntityProducer producer = AsyncEntityProducers.create(path, ContentType.APPLICATION_OCTET_STREAM,
                header1, header2);
        try {
            Assertions.assertTrue(producer.isChunked());
            Assertions.assertEquals(-1, producer.getContentLength());
            Assertions.assertEquals(ContentType.APPLICATION_OCTET_STREAM.toString(), producer.getContentType());
        } finally {
            producer.releaseResources();
        }
    }
}
