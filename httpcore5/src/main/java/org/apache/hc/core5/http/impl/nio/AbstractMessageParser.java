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
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.message.LazyLineParser;
import org.apache.hc.core5.http.message.LineParser;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Abstract {@link NHttpMessageParser} that serves as a base for all message
 * parser implementations.
 *
 * @param <T> The type of {@link HttpMessage}.
 * @since 4.0
 */
public abstract class AbstractMessageParser<T extends HttpMessage> implements NHttpMessageParser<T> {

    private enum State {
        READ_HEAD_LINE, READ_HEADERS, COMPLETED
    }

    private final Http1Config http1Config;
    private final LineParser lineParser;

    private State state;

    private T message;
    private CharArrayBuffer lineBuf;
    private final List<CharArrayBuffer> headerBufs;
    private int emptyLineCount;

    /**
     * Constructs a new instance for a subclass.
     *
     * @param http1Config HTTP/1.1 protocol parameters.
     * @param lineParser How to parse lines in an HTTP message.
     * @since 5.3
     */
    public AbstractMessageParser(final Http1Config http1Config, final LineParser lineParser) {
        super();
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.lineParser = lineParser != null ? lineParser : LazyLineParser.INSTANCE;
        this.headerBufs = new ArrayList<>();
        this.state = State.READ_HEAD_LINE;
    }

    /**
     * Constructs a new instance for a subclass.
     *
     * @param lineParser How to parse lines in an HTTP message.
     * @param messageConstraints HTTP/1.1 protocol parameters.
     * @deprecated Use {@link #AbstractMessageParser(Http1Config, LineParser)}
     */
    @Deprecated
    public AbstractMessageParser(final LineParser lineParser, final Http1Config messageConstraints) {
        this(messageConstraints, lineParser);
    }

    LineParser getLineParser() {
        return this.lineParser;
    }

    @Override
    public void reset() {
        this.state = State.READ_HEAD_LINE;
        this.headerBufs.clear();
        this.emptyLineCount = 0;
        this.message = null;
    }

    /**
     * Creates {@link HttpMessage} instance based on the content of the input
     *  buffer containing the first line of the incoming HTTP message.
     *
     * @param buffer the line buffer.
     * @return HTTP message.
     * @throws HttpException in case of HTTP protocol violation
     */
    protected abstract T createMessage(CharArrayBuffer buffer) throws HttpException;

    private T parseHeadLine() throws IOException, HttpException {
        if (this.lineBuf.isEmpty()) {
            this.emptyLineCount++;
            if (this.emptyLineCount >= this.http1Config.getMaxEmptyLineCount()) {
                throw new MessageConstraintException("Maximum empty line limit exceeded");
            }
            return null;
        }
        return createMessage(this.lineBuf);
    }

    private void parseHeader() throws IOException {
        final CharArrayBuffer current = this.lineBuf;
        final int count = this.headerBufs.size();
        if ((this.lineBuf.charAt(0) == ' ' || this.lineBuf.charAt(0) == '\t') && count > 0) {
            // Handle folded header line
            final CharArrayBuffer previous = this.headerBufs.get(count - 1);
            int i = 0;
            while (i < current.length()) {
                final char ch = current.charAt(i);
                if (ch != ' ' && ch != '\t') {
                    break;
                }
                i++;
            }
            final int maxLineLen = this.http1Config.getMaxLineLength();
            if (maxLineLen > 0 && previous.length() + 1 + current.length() - i > maxLineLen) {
                throw new MessageConstraintException("Maximum line length limit exceeded");
            }
            previous.append(' ');
            previous.append(current, i, current.length() - i);
        } else {
            this.headerBufs.add(current);
            this.lineBuf = null;
        }
    }

    @Override
    public T parse(
            final SessionInputBuffer sessionBuffer, final boolean endOfStream) throws IOException, HttpException {
        Args.notNull(sessionBuffer, "Session input buffer");
        while (this.state != State.COMPLETED) {
            if (this.lineBuf == null) {
                this.lineBuf = new CharArrayBuffer(64);
            } else {
                this.lineBuf.clear();
            }
            final boolean lineComplete = sessionBuffer.readLine(this.lineBuf, endOfStream);
            final int maxLineLen = this.http1Config.getMaxLineLength();
            if (maxLineLen > 0 &&
                    (this.lineBuf.length() > maxLineLen ||
                            !lineComplete && sessionBuffer.length() > maxLineLen)) {
                throw new MessageConstraintException("Maximum line length limit exceeded");
            }
            if (!lineComplete) {
                break;
            }

            switch (this.state) {
            case READ_HEAD_LINE:
                this.message = parseHeadLine();
                if (this.message != null) {
                    this.state = State.READ_HEADERS;
                }
                break;
            case READ_HEADERS:
                if (this.lineBuf.length() > 0) {
                    final int maxHeaderCount = this.http1Config.getMaxHeaderCount();
                    if (maxHeaderCount > 0 && headerBufs.size() >= maxHeaderCount) {
                        throw new MessageConstraintException("Maximum header count exceeded");
                    }

                    parseHeader();
                } else {
                    this.state = State.COMPLETED;
                }
                break;
            }
            if (endOfStream && !sessionBuffer.hasData()) {
                this.state = State.COMPLETED;
            }
        }
        if (this.state == State.COMPLETED) {
            for (final CharArrayBuffer buffer : this.headerBufs) {
                this.message.addHeader(this.lineParser.parseHeader(buffer));
            }
            return this.message;
        }
        return null;
    }

}
