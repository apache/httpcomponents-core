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

package org.apache.hc.core5.testing.nio;

import java.io.IOException;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http2.frame.FramePrinter;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.testing.classic.LoggingSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingH2StreamListener implements H2StreamListener {

    public final static LoggingH2StreamListener INSTANCE = new LoggingH2StreamListener();

    private final Logger headerLog;
    private final Logger frameLog;
    private final Logger framePayloadLog;
    private final Logger flowCtrlLog;
    private final FramePrinter framePrinter;

    private LoggingH2StreamListener() {
        this.framePrinter = new FramePrinter();
        this.headerLog = LoggerFactory.getLogger("org.apache.hc.core5.http.headers");
        this.frameLog = LoggerFactory.getLogger("org.apache.hc.core5.http2.frame");
        this.framePayloadLog = LoggerFactory.getLogger("org.apache.hc.core5.http2.frame.payload");
        this.flowCtrlLog = LoggerFactory.getLogger("org.apache.hc.core5.http2.flow");
    }

    private void logFrameInfo(final String prefix, final RawFrame frame) {
        try {
            final LogAppendable logAppendable = new LogAppendable(frameLog, prefix);
            framePrinter.printFrameInfo(frame, logAppendable);
            logAppendable.flush();
        } catch (final IOException ignore) {
            // ignore
        }
    }

    private void logFramePayload(final String prefix, final RawFrame frame) {
        try {
            final LogAppendable logAppendable = new LogAppendable(framePayloadLog, prefix);
            framePrinter.printPayload(frame, logAppendable);
            logAppendable.flush();
        } catch (final IOException ignore) {
            // ignore
        }
    }

    private void logFlowControl(final String prefix, final int streamId, final int delta, final int actualSize) {
        flowCtrlLog.debug("{} stream {} flow control {} -> {}", prefix, streamId, delta, actualSize);
    }

    @Override
    public void onHeaderInput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
        if (headerLog.isDebugEnabled()) {
            final String prefix = LoggingSupport.getId(connection);
            for (int i = 0; i < headers.size(); i++) {
                headerLog.debug("{} << {}", prefix, headers.get(i));
            }
        }
    }

    @Override
    public void onHeaderOutput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
        if (headerLog.isDebugEnabled()) {
            final String prefix = LoggingSupport.getId(connection);
            for (int i = 0; i < headers.size(); i++) {
                headerLog.debug("{} >> {}", prefix, headers.get(i));
            }
        }
    }

    @Override
    public void onFrameInput(final HttpConnection connection, final int streamId, final RawFrame frame) {
        if (frameLog.isDebugEnabled()) {
            logFrameInfo(LoggingSupport.getId(connection) + " <<", frame);
        }
        if (framePayloadLog.isDebugEnabled()) {
            logFramePayload(LoggingSupport.getId(connection) + " <<", frame);
        }
    }

    @Override
    public void onFrameOutput(final HttpConnection connection, final int streamId, final RawFrame frame) {
        if (frameLog.isDebugEnabled()) {
            logFrameInfo(LoggingSupport.getId(connection) + " >>", frame);
        }
        if (framePayloadLog.isDebugEnabled()) {
            logFramePayload(LoggingSupport.getId(connection) + " >>", frame);
        }
    }

    @Override
    public void onInputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
        if (flowCtrlLog.isDebugEnabled()) {
            logFlowControl(LoggingSupport.getId(connection) + "  in", streamId, delta, actualSize);
        }
    }

    @Override
    public void onOutputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
        if (flowCtrlLog.isDebugEnabled()) {
            logFlowControl(LoggingSupport.getId(connection) + " out", streamId, delta, actualSize);
        }
    }

}
