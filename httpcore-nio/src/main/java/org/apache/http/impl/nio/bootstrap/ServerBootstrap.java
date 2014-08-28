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
package org.apache.http.impl.nio.bootstrap;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.ExceptionLogger;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnectionFactory;
import org.apache.http.impl.nio.SSLNHttpServerConnectionFactory;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.protocol.HttpAsyncExpectationVerifier;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.nio.protocol.HttpAsyncRequestHandlerMapper;
import org.apache.http.nio.protocol.HttpAsyncService;
import org.apache.http.nio.protocol.UriHttpAsyncRequestHandlerMapper;
import org.apache.http.nio.reactor.ssl.SSLSetupHandler;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

/**
 * @since 4.4
 */
public class ServerBootstrap {

    private int listenerPort;
    private InetAddress localAddress;
    private IOReactorConfig ioReactorConfig;
    private ConnectionConfig connectionConfig;
    private LinkedList<HttpRequestInterceptor> requestFirst;
    private LinkedList<HttpRequestInterceptor> requestLast;
    private LinkedList<HttpResponseInterceptor> responseFirst;
    private LinkedList<HttpResponseInterceptor> responseLast;
    private String serverInfo;
    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy connStrategy;
    private HttpResponseFactory responseFactory;
    private HttpAsyncRequestHandlerMapper handlerMapper;
    private Map<String, HttpAsyncRequestHandler<?>> handlerMap;
    private HttpAsyncExpectationVerifier expectationVerifier;
    private SSLContext sslContext;
    private SSLSetupHandler sslSetupHandler;
    private NHttpConnectionFactory<? extends DefaultNHttpServerConnection> connectionFactory;
    private ExceptionLogger exceptionLogger;

    private ServerBootstrap() {
    }

    public static ServerBootstrap bootstrap() {
        return new ServerBootstrap();
    }

    /**
     * Sets listener port number.
     */
    public final ServerBootstrap setListenerPort(final int listenerPort) {
        this.listenerPort = listenerPort;
        return this;
    }

    /**
     * Assigns local interface for the listener.
     */
    public final ServerBootstrap setLocalAddress(final InetAddress localAddress) {
        this.localAddress = localAddress;
        return this;
    }

    /**
     * Sets I/O reactor configuration.
     */
    public final ServerBootstrap setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    /**
     * Sets connection configuration.
     * <p>
     * Please note this value can be overridden by the {@link #setConnectionFactory(
     *   org.apache.http.nio.NHttpConnectionFactory)} method.
     */
    public final ServerBootstrap setConnectionConfig(final ConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
        return this;
    }

