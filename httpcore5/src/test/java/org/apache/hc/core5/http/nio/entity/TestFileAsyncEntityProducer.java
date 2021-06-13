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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.WritableByteChannelMock;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.BasicDataStreamChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestFileAsyncEntityProducer {

    private File tempFile;

    @Before
    public void setup() throws Exception {
        tempFile = File.createTempFile("testing", ".txt");
        try (final Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.US_ASCII)) {
            writer.append("abcdef");
            writer.flush();
        }
    }

    @After
    public void cleanup() {
        if (tempFile != null) {
            tempFile.delete();
            tempFile = null;
        }
    }
    @Test
    public void testTextContent() throws Exception {

        final AsyncEntityProducer producer = new FileEntityProducer(tempFile, ContentType.TEXT_PLAIN);

        Assert.assertEquals(6, producer.getContentLength());
        Assert.assertEquals(ContentType.TEXT_PLAIN.toString(), producer.getContentType());
        Assert.assertNull(producer.getContentEncoding());

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

        producer.produce(streamChannel);
        producer.produce(streamChannel);

        Assert.assertFalse(byteChannel.isOpen());
        Assert.assertEquals("abcdef", byteChannel.dump(StandardCharsets.US_ASCII));
    }

    @Test
    public void testTextContentRepeatable() throws Exception {
        final AsyncEntityProducer producer = new FileEntityProducer(tempFile, ContentType.TEXT_PLAIN);

        Assert.assertEquals(6, producer.getContentLength());
        Assert.assertEquals(ContentType.TEXT_PLAIN.toString(), producer.getContentType());
        Assert.assertNull(producer.getContentEncoding());

        for (int i = 0; i < 3; i++) {
            final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
            final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

            producer.produce(streamChannel);
            producer.produce(streamChannel);

            Assert.assertFalse(byteChannel.isOpen());
            Assert.assertEquals("abcdef", byteChannel.dump(StandardCharsets.US_ASCII));

            producer.releaseResources();
        }
    }

}
