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

package org.apache.hc.core5.testing.nio.http2;

import java.io.IOException;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http2.frame.FramePrinter;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.Http2StreamListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class InternalHttp2StreamListener implements Http2StreamListener {

    private final String id;
    private final Logger headerLog;
    private final Logger frameLog;
    private final Logger framePayloadLog;
    private final Logger flowCtrlLog;
    private final FramePrinter framePrinter;

    public InternalHttp2StreamListener(final String id) {
        this.id = id;
        this.framePrinter = new FramePrinter();
        this.headerLog = LogManager.getLogger("org.apache.hc.core5.http.headers");
        this.frameLog = LogManager.getLogger("org.apache.hc.core5.http2.frame");
        this.framePayloadLog = LogManager.getLogger("org.apache.hc.core5.http2.frame.payload");
        this.flowCtrlLog = LogManager.getLogger("org.apache.hc.core5.http2.flow");
    }

    private void logFrameInfo(final String prefix, final RawFrame frame) {
        try {
            final LogAppendable logAppendable = new LogAppendable(frameLog, prefix);
            framePrinter.printFrameInfo(frame, logAppendable);
            logAppendable.flush();
        } catch (IOException ignore) {
        }
    }

    private void logFramePayload(final String prefix, final RawFrame frame) {
        try {
            final LogAppendable logAppendable = new LogAppendable(framePayloadLog, prefix);
            framePrinter.printPayload(frame, logAppendable);
            logAppendable.flush();
        } catch (IOException ignore) {
        }
    }

    private void logFlowControl(final String prefix, final int streamId, final int delta, final int actualSize) {
        final StringBuilder buffer = new StringBuilder();
        buffer.append(prefix).append(" stream ").append(streamId).append(" flow control " )
                .append(delta).append(" -> ")
                .append(actualSize);
        flowCtrlLog.debug(buffer.toString());
    }

    @Override
    public void onHeaderInput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
        if (headerLog.isDebugEnabled()) {
            for (int i = 0; i < headers.size(); i++) {
                headerLog.debug(id + " << " + headers.get(i));
            }
        }
    }

    @Override
    public void onHeaderOutput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
        if (headerLog.isDebugEnabled()) {
            for (int i = 0; i < headers.size(); i++) {
                headerLog.debug(id + " >> " + headers.get(i));
            }
        }
    }

    @Override
    public void onFrameInput(final HttpConnection connection, final int streamId, final RawFrame frame) {
        if (frameLog.isDebugEnabled()) {
            logFrameInfo(id + " <<", frame);
        }
        if (framePayloadLog.isDebugEnabled()) {
            logFramePayload(id + " <<", frame);
        }
    }

    @Override
    public void onFrameOutput(final HttpConnection connection, final int streamId, final RawFrame frame) {
        if (frameLog.isDebugEnabled()) {
            logFrameInfo(id + " >>", frame);
        }
        if (framePayloadLog.isDebugEnabled()) {
            logFramePayload(id + " >>", frame);
        }
    }

    @Override
    public void onInputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
        if (flowCtrlLog.isDebugEnabled()) {
            logFlowControl(id + " <<", streamId, delta, actualSize);
        }
    }

    @Override
    public void onOutputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
        if (flowCtrlLog.isDebugEnabled()) {
            logFlowControl(id + " >>", streamId, delta, actualSize);
        }
    }

}