    /**
     * Assigns {@link org.apache.http.protocol.HttpProcessor} instance.
     */
    public final ServerBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     * <p>
     * Please note this value can be overridden by the {@link #setHttpProcessor(
     * org.apache.http.protocol.HttpProcessor)} method.
     */
    public final ServerBootstrap addInterceptorFirst(final HttpResponseInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (responseFirst == null) {
            responseFirst = new LinkedList<HttpResponseInterceptor>();
        }
        responseFirst.addFirst(itcp);
        return this;
    }

    /**
     * Adds this protocol interceptor to the tail of the protocol processing list.
     * <p>
     * Please note this value can be overridden by the {@link #setHttpProcessor(
     * org.apache.http.protocol.HttpProcessor)} method.
     */
    public final ServerBootstrap addInterceptorLast(final HttpResponseInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (responseLast == null) {
            responseLast = new LinkedList<HttpResponseInterceptor>();
        }
        responseLast.addLast(itcp);
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     * <p>
     * Please note this value can be overridden by the {@link #setHttpProcessor(
     * org.apache.http.protocol.HttpProcessor)} method.
     */
    public final ServerBootstrap addInterceptorFirst(final HttpRequestInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (requestFirst == null) {
            requestFirst = new LinkedList<HttpRequestInterceptor>();
        }
        requestFirst.addFirst(itcp);
        return this;
    }

    /**
     * Adds this protocol interceptor to the tail of the protocol processing list.
     * <p>
     * Please note this value can be overridden by the {@link #setHttpProcessor(
     * org.apache.http.protocol.HttpProcessor)} method.
     */
    public final ServerBootstrap addInterceptorLast(final HttpRequestInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (requestLast == null) {
            requestLast = new LinkedList<HttpRequestInterceptor>();
        }
        requestLast.addLast(itcp);
        return this;
    }

    /**
     * Assigns {@code Server} response header value.
     * <p>
     * Please note this value can be overridden by the {@link #setHttpProcessor(
     * org.apache.http.protocol.HttpProcessor)} method.
     */
    public final ServerBootstrap setServerInfo(final String serverInfo) {
        this.serverInfo = serverInfo;
        return this;
    }

    /**
     * Assigns {@link org.apache.http.ConnectionReuseStrategy} instance.
     */
    public final ServerBootstrap setConnectionReuseStrategy(final ConnectionReuseStrategy connStrategy) {
        this.connStrategy = connStrategy;
        return this;
    }

    /**
     * Assigns {@link org.apache.http.HttpResponseFactory} instance.
     */
    public final ServerBootstrap setResponseFactory(final HttpResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
        return this;
    }

    /**
     * Assigns {@link org.apache.http.nio.protocol.HttpAsyncRequestHandlerMapper} instance.
     */
    public final ServerBootstrap setHandlerMapper(final HttpAsyncRequestHandlerMapper handlerMapper) {
        this.handlerMapper = handlerMapper;
        return this;
    }

    /**
     * Registers the given {@link org.apache.http.nio.protocol.HttpAsyncRequestHandler}
     * as a handler for URIs matching the given pattern.
     * <p>
     * Please note this value can be overridden by the {@link #setHandlerMapper(
     *   org.apache.http.nio.protocol.HttpAsyncRequestHandlerMapper)} )} method.
     *
     * @param pattern the pattern to register the handler for.
     * @param handler the handler.
     */
    public final ServerBootstrap registerHandler(final String pattern, final HttpAsyncRequestHandler<?> handler) {
        if (pattern == null || handler == null) {
            return this;
        }
        if (handlerMap == null) {
            handlerMap = new HashMap<String, HttpAsyncRequestHandler<?>>();
        }
        handlerMap.put(pattern, handler);
        return this;
    }

    /**
     * Assigns {@link org.apache.http.nio.protocol.HttpAsyncExpectationVerifier} instance.
     */
    public final ServerBootstrap setExpectationVerifier(final HttpAsyncExpectationVerifier expectationVerifier) {
        this.expectationVerifier = expectationVerifier;
        return this;
    }

    /**
     * Assigns {@link org.apache.http.nio.NHttpConnectionFactory} instance.
     */
    public final ServerBootstrap setConnectionFactory(
            final NHttpConnectionFactory<? extends DefaultNHttpServerConnection> connectionFactory) {
        this.connectionFactory = connectionFactory;
        return this;
    }

    /**
     * Assigns {@link javax.net.ssl.SSLContext} instance.
     * <p>
     * Please note this value can be overridden by the {@link #setConnectionFactory(
     *   org.apache.http.nio.NHttpConnectionFactory)} method.
     */
    public final ServerBootstrap setSslContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * Assigns {@link org.apache.http.nio.reactor.ssl.SSLSetupHandler} instance.
     * <p>
     * Please note this value can be overridden by the {@link #setConnectionFactory(
     *   org.apache.http.nio.NHttpConnectionFactory)} method.
     */
    public ServerBootstrap setSslSetupHandler(final SSLSetupHandler sslSetupHandler) {
        this.sslSetupHandler = sslSetupHandler;
        return this;
    }

    /**
     * Assigns {@link org.apache.http.ExceptionLogger} instance.
     */
    public final ServerBootstrap setExceptionLogger(final ExceptionLogger exceptionLogger) {
        this.exceptionLogger = exceptionLogger;
        return this;
    }

    public HttpServer create() {

        HttpProcessor httpProcessorCopy = this.httpProcessor;
        if (httpProcessorCopy == null) {

            final HttpProcessorBuilder b = HttpProcessorBuilder.create();
            if (requestFirst != null) {
                for (final HttpRequestInterceptor i: requestFirst) {
                    b.addFirst(i);
                }
            }
            if (responseFirst != null) {
                for (final HttpResponseInterceptor i: responseFirst) {
                    b.addFirst(i);
                }
            }

            String serverInfoCopy = this.serverInfo;
            if (serverInfoCopy == null) {
                serverInfoCopy = "Apache-HttpCore-NIO/1.1";
            }

            b.addAll(
                    new ResponseDate(),
                    new ResponseServer(serverInfoCopy),
                    new ResponseContent(),
                    new ResponseConnControl());
            if (requestLast != null) {
                for (final HttpRequestInterceptor i: requestLast) {
                    b.addLast(i);
                }
            }
            if (responseLast != null) {
                for (final HttpResponseInterceptor i: responseLast) {
                    b.addLast(i);
                }
            }
            httpProcessorCopy = b.build();
        }

        HttpAsyncRequestHandlerMapper handlerMapperCopy = this.handlerMapper;
        if (handlerMapperCopy == null) {
            final UriHttpAsyncRequestHandlerMapper reqistry = new UriHttpAsyncRequestHandlerMapper();
            if (handlerMap != null) {
                for (Map.Entry<String, HttpAsyncRequestHandler<?>> entry: handlerMap.entrySet()) {
                    reqistry.register(entry.getKey(), entry.getValue());
                }
            }
            handlerMapperCopy = reqistry;
        }

        ConnectionReuseStrategy connStrategyCopy = this.connStrategy;
        if (connStrategyCopy == null) {
            connStrategyCopy = DefaultConnectionReuseStrategy.INSTANCE;
        }

        HttpResponseFactory responseFactoryCopy = this.responseFactory;
        if (responseFactoryCopy == null) {
            responseFactoryCopy = DefaultHttpResponseFactory.INSTANCE;
        }

        NHttpConnectionFactory<? extends DefaultNHttpServerConnection> connectionFactoryCopy = this.connectionFactory;
        if (connectionFactoryCopy == null) {
            if (this.sslContext != null) {
                connectionFactoryCopy = new SSLNHttpServerConnectionFactory(
                        this.sslContext, this.sslSetupHandler, this.connectionConfig);
            } else {
                connectionFactoryCopy = new DefaultNHttpServerConnectionFactory(this.connectionConfig);
            }
        }

        ExceptionLogger exceptionLoggerCopy = this.exceptionLogger;
        if (exceptionLoggerCopy == null) {
            exceptionLoggerCopy = ExceptionLogger.NO_OP;
        }

        final HttpAsyncService httpService = new HttpAsyncService(
                httpProcessorCopy, connStrategyCopy, responseFactoryCopy, handlerMapperCopy,
                this.expectationVerifier, exceptionLoggerCopy);

        return new HttpServer(this.listenerPort, this.localAddress, this.ioReactorConfig,
                httpService, connectionFactoryCopy, exceptionLoggerCopy);

    }

}
