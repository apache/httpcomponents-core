package org.apache.http.nio.examples;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;

import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.nio.IOConsumer;
import org.apache.http.nio.IOEventDispatch;
import org.apache.http.nio.IOProducer;
import org.apache.http.nio.IOSession;
import org.apache.http.nio.IOReactor;
import org.apache.http.nio.impl.AsyncHttpDataReceiver;
import org.apache.http.nio.impl.AsyncHttpDataTransmitter;
import org.apache.http.nio.impl.DefaultIOReactor;
import org.apache.http.params.HttpParams;

public class AsyncEchoServer {

    public static void main(String[] args) throws Exception {
        HttpParams params = new DefaultHttpParams(); 
        IOEventDispatch ioEventDispatch = new DefaultIoEventDispatch();
        IOReactor ioReactor = new DefaultIOReactor(new InetSocketAddress(8080), params);
        try {
            ioReactor.execute(ioEventDispatch);
        } catch (InterruptedIOException ex) {
            System.err.println("Interrupted");
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
        System.out.println("Shutdown");
    }
    
    static class DefaultIoEventDispatch implements IOEventDispatch {

        public static final String CONSUMER = "CONSUMER";
        public static final String PRODUCER = "PRODUCER";
        public static final String WORKER = "WORKER";
        
        public void connected(IOSession session) {
            System.out.println("connected");
            
            AsyncHttpDataReceiver datareceiver = new AsyncHttpDataReceiver(session, 2048);
            session.setAttribute(CONSUMER, datareceiver);
            AsyncHttpDataTransmitter datatransmitter = new AsyncHttpDataTransmitter(session, 2048);
            session.setAttribute(PRODUCER, datatransmitter);
            
            Thread worker = new WorkerThread(session, datareceiver, datatransmitter);
            session.setAttribute(WORKER, worker);

            worker.setDaemon(true);
            worker.start();
            
            session.setSocketTimeout(20000);
        }

        public void inputReady(final IOSession session) {
            System.out.println("readable");
            IOConsumer consumer = (IOConsumer) session.getAttribute(CONSUMER);
            try {
                consumer.consumeInput();
            } catch (IOException ex) {
                consumer.shutdown(ex);
            }
        }

        public void outputReady(final IOSession session) {
            System.out.println("writeable");
            IOProducer producer = (IOProducer) session.getAttribute(PRODUCER);
            try {
                producer.produceOutput();
            } catch (IOException ex) {
                producer.shutdown(ex);
            }
        }

        public void timeout(final IOSession session) {
            System.out.println("timeout");
            session.close();
        }
        
        public void disconnected(final IOSession session) {
            System.out.println("disconnected");
            
            IOConsumer consumer = (IOConsumer) session.getAttribute(CONSUMER);
            IOProducer producer = (IOProducer) session.getAttribute(PRODUCER);
            
            consumer.shutdown();
            producer.shutdown();
            
            Thread worker = (Thread) session.getAttribute(WORKER);
            worker.interrupt();
        }
        
    }
    
    static class WorkerThread extends Thread {

        private final IOSession session;
        private final AsyncHttpDataReceiver datareceiver;
        private final AsyncHttpDataTransmitter datatransmitter;
        
        public WorkerThread(
                final IOSession session,
                final AsyncHttpDataReceiver datareceiver, 
                final AsyncHttpDataTransmitter datatransmitter) {
            super();
            this.session = session;
            this.datareceiver = datareceiver;
            this.datatransmitter = datatransmitter;
        }
        
        public void run() {
            try {
                while (!this.session.isClosed() && !Thread.interrupted()) {
                    String s = this.datareceiver.readLine();
                    System.out.println(s);
                    if ("EXIT".equalsIgnoreCase(s)) {
                        this.session.close();
                        break;
                    }
                    if (s != null) {
                        this.datatransmitter.writeLine(s);
                        this.datatransmitter.flush();
                    }
                }
            } catch (IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            }
        }
        
    }
    
}
