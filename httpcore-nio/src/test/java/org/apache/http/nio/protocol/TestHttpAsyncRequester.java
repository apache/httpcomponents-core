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

package org.apache.http.nio.protocol;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import junit.framework.Assert;

import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpHost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.pool.BasicNIOPoolEntry;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.protocol.HttpAsyncRequester.ConnRequestCallback;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.pool.ConnPool;
import org.apache.http.pool.PoolEntry;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class TestHttpAsyncRequester {

    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy reuseStrategy;
    private HttpParams params;
    private HttpAsyncRequester requester;
    private HttpContext exchangeContext;
    private HttpContext connContext;
    private HttpAsyncRequestProducer requestProducer;
    private HttpAsyncResponseConsumer<Object> responseConsumer;
    private NHttpClientConnection conn;
    private FutureCallback<Object> callback;
    private ConnPool<HttpHost, PoolEntry<HttpHost, NHttpClientConnection>> connPool;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        this.httpProcessor = Mockito.mock(HttpProcessor.class);
        this.reuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        this.params = new BasicHttpParams();
        this.requester = new HttpAsyncRequester(
                this.httpProcessor, this.reuseStrategy, this.params);
        this.exchangeContext = new BasicHttpContext();
        this.requestProducer = Mockito.mock(HttpAsyncRequestProducer.class);
        this.responseConsumer = Mockito.mock(HttpAsyncResponseConsumer.class);
        this.conn = Mockito.mock(NHttpClientConnection.class);
        this.callback = Mockito.mock(FutureCallback.class);
        this.connContext = new BasicHttpContext();
        this.connPool = Mockito.mock(ConnPool.class);

        Mockito.when(this.conn.getContext()).thenReturn(this.connContext);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testInvalidExecution() throws Exception {
        try {
            this.requester.execute(
                    null,
                    this.responseConsumer,
                    this.conn);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            this.requester.execute(
                    this.requestProducer,
                    null,
                    this.conn);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            this.requester.execute(
                    this.requestProducer,
                    this.responseConsumer,
                    (NHttpClientConnection) null);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            this.requester.execute(
                    this.requestProducer,
                    this.responseConsumer,
                    this.conn,
                    null);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }

        try {
            this.requester.execute(
                    null,
                    this.responseConsumer,
                    this.connPool);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            this.requester.execute(
                    this.requestProducer,
                    null,
                    this.connPool);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            this.requester.execute(
                    this.requestProducer,
                    this.responseConsumer,
                    (ConnPool<HttpHost, PoolEntry<HttpHost, NHttpClientConnection>>) null);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            this.requester.execute(
                    this.requestProducer,
                    this.responseConsumer,
                    this.connPool,
                    null);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testSimpleExecute() throws Exception {
        Mockito.when(this.conn.isOpen()).thenReturn(Boolean.TRUE);
        Future<Object> future = this.requester.execute(
                this.requestProducer,
                this.responseConsumer,
                this.conn, this.exchangeContext, null);
        Assert.assertNotNull(future);
        Assert.assertNotNull(this.connContext.getAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER));
        Mockito.verify(this.conn).requestOutput();
    }

    @Test
    public void testExecuteConnectionClosedUnexpectedly() throws Exception {
        Mockito.when(this.conn.isOpen()).thenReturn(false);
        Future<Object> future = this.requester.execute(
                this.requestProducer,
                this.responseConsumer,
                this.conn, this.exchangeContext, null);
        Assert.assertNotNull(future);
        Mockito.verify(this.requestProducer).failed(Mockito.any(ConnectionClosedException.class));
        Mockito.verify(this.responseConsumer).failed(Mockito.any(ConnectionClosedException.class));
        Mockito.verify(this.requestProducer, Mockito.atLeastOnce()).close();
        Mockito.verify(this.responseConsumer, Mockito.atLeastOnce()).close();
        Assert.assertTrue(future.isDone());
        Assert.assertNotNull(future.isDone());
        try {
            future.get();
        } catch (ExecutionException ex) {
            Throwable cause =  ex.getCause();
            Assert.assertNotNull(cause);
            Assert.assertTrue(cause instanceof ConnectionClosedException);
        }

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testPooledConnectionRequestFailed() throws Exception {
        HttpHost host = new HttpHost("somehost");
        Mockito.when(this.requestProducer.getTarget()).thenReturn(host);

        Future<Object> future = this.requester.execute(
                this.requestProducer,
                this.responseConsumer,
                this.connPool, this.exchangeContext, this.callback);
        Assert.assertNotNull(future);
        ArgumentCaptor<FutureCallback> argCaptor = ArgumentCaptor.forClass(FutureCallback.class);
        Mockito.verify(this.connPool).lease(
                Mockito.eq(host), Mockito.isNull(), argCaptor.capture());
        ConnRequestCallback connRequestCallback = (ConnRequestCallback) argCaptor.getValue();

        Exception oppsie = new Exception();
        connRequestCallback.failed(oppsie);
        Mockito.verify(this.responseConsumer).failed(oppsie);
        Mockito.verify(this.callback).failed(oppsie);
        Mockito.verify(this.responseConsumer).close();
        Mockito.verify(this.requestProducer).close();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testPooledConnectionRequestCancelled() throws Exception {
        HttpHost host = new HttpHost("somehost");
        Mockito.when(this.requestProducer.getTarget()).thenReturn(host);

        Future<Object> future = this.requester.execute(
                this.requestProducer,
                this.responseConsumer,
                this.connPool, this.exchangeContext, this.callback);
        Assert.assertNotNull(future);
        ArgumentCaptor<FutureCallback> argCaptor = ArgumentCaptor.forClass(FutureCallback.class);
        Mockito.verify(this.connPool).lease(
                Mockito.eq(host), Mockito.isNull(), argCaptor.capture());
        ConnRequestCallback connRequestCallback = (ConnRequestCallback) argCaptor.getValue();

        connRequestCallback.cancelled();
        Mockito.verify(this.responseConsumer).cancel();
        Mockito.verify(this.callback).cancelled();
        Mockito.verify(this.responseConsumer).close();
        Mockito.verify(this.requestProducer).close();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testPooledConnectionAutoReleaseOnRequestCancel() throws Exception {
        HttpHost host = new HttpHost("somehost");
        Mockito.when(this.requestProducer.getTarget()).thenReturn(host);

        Future<Object> future = this.requester.execute(
                this.requestProducer,
                this.responseConsumer,
                this.connPool, this.exchangeContext, this.callback);
        Assert.assertNotNull(future);
        ArgumentCaptor<FutureCallback> argCaptor = ArgumentCaptor.forClass(FutureCallback.class);
        Mockito.verify(this.connPool).lease(
                Mockito.eq(host), Mockito.isNull(), argCaptor.capture());
        ConnRequestCallback connRequestCallback = (ConnRequestCallback) argCaptor.getValue();

        future.cancel(true);
        BasicNIOPoolEntry entry = new BasicNIOPoolEntry("id", host, this.conn);
        connRequestCallback.completed(entry);
        Mockito.verify(this.connPool).release(entry, true);
        Mockito.verify(this.conn, Mockito.never()).requestOutput();
    }


    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testPooledRequestExecutionSucceeded() throws Exception {
        HttpHost host = new HttpHost("somehost");
        Mockito.when(this.requestProducer.getTarget()).thenReturn(host);
        Mockito.when(this.conn.isOpen()).thenReturn(true);

        Future<Object> future = this.requester.execute(
                this.requestProducer,
                this.responseConsumer,
                this.connPool, this.exchangeContext, this.callback);
        Assert.assertNotNull(future);
        ArgumentCaptor<FutureCallback> argCaptor = ArgumentCaptor.forClass(FutureCallback.class);
        Mockito.verify(this.connPool).lease(
                Mockito.eq(host), Mockito.isNull(), argCaptor.capture());
        ConnRequestCallback connRequestCallback = (ConnRequestCallback) argCaptor.getValue();

        BasicNIOPoolEntry entry = new BasicNIOPoolEntry("id", host, this.conn);
        connRequestCallback.completed(entry);
        BasicAsyncRequestExecutionHandler exchangeHandler = (BasicAsyncRequestExecutionHandler) this.connContext.getAttribute(
                HttpAsyncRequestExecutor.HTTP_HANDLER);
        Assert.assertNotNull(exchangeHandler);
        Mockito.verify(this.conn).requestOutput();

        Object result = new Object();
        Mockito.when(this.responseConsumer.getResult()).thenReturn(result);
        exchangeHandler.responseCompleted(this.exchangeContext);
        Mockito.verify(this.callback).completed(result);
        Mockito.verify(this.responseConsumer).close();
        Mockito.verify(this.requestProducer).close();
        Mockito.verify(this.connPool).release(entry, true);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testPooledRequestExecutionFailed() throws Exception {
        HttpHost host = new HttpHost("somehost");
        Mockito.when(this.requestProducer.getTarget()).thenReturn(host);
        Mockito.when(this.conn.isOpen()).thenReturn(true);

        Future<Object> future = this.requester.execute(
                this.requestProducer,
                this.responseConsumer,
                this.connPool, this.exchangeContext, this.callback);
        Assert.assertNotNull(future);
        ArgumentCaptor<FutureCallback> argCaptor = ArgumentCaptor.forClass(FutureCallback.class);
        Mockito.verify(this.connPool).lease(
                Mockito.eq(host), Mockito.isNull(), argCaptor.capture());
        ConnRequestCallback connRequestCallback = (ConnRequestCallback) argCaptor.getValue();

        BasicNIOPoolEntry entry = new BasicNIOPoolEntry("id", host, this.conn);
        connRequestCallback.completed(entry);
        BasicAsyncRequestExecutionHandler exchangeHandler = (BasicAsyncRequestExecutionHandler) this.connContext.getAttribute(
                HttpAsyncRequestExecutor.HTTP_HANDLER);
        Assert.assertNotNull(exchangeHandler);
        Mockito.verify(this.conn).requestOutput();

        Exception oppsie = new Exception();
        exchangeHandler.failed(oppsie);
        Mockito.verify(this.responseConsumer).failed(oppsie);
        Mockito.verify(this.callback).failed(oppsie);
        Mockito.verify(this.responseConsumer).close();
        Mockito.verify(this.requestProducer).close();
        Mockito.verify(this.connPool).release(entry, false);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testPooledRequestExecutionCancelled() throws Exception {
        HttpHost host = new HttpHost("somehost");
        Mockito.when(this.requestProducer.getTarget()).thenReturn(host);
        Mockito.when(this.conn.isOpen()).thenReturn(true);

        Future<Object> future = this.requester.execute(
                this.requestProducer,
                this.responseConsumer,
                this.connPool, this.exchangeContext, this.callback);
        Assert.assertNotNull(future);
        ArgumentCaptor<FutureCallback> argCaptor = ArgumentCaptor.forClass(FutureCallback.class);
        Mockito.verify(this.connPool).lease(
                Mockito.eq(host), Mockito.isNull(), argCaptor.capture());
        ConnRequestCallback connRequestCallback = (ConnRequestCallback) argCaptor.getValue();

        BasicNIOPoolEntry entry = new BasicNIOPoolEntry("id", host, this.conn);
        connRequestCallback.completed(entry);
        BasicAsyncRequestExecutionHandler exchangeHandler = (BasicAsyncRequestExecutionHandler) this.connContext.getAttribute(
                HttpAsyncRequestExecutor.HTTP_HANDLER);
        Assert.assertNotNull(exchangeHandler);
        Mockito.verify(this.conn).requestOutput();

        exchangeHandler.cancel();
        Mockito.verify(this.responseConsumer).cancel();
        Mockito.verify(this.callback).cancelled();
        Mockito.verify(this.responseConsumer).close();
        Mockito.verify(this.requestProducer).close();
        Mockito.verify(this.connPool).release(entry, false);
    }

}
