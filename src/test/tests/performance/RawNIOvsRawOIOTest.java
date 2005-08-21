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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Random;

public class RawNIOvsRawOIOTest {

    private static int BUFFER_SIZE = 8192; 
    private static int SO_TIMEOUT = 20000; 

    private static int PORT = 8082; 
    private static int RUN_COUNT = 20; 
    
    public RawNIOvsRawOIOTest() {
        super();
    }

    public static void main(String[] args) throws Exception {
        byte[] data = new byte[50000000];
        Random rnd = new Random();
        rnd.nextBytes(data);

        TestDataProcessor oldioProc = new OldIORawReceiver();
        executeTest(data, new OldIOListenerThread(oldioProc, PORT));
        System.out.println("Old IO average time (ms): " + 
                oldioProc.getTotalTime() / RUN_COUNT);
        
        TestDataProcessor nioBlockingProc = new NIOBlockingReceiver();
        executeTest(data, new NIOListenerThread(nioBlockingProc, PORT));
        System.out.println("Blocking NIO average time (ms): " + 
                nioBlockingProc.getTotalTime() / RUN_COUNT);

        TestDataProcessor nioSelectProc = new NIOSelectReceiver();
        executeTest(data, new NIOListenerThread(nioSelectProc, PORT));
        System.out.println("NIO with Select average time (ms): " + 
                nioSelectProc.getTotalTime() / RUN_COUNT);

        System.exit(0);
    }
    
    private static void executeTest(final byte[] data, final Thread t)
            throws IOException, InterruptedException {
        t.start();
        Thread.sleep(200);
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
        t.destroy();
        t.join(1000);
   }
    
    static class OldIORawReceiver implements TestDataProcessor {
        
        private long totalTime = 0;
        
        public OldIORawReceiver() {
            super();
        }
        
        public void process(final Socket socket) throws IOException {
            InputStream in = new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE); 
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

    static class NIOBlockingReceiver implements TestDataProcessor {
        
        private long totalTime = 0;
        
        public NIOBlockingReceiver() {
            super();
        }
        
        public void process(final Socket socket) throws IOException {
            SocketChannel channel = socket.getChannel();
            channel.configureBlocking(true);
            ByteBuffer tmp = ByteBuffer.allocateDirect(BUFFER_SIZE);
            long start = System.currentTimeMillis(); 
            for (;;) {
                int l = channel.read(tmp);
                if (l == -1) {
                    break;
                }
                tmp.clear();
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

    static class NIOSelectReceiver implements TestDataProcessor {
        
        private long totalTime = 0;
        
        public NIOSelectReceiver() {
            super();
        }
        
        public void process(final Socket socket) throws IOException {
            SocketChannel channel = socket.getChannel();
            channel.configureBlocking(false);
            Selector selector = Selector.open();
            channel.register(selector, SelectionKey.OP_READ);
            ByteBuffer tmp = ByteBuffer.allocateDirect(BUFFER_SIZE);
            long start = System.currentTimeMillis(); 
            for (;;) {
                selector.select(SO_TIMEOUT);
                int l = channel.read(tmp);
                if (l == -1) {
                    break;
                }
                tmp.clear();
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