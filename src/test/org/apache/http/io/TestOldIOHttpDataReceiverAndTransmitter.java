/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.util.EncodingUtil;

public class TestOldIOHttpDataReceiverAndTransmitter extends TestCase {

    public TestOldIOHttpDataReceiverAndTransmitter(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestOldIOHttpDataReceiverAndTransmitter.class);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestOldIOHttpDataReceiverAndTransmitter.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public void testConstructors() {
        String s = "aaaaa";
        InputStream in = new ByteArrayInputStream(EncodingUtil.getAsciiBytes(s));
        new InputStreamHttpDataReceiver(in);
        try {
            new InputStreamHttpDataReceiver(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        new OutputStreamHttpDataTransmitter(new ByteArrayOutputStream()); 
        try {
            new OutputStreamHttpDataTransmitter(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
    public void testInputStreamHttpDataReceiver() throws IOException {
        String s = "aaaaa";
        InputStream in = new ByteArrayInputStream(EncodingUtil.getAsciiBytes(s));
        HttpDataReceiver datareceiver = new InputStreamHttpDataReceiver(in);
        assertTrue(datareceiver.isDataAvailable(1));
        assertEquals('a', datareceiver.read());
        byte[] tmp = new byte[2];
        datareceiver.read(tmp);
        assertEquals('a', tmp[0]);
        assertEquals('a', tmp[1]);
        datareceiver.read(tmp, 0, tmp.length);
        assertEquals('a', tmp[0]);
        assertEquals('a', tmp[1]);
        assertEquals(-1, datareceiver.read());
        datareceiver.reset(new DefaultHttpParams(null));
    }

    public void testOutputStreamHttpDataTransmitter() throws IOException {
        String s = "aaaaa\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpDataTransmitter datatransmitter = new OutputStreamHttpDataTransmitter(out);
        datatransmitter.reset(new DefaultHttpParams(null));
        
        datatransmitter.write('a');
        datatransmitter.write(new byte[] {'a'});
        datatransmitter.write(new byte[] {'a'}, 0, 1);
        datatransmitter.writeLine("aa");
        datatransmitter.flush();
        
        assertEquals(s, out.toString("US-ASCII"));
    }
}

