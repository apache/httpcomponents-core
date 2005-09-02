/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package tests.performance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;

import org.apache.http.impl.io.DefaultHttpDataReceiverFactory;
import org.apache.http.io.HttpDataReceiver;
import org.apache.http.io.HttpDataReceiverFactory;

public class HttpNIOvsHttpOIOTest {

    private static int BUFFER_SIZE = 8192; 

    private static int PORT = 8082; 
    private static int RUN_COUNT = 1; 
    
    public HttpNIOvsHttpOIOTest() {
        super();
    }

    public static void main(String[] args) throws Exception {
    	Random rnd = new Random();
      
        byte[] data1 = new byte[50000000];
        rnd.nextBytes(data1);

        TestDataProcessor p1 = new ByteArrayReceiver(
        		new DefaultHttpDataReceiverFactory(false));
        executeTest(data1, new OldIOListenerThread(p1, PORT));
        System.out.println("Old IO byte array read average time (ms): " + 
                p1.getTotalTime() / RUN_COUNT);
        
        TestDataProcessor p2 = new ByteArrayReceiver(
        		new DefaultHttpDataReceiverFactory(true));
        executeTest(data1, new NIOListenerThread(p2, PORT));
        System.out.println("NIO byte array read average time (ms): " + 
                p2.getTotalTime() / RUN_COUNT);
        
        data1 = null;
        
        byte[] data2 = new byte[5000000];
        rnd.nextBytes(data2);

        TestDataProcessor p3 = new ByteReceiver(
        		new DefaultHttpDataReceiverFactory(false));
        executeTest(data2, new OldIOListenerThread(p3, PORT));
        System.out.println("Old IO one byte read average time (ms): " + 
                p3.getTotalTime() / RUN_COUNT);
        
        TestDataProcessor p4 = new ByteReceiver(
        		new DefaultHttpDataReceiverFactory(true));
        executeTest(data2, new NIOListenerThread(p4, PORT));
        System.out.println("NIO one byte read average time (ms): " + 
                p4.getTotalTime() / RUN_COUNT);

        data2 = null;
        
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(200000);
        StringBuffer s = new StringBuffer();   
        for (int i = 0; i < 100000; i++) {
        	s.setLength(0);
        	int count = rnd.nextInt(9) + 1;
        	for (int j = 0; j < count; j++) {
        		if (j > 0) {
            		s.append(" ");
        		}
        		s.append("yada");
        	}
    		s.append("\r\n");
    		buffer.write(s.toString().getBytes("US-ASCII"));
        }
        byte[] data3 = buffer.toByteArray();
        buffer = null;

        TestDataProcessor p5 = new LineReceiver(
        		new DefaultHttpDataReceiverFactory(false));
        executeTest(data3, new OldIOListenerThread(p5, PORT));
        System.out.println("Old IO one line read average time (ms): " + 
                p5.getTotalTime() / RUN_COUNT);
        
        TestDataProcessor p6 = new LineReceiver(
        		new DefaultHttpDataReceiverFactory(true));
        executeTest(data3, new NIOListenerThread(p6, PORT));
        System.out.println("NIO one line read average time (ms): " + 
                p6.getTotalTime() / RUN_COUNT);
        
        System.exit(0);
    }
    
    private static void executeTest(final byte[] data, final Thread t)
            throws IOException, InterruptedException {
        t.start();
        Thread.sleep(200);
        try {
            for (int i = 0; i < RUN_COUNT; i++) {
                Socket conn = new Socket("localhost", PORT);
                try {
                    OutputStream out = conn.getOutputStream();
                    out.write(data);
                    out.flush();
                    out.close();
                } finally {
                    conn.close();
                }
                Thread.sleep(100);
            }
        } finally {
            t.destroy();
            t.join(1000);
        }
   }
    
    static class ByteArrayReceiver implements TestDataProcessor {
        
    	final HttpDataReceiverFactory factory;
        private long totalTime = 0;
        
        public ByteArrayReceiver(final HttpDataReceiverFactory factory) {
            super();
            this.factory = factory;
        }
        
        public void process(final Socket socket) throws IOException {
        	HttpDataReceiver in = this.factory.create(socket);
            byte[] tmp = new byte[BUFFER_SIZE];
            long start = System.currentTimeMillis(); 
            while (in.read(tmp) != -1) {
            }
            long t = System.currentTimeMillis() - start;
            synchronized (this) {
                this.totalTime += t; 
            }
        }

        public synchronized long getTotalTime() {
            return this.totalTime;
        }
        
    }
        
    static class ByteReceiver implements TestDataProcessor {
        
    	final HttpDataReceiverFactory factory;
        private long totalTime = 0;
        
        public ByteReceiver(final HttpDataReceiverFactory factory) {
            super();
            this.factory = factory;
        }
        
        public void process(final Socket socket) throws IOException {
        	HttpDataReceiver in = this.factory.create(socket);
            long start = System.currentTimeMillis(); 
            while (in.read() != -1) {
            }
            long t = System.currentTimeMillis() - start;
            synchronized (this) {
                this.totalTime += t; 
            }
        }

        public synchronized long getTotalTime() {
            return this.totalTime;
        }
        
    }
    
    static class LineReceiver implements TestDataProcessor {
        
    	final HttpDataReceiverFactory factory;
        private long totalTime = 0;
        
        public LineReceiver(final HttpDataReceiverFactory factory) {
            super();
            this.factory = factory;
        }
        
        public void process(final Socket socket) throws IOException {
        	HttpDataReceiver in = this.factory.create(socket);
            long start = System.currentTimeMillis(); 
            while (in.readLine() != null) {
            }
            long t = System.currentTimeMillis() - start;
            synchronized (this) {
                this.totalTime += t; 
            }
        }

        public synchronized long getTotalTime() {
            return this.totalTime;
        }
        
    }
    
}