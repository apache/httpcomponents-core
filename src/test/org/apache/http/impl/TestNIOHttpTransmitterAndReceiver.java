/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
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

package org.apache.http.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;

import org.apache.http.HttpDataReceiver;
import org.apache.http.HttpDataTransmitter;

import junit.framework.*;

/**
 * Simple tests for {@link NIOHttpDataTransmitter} and {@link NIOHttpDataReceiver}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Id$
 */
public class TestNIOHttpTransmitterAndReceiver extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestNIOHttpTransmitterAndReceiver(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestNIOHttpTransmitterAndReceiver.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestNIOHttpTransmitterAndReceiver.class);
    }

    public void testConstructor() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpDataTransmitter transmitter1 = 
            new NIOHttpDataTransmitter(Channels.newChannel(out), -10); 
        HttpDataTransmitter transmitter2 = 
            new NIOHttpDataTransmitter(Channels.newChannel(out), 200000000); 
        try {
            HttpDataTransmitter transmitter3 = new NIOHttpDataTransmitter(null, 1024); 
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        HttpDataReceiver receiver1 = 
            new NIOHttpDataReceiver(Channels.newChannel(in), -10); 
        HttpDataReceiver receiver2 = 
            new NIOHttpDataReceiver(Channels.newChannel(in), 200000000); 
        try {
            HttpDataReceiver receiver3 = new NIOHttpDataReceiver(null, 1024); 
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }
    
    public void testBasicReadWriteLine() throws Exception {
        
        String[] teststrs = new String[5];
        teststrs[0] = "Hello";
        teststrs[1] = "This string should be much longer than the size of the output buffer " +
                "which is only 16 bytes for this test";
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < 15; i++) {
            buffer.append("123456789 ");
        }
        buffer.append("and stuff like that");
        teststrs[2] = buffer.toString();
        teststrs[3] = "";
        teststrs[4] = "And goodbye";
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpDataTransmitter transmitter = 
            new NIOHttpDataTransmitter(Channels.newChannel(out), 16); 
        for (int i = 0; i < teststrs.length; i++) {
            transmitter.writeLine(teststrs[i]);
        }
        //this write operation should have no effect
        transmitter.writeLine(null);
        transmitter.flush();
        
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        HttpDataReceiver receiver = 
            new NIOHttpDataReceiver(Channels.newChannel(in), 16);

        assertTrue(receiver.isDataAvailable(0));
        
        for (int i = 0; i < teststrs.length; i++) {
            assertEquals(teststrs[i], receiver.readLine());
        }
        assertNull(receiver.readLine());
        assertNull(receiver.readLine());
    }

    public void testComplexReadWriteLine() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpDataTransmitter transmitter = 
            new NIOHttpDataTransmitter(Channels.newChannel(out), 16); 
        transmitter.write(new byte[] {'a', '\n'});
        transmitter.write(new byte[] {'\r', '\n'});
        transmitter.write(new byte[] {'\r', '\r', '\n'});
        transmitter.write(new byte[] {'\n'});
        //these write operations should have no effect
        transmitter.write(null);
        transmitter.write(null, 0, 12);
        
        transmitter.flush();

        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < 14; i++) {
            buffer.append("a");
        }
        String s1 = buffer.toString();
        buffer.append("\r\n");
        transmitter.write(buffer.toString().getBytes("US-ASCII"));
        transmitter.flush();

        buffer.setLength(0);
        for (int i = 0; i < 15; i++) {
            buffer.append("a");
        }
        String s2 = buffer.toString();
        buffer.append("\r\n");
        transmitter.write(buffer.toString().getBytes("US-ASCII"));
        transmitter.flush();

        buffer.setLength(0);
        for (int i = 0; i < 16; i++) {
            buffer.append("a");
        }
        String s3 = buffer.toString();
        buffer.append("\r\n");
        transmitter.write(buffer.toString().getBytes("US-ASCII"));
        transmitter.flush();

        transmitter.write(new byte[] {'a'});
        transmitter.flush();
        
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        HttpDataReceiver receiver = 
            new NIOHttpDataReceiver(Channels.newChannel(in), 16);

        assertEquals("a", receiver.readLine());
        assertEquals("", receiver.readLine());
        assertEquals("\r", receiver.readLine());
        assertEquals("", receiver.readLine());
        assertEquals(s1, receiver.readLine());
        assertEquals(s2, receiver.readLine());
        assertEquals(s3, receiver.readLine());
        assertEquals("a", receiver.readLine());
        assertNull(receiver.readLine());
        assertNull(receiver.readLine());
    }
    
    public void testReadWriteBytes() throws Exception {
        // make the buffer larger than that of transmitter
        byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)('0' + i);
        }
        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        HttpDataTransmitter transmitter = 
            new NIOHttpDataTransmitter(Channels.newChannel(outstream), 16);
        int off = 0;
        int remaining = out.length;
        while (remaining > 0) {
            int chunk = 10;
            if (chunk > remaining) {
                chunk = remaining;
            }
            transmitter.write(out, off, chunk);
            off += chunk;
            remaining -= chunk;
        }
        transmitter.flush();

        byte[] tmp = outstream.toByteArray();
        assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            assertEquals(out[i], tmp[i]);
        }
        
        ByteArrayInputStream instream = new ByteArrayInputStream(tmp);
        HttpDataReceiver receiver = 
            new NIOHttpDataReceiver(Channels.newChannel(instream), 16);

        // these read operations will have no effect
        assertEquals(0, receiver.read(null, 0, 10));
        assertEquals(0, receiver.read(null));        
        
        byte[] in = new byte[40];
        int noRead = 0;
        off = 0;
        remaining = in.length;
        while (remaining > 0) {
            int chunk = 10;
            if (chunk > remaining) {
                chunk = remaining;
            }
            int l = receiver.read(in, off, chunk);
            if (l == -1) {
                break;
            }
            off += l;
            remaining -= l;
        }
        for (int i = 0; i < out.length; i++) {
            assertEquals(out[i], in[i]);
        }
        assertEquals(-1, receiver.read(tmp));
        assertEquals(-1, receiver.read(tmp));
    }
    
    public void testReadWriteByte() throws Exception {
        // make the buffer larger than that of transmitter
        byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)('0' + i);
        }
        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        HttpDataTransmitter transmitter = 
            new NIOHttpDataTransmitter(Channels.newChannel(outstream), 16);
        for (int i = 0; i < out.length; i++) {
            transmitter.write(out[i]);
        }
        transmitter.flush();

        byte[] tmp = outstream.toByteArray();
        assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            assertEquals(out[i], tmp[i]);
        }
        
        ByteArrayInputStream instream = new ByteArrayInputStream(tmp);
        HttpDataReceiver receiver = 
            new NIOHttpDataReceiver(Channels.newChannel(instream), 16);

        byte[] in = new byte[40];
        for (int i = 0; i < in.length; i++) {
            in[i] = (byte)receiver.read();
        }
        for (int i = 0; i < out.length; i++) {
            assertEquals(out[i], in[i]);
        }
        assertEquals(-1, receiver.read());
        assertEquals(-1, receiver.read());
    }
    
}
