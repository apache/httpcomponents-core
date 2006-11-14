package org.apache.http.nio.examples;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;

import org.apache.http.ConnectionClosedException;
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
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.impl.DefaultServerIOEventDispatch;
import org.apache.http.nio.impl.reactor.DefaultIOReactor;
import org.apache.http.nio.protocol.AsyncHttpService;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactor;
import org.apache.http.nio.util.ContentInputBuffer;
import org.apache.http.nio.util.ContentOutputBuffer;
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
            .setParameter(HttpProtocolParams.ORIGIN_SERVER, "Jakarta-HttpComponents-NIO/1.1");

        IOReactor ioReactor = new DefaultIOReactor(params);

        // Set up request handlers
        HttpRequestHandlerRegistry reqistry = new HttpRequestHandlerRegistry();
        reqistry.register("*", new HttpFileHandler(args[0]));
        
        MyNHttpServiceHandler handler = new MyNHttpServiceHandler(reqistry, params);
        IOEventDispatch ioEventDispatch = new DefaultServerIOEventDispatch(handler, params);
        
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
    
    public static class MyNHttpServiceHandler implements NHttpServiceHandler {
        
        private static final String HTTP_ASYNC_SERVICE = "http.async-service";
        private static final String HTTP_WORKER_THREAD = "http.worker-thread";
        
        final private HttpRequestHandlerRegistry reqistry;
        final private HttpParams params;
        
        public MyNHttpServiceHandler(
                final HttpRequestHandlerRegistry reqistry, 
                final HttpParams params) {
            super();
            this.reqistry = reqistry;
            this.params = params;
        }
        
        private void shutdownConnection(final NHttpServerConnection conn) {
            try {
                conn.shutdown();
            } catch (IOException ignore) {
            }
        }

        public void connected(final NHttpServerConnection conn) {
            // Set up the HTTP protocol processor
            BasicHttpProcessor httpproc = new BasicHttpProcessor();
            httpproc.addInterceptor(new ResponseDate());
            httpproc.addInterceptor(new ResponseServer());
            httpproc.addInterceptor(new ResponseContent());
            httpproc.addInterceptor(new ResponseConnControl());

            // Allocate large content input / output buffers
            ContentInputBuffer inbuffer = new ContentInputBuffer(20480, conn); 
            ContentOutputBuffer outbuffer = new ContentOutputBuffer(20480, conn); 
            
            // Set up the HTTP service
            AsyncHttpService httpService = new AsyncHttpService(
                    inbuffer,
                    outbuffer,
                    httpproc,
                    new DefaultConnectionReuseStrategy(), 
                    new DefaultHttpResponseFactory());
            httpService.setParams(this.params);
            httpService.setHandlerResolver(this.reqistry);
            
            conn.getContext().setAttribute(HTTP_ASYNC_SERVICE, httpService);
        }

        public void closed(final NHttpServerConnection conn) {
            AsyncHttpService httpService = (AsyncHttpService) conn.getContext()
                .getAttribute(HTTP_ASYNC_SERVICE);
            httpService.shutdown();
        }

        public void exception(final NHttpServerConnection conn, final HttpException httpex) {
            AsyncHttpService httpService = (AsyncHttpService) conn.getContext()
                .getAttribute(HTTP_ASYNC_SERVICE);
            try {
                httpService.handleException(conn, httpex);
            } catch (IOException ex) {
                httpService.shutdown(ex);
                shutdownConnection(conn);
            } catch (HttpException ex) {
                System.err.println("Unexpected HTTP protocol error: " + ex.getMessage());
                httpService.shutdown();
                shutdownConnection(conn);
            }
        }

        public void exception(final NHttpServerConnection conn, final IOException ioex) {
            AsyncHttpService httpService = (AsyncHttpService) conn.getContext()
                .getAttribute(HTTP_ASYNC_SERVICE);
            httpService.shutdown(ioex);
            shutdownConnection(conn);
        }

        public void timeout(final NHttpServerConnection conn) {
            exception(conn, new SocketTimeoutException("Socket timeout"));
        }

        public void requestReceived(final NHttpServerConnection conn) {
            HttpRequest request = conn.getHttpRequest();
            AsyncHttpService httpService = (AsyncHttpService) conn.getContext()
                .getAttribute(HTTP_ASYNC_SERVICE);
            WorkerThread worker = (WorkerThread) conn.getContext()
                .getAttribute(HTTP_WORKER_THREAD);
            
            // BIG FAT UGLY WARNING!
            // =====================
            // (1) This sample application employs an over-simplistic 
            //     thread synchronization. In the life applications
            //     consider implementing a proper connection locking
            //     mechanism to ensure that only one worker thread
            //     can have access to an HTTP connection at a time
            //
            // (2) Do NOT start a new thread per request in real life 
            //     applications! Do make sure to use a thread pool.
            //
            
            if (worker != null) {
                try {
                    worker.join();
                } catch (InterruptedException ex) {
                    return;
                }
            }
            
            worker = new WorkerThread(httpService, request, conn); 
            conn.getContext().setAttribute(HTTP_WORKER_THREAD, worker);
            
            worker.setDaemon(true);
            worker.start();
        }

        public void inputReady(final NHttpServerConnection conn, final ContentDecoder decoder) {
            AsyncHttpService httpService = (AsyncHttpService) conn.getContext()
                .getAttribute(HTTP_ASYNC_SERVICE);
            try {
                httpService.consumeContent(decoder);
            } catch (IOException ex) {
                httpService.shutdown(ex);
                shutdownConnection(conn);
            }
        }

        public void outputReady(NHttpServerConnection conn, ContentEncoder encoder) {
            AsyncHttpService httpService = (AsyncHttpService) conn.getContext()
                .getAttribute(HTTP_ASYNC_SERVICE);
            try {
                httpService.produceContent(encoder);
            } catch (IOException ex) {
                httpService.shutdown(ex);
                shutdownConnection(conn);
            }
        }
    }

    static class WorkerThread extends Thread {

        private final AsyncHttpService httpService;
        private final HttpRequest request;
        private final NHttpServerConnection conn;
        
        public WorkerThread(
                final AsyncHttpService httpService,
                final HttpRequest request,
                final NHttpServerConnection conn) {
            super();
            this.httpService = httpService;
            this.request = request;
            this.conn = conn;
        }
        
        public void run() {
            System.out.println("New request thread");
            try {
                this.httpService.handleRequest(this.request, this.conn);
            } catch (ConnectionClosedException ex) {
                System.err.println("Client closed connection");
            } catch (IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            } catch (HttpException ex) {
                System.err.println("Unrecoverable HTTP protocol violation: " + ex.getMessage());
            }
            System.out.println("Request thread terminated");
        }

    }
    
}
