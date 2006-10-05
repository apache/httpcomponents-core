package org.apache.http.nio.examples;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.message.HttpGet;
import org.apache.http.nio.IOConsumer;
import org.apache.http.nio.IOEventDispatch;
import org.apache.http.nio.IOProducer;
import org.apache.http.nio.IOReactor;
import org.apache.http.nio.IOSession;
import org.apache.http.nio.SessionRequest;
import org.apache.http.nio.SessionRequestCallback;
import org.apache.http.nio.impl.AsyncHttpClientConnection;
import org.apache.http.nio.impl.DefaultIOReactor;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;

public class AsyncHttpClient {

    public static void main(String[] args) throws Exception {
        HttpParams params = new DefaultHttpParams(null);
        params
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 5000)
            .setIntParameter(HttpConnectionParams.CONNECTION_TIMEOUT, 10000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
            .setParameter(HttpProtocolParams.USER_AGENT, "Jakarta-HttpComponents-NIO/1.1");

        final IOEventDispatch ioEventDispatch = new DefaultIoEventDispatch(params);
        final IOReactor ioReactor = new DefaultIOReactor(params);

        SessionRequestCallback sessionReqCallback = new DefaultSessionRequestCallback(params);
        
        SessionRequest req1 = ioReactor.connect(
                new InetSocketAddress("www.yahoo.com", 80), null);
        req1.setCallback(sessionReqCallback);
        
        SessionRequest req2 = ioReactor.connect(
                new InetSocketAddress("www.google.com", 80), null);
        req2.setCallback(sessionReqCallback);
        
        SessionRequest req3 = ioReactor.connect(
                new InetSocketAddress("www.apache.org", 80), null);
        req3.setCallback(sessionReqCallback);
        
        Thread ioThread = new Thread(new Runnable() {

            public void run() {
                try {
                    
                    ioReactor.execute(ioEventDispatch);
                } catch (InterruptedIOException ex) {
                    System.err.println("Interrupted");
                } catch (IOException e) {
                    System.err.println("I/O error: " + e.getMessage());
                }
                System.out.println("Shutdown");
            }
            
        });
        ioThread.setDaemon(true);
        ioThread.start();
        req1.waitFor();
        req2.waitFor();
        req3.waitFor();
    }

    private static final String CONSUMER = "CONSUMER";
    private static final String PRODUCER = "PRODUCER";
    private static final String CONNECTION = "CONNECTION";
    private static final String TARGET = "TARGET";
    private static final String WORKER = "WORKER";
    
    static class DefaultSessionRequestCallback implements SessionRequestCallback {

        private final HttpParams params;
        
        public DefaultSessionRequestCallback(final HttpParams params) {
            super();
            if (params == null) {
                throw new IllegalArgumentException("HTTP parameters may nor be null");
            }
            this.params = params;
        }
        
        public void completed(final SessionRequest request) {
            IOSession session = request.getSession();
            InetSocketAddress address = (InetSocketAddress) request.getRemoteAddress();
            
            HttpHost targetHost = new HttpHost(address.getHostName(), address.getPort()); 
            
            AsyncHttpClientConnection conn = new AsyncHttpClientConnection(session, this.params); 
            session.setAttribute(CONNECTION, conn);
            session.setAttribute(TARGET, targetHost);
            session.setAttribute(CONSUMER, conn.getIOConsumer());
            session.setAttribute(PRODUCER, conn.getIOProducer());
            
        }

        public void failed(final SessionRequest request) {
            IOException ex = request.getException();
            System.err.println(request.getRemoteAddress() + ": Connection failed");
            ex.printStackTrace();
        }

        public void timeout(final SessionRequest request) {
            request.cancel();
            System.err.println(request.getRemoteAddress() + ": Connection timeout");
        }

    }    
    
    static class DefaultIoEventDispatch implements IOEventDispatch {

        public DefaultIoEventDispatch(final HttpParams params) {
            super();
        }
        
