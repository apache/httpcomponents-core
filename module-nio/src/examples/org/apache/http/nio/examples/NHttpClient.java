package org.apache.http.nio.examples;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.message.HttpGet;
import org.apache.http.nio.impl.DefaultClientIOEventDispatch;
import org.apache.http.nio.impl.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.protocol.BufferingHttpClientHandler;
import org.apache.http.nio.protocol.EventListener;
import org.apache.http.nio.protocol.HttpRequestExecutionHandler;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;

public class NHttpClient {

    public static void main(String[] args) throws Exception {
        HttpParams params = new DefaultHttpParams(null);
        params
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 5000)
            .setIntParameter(HttpConnectionParams.CONNECTION_TIMEOUT, 10000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
            .setParameter(HttpProtocolParams.USER_AGENT, "Jakarta-HttpComponents-NIO/1.1");

        final ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(2, params);

        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new RequestContent());
        httpproc.addInterceptor(new RequestTargetHost());
        httpproc.addInterceptor(new RequestConnControl());
        httpproc.addInterceptor(new RequestUserAgent());
        httpproc.addInterceptor(new RequestExpectContinue());
        
        BufferingHttpClientHandler handler = new BufferingHttpClientHandler(
                httpproc,
                new MyHttpRequestExecutionHandler(),
                new DefaultConnectionReuseStrategy(),
                params);

        handler.setEventListener(new EventLogger());
        
        final IOEventDispatch ioEventDispatch = new DefaultClientIOEventDispatch(handler, params);
        
        Thread t = new Thread(new Runnable() {
         
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
        t.start();

        SessionRequest[] reqs = new SessionRequest[3];
        reqs[0] = ioReactor.connect(
                new InetSocketAddress("www.yahoo.com", 80), 
                null, 
                new HttpHost("www.yahoo.com"));
        reqs[1] = ioReactor.connect(
                new InetSocketAddress("www.google.com", 80), 
                null,
                new HttpHost("www.google.ch"));
        reqs[2] = ioReactor.connect(
                new InetSocketAddress("www.apache.org", 80), 
                null,
                new HttpHost("www.apache.org"));
        
    }
    
    static class MyHttpRequestExecutionHandler implements HttpRequestExecutionHandler {

        public void initalizeContext(final HttpContext context, final Object attachment) {
            HttpHost targetHost = (HttpHost) attachment;
            context.setAttribute(HttpExecutionContext.HTTP_TARGET_HOST, targetHost);
        }

        public HttpRequest submitRequest(final HttpContext context) {
            HttpHost targetHost = (HttpHost) context.getAttribute(
                    HttpExecutionContext.HTTP_TARGET_HOST);
            Integer countObj = (Integer) context.getAttribute(
                    "request-count");
            int counter = 0; 
            if (countObj != null) {
                counter = countObj.intValue(); 
            }
            counter++;
            context.setAttribute("request-count", new Integer(counter));
            if (counter < 3) {
                System.out.println("--------------");
                System.out.println("Sending request to " + targetHost);
                System.out.println("--------------");
                return new HttpGet("/");
            } else {
                // Return null to terminate the connection
                return null;
            }
        }
        
        public void handleResponse(final HttpResponse response, final HttpContext context) {
            HttpEntity entity = response.getEntity();
            try {
                String content = EntityUtils.toString(entity);
                
                System.out.println("--------------");
                System.out.println(response.getStatusLine());
                System.out.println("--------------");
                System.out.println("Document length: " + content.length());
                System.out.println("--------------");
            } catch (IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            }
        }
        
    }
    
    static class EventLogger implements EventListener {

        private int openNo = 0;
        private int closedNo = 0;
        
        public void connectionOpen(final InetAddress address) {
            this.openNo++;
            System.out.println("Connection open: " + address);
        }

        public void connectionTimeout(final InetAddress address) {
            System.out.println("Connection timed out: " + address);
        }

        public void connectionClosed(InetAddress address) {
            System.out.println("Connection closed: " + address);
            this.closedNo++;
            if (this.openNo == this.closedNo) {
                System.exit(0);
            }
        }

        public void fatalIOException(IOException ex) {
            System.err.println("I/O error: " + ex.getMessage());
        }

        public void fatalProtocolException(HttpException ex) {
            System.err.println("HTTP error: " + ex.getMessage());
        }
        
    }
        
}
