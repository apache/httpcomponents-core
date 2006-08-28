/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
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

package org.apache.http.nio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.io.CharArrayBuffer;
import org.apache.http.nio.impl.NIOHttpDataReceiver;
import org.apache.http.nio.impl.NIOHttpDataTransmitter;
import org.apache.http.nio.mockup.NIOHttpDataReceiverMockup;
import org.apache.http.nio.mockup.NIOHttpDataTransmitterMockup;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

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

    public void testInit() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new NIOHttpDataTransmitterMockup(Channels.newChannel(out), 10); 
        try {
            new NIOHttpDataTransmitterMockup(Channels.newChannel(out), -10); 
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        try {
            new NIOHttpDataTransmitterMockup(null, 1024); 
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        new NIOHttpDataReceiverMockup(Channels.newChannel(in), 10); 
        try {
            new NIOHttpDataReceiverMockup(Channels.newChannel(in), -10); 
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        try {
            new NIOHttpDataReceiverMockup((ReadableByteChannel)null, 1024); 
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
        
        NIOHttpDataTransmitterMockup transmitter = new NIOHttpDataTransmitterMockup(); 
        for (int i = 0; i < teststrs.length; i++) {
            transmitter.writeLine(teststrs[i]);
        }
        //this write operation should have no effect
        transmitter.writeLine((String)null);
        transmitter.writeLine((CharArrayBuffer)null);
        transmitter.flush();
        
        NIOHttpDataReceiverMockup receiver = new NIOHttpDataReceiverMockup(transmitter.getData());

        assertTrue(receiver.isDataAvailable(0));
        
        for (int i = 0; i < teststrs.length; i++) {
            assertEquals(teststrs[i], receiver.readLine());
        }
        assertNull(receiver.readLine());
        assertNull(receiver.readLine());
    }

    public void testComplexReadWriteLine() throws Exception {
        NIOHttpDataTransmitterMockup transmitter = new NIOHttpDataTransmitterMockup(); 
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
        
        NIOHttpDataReceiverMockup receiver = new NIOHttpDataReceiverMockup(transmitter.getData());

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
    
    public void testBasicReadWriteLineLargeBuffer() throws Exception {
        
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
        
        NIOHttpDataTransmitterMockup transmitter = new NIOHttpDataTransmitterMockup(); 
        for (int i = 0; i < teststrs.length; i++) {
            transmitter.writeLine(teststrs[i]);
        }
        //this write operation should have no effect
        transmitter.writeLine((String)null);
        transmitter.writeLine((CharArrayBuffer)null);
        transmitter.flush();
        
        NIOHttpDataReceiverMockup receiver = new NIOHttpDataReceiverMockup(transmitter.getData(), 1024);

        assertTrue(receiver.isDataAvailable(0));
        
        for (int i = 0; i < teststrs.length; i++) {
            assertEquals(teststrs[i], receiver.readLine());
        }
        assertNull(receiver.readLine());
        assertNull(receiver.readLine());
    }

    public void testReadWriteBytes() throws Exception {
        // make the buffer larger than that of transmitter
        byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)('0' + i);
        }
        NIOHttpDataTransmitterMockup transmitter = new NIOHttpDataTransmitterMockup();
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

        byte[] tmp = transmitter.getData();
        assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            assertEquals(out[i], tmp[i]);
        }
        
        NIOHttpDataReceiverMockup receiver = new NIOHttpDataReceiverMockup(tmp);

        // these read operations will have no effect
        assertEquals(0, receiver.read(null, 0, 10));
        assertEquals(0, receiver.read(null));        
        
        byte[] in = new byte[40];
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
        NIOHttpDataTransmitterMockup transmitter = new NIOHttpDataTransmitterMockup();
        for (int i = 0; i < out.length; i++) {
            transmitter.write(out[i]);
        }
        transmitter.flush();

        byte[] tmp = transmitter.getData();
        assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            assertEquals(out[i], tmp[i]);
        }
        
        NIOHttpDataReceiverMockup receiver = new NIOHttpDataReceiverMockup(tmp);

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
    
    public void testLineLimit() throws Exception {
        HttpParams params = new DefaultHttpParams();
        String s = "a very looooooooooooooooooooooooooooooooooooooong line\r\n     ";
        byte[] tmp = s.getBytes("US-ASCII"); 
        NIOHttpDataReceiverMockup receiver1 = new NIOHttpDataReceiverMockup(tmp, 5);
        // no limit
        params.setIntParameter(HttpConnectionParams.MAX_LINE_LENGTH, 0);
        receiver1.reset(params);
        assertNotNull(receiver1.readLine());
        
        NIOHttpDataReceiverMockup receiver2 = new NIOHttpDataReceiverMockup(tmp, 5);
        // 15 char limit
        params.setIntParameter(HttpConnectionParams.MAX_LINE_LENGTH, 15);
        receiver2.reset(params);
        try {
            receiver2.readLine();
            fail("IOException should have been thrown");
        } catch (IOException ex) {
            // expected
        }
    }

    static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };
        
    static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438, 
        0x432, 0x435, 0x442 
    }; 
    
    private static String constructString(int [] unicodeChars) {
        StringBuffer buffer = new StringBuffer();
        if (unicodeChars != null) {
            for (int i = 0; i < unicodeChars.length; i++) {
                buffer.append((char)unicodeChars[i]); 
            }
        }
        return buffer.toString();
    }

    public void testMultibyteCodedReadWriteLine() throws Exception {
        String s1 = constructString(SWISS_GERMAN_HELLO);
        String s2 = constructString(RUSSIAN_HELLO);
        String s3 = "Like hello and stuff";
        
        HttpParams params = new DefaultHttpParams(null);
        HttpProtocolParams.setHttpElementCharset(params, "UTF-8");
        
        NIOHttpDataTransmitterMockup transmitter = new NIOHttpDataTransmitterMockup();
        transmitter.reset(params);

        for (int i = 0; i < 10; i++) {
            transmitter.writeLine(s1);
            transmitter.writeLine(s2);
            transmitter.writeLine(s3);
        }
        transmitter.flush();
        
        NIOHttpDataReceiverMockup receiver = new NIOHttpDataReceiverMockup(transmitter.getData());
        receiver.reset(params);

        assertTrue(receiver.isDataAvailable(0));
        
        for (int i = 0; i < 10; i++) {
            assertEquals(s1, receiver.readLine());
            assertEquals(s2, receiver.readLine());
            assertEquals(s3, receiver.readLine());
        }
        assertNull(receiver.readLine());
        assertNull(receiver.readLine());
    }

}
