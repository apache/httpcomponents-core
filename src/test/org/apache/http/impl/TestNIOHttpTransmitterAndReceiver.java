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
        transmitter.flush();
        
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        HttpDataReceiver receiver = 
            new NIOHttpDataReceiver(Channels.newChannel(in), 16);

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
}
