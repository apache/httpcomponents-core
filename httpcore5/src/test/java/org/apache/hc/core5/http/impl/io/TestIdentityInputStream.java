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

package org.apache.hc.core5.http.impl.io;

import java.io.ByteArrayInputStream;

import org.apache.hc.core5.http.StreamClosedException;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IdentityInputStream}.
 */
public class TestIdentityInputStream {

    @Test
    public void testBasicRead() throws Exception {
        final byte[] input = new byte[] {'a', 'b', 'c'};
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final IdentityInputStream in = new IdentityInputStream(inBuffer, inputStream);
        final byte[] tmp = new byte[2];
        Assertions.assertEquals(2, in.read(tmp, 0, tmp.length));
        Assertions.assertEquals('a', tmp[0]);
        Assertions.assertEquals('b', tmp[1]);
        Assertions.assertEquals('c', in.read());
        Assertions.assertEquals(-1, in.read(tmp, 0, tmp.length));
        Assertions.assertEquals(-1, in.read());
        Assertions.assertEquals(-1, in.read(tmp, 0, tmp.length));
        Assertions.assertEquals(-1, in.read());
        in.close();
    }

    @Test
    public void testClosedCondition() throws Exception {
        final byte[] input = new byte[] {'a', 'b', 'c'};
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final IdentityInputStream in = new IdentityInputStream(inBuffer, inputStream);

        in.close();
        in.close();

        Assertions.assertEquals(0, in.available());
        final byte[] tmp = new byte[2];
        Assertions.assertThrows(StreamClosedException.class, () -> in.read(tmp, 0, tmp.length));
        Assertions.assertThrows(StreamClosedException.class, () -> in.read());
    }

    @Test
    public void testAvailable() throws Exception {
        final byte[] input = new byte[] {'a', 'b', 'c'};
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(new BasicHttpTransportMetrics(), 16, 16, 1024, null);
        final IdentityInputStream in = new IdentityInputStream(inBuffer, inputStream);
        in.read();
        Assertions.assertEquals(2, in.available());
        in.close();
    }

    @Test
    public void testAvailableInStream() throws Exception {
        final byte[] input = new byte[] {'a', 'b', 'c', 'd', 'e', 'f'};
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(new BasicHttpTransportMetrics(), 16, 0, 1024, null);
        final IdentityInputStream in = new IdentityInputStream(inBuffer, inputStream);
        final byte[] tmp = new byte[3];
        Assertions.assertEquals(3, in.read(tmp));
        Assertions.assertEquals(3, in.available());
        in.close();
    }

}
