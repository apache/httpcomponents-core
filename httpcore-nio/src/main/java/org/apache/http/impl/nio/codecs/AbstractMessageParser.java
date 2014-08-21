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
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.MessageConstraintException;
import org.apache.http.ParseException;
import org.apache.http.ProtocolException;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.config.MessageConstraints;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.LineParser;
import org.apache.http.nio.NHttpMessageParser;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.params.HttpParamConfig;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

/**
 * Abstract {@link NHttpMessageParser} that serves as a base for all message
 * parser implementations.
 *
 * @since 4.0
 */
@SuppressWarnings("deprecation")
@NotThreadSafe
public abstract class AbstractMessageParser<T extends HttpMessage> implements NHttpMessageParser<T> {

    private final SessionInputBuffer sessionBuffer;

    private static final int READ_HEAD_LINE = 0;
    private static final int READ_HEADERS   = 1;
    private static final int COMPLETED      = 2;

    private int state;
    private boolean endOfStream;

    private T message;
    private CharArrayBuffer lineBuf;
    private final List<CharArrayBuffer> headerBufs;

    protected final LineParser lineParser;
    private final MessageConstraints constraints;

    /**
     * Creates an instance of this class.
     *
     * @param buffer the session input buffer.
     * @param lineParser the line parser.
     * @param params HTTP parameters.
     *
     * @deprecated (4.3) use
     *   {@link AbstractMessageParser#AbstractMessageParser(SessionInputBuffer, LineParser,
     *     MessageConstraints)}
     */
    @Deprecated
    public AbstractMessageParser(
            final SessionInputBuffer buffer,
            final LineParser lineParser,
            final HttpParams params) {
        super();
        Args.notNull(buffer, "Session input buffer");
        Args.notNull(params, "HTTP parameters");
        this.sessionBuffer = buffer;
        this.state = READ_HEAD_LINE;
        this.endOfStream = false;
        this.headerBufs = new ArrayList<CharArrayBuffer>();
        this.constraints = HttpParamConfig.getMessageConstraints(params);
        this.lineParser = (lineParser != null) ? lineParser : BasicLineParser.INSTANCE;
    }

    /**
     * Creates an instance of AbstractMessageParser.
     *
     * @param buffer the session input buffer.
     * @param lineParser the line parser. If {@code null} {@link BasicLineParser#INSTANCE}
     *   will be used.
     * @param constraints Message constraints. If {@code null}
     *   {@link MessageConstraints#DEFAULT} will be used.
     *
     * @since 4.3
     */
    public AbstractMessageParser(
            final SessionInputBuffer buffer,
            final LineParser lineParser,
            final MessageConstraints constraints) {
        super();
        this.sessionBuffer = Args.notNull(buffer, "Session input buffer");
        this.lineParser = lineParser != null ? lineParser : BasicLineParser.INSTANCE;
        this.constraints = constraints != null ? constraints : MessageConstraints.DEFAULT;
        this.headerBufs = new ArrayList<CharArrayBuffer>();
        this.state = READ_HEAD_LINE;
        this.endOfStream = false;
    }

    @Override
    public void reset() {
        this.state = READ_HEAD_LINE;
        this.endOfStream = false;
        this.headerBufs.clear();
        this.message = null;
    }

    @Override
    public int fillBuffer(final ReadableByteChannel channel) throws IOException {
        final int bytesRead = this.sessionBuffer.fill(channel);
        if (bytesRead == -1) {
            this.endOfStream = true;
        }
        return bytesRead;
    }

    /**
     * Creates {@link HttpMessage} instance based on the content of the input
     *  buffer containing the first line of the incoming HTTP message.
     *
     * @param buffer the line buffer.
     * @return HTTP message.
     * @throws HttpException in case of HTTP protocol violation
     * @throws ParseException in case of a parse error.
     */
    protected abstract T createMessage(CharArrayBuffer buffer)
        throws HttpException, ParseException;

    private void parseHeadLine() throws HttpException, ParseException {
        this.message = createMessage(this.lineBuf);
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
            final int maxLineLen = this.constraints.getMaxLineLength();
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
    public T parse() throws IOException, HttpException {
        while (this.state != COMPLETED) {
            if (this.lineBuf == null) {
                this.lineBuf = new CharArrayBuffer(64);
            } else {
                this.lineBuf.clear();
            }
            final boolean lineComplete = this.sessionBuffer.readLine(this.lineBuf, this.endOfStream);
            final int maxLineLen = this.constraints.getMaxLineLength();
            if (maxLineLen > 0 &&
                    (this.lineBuf.length() > maxLineLen ||
                            (!lineComplete && this.sessionBuffer.length() > maxLineLen))) {
                throw new MessageConstraintException("Maximum line length limit exceeded");
            }
            if (!lineComplete) {
                break;
            }

            switch (this.state) {
            case READ_HEAD_LINE:
                try {
                    parseHeadLine();
                } catch (final ParseException px) {
                    throw new ProtocolException(px.getMessage(), px);
                }
                this.state = READ_HEADERS;
                break;
            case READ_HEADERS:
                if (this.lineBuf.length() > 0) {
                    final int maxHeaderCount = this.constraints.getMaxHeaderCount();
                    if (maxHeaderCount > 0 && headerBufs.size() >= maxHeaderCount) {
                        throw new MessageConstraintException("Maximum header count exceeded");
                    }

                    parseHeader();
                } else {
                    this.state = COMPLETED;
                }
                break;
            }
            if (this.endOfStream && !this.sessionBuffer.hasData()) {
                this.state = COMPLETED;
            }
        }
        if (this.state == COMPLETED) {
            for (final CharArrayBuffer buffer : this.headerBufs) {
                try {
                    this.message.addHeader(lineParser.parseHeader(buffer));
                } catch (final ParseException ex) {
                    throw new ProtocolException(ex.getMessage(), ex);
                }
            }
            return this.message;
        } else {
            return null;
        }
    }

}
