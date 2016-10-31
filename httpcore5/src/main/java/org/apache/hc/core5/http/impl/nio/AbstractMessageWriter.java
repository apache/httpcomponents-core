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

package org.apache.hc.core5.http.impl.nio;

import java.io.IOException;
import java.util.Iterator;

import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.message.BasicLineFormatter;
import org.apache.hc.core5.http.message.LineFormatter;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.http.nio.NHttpMessageWriter;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Abstract {@link NHttpMessageWriter} that serves as a base for all message
 * writer implementations.
 *
 * @since 4.0
 */
public abstract class AbstractMessageWriter<T extends HttpMessage> implements NHttpMessageWriter<T> {

    private final CharArrayBuffer lineBuf;
    private final LineFormatter lineFormatter;

    /**
     * Creates an instance of AbstractMessageWriter.
     *
     * @param formatter the line formatter If {@code null} {@link BasicLineFormatter#INSTANCE}
     *   will be used.
     *
     * @since 4.3
     */
    public AbstractMessageWriter(final LineFormatter formatter) {
        super();
        this.lineFormatter = (formatter != null) ? formatter : BasicLineFormatter.INSTANCE;
        this.lineBuf = new CharArrayBuffer(64);
    }

    LineFormatter getLineFormatter() {
        return this.lineFormatter;
    }

    @Override
    public void reset() {
    }

    /**
     * Writes out the first line of {@link HttpMessage}.
     *
     * @param message HTTP message.
     */
    protected abstract void writeHeadLine(T message, CharArrayBuffer buffer) throws IOException;

    @Override
    public void write(final T message, final SessionOutputBuffer sessionBuffer) throws IOException, HttpException {
        Args.notNull(message, "HTTP message");
        Args.notNull(sessionBuffer, "Session output buffer");

        writeHeadLine(message, this.lineBuf);
        sessionBuffer.writeLine(this.lineBuf);
        for (final Iterator<Header> it = message.headerIterator(); it.hasNext(); ) {
            final Header header = it.next();
            if (header instanceof FormattedHeader) {
                final CharArrayBuffer buffer = ((FormattedHeader) header).getBuffer();
                sessionBuffer.writeLine(buffer);
            } else {
                this.lineBuf.clear();
                this.lineFormatter.formatHeader(this.lineBuf, header);
                sessionBuffer.writeLine(this.lineBuf);
            }
        }
        this.lineBuf.clear();
        sessionBuffer.writeLine(this.lineBuf);
    }

}