        public void connected(final IOSession session) {
            
            HttpClientConnection conn = (HttpClientConnection) session.getAttribute(CONNECTION);
            HttpHost targetHost = (HttpHost) session.getAttribute(TARGET);
            HttpContext localcontext = new HttpExecutionContext(null);
            localcontext.setAttribute(HttpExecutionContext.HTTP_TARGET_HOST, targetHost);
            
            // Set up HTTP executor
            BasicHttpProcessor httpproc = new BasicHttpProcessor();
            httpproc.addInterceptor(new RequestContent());
            httpproc.addInterceptor(new RequestTargetHost());
            httpproc.addInterceptor(new RequestConnControl());
            httpproc.addInterceptor(new RequestUserAgent());
            httpproc.addInterceptor(new RequestExpectContinue());
            HttpRequestExecutor httpexecutor = new HttpRequestExecutor(httpproc); 
            
            Thread worker = new WorkerThread(httpexecutor, conn, localcontext);
            session.setAttribute(WORKER, worker);

            worker.setDaemon(false);
            worker.start();
            session.setSocketTimeout(20000);
        }

        public void inputReady(final IOSession session) {
            IOConsumer consumer = (IOConsumer) session.getAttribute(CONSUMER);
            try {
                consumer.consumeInput();
            } catch (IOException ex) {
                consumer.shutdown(ex);
            }
        }

        public void outputReady(final IOSession session) {
            IOProducer producer = (IOProducer) session.getAttribute(PRODUCER);
            try {
                producer.produceOutput();
            } catch (IOException ex) {
                producer.shutdown(ex);
            }
        }

        public void timeout(final IOSession session) {
            IOConsumer consumer = (IOConsumer) session.getAttribute(CONSUMER);
            consumer.shutdown(new SocketTimeoutException("Socket read timeout"));
        }
        
        public void disconnected(final IOSession session) {
            HttpConnection conn = (HttpConnection) session.getAttribute(CONNECTION);
            try {
                conn.shutdown();
            } catch (IOException ex) {
                System.err.println("I/O error while shutting down connection");
            }
            
            Thread worker = (Thread) session.getAttribute(WORKER);
            worker.interrupt();
        }
        
    }
    
    static class WorkerThread extends Thread {

        private final HttpRequestExecutor httpexecutor;
        private final HttpClientConnection conn;
        private final HttpContext context;
        
        public WorkerThread(
                final HttpRequestExecutor httpexecutor, 
                final HttpClientConnection conn, 
                final HttpContext context) {
            super();
            this.httpexecutor = httpexecutor;
            this.conn = conn;
            this.context = context;
        }
        
        public void run() {
            System.out.println("New connection thread");
            try {
                String[] targets = {
                        "/",
                        "/servlets-examples/servlet/RequestInfoExample", 
                        "/somewhere%20in%20pampa"};
                
                ConnectionReuseStrategy connStrategy = new DefaultConnectionReuseStrategy();
                
                for (int i = 0; i < targets.length; i++) {
                    HttpGet request = new HttpGet(targets[i]);
                    System.out.println(">> Request URI: " + request.getRequestLine().getUri());
                    HttpResponse response = httpexecutor.execute(request, this.conn, this.context);
                    System.out.println("<< Response: " + response.getStatusLine());
                    System.out.println(EntityUtils.toString(response.getEntity()));
                    System.out.println("==============");
                    if (!connStrategy.keepAlive(response, this.context)) {
                        conn.close();
                        System.out.println("Connection closed...");
                        break;
                    } else {
                        System.out.println("Connection kept alive...");
                    }
                }
            } catch (IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            } catch (HttpException ex) {
                System.err.println("Unrecoverable HTTP protocol violation: " + ex.getMessage());
            } finally {
                try {
                    this.conn.shutdown();
                } catch (IOException ignore) {}
            }
        }

    }
    
}
