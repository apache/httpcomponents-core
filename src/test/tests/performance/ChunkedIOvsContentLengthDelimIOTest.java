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

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;

import org.apache.http.impl.io.DefaultHttpDataReceiverFactory;
import org.apache.http.impl.io.OldIOSocketHttpDataTransmitter;
import org.apache.http.io.ChunkedInputStream;
import org.apache.http.io.ChunkedOutputStream;
import org.apache.http.io.ContentLengthInputStream;
import org.apache.http.io.HttpDataReceiverFactory;
import org.apache.http.io.HttpDataTransmitter;
import org.apache.http.io.IdentityOutputStream;

public class ChunkedIOvsContentLengthDelimIOTest {

    private static int BUFFER_SIZE = 8192; 
    private static int CONTENT_LEN = 10000000;

    private static int PORT = 8082; 
    private static int RUN_COUNT = 100; 
    
    public ChunkedIOvsContentLengthDelimIOTest() {
        super();
    }

    public static void main(String[] args) throws Exception {
    	Random rnd = new Random();
      
        byte[] data1 = new byte[CONTENT_LEN];
        rnd.nextBytes(data1);

        TestDataProcessor p1 = new ContentLengthReceiver(
        		new DefaultHttpDataReceiverFactory(false));
        executeTest(data1, false, new OldIOListenerThread(p1, PORT));
        System.out.println("Byte array read average time (ms): " + 
                p1.getTotalTime() / RUN_COUNT);
        
        TestDataProcessor p2 = new ChunkedReceiver(
        		new DefaultHttpDataReceiverFactory(false));
        executeTest(data1, true, new OldIOListenerThread(p2, PORT));
        System.out.println("Chunked byte array read (classic IO) average time (ms): " + 
                p2.getTotalTime() / RUN_COUNT);
        
        TestDataProcessor p3 = new ChunkedReceiver(
        		new DefaultHttpDataReceiverFactory(true));
        executeTest(data1, true, new NIOListenerThread(p3, PORT));
        System.out.println("Chunked byte array read (NIO) average time (ms): " + 
                p3.getTotalTime() / RUN_COUNT);

        data1 = null;
        
        System.exit(0);
    }
    
    private static void executeTest(
    		final byte[] data,
    		boolean chunked,
    		final Thread t)
            throws IOException, InterruptedException {
        t.start();
        Thread.sleep(200);
        try {
            for (int i = 0; i < RUN_COUNT; i++) {
                Socket conn = new Socket("localhost", PORT);
                try {
                	HttpDataTransmitter datatransmitter = 
                		new OldIOSocketHttpDataTransmitter(conn);
                    OutputStream out = null;
                    if (chunked) {
                    	out = new ChunkedOutputStream(datatransmitter); 
                    } else {
                    	out = new IdentityOutputStream(datatransmitter); 
                    }
                    int off = 0; 
                    int remaining = data.length;
                    while (remaining > 0) {
                    	int chunk = Math.min(2048, remaining); 
                        out.write(data, off, chunk);
                        off += chunk;
                        remaining -= chunk;
                    }
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
    
    static class ContentLengthReceiver implements TestDataProcessor {
        
    	final HttpDataReceiverFactory factory;
        private long totalTime = 0;
        
        public ContentLengthReceiver(final HttpDataReceiverFactory factory) {
            super();
            this.factory = factory;
        }
        
        public void process(final Socket socket) throws IOException {
        	ContentLengthInputStream in = new ContentLengthInputStream(
        			this.factory.create(socket), CONTENT_LEN);  
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
    
    static class ChunkedReceiver implements TestDataProcessor {
        
    	final HttpDataReceiverFactory factory;
        private long totalTime = 0;
        
        public ChunkedReceiver(final HttpDataReceiverFactory factory) {
            super();
            this.factory = factory;
        }
        
        public void process(final Socket socket) throws IOException {
        	ChunkedInputStream in = new ChunkedInputStream(
        			this.factory.create(socket));  
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
}