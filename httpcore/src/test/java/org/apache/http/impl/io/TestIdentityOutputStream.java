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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import junit.framework.TestCase;

import org.apache.http.mockup.SessionOutputBufferMockup;

public class TestIdentityOutputStream extends TestCase {

    public TestIdentityOutputStream(String testName) {
        super(testName);
    }

    public void testConstructors() throws Exception {
        new IdentityOutputStream(new SessionOutputBufferMockup());
        try {
            new IdentityOutputStream(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testBasics() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        SessionOutputBufferMockup datatransmitter = new SessionOutputBufferMockup(buffer);
        OutputStream out = new IdentityOutputStream(datatransmitter);

        byte[] tmp = new byte[10];
        out.write(tmp, 0, 10);
        out.write(tmp);
        out.write(1);
        out.flush();
        out.close();
        byte[] data = datatransmitter.getData();
        assertEquals(21, data.length);
    }

    public void testClose() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        SessionOutputBufferMockup datatransmitter = new SessionOutputBufferMockup(buffer);
        OutputStream out = new IdentityOutputStream(datatransmitter);
        out.close();
        out.close();
        byte[] tmp = new byte[10];
        try {
            out.write(tmp);
            fail("IOException should have been thrown");
        } catch (IOException ex) {
            // expected
        }
        try {
            out.write(1);
            fail("IOException should have been thrown");
        } catch (IOException ex) {
            // expected
        }
    }

    public void testConstructor() throws Exception {
        SessionOutputBufferMockup transmitter = new SessionOutputBufferMockup();
        new IdentityOutputStream(transmitter);
        try {
            new IdentityOutputStream(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    public void testBasicWrite() throws Exception {
        SessionOutputBufferMockup transmitter = new SessionOutputBufferMockup();
        IdentityOutputStream outstream = new IdentityOutputStream(transmitter);
        outstream.write(new byte[] {'a', 'b'}, 0, 2);
        outstream.write('c');
        outstream.flush();

        byte[] input = transmitter.getData();

        assertNotNull(input);
        byte[] expected = new byte[] {'a', 'b', 'c'};
        assertEquals(expected.length, input.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], input[i]);
        }
    }

    public void testClosedCondition() throws Exception {
        SessionOutputBufferMockup transmitter = new SessionOutputBufferMockup();
        IdentityOutputStream outstream = new IdentityOutputStream(transmitter);
        outstream.close();
        outstream.close();

        try {
            byte[] tmp = new byte[2];
            outstream.write(tmp, 0, tmp.length);
            fail("IOException should have been thrown");
        } catch (IOException e) {
            //expected
        }
        try {
            outstream.write('a');
            fail("IOException should have been thrown");
        } catch (IOException e) {
            //expected
        }
    }


}

