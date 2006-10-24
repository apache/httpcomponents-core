package org.apache.http.nio.examples;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.IOEventDispatch;
import org.apache.http.nio.IOReactor;
import org.apache.http.nio.handler.ContentDecoder;
import org.apache.http.nio.handler.ContentEncoder;
import org.apache.http.nio.handler.NHttpServerConnection;
import org.apache.http.nio.handler.NHttpServiceHandler;
import org.apache.http.nio.impl.DefaultIOReactor;
import org.apache.http.nio.impl.handler.DefaultServerIOEventDispatch;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.EncodingUtils;

public class NHttpServer {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Please specify document root directory");
            System.exit(1);
        }
        HttpParams params = new DefaultHttpParams(null);
        params
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 5000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
            .setParameter(HttpProtocolParams.ORIGIN_SERVER, "Jakarta-HttpComponents-NIO/1.1");

        IOEventDispatch ioEventDispatch = new DefaultServerIOEventDispatch(
                new MyNHttpServiceHandler(args[0]), params);
        IOReactor ioReactor = new DefaultIOReactor(params);
        try {
            ioReactor.listen(new InetSocketAddress(8080));
            ioReactor.execute(ioEventDispatch);
        } catch (InterruptedIOException ex) {
            System.err.println("Interrupted");
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
        System.out.println("Shutdown");
    }

    public static class MyNHttpServiceHandler implements NHttpServiceHandler {

        private final String docRoot;
        private final HttpResponseFactory responseFactory;
        private final ByteBuffer inbuf;
        private final ByteBuffer outbuf;
        private final HttpProcessor httpProcessor;
        private final ConnectionReuseStrategy connStrategy;
        
        public MyNHttpServiceHandler(final String docRoot) {
            super();
            this.docRoot = docRoot;
            this.responseFactory = new DefaultHttpResponseFactory();
            this.inbuf = ByteBuffer.allocateDirect(2048);
            this.outbuf = ByteBuffer.allocateDirect(2048);
            BasicHttpProcessor httpProcessor = new BasicHttpProcessor();
            httpProcessor.addInterceptor(new ResponseDate());
            httpProcessor.addInterceptor(new ResponseServer());                    
            httpProcessor.addInterceptor(new ResponseContent());
            httpProcessor.addInterceptor(new ResponseConnControl());
            this.httpProcessor = httpProcessor;
            this.connStrategy = new DefaultConnectionReuseStrategy();
        }
        
        private void shutdownConnection(final HttpConnection conn) {
            try {
                conn.shutdown();
            } catch (IOException ignore) {
            }
        }
        
        private void commitResponse(
                final NHttpServerConnection conn,
                final HttpResponse response) {
            try {

                ReadableByteChannel channel = null;
                if (response.getEntity() != null) {
                    InputStream instream = response.getEntity().getContent(); 
                    if (instream instanceof FileInputStream) {
                        channel = ((FileInputStream)instream).getChannel();
                    } else {
                        channel = Channels.newChannel(instream);
                    }
                    
                }
                
                conn.getContext().setAttribute("out-channel", channel);
                
                this.httpProcessor.process(response, conn.getContext());
                conn.submitResponse(response);
            } catch (HttpException ex) {
                ex.printStackTrace();
                shutdownConnection(conn);
            } catch (IOException ex) {
                ex.printStackTrace();
                shutdownConnection(conn);
            }
        }

        private void service(final NHttpServerConnection conn) {
            HttpRequest request = conn.getHttpRequest();
            HttpVersion ver = request.getRequestLine().getHttpVersion();
            HttpResponse response =  this.responseFactory.newHttpResponse(ver, 200);

            String target = request.getRequestLine().getUri();
            try {

                File file = new File(this.docRoot, URLDecoder.decode(target, "UTF-8"));
                if (!file.exists()) {
                    
                    response.setStatusCode(HttpStatus.SC_NOT_FOUND);
                    byte[] msg = EncodingUtils.getAsciiBytes(
                            file.getName() + ": not found");
                    ByteArrayEntity entity = new ByteArrayEntity(msg);
                    entity.setContentType("text/plain; charset=US-ASCII");
                    response.setEntity(entity);
                    
                } else if (!file.canRead() || file.isDirectory()) {

                    response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                    byte[] msg = EncodingUtils.getAsciiBytes(
                            file.getName() + ": access denied");
                    ByteArrayEntity entity = new ByteArrayEntity(msg);
                    entity.setContentType("text/plain; charset=US-ASCII");
                    response.setEntity(entity);

                } else {

                    FileEntity entity = new FileEntity(file, "text/html");
                    response.setEntity(entity);
                    
                }
                
            } catch (UnsupportedEncodingException ex) {
                throw new Error("UTF-8 not supported");
            }
            
            HttpContext context = conn.getContext();
            context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
            context.setAttribute(HttpExecutionContext.HTTP_RESPONSE, response);
            context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
            
            commitResponse(conn, response);
            
        }
        
        public void requestReceived(final NHttpServerConnection conn) {
            HttpRequest request = conn.getHttpRequest();
            HttpVersion ver = request.getRequestLine().getHttpVersion();
            if (request instanceof HttpEntityEnclosingRequest) {
                
                if (((HttpEntityEnclosingRequest) request).expectContinue()) {
                    HttpResponse ack = this.responseFactory.newHttpResponse(ver, 100);
                    try {
                        conn.submitResponse(ack);
                    } catch (HttpException ex) {
                        ex.printStackTrace();
                        shutdownConnection(conn);
                        return;
                    }
                }
                
                // Request content is expected. 
                ByteArrayOutputStream bytestream = new ByteArrayOutputStream();
                WritableByteChannel channel = Channels.newChannel(bytestream);
                
                HttpContext context = conn.getContext();
                context.setAttribute("in-buffer", bytestream);
                context.setAttribute("in-channel", channel);
                // Wait until the request content is fully received
            } else {
                // No request content is expected. 
                //Proceed with service right away
                service(conn);
            }
        }

        public void closed(final NHttpServerConnection conn) {
            System.out.println("Connection closed");
        }

        public void exception(final NHttpServerConnection conn, final HttpException ex) {
            HttpRequest request = conn.getHttpRequest();
            HttpVersion ver = request.getRequestLine().getHttpVersion();
            HttpResponse response =  this.responseFactory.newHttpResponse(
                    ver, HttpStatus.SC_BAD_REQUEST);
            byte[] msg = EncodingUtils.getAsciiBytes(
                    "Malformed HTTP request: " + ex.getMessage());
            ByteArrayEntity entity = new ByteArrayEntity(msg);
            entity.setContentType("text/plain; charset=US-ASCII");
            response.setEntity(entity);
            commitResponse(conn, response);
        }

        public void exception(NHttpServerConnection conn, IOException ex) {
            System.err.println("I/O error: " + ex.getMessage());
            shutdownConnection(conn);
        }

        public void inputReady(final NHttpServerConnection conn, final ContentDecoder decoder) {

            HttpRequest request = conn.getHttpRequest();
            HttpContext context = conn.getContext();
            WritableByteChannel channel = (WritableByteChannel) context
                .getAttribute("in-channel");

            try {
                while (decoder.read(this.inbuf) > 0) {
                    this.inbuf.flip();
                    channel.write(this.inbuf);
                    this.inbuf.compact();
                }
                if (decoder.isCompleted()) {
                    // Request entity has been fully received
                    
                    ByteArrayOutputStream bytestream = (ByteArrayOutputStream) context
                        .getAttribute("in-buffer");
                    byte[] content = bytestream.toByteArray();
                    
                    ByteArrayEntity entity = new ByteArrayEntity(content);
                    entity.setContentType(request.getFirstHeader(HTTP.CONTENT_TYPE));
                    
                    ((HttpEntityEnclosingRequest) request).setEntity(entity);
                    
                    service(conn);
                }
                
            } catch (IOException ex) {
                shutdownConnection(conn);
                ex.printStackTrace();
            }
        }

        public void outputReady(final NHttpServerConnection conn, final ContentEncoder encoder) {

            HttpContext context = conn.getContext();
            HttpResponse response = conn.getHttpResponse();
            ReadableByteChannel channel = (ReadableByteChannel) context
                .getAttribute("out-channel");

            try {
                int bytesRead = channel.read(this.outbuf);
                if (bytesRead == -1) {
                    encoder.complete();
                } else {
                    this.outbuf.flip();
                    encoder.write(this.outbuf);
                    this.outbuf.compact();
                }

                if (encoder.isCompleted()) {
                    if (!this.connStrategy.keepAlive(response, context)) {
                        conn.close();
                    }
                }
            
            } catch (IOException ex) {
                shutdownConnection(conn);
                ex.printStackTrace();
            }

        }

        public void timeout(final NHttpServerConnection conn) {
            System.err.println("Timeout");
            shutdownConnection(conn);
        }
        
    }
    
}
