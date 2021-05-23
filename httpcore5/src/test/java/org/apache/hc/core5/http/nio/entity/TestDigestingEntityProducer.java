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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.WritableByteChannelMock;
import org.apache.hc.core5.http.nio.BasicDataStreamChannel;
import org.junit.Assert;
import org.junit.Test;

public class TestDigestingEntityProducer {

    @Test
    public void testProduceData() throws Exception {

        final DigestingEntityProducer producer = new DigestingEntityProducer("MD5",
                new StringAsyncEntityProducer("12345", ContentType.TEXT_PLAIN));

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final BasicDataStreamChannel dataStreamChannel = new BasicDataStreamChannel(byteChannel);
        while (byteChannel.isOpen()) {
            producer.produce(dataStreamChannel);
        }

        Assert.assertEquals("12345", byteChannel.dump(StandardCharsets.US_ASCII));
        final List<Header> trailers = dataStreamChannel.getTrailers();
        Assert.assertNotNull(trailers);
        Assert.assertEquals(2, trailers.size());

        Assert.assertEquals("digest-algo", trailers.get(0).getName());
        Assert.assertEquals("MD5", trailers.get(0).getValue());
        Assert.assertEquals("digest", trailers.get(1).getName());
        Assert.assertEquals("827ccb0eea8a706c4c34a16891f84e7b", trailers.get(1).getValue());
    }

}
