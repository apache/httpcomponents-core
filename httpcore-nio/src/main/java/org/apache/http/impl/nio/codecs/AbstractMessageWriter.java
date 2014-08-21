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

package org.apache.http.impl.nio.codecs;

import java.io.IOException;
import java.util.Iterator;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.message.BasicLineFormatter;
import org.apache.http.message.LineFormatter;
import org.apache.http.nio.NHttpMessageWriter;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

/**
 * Abstract {@link NHttpMessageWriter} that serves as a base for all message
 * writer implementations.
 *
 * @since 4.0
 */
@SuppressWarnings("deprecation")
@NotThreadSafe
public abstract class AbstractMessageWriter<T extends HttpMessage> implements NHttpMessageWriter<T> {

    protected final SessionOutputBuffer sessionBuffer;
    protected final CharArrayBuffer lineBuf;
    protected final LineFormatter lineFormatter;

    /**
     * Creates an instance of this class.
     *
     * @param buffer the session output buffer.
     * @param formatter the line formatter.
     * @param params HTTP parameters.
     *
     * @deprecated (4.3) use
     *   {@link AbstractMessageWriter#AbstractMessageWriter(SessionOutputBuffer, LineFormatter)}
     */
    @Deprecated
    public AbstractMessageWriter(final SessionOutputBuffer buffer,
                                 final LineFormatter formatter,
                                 final HttpParams params) {
        super();
        Args.notNull(buffer, "Session input buffer");
        this.sessionBuffer = buffer;
        this.lineBuf = new CharArrayBuffer(64);
        this.lineFormatter = (formatter != null) ? formatter : BasicLineFormatter.INSTANCE;
    }

    /**
     * Creates an instance of AbstractMessageWriter.
     *
     * @param buffer the session output buffer.
     * @param formatter the line formatter If {@code null} {@link BasicLineFormatter#INSTANCE}
     *   will be used.
     *
     * @since 4.3
     */
    public AbstractMessageWriter(
            final SessionOutputBuffer buffer,
            final LineFormatter formatter) {
        super();
        this.sessionBuffer = Args.notNull(buffer, "Session input buffer");
        this.lineFormatter = (formatter != null) ? formatter : BasicLineFormatter.INSTANCE;
        this.lineBuf = new CharArrayBuffer(64);
    }

    @Override
    public void reset() {
    }

    /**
     * Writes out the first line of {@link HttpMessage}.
     *
     * @param message HTTP message.
     */
    protected abstract void writeHeadLine(T message) throws IOException;

    @Override
    public void write(final T message) throws IOException, HttpException {
        Args.notNull(message, "HTTP message");
        writeHeadLine(message);
        for (final Iterator<?> it = message.headerIterator(); it.hasNext(); ) {
            final Header header = (Header) it.next();
            this.sessionBuffer.writeLine
                (lineFormatter.formatHeader(this.lineBuf, header));
        }
        this.lineBuf.clear();
        this.sessionBuffer.writeLine(this.lineBuf);
    }

}
