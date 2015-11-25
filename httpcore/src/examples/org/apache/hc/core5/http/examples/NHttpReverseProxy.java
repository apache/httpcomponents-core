/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.core5.http.examples;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.config.ConnectionConfig;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.EnglishReasonPhraseCatalog;
import org.apache.hc.core5.http.impl.nio.BasicAsyncResponseProducer;
import org.apache.hc.core5.http.impl.nio.DefaultHttpClientIODispatch;
import org.apache.hc.core5.http.impl.nio.DefaultHttpServerIODispatch;
import org.apache.hc.core5.http.impl.nio.HttpAsyncRequestExecutor;
import org.apache.hc.core5.http.impl.nio.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.nio.HttpAsyncService;
import org.apache.hc.core5.http.impl.nio.UriHttpAsyncRequestHandlerMapper;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.HttpAsyncExchange;
import org.apache.hc.core5.http.nio.HttpAsyncRequestConsumer;
import org.apache.hc.core5.http.nio.HttpAsyncRequestHandler;
import org.apache.hc.core5.http.nio.HttpAsyncRequestHandlerMapper;
import org.apache.hc.core5.http.nio.HttpAsyncRequestProducer;
import org.apache.hc.core5.http.nio.HttpAsyncResponseConsumer;
import org.apache.hc.core5.http.nio.HttpAsyncResponseProducer;
import org.apache.hc.core5.http.nio.IOControl;
import org.apache.hc.core5.http.nio.NHttpClientConnection;
import org.apache.hc.core5.http.nio.NHttpConnection;
import org.apache.hc.core5.http.nio.NHttpServerConnection;
import org.apache.hc.core5.http.nio.entity.NStringEntity;
import org.apache.hc.core5.http.pool.nio.BasicNIOConnPool;
import org.apache.hc.core5.http.pool.nio.BasicNIOPoolEntry;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.ImmutableHttpProcessor;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http.protocol.ResponseConnControl;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.apache.hc.core5.http.protocol.ResponseDate;
import org.apache.hc.core5.http.protocol.ResponseServer;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.pool.nio.NIOConnFactory;
import org.apache.hc.core5.reactor.ConnectingIOReactor;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.DefaultListeningIOReactor;
import org.apache.hc.core5.reactor.IOEventDispatch;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListeningIOReactor;

/**
 * Asynchronous, fully streaming HTTP/1.1 reverse proxy.
 */
