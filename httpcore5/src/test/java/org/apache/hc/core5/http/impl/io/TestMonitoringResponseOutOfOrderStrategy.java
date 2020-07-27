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

package org.apache.hc.core5.http.impl.io;

import java.io.IOException;
import java.io.InputStream;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.ResponseOutOfOrderStrategy;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class TestMonitoringResponseOutOfOrderStrategy {

    private static final ClassicHttpRequest REQUEST = new BasicClassicHttpRequest("POST", "/path");

    @Test
    public void testFirstByteIsNotCheckedSsl() throws IOException {
        final boolean earlyResponse = MonitoringResponseOutOfOrderStrategy.INSTANCE.isEarlyResponseDetected(
                REQUEST,
                connection(true, true),
                // SSLSocket streams report zero bytes available
                socketInputStream(0),
                0,
                1);
        Assert.assertFalse(earlyResponse);
    }

    @Test
    public void testFirstByteIsNotCheckedPlain() throws IOException {
        final boolean earlyResponse = MonitoringResponseOutOfOrderStrategy.INSTANCE.isEarlyResponseDetected(
                REQUEST,
                connection(true, false),
                socketInputStream(1),
                0,
                1);
        Assert.assertFalse(earlyResponse);
    }

    @Test
    public void testWritesWithinChunkAreNotChecked() throws IOException {
        final boolean earlyResponse = MonitoringResponseOutOfOrderStrategy.INSTANCE.isEarlyResponseDetected(
                REQUEST,
                connection(true, true),
                socketInputStream(0),
                1,
                8190);
        Assert.assertFalse(
                "There is data available, but checks shouldn't occur until just prior to the 8192nd byte",
                earlyResponse);
    }

    @Test
    public void testWritesAcrossChunksAreChecked() throws IOException {
        final boolean earlyResponse = MonitoringResponseOutOfOrderStrategy.INSTANCE.isEarlyResponseDetected(
                REQUEST,
                connection(true, true),
                socketInputStream(0),
                8191,
                1);
        Assert.assertTrue(earlyResponse);
    }

    @Test
    public void testMaximumChunks() throws IOException {
        final ResponseOutOfOrderStrategy strategy = new MonitoringResponseOutOfOrderStrategy(1, 2);
        Assert.assertTrue(strategy.isEarlyResponseDetected(
                REQUEST,
                connection(true, true),
                socketInputStream(0),
                0,
                1));
        Assert.assertTrue(strategy.isEarlyResponseDetected(
                REQUEST,
                connection(true, true),
                socketInputStream(0),
                1,
                2));
        Assert.assertFalse(strategy.isEarlyResponseDetected(
                REQUEST,
                connection(true, true),
                socketInputStream(0),
                2,
                3));
    }

    private static InputStream socketInputStream(final int available) throws IOException {
        final InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(stream.available()).thenReturn(available);
        return stream;
    }

    private static HttpClientConnection connection(final boolean dataAvailable, final boolean ssl) throws IOException {
        final HttpClientConnection connection = Mockito.mock(HttpClientConnection.class);
        Mockito.when(connection.isDataAvailable(ArgumentMatchers.any(Timeout.class))).thenReturn(dataAvailable);
        if (ssl) {
            Mockito.when(connection.getSSLSession()).thenReturn(Mockito.mock(SSLSession.class));
        }
        return connection;
    }
}
