package org.apache.http.nio.examples;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.params.DefaultHttpParams;
import org.apache.http.nio.concurrent.Executor;
import org.apache.http.nio.impl.DefaultServerIOEventDispatch;
import org.apache.http.nio.impl.reactor.DefaultListeningIOReactor;
import org.apache.http.nio.params.HttpNIOParams;
import org.apache.http.nio.protocol.EventListener;
import org.apache.http.nio.protocol.ThrottlingHttpServiceHandler;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.EntityUtils;

public class AsyncHttpServer {

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
            .setIntParameter(HttpNIOParams.CONTENT_BUFFER_SIZE, 10240)
            .setParameter(HttpProtocolParams.ORIGIN_SERVER, "Jakarta-HttpComponents-NIO/1.1");

        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new ResponseDate());
        httpproc.addInterceptor(new ResponseServer());
        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseConnControl());
        
        ThrottlingHttpServiceHandler handler = new ThrottlingHttpServiceHandler(
                httpproc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                new SimpleExecutor(),
                params);
        
        // Set up request handlers
        HttpRequestHandlerRegistry reqistry = new HttpRequestHandlerRegistry();
        reqistry.register("*", new HttpFileHandler(args[0]));
        
        handler.setHandlerResolver(reqistry);
        
        // Provide an event logger
        handler.setEventListener(new EventLogger());
        
        IOEventDispatch ioEventDispatch = new DefaultServerIOEventDispatch(handler, params);
        ListeningIOReactor ioReactor = new DefaultListeningIOReactor(2, params);
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

    static class HttpFileHandler implements HttpRequestHandler  {
        
        private final String docRoot;
        
        public HttpFileHandler(final String docRoot) {
            super();
            this.docRoot = docRoot;
        }
        
        public void handle(
                final HttpRequest request, 
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {

            String method = request.getRequestLine().getMethod().toUpperCase();
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
                throw new MethodNotSupportedException(method + " method not supported"); 
            }

            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                byte[] entityContent = EntityUtils.toByteArray(entity);
                System.out.println("Incoming entity content (bytes): " + entityContent.length);
            }
            
            String target = request.getRequestLine().getUri();
            final File file = new File(this.docRoot, URLDecoder.decode(target, "UTF-8"));
            if (!file.exists()) {

                response.setStatusCode(HttpStatus.SC_NOT_FOUND);
                EntityTemplate body = new EntityTemplate(new ContentProducer() {
                    
                    public void writeTo(final OutputStream outstream) throws IOException {
                        OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8"); 
                        writer.write("<html><body><h1>");
                        writer.write("File ");
                        writer.write(file.getPath());
                        writer.write(" not found");
                        writer.write("</h1></body></html>");
                        writer.flush();
                    }
                    
                });
                body.setContentType("text/html; charset=UTF-8");
                response.setEntity(body);
                System.out.println("File " + file.getPath() + " not found");
                
            } else if (!file.canRead() || file.isDirectory()) {
                
                response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                EntityTemplate body = new EntityTemplate(new ContentProducer() {
                    
                    public void writeTo(final OutputStream outstream) throws IOException {
                        OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8"); 
                        writer.write("<html><body><h1>");
                        writer.write("Access denied");
                        writer.write("</h1></body></html>");
                        writer.flush();
                    }
                    
                });
                body.setContentType("text/html; charset=UTF-8");
                response.setEntity(body);
                System.out.println("Cannot read file " + file.getPath());
                
            } else {
                
                response.setStatusCode(HttpStatus.SC_OK);
                FileEntity body = new FileEntity(file, "text/html");
                response.setEntity(body);
                System.out.println("Serving file " + file.getPath());
                
            }
        }
        
    }
    
    static class EventLogger implements EventListener {

        public void connectionOpen(final InetAddress address) {
            System.out.println("Connection open: " + address);
        }

        public void connectionTimeout(final InetAddress address) {
            System.out.println("Connection timed out: " + address);
        }

        public void connectionClosed(InetAddress address) {
            System.out.println("Connection closed: " + address);
        }

        public void fatalIOException(IOException ex) {
            System.err.println("I/O error: " + ex.getMessage());
        }

        public void fatalProtocolException(HttpException ex) {
            System.err.println("HTTP error: " + ex.getMessage());
        }
        
    }
    
    static class SimpleExecutor implements Executor {

        public void execute(final Runnable command) {
            Thread t = new Thread(command);
            t.setDaemon(true);
            t.start();
        }
        
    }
    
}
