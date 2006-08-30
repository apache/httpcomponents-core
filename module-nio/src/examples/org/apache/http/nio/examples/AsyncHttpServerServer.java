package org.apache.http.nio.examples;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;

import org.apache.http.HttpConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.nio.IOConsumer;
import org.apache.http.nio.IOEventDispatch;
import org.apache.http.nio.IOProducer;
import org.apache.http.nio.IOSession;
import org.apache.http.nio.IOReactor;
import org.apache.http.nio.impl.AsyncHttpServerConnection;
import org.apache.http.nio.impl.DefaultListeningIOReactor;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.protocol.SyncHttpExecutionContext;

public class AsyncHttpServerServer {

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

        HttpContext globalContext = new SyncHttpExecutionContext(null);
        globalContext.setAttribute("server.docroot", args[0]);
        
        IOEventDispatch ioEventDispatch = new DefaultIoEventDispatch(params, globalContext);
        IOReactor ioReactor = new DefaultListeningIOReactor(new InetSocketAddress(8080), params);
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

        private static final String CONSUMER = "CONSUMER";
        private static final String PRODUCER = "PRODUCER";
        private static final String CONNECTION = "CONNECTION";
        private static final String WORKER = "WORKER";
        
        private final HttpParams params;
        private final HttpContext context;
        
        public DefaultIoEventDispatch(final HttpParams params, final HttpContext context) {
            super();
            if (params == null) {
                throw new IllegalArgumentException("HTTP parameters may nor be null");
            }
            this.params = params;
            this.context = context;
        }
        
        public void connected(IOSession session) {
            AsyncHttpServerConnection conn = new AsyncHttpServerConnection(session, this.params); 
            session.setAttribute(CONNECTION, conn);
            session.setAttribute(CONSUMER, conn.getIOConsumer());
            session.setAttribute(PRODUCER, conn.getIOProducer());
            
            HttpFileService service = new HttpFileService(conn, this.context);
            service.setParams(this.params);
            
            Thread worker = new WorkerThread(service);
            session.setAttribute(WORKER, worker);

            worker.setDaemon(true);
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
    
    static class HttpFileService extends HttpService {
        
        public HttpFileService(final HttpServerConnection conn, final HttpContext context) {
            super(conn, context);
            addInterceptor(new ResponseDate());
            addInterceptor(new ResponseServer());                    
            addInterceptor(new ResponseContent());
            addInterceptor(new ResponseConnControl());
        }

        protected void doService(final HttpRequest request, final HttpResponse response) 
                throws HttpException, IOException {
            String method = request.getRequestLine().getMethod();
            if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
                throw new MethodNotSupportedException(method + " method not supported"); 
            }
            String docroot = (String) getContext().getAttribute("server.docroot");
            
            String target = request.getRequestLine().getUri();
            
            final File file = new File(docroot, URLDecoder.decode(target, "UTF-8"));
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
        
        protected void logMessage(final String s) {
            System.out.println(s);
        }
        
        protected void logIOException(final IOException ex) {
            System.err.println("IO error: " + ex.getMessage());
        }
        
        protected void logProtocolException(final HttpException ex) {
            System.err.println("HTTP protocol error: " + ex.getMessage());
        }
        
    }
    
    static class WorkerThread extends Thread {

        private final HttpService httpservice;
        
        public WorkerThread(final HttpService httpservice) {
            super();
            this.httpservice = httpservice;
        }
        
        public void run() {
            System.out.println("New connection thread");
            try {
                while (!this.httpservice.isDestroyed() && this.httpservice.isActive()) {
                    this.httpservice.handleRequest();
                }
            } finally {
                this.httpservice.destroy();
            }
        }

    }
    
}