public class NHttpReverseProxy {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: NHttpReverseProxy <hostname> [port]");
            System.exit(1);
        }
        URI uri = new URI(args[0]);
        int port = 8080;
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }

        // Target host
        HttpHost targetHost = new HttpHost(
                uri.getHost(),
                uri.getPort() > 0 ? uri.getPort() : 80,
                uri.getScheme() != null ? uri.getScheme() : "http");

        System.out.println("Reverse proxy to " + targetHost);

        IOReactorConfig config = IOReactorConfig.custom()
            .setIoThreadCount(1)
            .setSoTimeout(3000)
            .setConnectTimeout(3000)
            .build();
        final ConnectingIOReactor connectingIOReactor = new DefaultConnectingIOReactor(config);
        final ListeningIOReactor listeningIOReactor = new DefaultListeningIOReactor(config);

        // Set up HTTP protocol processor for incoming connections
        HttpProcessor inhttpproc = new ImmutableHttpProcessor(
                new ResponseDate(),
                new ResponseServer("Test/1.1"),
                new ResponseContent(),
                new ResponseConnControl());

        // Set up HTTP protocol processor for outgoing connections
        HttpProcessor outhttpproc;
        outhttpproc = new ImmutableHttpProcessor(
                new HttpRequestInterceptor[] {
                        new RequestContent(),
                        new RequestTargetHost(),
                        new RequestConnControl(),
                        new RequestUserAgent("Test/1.1"),
                        new RequestExpectContinue()
        });

        ProxyClientProtocolHandler clientHandler = new ProxyClientProtocolHandler();
        HttpAsyncRequester executor = new HttpAsyncRequester(outhttpproc);

        ProxyConnPool connPool = new ProxyConnPool(connectingIOReactor, ConnectionConfig.DEFAULT);
        connPool.setMaxTotal(100);
        connPool.setDefaultMaxPerRoute(20);

        UriHttpAsyncRequestHandlerMapper handlerRegistry = new UriHttpAsyncRequestHandlerMapper();
        handlerRegistry.register("*", new ProxyRequestHandler(targetHost, executor, connPool));

        ProxyServiceHandler serviceHandler = new ProxyServiceHandler(
                inhttpproc,
                new ProxyIncomingConnectionReuseStrategy(),
                handlerRegistry);

        final IOEventDispatch connectingEventDispatch = new DefaultHttpClientIODispatch(
                clientHandler, ConnectionConfig.DEFAULT);

        final IOEventDispatch listeningEventDispatch = new DefaultHttpServerIODispatch(
                serviceHandler, ConnectionConfig.DEFAULT);

        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    connectingIOReactor.execute(connectingEventDispatch);
                } catch (InterruptedIOException ex) {
                    System.err.println("Interrupted");
                } catch (IOException ex) {
                    ex.printStackTrace();
                } finally {
                    try {
                        listeningIOReactor.shutdown();
                    } catch (IOException ex2) {
                        ex2.printStackTrace();
                    }
                }
            }

        });
        t.start();
        try {
            listeningIOReactor.listen(new InetSocketAddress(port));
            listeningIOReactor.execute(listeningEventDispatch);
        } catch (InterruptedIOException ex) {
            System.err.println("Interrupted");
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                connectingIOReactor.shutdown();
            } catch (IOException ex2) {
                ex2.printStackTrace();
            }
        }
    }

    static class ProxyHttpExchange {

        private final ByteBuffer inBuffer;
        private final ByteBuffer outBuffer;

        private volatile String id;
        private volatile HttpHost target;
        private volatile HttpAsyncExchange responseTrigger;
        private volatile IOControl originIOControl;
        private volatile IOControl clientIOControl;
        private volatile HttpRequest request;
        private volatile boolean requestReceived;
        private volatile HttpResponse response;
        private volatile boolean responseReceived;
        private volatile Exception ex;

        public ProxyHttpExchange() {
            super();
            this.inBuffer = ByteBuffer.allocateDirect(10240);
            this.outBuffer = ByteBuffer.allocateDirect(10240);
        }

        public ByteBuffer getInBuffer() {
            return this.inBuffer;
        }

        public ByteBuffer getOutBuffer() {
            return this.outBuffer;
        }

        public String getId() {
            return this.id;
        }

        public void setId(final String id) {
            this.id = id;
        }

        public HttpHost getTarget() {
            return this.target;
        }

        public void setTarget(final HttpHost target) {
            this.target = target;
        }

        public HttpRequest getRequest() {
            return this.request;
        }

        public void setRequest(final HttpRequest request) {
            this.request = request;
        }

        public HttpResponse getResponse() {
            return this.response;
        }

        public void setResponse(final HttpResponse response) {
            this.response = response;
        }

        public HttpAsyncExchange getResponseTrigger() {
            return this.responseTrigger;
        }

        public void setResponseTrigger(final HttpAsyncExchange responseTrigger) {
            this.responseTrigger = responseTrigger;
        }

        public IOControl getClientIOControl() {
            return this.clientIOControl;
        }

        public void setClientIOControl(final IOControl clientIOControl) {
            this.clientIOControl = clientIOControl;
        }

        public IOControl getOriginIOControl() {
            return this.originIOControl;
        }

        public void setOriginIOControl(final IOControl originIOControl) {
            this.originIOControl = originIOControl;
        }

        public boolean isRequestReceived() {
            return this.requestReceived;
        }

        public void setRequestReceived() {
            this.requestReceived = true;
        }

        public boolean isResponseReceived() {
            return this.responseReceived;
        }

        public void setResponseReceived() {
            this.responseReceived = true;
        }

        public Exception getException() {
            return this.ex;
        }

        public void setException(final Exception ex) {
            this.ex = ex;
        }

        public void reset() {
            this.inBuffer.clear();
            this.outBuffer.clear();
            this.target = null;
            this.id = null;
            this.responseTrigger = null;
            this.clientIOControl = null;
            this.originIOControl = null;
            this.request = null;
            this.requestReceived = false;
            this.response = null;
            this.responseReceived = false;
            this.ex = null;
        }

    }

    static class ProxyRequestHandler implements HttpAsyncRequestHandler<ProxyHttpExchange> {

        private final HttpHost target;
        private final HttpAsyncRequester executor;
        private final BasicNIOConnPool connPool;
        private final AtomicLong counter;

        public ProxyRequestHandler(
                final HttpHost target,
                final HttpAsyncRequester executor,
                final BasicNIOConnPool connPool) {
            super();
            this.target = target;
            this.executor = executor;
            this.connPool = connPool;
            this.counter = new AtomicLong(1);
        }

        @Override
        public HttpAsyncRequestConsumer<ProxyHttpExchange> processRequest(
                final HttpRequest request,
                final HttpContext context) {
            ProxyHttpExchange httpExchange = (ProxyHttpExchange) context.getAttribute("http-exchange");
            if (httpExchange == null) {
                httpExchange = new ProxyHttpExchange();
                context.setAttribute("http-exchange", httpExchange);
            }
            synchronized (httpExchange) {
                httpExchange.reset();
                String id = String.format("%08X", this.counter.getAndIncrement());
                httpExchange.setId(id);
                httpExchange.setTarget(this.target);
                return new ProxyRequestConsumer(httpExchange, this.executor, this.connPool);
            }
        }

        @Override
        public void handle(
                final ProxyHttpExchange httpExchange,
                final HttpAsyncExchange responseTrigger,
                final HttpContext context) throws HttpException, IOException {
            synchronized (httpExchange) {
                Exception ex = httpExchange.getException();
                if (ex != null) {
                    System.out.println("[client<-proxy] " + httpExchange.getId() + " " + ex);
                    int status = HttpStatus.SC_INTERNAL_SERVER_ERROR;
                    HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, status,
                            EnglishReasonPhraseCatalog.INSTANCE.getReason(status, Locale.US));
                    String message = ex.getMessage();
                    if (message == null) {
                        message = "Unexpected error";
                    }
                    response.setEntity(new NStringEntity(message, ContentType.DEFAULT_TEXT));
                    responseTrigger.submitResponse(new BasicAsyncResponseProducer(response));
                    System.out.println("[client<-proxy] " + httpExchange.getId() + " error response triggered");
                }
                HttpResponse response = httpExchange.getResponse();
                if (response != null) {
                    responseTrigger.submitResponse(new ProxyResponseProducer(httpExchange));
                    System.out.println("[client<-proxy] " + httpExchange.getId() + " response triggered");
                }
                // No response yet.
                httpExchange.setResponseTrigger(responseTrigger);
            }
        }

    }

    static class ProxyRequestConsumer implements HttpAsyncRequestConsumer<ProxyHttpExchange> {

        private final ProxyHttpExchange httpExchange;
        private final HttpAsyncRequester executor;
        private final BasicNIOConnPool connPool;

        private volatile boolean completed;

        public ProxyRequestConsumer(
                final ProxyHttpExchange httpExchange,
                final HttpAsyncRequester executor,
                final BasicNIOConnPool connPool) {
            super();
            this.httpExchange = httpExchange;
            this.executor = executor;
            this.connPool = connPool;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public void requestReceived(final HttpRequest request) {
            synchronized (this.httpExchange) {
                System.out.println("[client->proxy] " + this.httpExchange.getId() + " " + request.getRequestLine());
                this.httpExchange.setRequest(request);
                this.executor.execute(
                        new ProxyRequestProducer(this.httpExchange),
                        new ProxyResponseConsumer(this.httpExchange),
                        this.connPool);
            }
        }

        @Override
        public void consumeContent(
                final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
            synchronized (this.httpExchange) {
                this.httpExchange.setClientIOControl(ioctrl);
                // Receive data from the client
                ByteBuffer buf = this.httpExchange.getInBuffer();
                int n = decoder.read(buf);
                System.out.println("[client->proxy] " + this.httpExchange.getId() + " " + n + " bytes read");
                if (decoder.isCompleted()) {
                    System.out.println("[client->proxy] " + this.httpExchange.getId() + " content fully read");
                }
                // If the buffer is full, suspend client input until there is free
                // space in the buffer
                if (!buf.hasRemaining()) {
                    ioctrl.suspendInput();
                    System.out.println("[client->proxy] " + this.httpExchange.getId() + " suspend client input");
                }
                // If there is some content in the input buffer make sure origin
                // output is active
                if (buf.position() > 0) {
                    if (this.httpExchange.getOriginIOControl() != null) {
                        this.httpExchange.getOriginIOControl().requestOutput();
                        System.out.println("[client->proxy] " + this.httpExchange.getId() + " request origin output");
                    }
                }
            }
        }

        @Override
        public void requestCompleted(final HttpContext context) {
            synchronized (this.httpExchange) {
                this.completed = true;;
                System.out.println("[client->proxy] " + this.httpExchange.getId() + " request completed");
                this.httpExchange.setRequestReceived();
                if (this.httpExchange.getOriginIOControl() != null) {
                    this.httpExchange.getOriginIOControl().requestOutput();
                    System.out.println("[client->proxy] " + this.httpExchange.getId() + " request origin output");
                }
            }
        }

        @Override
        public Exception getException() {
            return null;
        }

        @Override
        public ProxyHttpExchange getResult() {
            return this.httpExchange;
        }

        @Override
        public boolean isDone() {
            return this.completed;
        }

        @Override
        public void failed(final Exception ex) {
            System.out.println("[client->proxy] " + ex.toString());
        }

    }

    static class ProxyRequestProducer implements HttpAsyncRequestProducer {

        private final ProxyHttpExchange httpExchange;

        public ProxyRequestProducer(final ProxyHttpExchange httpExchange) {
            super();
            this.httpExchange = httpExchange;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public HttpHost getTarget() {
            synchronized (this.httpExchange) {
                return this.httpExchange.getTarget();
            }
        }

        @Override
        public HttpRequest generateRequest() {
            synchronized (this.httpExchange) {
                HttpRequest request = this.httpExchange.getRequest();
                System.out.println("[proxy->origin] " + this.httpExchange.getId() + " " + request.getRequestLine());
                // Rewrite request!!!!
                BasicHttpRequest newREquest = new BasicHttpRequest(request.getRequestLine());
                newREquest.setEntity(request.getEntity());
                return newREquest;
            }
        }

        @Override
        public void produceContent(
                final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
            synchronized (this.httpExchange) {
                this.httpExchange.setOriginIOControl(ioctrl);
                // Send data to the origin server
                ByteBuffer buf = this.httpExchange.getInBuffer();
                buf.flip();
                int n = encoder.write(buf);
                buf.compact();
                System.out.println("[proxy->origin] " + this.httpExchange.getId() + " " + n + " bytes written");
                // If there is space in the buffer and the message has not been
                // transferred, make sure the client is sending more data
                if (buf.hasRemaining() && !this.httpExchange.isRequestReceived()) {
                    if (this.httpExchange.getClientIOControl() != null) {
                        this.httpExchange.getClientIOControl().requestInput();
                        System.out.println("[proxy->origin] " + this.httpExchange.getId() + " request client input");
                    }
                }
                if (buf.position() == 0) {
                    if (this.httpExchange.isRequestReceived()) {
                        encoder.complete();
                        System.out.println("[proxy->origin] " + this.httpExchange.getId() + " content fully written");
                    } else {
                        // Input buffer is empty. Wait until the client fills up
                        // the buffer
                        ioctrl.suspendOutput();
                        System.out.println("[proxy->origin] " + this.httpExchange.getId() + " suspend origin output");
                    }
                }
            }
        }

        @Override
        public void requestCompleted(final HttpContext context) {
            synchronized (this.httpExchange) {
                System.out.println("[proxy->origin] " + this.httpExchange.getId() + " request completed");
            }
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public void resetRequest() {
        }

        @Override
        public void failed(final Exception ex) {
            System.out.println("[proxy->origin] " + ex.toString());
        }

    }

    static class ProxyResponseConsumer implements HttpAsyncResponseConsumer<ProxyHttpExchange> {

        private final ProxyHttpExchange httpExchange;

        private volatile boolean completed;

        public ProxyResponseConsumer(final ProxyHttpExchange httpExchange) {
            super();
            this.httpExchange = httpExchange;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public void responseReceived(final HttpResponse response) {
            synchronized (this.httpExchange) {
                System.out.println("[proxy<-origin] " + this.httpExchange.getId() + " " + response.getStatusLine());
                this.httpExchange.setResponse(response);
                HttpAsyncExchange responseTrigger = this.httpExchange.getResponseTrigger();
                if (responseTrigger != null && !responseTrigger.isCompleted()) {
                    System.out.println("[client<-proxy] " + this.httpExchange.getId() + " response triggered");
                    responseTrigger.submitResponse(new ProxyResponseProducer(this.httpExchange));
                }
            }
        }

        @Override
        public void consumeContent(
                final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
            synchronized (this.httpExchange) {
                this.httpExchange.setOriginIOControl(ioctrl);
                // Receive data from the origin
                ByteBuffer buf = this.httpExchange.getOutBuffer();
                int n = decoder.read(buf);
                System.out.println("[proxy<-origin] " + this.httpExchange.getId() + " " + n + " bytes read");
                if (decoder.isCompleted()) {
                    System.out.println("[proxy<-origin] " + this.httpExchange.getId() + " content fully read");
                }
                // If the buffer is full, suspend origin input until there is free
                // space in the buffer
                if (!buf.hasRemaining()) {
                    ioctrl.suspendInput();
                    System.out.println("[proxy<-origin] " + this.httpExchange.getId() + " suspend origin input");
                }
                // If there is some content in the input buffer make sure client
                // output is active
                if (buf.position() > 0) {
                    if (this.httpExchange.getClientIOControl() != null) {
                        this.httpExchange.getClientIOControl().requestOutput();
                        System.out.println("[proxy<-origin] " + this.httpExchange.getId() + " request client output");
                    }
                }
            }
        }

        @Override
        public void responseCompleted(final HttpContext context) {
            synchronized (this.httpExchange) {
                if (this.completed) {
                    return;
                }
                this.completed = true;
                System.out.println("[proxy<-origin] " + this.httpExchange.getId() + " response completed");
                this.httpExchange.setResponseReceived();
                if (this.httpExchange.getClientIOControl() != null) {
                    this.httpExchange.getClientIOControl().requestOutput();
                    System.out.println("[proxy<-origin] " + this.httpExchange.getId() + " request client output");
                }
            }
        }

        @Override
        public void failed(final Exception ex) {
            synchronized (this.httpExchange) {
                if (this.completed) {
                    return;
                }
                this.completed = true;
                this.httpExchange.setException(ex);
                HttpAsyncExchange responseTrigger = this.httpExchange.getResponseTrigger();
                if (responseTrigger != null && !responseTrigger.isCompleted()) {
                    System.out.println("[client<-proxy] " + this.httpExchange.getId() + " " + ex);
                    int status = HttpStatus.SC_INTERNAL_SERVER_ERROR;
                    HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, status,
                            EnglishReasonPhraseCatalog.INSTANCE.getReason(status, Locale.US));
                    String message = ex.getMessage();
                    if (message == null) {
                        message = "Unexpected error";
                    }
                    response.setEntity(new NStringEntity(message, ContentType.DEFAULT_TEXT));
                    responseTrigger.submitResponse(new BasicAsyncResponseProducer(response));
                }
            }
        }

        @Override
        public boolean cancel() {
            synchronized (this.httpExchange) {
                if (this.completed) {
                    return false;
                }
                failed(new InterruptedIOException("Cancelled"));
                return true;
            }
        }

        @Override
        public ProxyHttpExchange getResult() {
            return this.httpExchange;
        }

        @Override
        public Exception getException() {
            return null;
        }

        @Override
        public boolean isDone() {
            return this.completed;
        }

    }

    static class ProxyResponseProducer implements HttpAsyncResponseProducer {

        private final ProxyHttpExchange httpExchange;

        public ProxyResponseProducer(final ProxyHttpExchange httpExchange) {
            super();
            this.httpExchange = httpExchange;
        }

        @Override
        public void close() throws IOException {
            this.httpExchange.reset();
        }

        @Override
        public HttpResponse generateResponse() {
            synchronized (this.httpExchange) {
                HttpResponse response = this.httpExchange.getResponse();
                System.out.println("[client<-proxy] " + this.httpExchange.getId() + " " + response.getStatusLine());
                // Rewrite response!!!!
                BasicHttpResponse r = new BasicHttpResponse(response.getStatusLine());
                r.setEntity(response.getEntity());
                return r;
            }
        }

        @Override
        public void produceContent(
                final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
            synchronized (this.httpExchange) {
                this.httpExchange.setClientIOControl(ioctrl);
                // Send data to the client
                ByteBuffer buf = this.httpExchange.getOutBuffer();
                buf.flip();
                int n = encoder.write(buf);
                buf.compact();
                System.out.println("[client<-proxy] " + this.httpExchange.getId() + " " + n + " bytes written");
                // If there is space in the buffer and the message has not been
                // transferred, make sure the origin is sending more data
                if (buf.hasRemaining() && !this.httpExchange.isResponseReceived()) {
                    if (this.httpExchange.getOriginIOControl() != null) {
                        this.httpExchange.getOriginIOControl().requestInput();
                        System.out.println("[client<-proxy] " + this.httpExchange.getId() + " request origin input");
                    }
                }
                if (buf.position() == 0) {
                    if (this.httpExchange.isResponseReceived()) {
                        encoder.complete();
                        System.out.println("[client<-proxy] " + this.httpExchange.getId() + " content fully written");
                    } else {
                        // Input buffer is empty. Wait until the origin fills up
                        // the buffer
                        ioctrl.suspendOutput();
                        System.out.println("[client<-proxy] " + this.httpExchange.getId() + " suspend client output");
                    }
                }
            }
        }

        @Override
        public void responseCompleted(final HttpContext context) {
            synchronized (this.httpExchange) {
                System.out.println("[client<-proxy] " + this.httpExchange.getId() + " response completed");
            }
        }

        @Override
        public void failed(final Exception ex) {
            System.out.println("[client<-proxy] " + ex.toString());
        }

    }

    static class ProxyIncomingConnectionReuseStrategy extends DefaultConnectionReuseStrategy {

        @Override
        public boolean keepAlive(final HttpRequest request, final HttpResponse response, final HttpContext context) {
            NHttpConnection conn = (NHttpConnection) context.getAttribute(
                    HttpCoreContext.HTTP_CONNECTION);
            boolean keepAlive = super.keepAlive(request, response, context);
            if (keepAlive) {
                System.out.println("[client->proxy] connection kept alive " + conn);
            }
            return keepAlive;
        }

    };

    static class ProxyOutgoingConnectionReuseStrategy extends DefaultConnectionReuseStrategy {

        @Override
        public boolean keepAlive(final HttpRequest request, final HttpResponse response, final HttpContext context) {
            NHttpConnection conn = (NHttpConnection) context.getAttribute(
                    HttpCoreContext.HTTP_CONNECTION);
            boolean keepAlive = super.keepAlive(request, response, context);
            if (keepAlive) {
                System.out.println("[proxy->origin] connection kept alive " + conn);
            }
            return keepAlive;
        }

    };

    static class ProxyServiceHandler extends HttpAsyncService {

        public ProxyServiceHandler(
                final HttpProcessor httpProcessor,
                final ConnectionReuseStrategy reuseStrategy,
                final HttpAsyncRequestHandlerMapper handlerResolver) {
            super(httpProcessor, reuseStrategy, null, handlerResolver, null);
        }

        @Override
        protected void log(final Exception ex) {
            ex.printStackTrace();
        }

        @Override
        public void connected(final NHttpServerConnection conn) {
            System.out.println("[client->proxy] connection open " + conn);
            super.connected(conn);
        }

        @Override
        public void closed(final NHttpServerConnection conn) {
            System.out.println("[client->proxy] connection closed " + conn);
            super.closed(conn);
        }

    }

    static class ProxyClientProtocolHandler extends HttpAsyncRequestExecutor {

        public ProxyClientProtocolHandler() {
            super(HttpAsyncRequestExecutor.DEFAULT_WAIT_FOR_CONTINUE, new ProxyOutgoingConnectionReuseStrategy(), null);
        }

        @Override
        protected void log(final Exception ex) {
            ex.printStackTrace();
        }

        @Override
        public void connected(final NHttpClientConnection conn,
                final Object attachment) throws IOException, HttpException {
            System.out.println("[proxy->origin] connection open " + conn);
            super.connected(conn, attachment);
        }

        @Override
        public void closed(final NHttpClientConnection conn) {
            System.out.println("[proxy->origin] connection closed " + conn);
            super.closed(conn);
        }

    }

    static class ProxyConnPool extends BasicNIOConnPool {

        public ProxyConnPool(
                final ConnectingIOReactor ioreactor,
                final ConnectionConfig config) {
            super(ioreactor, config);
        }

        public ProxyConnPool(
                final ConnectingIOReactor ioreactor,
                final NIOConnFactory<HttpHost, NHttpClientConnection> connFactory,
                final int connectTimeout) {
            super(ioreactor, connFactory, connectTimeout);
        }

        @Override
        public void release(final BasicNIOPoolEntry entry, boolean reusable) {
            System.out.println("[proxy->origin] connection released " + entry.getConnection());
            super.release(entry, reusable);
            StringBuilder buf = new StringBuilder();
            PoolStats totals = getTotalStats();
            buf.append("[total kept alive: ").append(totals.getAvailable()).append("; ");
            buf.append("total allocated: ").append(totals.getLeased() + totals.getAvailable());
            buf.append(" of ").append(totals.getMax()).append("]");
            System.out.println("[proxy->origin] " + buf.toString());
        }

    }

}
