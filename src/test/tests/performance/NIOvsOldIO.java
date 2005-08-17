package tests.performance;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Random;

public class NIOvsOldIO {

    private static int BUFFER_SIZE = 8192; 
    private static int SO_TIMEOUT = 20000; 

    private static int PORT = 8082; 
    private static int RUN_COUNT = 20; 
    
    public NIOvsOldIO() {
        super();
    }

    public static void main(String[] args) throws Exception {
        byte[] data = new byte[1000000];
        Random rnd = new Random();
        rnd.nextBytes(data);

        DataProcessor oldioProc = new OldIORawReceiver();
        executeTest(data, new OldIOListenerThread(oldioProc, PORT));
        System.out.println("Old IO average time (ms): " + 
                oldioProc.getTotalTime() / RUN_COUNT);
        
        DataProcessor nioBlockingProc = new NIOBlockingReceiver();
        executeTest(data, new NIOListenerThread(nioBlockingProc, PORT));
        System.out.println("Blocking NIO average time (ms): " + 
                nioBlockingProc.getTotalTime() / RUN_COUNT);

        DataProcessor nioSelectProc = new NIOSelectReceiver();
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
    
    static interface DataProcessor {
        
        void process(Socket socket) throws IOException;
        
        long getTotalTime();
        
    }
    
    static class OldIORawReceiver implements DataProcessor {
        
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

    static class NIOBlockingReceiver implements DataProcessor {
        
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

    static class NIOSelectReceiver implements DataProcessor {
        
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

    static class OldIOListenerThread extends Thread {
        
        private final int port; 
        private final DataProcessor dataprocessor;
        private ServerSocket serversocket;
        
        public OldIOListenerThread(final DataProcessor dataprocessor, int port) {
            super();
            this.port = port;
            this.dataprocessor = dataprocessor;
        }
        
        public void run() {
            try {
                try {
                    this.serversocket = new ServerSocket(this.port);
                    while (!Thread.interrupted()) {
                        Socket socket = this.serversocket.accept();
                        try {
                            socket.setTcpNoDelay(true);
                            socket.setSendBufferSize(BUFFER_SIZE);
                            socket.setReceiveBufferSize(BUFFER_SIZE);
                            socket.setSoTimeout(SO_TIMEOUT);
                            this.dataprocessor.process(socket);
                        } finally {
                            socket.close();
                        }
                    }
                } finally {
                    this.serversocket.close();
                }
            } catch (IOException ex) {
                if (!isInterrupted()) {
                    ex.printStackTrace();
                }
            }
        }
        
        public void destroy() {
            interrupt();
            try {
                this.serversocket.close();
            } catch (IOException ignore) {
            }
        }
        
    }

    static class NIOListenerThread extends Thread {
        
        private final int port; 
        private final DataProcessor dataprocessor;
        private ServerSocketChannel serverchannel;
        
        public NIOListenerThread(final DataProcessor dataprocessor, int port) {
            super();
            this.port = port;
            this.dataprocessor = dataprocessor;
        }
        
        public void run() {
            try {
                this.serverchannel = ServerSocketChannel.open();
                try {
                    this.serverchannel.socket().bind(new InetSocketAddress(this.port));
                    while (!Thread.interrupted()) {
                        SocketChannel channel = this.serverchannel.accept();
                        try {
                            Socket socket = channel.socket();
                            socket.setTcpNoDelay(true);
                            socket.setSendBufferSize(BUFFER_SIZE);
                            socket.setReceiveBufferSize(BUFFER_SIZE);
                            socket.setSoTimeout(SO_TIMEOUT);
                            this.dataprocessor.process(socket);
                        } finally {
                            channel.close();
                        }
                    }
                } finally {
                    this.serverchannel.close();
                }
            } catch (IOException ex) {
                if (!isInterrupted()) {
                    ex.printStackTrace();
                }
            }
        }
        
        public void destroy() {
            interrupt();
            try {
                this.serverchannel.close();
            } catch (IOException ignore) {
            }
        }
        
    }
}