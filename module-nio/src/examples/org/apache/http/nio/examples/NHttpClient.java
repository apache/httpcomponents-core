package org.apache.http.nio.examples;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.nio.IOEventDispatch;
import org.apache.http.nio.IOReactor;
import org.apache.http.nio.SessionRequest;
import org.apache.http.nio.handler.ContentDecoder;
import org.apache.http.nio.handler.ContentEncoder;
import org.apache.http.nio.handler.NHttpClientConnection;
import org.apache.http.nio.handler.NHttpClientHandler;
import org.apache.http.nio.impl.DefaultIOReactor;
import org.apache.http.nio.impl.handler.DefaultClientIOEventDispatch;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpProcessor;
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

        IOReactor ioReactor = new DefaultIOReactor(params);

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

        NHttpClientHandler handler = new MyNHttpClientHandler(reqs, params);
        IOEventDispatch ioEventDispatch = new DefaultClientIOEventDispatch(handler, params);
        
        try {
            ioReactor.execute(ioEventDispatch);
        } catch (InterruptedIOException ex) {
            System.err.println("Interrupted");
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
        System.out.println("Shutdown");
    }
    
    static class MyNHttpClientHandler implements NHttpClientHandler {

        private final SessionRequest[] reqs;
        private final HttpParams params;
        private final HttpRequestFactory requestFactory; 
        private final HttpProcessor httpProcessor;
        private final ByteBuffer inbuf;
        private final ConnectionReuseStrategy connStrategy;
        
        private int connCount = 0;
        
        public MyNHttpClientHandler(final SessionRequest[] reqs, final HttpParams params) {
            super();
            this.reqs = reqs;
            this.params = params;
            this.requestFactory = new DefaultHttpRequestFactory();
            BasicHttpProcessor httpproc = new BasicHttpProcessor();
            httpproc.addInterceptor(new RequestContent());
            httpproc.addInterceptor(new RequestTargetHost());
            httpproc.addInterceptor(new RequestConnControl());
            httpproc.addInterceptor(new RequestUserAgent());
            httpproc.addInterceptor(new RequestExpectContinue());
            this.httpProcessor = httpproc;
            this.inbuf = ByteBuffer.allocateDirect(2048);
            this.connStrategy = new DefaultConnectionReuseStrategy();
        }
        
        private void shutdownConnection(final HttpConnection conn) {
            try {
                conn.shutdown();
            } catch (IOException ignore) {
            }
        }
        
        public void connected(final NHttpClientConnection conn, final Object attachment) {
            try {
                HttpContext context = conn.getContext();
                
                HttpHost targetHost = (HttpHost) attachment;
                
                context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
                context.setAttribute(HttpExecutionContext.HTTP_TARGET_HOST, targetHost);
                
                HttpRequest request = this.requestFactory.newHttpRequest("GET", "/");
                request.getParams().setDefaults(this.params);
                
                this.httpProcessor.process(request, context);
                
                conn.submitRequest(request);

                context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
            
            } catch (IOException ex) {
                shutdownConnection(conn);
                System.err.println("I/O error: " + ex.getMessage());
            } catch (HttpException ex) {
                shutdownConnection(conn);
                System.err.println("Unexpected HTTP protocol error: " + ex.getMessage());
            }
        }

        public void closed(final NHttpClientConnection conn) {
            System.out.println("Connection closed");
            this.connCount++;
            if (this.connCount >= this.reqs.length) {
                System.exit(0);
            }
        }

        public void exception(final NHttpClientConnection conn, final HttpException ex) {
            System.err.println("HTTP protocol error: " + ex.getMessage());
            shutdownConnection(conn);
        }

        public void exception(final NHttpClientConnection conn, final IOException ex) {
            System.err.println("I/O error: " + ex.getMessage());
            shutdownConnection(conn);
        }

        public void inputReady(final NHttpClientConnection conn, final ContentDecoder decoder) {
            HttpResponse response = conn.getHttpResponse();
            HttpContext context = conn.getContext();
            HttpHost targetHost = (HttpHost) context
                .getAttribute(HttpExecutionContext.HTTP_TARGET_HOST);
            WritableByteChannel channel = (WritableByteChannel) context
                .getAttribute("in-channel");

            try {
                while (decoder.read(this.inbuf) > 0) {
                    this.inbuf.flip();
                    channel.write(this.inbuf);
                    this.inbuf.compact();
                }
                if (decoder.isCompleted()) {
                    HttpEntity entity = response.getEntity();
                    
                    ByteArrayOutputStream bytestream = (ByteArrayOutputStream) context
                        .getAttribute("in-buffer");
                    byte[] content = bytestream.toByteArray();
                    
                    String charset = EntityUtils.getContentCharSet(entity);
                    if (charset == null) {
                        charset = HTTP.DEFAULT_CONTENT_CHARSET;
                    }
                    
                    System.out.println("--------------");
                    System.out.println("Target: " + targetHost);
                    System.out.println("--------------");
                    System.out.println(response.getStatusLine());
                    System.out.println("--------------");
                    System.out.println(new String(content, charset));
                    System.out.println("--------------");

                    if (!this.connStrategy.keepAlive(response, context)) {
                        conn.close();
                    }
                }
                
            } catch (IOException ex) {
                shutdownConnection(conn);
                System.err.println("I/O error: " + ex.getMessage());
            }
        }

        public void outputReady(final NHttpClientConnection conn, final ContentEncoder encoder) {
        }

        public void responseReceived(final NHttpClientConnection conn) {
            HttpResponse response = conn.getHttpResponse();
            
            if (response.getStatusLine().getStatusCode() >= 200) {
                ByteArrayOutputStream bytestream = new ByteArrayOutputStream();
                WritableByteChannel channel = Channels.newChannel(bytestream);
                
                HttpContext context = conn.getContext();
                context.setAttribute("in-buffer", bytestream);
                context.setAttribute("in-channel", channel);
            }
        }

        public void timeout(final NHttpClientConnection conn) {
            System.err.println("Timeout");
            shutdownConnection(conn);
        }
        
    } 
    
}
