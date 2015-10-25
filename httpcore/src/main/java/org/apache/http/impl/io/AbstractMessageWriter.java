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

package org.apache.http.impl.io;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.apache.http.FormattedHeader;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.io.HttpMessageWriter;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicLineFormatter;
import org.apache.http.message.LineFormatter;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

/**
 * Abstract base class for HTTP message writers that serialize output to
 * an instance of {@link org.apache.http.io.SessionOutputBuffer}.
 *
 * @since 4.0
 */
@NotThreadSafe
public abstract class AbstractMessageWriter<T extends HttpMessage> implements HttpMessageWriter<T> {

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
        this.lineFormatter = formatter != null ? formatter : BasicLineFormatter.INSTANCE;
        this.lineBuf = new CharArrayBuffer(128);
    }

    LineFormatter getLineFormatter() {
        return this.lineFormatter;
    }

    /**
     * Subclasses must override this method to write out the first header line
     * based on the {@link HttpMessage} passed as a parameter.
     *
     * @param message the message whose first line is to be written out.
     * @throws IOException in case of an I/O error.
     */
    protected abstract void writeHeadLine(T message, CharArrayBuffer lineBuf) throws IOException;

    @Override
    public void write(final T message, final SessionOutputBuffer buffer) throws IOException, HttpException {
        Args.notNull(message, "HTTP message");
        Args.notNull(buffer, "Session output buffer");
        writeHeadLine(message, this.lineBuf);
        buffer.writeLine(this.lineBuf);
        for (final Iterator<Header> it = message.headerIterator(); it.hasNext(); ) {
            final Header header = it.next();
            if (header instanceof FormattedHeader) {
                final CharArrayBuffer chbuffer = ((FormattedHeader) header).getBuffer();
                buffer.writeLine(chbuffer);
            } else {
                this.lineBuf.clear();
                lineFormatter.formatHeader(this.lineBuf, header);
                buffer.writeLine(this.lineBuf);
            }
        }
        addTrailerHeader(buffer, message);
        this.lineBuf.clear();
        buffer.writeLine(this.lineBuf);
    }

    private void addTrailerHeader(final SessionOutputBuffer buffer, final T message) throws IOException {
        final Set<String> trailerNames = message.getTrailers().keySet();
        if (trailerNames.isEmpty()) {
            return;
        }
        this.lineBuf.clear();
        lineFormatter.formatHeader(this.lineBuf, new BasicHeader("Trailer", join(trailerNames)));
        buffer.writeLine(this.lineBuf);
    }

    private String join(final Collection<String> headerNames) {
        final CharArrayBuffer valueBuffer = new CharArrayBuffer(128);
        final Iterator<String> headerNameIterator = headerNames.iterator();
        if (headerNameIterator.hasNext()) {
            valueBuffer.append(headerNameIterator.next());
        }
        while (headerNameIterator.hasNext()) {
            valueBuffer.append(", ");
            valueBuffer.append(headerNameIterator.next());
        }
        return valueBuffer.toString();
    }
}
