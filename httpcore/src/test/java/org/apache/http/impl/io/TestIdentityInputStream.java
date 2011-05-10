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

package org.apache.http.impl.io;

import org.apache.http.impl.SessionInputBufferMock;
import org.apache.http.io.SessionInputBuffer;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link IdentityInputStream}.
 *
 */
public class TestIdentityInputStream {

    @Test
    public void testConstructor() throws Exception {
        SessionInputBuffer receiver = new SessionInputBufferMock(new byte[] {});
        new IdentityInputStream(receiver);
        try {
            new IdentityInputStream(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    @Test
    public void testBasicRead() throws Exception {
        byte[] input = new byte[] {'a', 'b', 'c'};
        SessionInputBufferMock receiver = new SessionInputBufferMock(input);
        IdentityInputStream instream = new IdentityInputStream(receiver);
        byte[] tmp = new byte[2];
        Assert.assertEquals(2, instream.read(tmp, 0, tmp.length));
        Assert.assertEquals('a', tmp[0]);
        Assert.assertEquals('b', tmp[1]);
        Assert.assertEquals('c', instream.read());
        Assert.assertEquals(-1, instream.read(tmp, 0, tmp.length));
        Assert.assertEquals(-1, instream.read());
        Assert.assertEquals(-1, instream.read(tmp, 0, tmp.length));
        Assert.assertEquals(-1, instream.read());
    }

    @Test
    public void testClosedCondition() throws Exception {
        byte[] input = new byte[] {'a', 'b', 'c'};
        SessionInputBufferMock receiver = new SessionInputBufferMock(input);
        IdentityInputStream instream = new IdentityInputStream(receiver);

        instream.close();
        instream.close();

        Assert.assertEquals(0, instream.available());
        byte[] tmp = new byte[2];
        Assert.assertEquals(-1, instream.read(tmp, 0, tmp.length));
        Assert.assertEquals(-1, instream.read());
        Assert.assertEquals(-1, instream.read(tmp, 0, tmp.length));
        Assert.assertEquals(-1, instream.read());
    }

    @Test
    public void testAvailable() throws Exception {
        byte[] input = new byte[] {'a', 'b', 'c'};
        SessionInputBufferMock receiver = new SessionInputBufferMock(input);
        IdentityInputStream instream = new IdentityInputStream(receiver);
        instream.read();
        Assert.assertEquals(2, instream.available());
    }

}
