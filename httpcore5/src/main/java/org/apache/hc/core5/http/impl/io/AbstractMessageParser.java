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
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.HttpMessageParser;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.message.LazyLineParser;
import org.apache.hc.core5.http.message.LineParser;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Abstract base class for HTTP message parsers that obtain input from
 * an instance of {@link org.apache.hc.core5.http.io.SessionInputBuffer}.
 *
 * @since 4.0
 */
public abstract class AbstractMessageParser<T extends HttpMessage> implements HttpMessageParser<T> {

    private static final int HEAD_LINE    = 0;
    private static final int HEADERS      = 1;

    private final Http1Config http1Config;
    private final List<CharArrayBuffer> headerLines;
    private final CharArrayBuffer headLine;
    private final LineParser lineParser;

    private int state;
    private T message;

    /**
     * Creates new instance of AbstractMessageParser.
     *
     * @param lineParser the line parser. If {@code null}
     *   {@link org.apache.hc.core5.http.message.LazyLineParser#INSTANCE} will be used.
     * @param http1Config the message http1Config. If {@code null}
     *   {@link Http1Config#DEFAULT} will be used.
     *
     * @since 4.3
     */
    public AbstractMessageParser(final LineParser lineParser, final Http1Config http1Config) {
        super();
        this.lineParser = lineParser != null ? lineParser : LazyLineParser.INSTANCE;
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.headerLines = new ArrayList<>();
        this.headLine = new CharArrayBuffer(128);
        this.state = HEAD_LINE;
    }

    LineParser getLineParser() {
        return this.lineParser;
    }

    /**
     * Parses HTTP headers from the data receiver stream according to the generic
     * format as specified by the HTTP/1.1 protocol specification.
     *
     * @param inBuffer Session input buffer
     * @param inputStream Input stream
     * @param maxHeaderCount maximum number of headers allowed. If the number
     *  of headers received from the data stream exceeds maxCount value, an
     *  IOException will be thrown. Setting this parameter to a negative value
     *  or zero will disable the check.
     * @param maxLineLen maximum number of characters for a header line,
     *  including the continuation lines. Setting this parameter to a negative
     *  value or zero will disable the check.
     * @return array of HTTP headers
     * @param lineParser the line parser. If {@code null}
     *   {@link org.apache.hc.core5.http.message.LazyLineParser#INSTANCE} will be used
     *
     * @throws IOException in case of an I/O error
     * @throws HttpException in case of HTTP protocol violation
     */
    public static Header[] parseHeaders(
            final SessionInputBuffer inBuffer,
            final InputStream inputStream,
            final int maxHeaderCount,
            final int maxLineLen,
            final LineParser lineParser) throws HttpException, IOException {
        final List<CharArrayBuffer> headerLines = new ArrayList<>();
        return parseHeaders(inBuffer, inputStream, maxHeaderCount, maxLineLen,
                lineParser != null ? lineParser : LazyLineParser.INSTANCE, headerLines);
    }

    /**
     * Parses HTTP headers from the data receiver stream according to the generic
     * format as specified by the HTTP/1.1 protocol specification.
     *
     * @param inBuffer Session input buffer
     * @param inputStream Input stream
     * @param maxHeaderCount maximum number of headers allowed. If the number
     *  of headers received from the data stream exceeds maxCount value, an
     *  IOException will be thrown. Setting this parameter to a negative value
     *  or zero will disable the check.
     * @param maxLineLen maximum number of characters for a header line,
     *  including the continuation lines. Setting this parameter to a negative
     *  value or zero will disable the check.
     * @param parser line parser to use.
     * @param headerLines List of header lines. This list will be used to store
     *   intermediate results. This makes it possible to resume parsing of
     *   headers in case of a {@link java.io.InterruptedIOException}.
     *
     * @return array of HTTP headers
     *
     * @throws IOException in case of an I/O error
     * @throws HttpException in case of HTTP protocol violation
     *
     * @since 4.1
     */
    public static Header[] parseHeaders(
            final SessionInputBuffer inBuffer,
            final InputStream inputStream,
            final int maxHeaderCount,
            final int maxLineLen,
            final LineParser parser,
            final List<CharArrayBuffer> headerLines) throws HttpException, IOException {
        Args.notNull(inBuffer, "Session input buffer");
        Args.notNull(inputStream, "Input stream");
        Args.notNull(parser, "Line parser");
        Args.notNull(headerLines, "Header line list");

        CharArrayBuffer current = null;
        CharArrayBuffer previous = null;
        for (;;) {
            if (current == null) {
                current = new CharArrayBuffer(64);
            } else {
                current.clear();
            }
            final int readLen = inBuffer.readLine(current, inputStream);
            if (readLen == -1 || current.length() < 1) {
                break;
            }
            // Parse the header name and value
            // Check for folded headers first
            // Detect LWS-char see HTTP/1.0 or HTTP/1.1 Section 2.2
            // discussion on folded headers
            if ((current.charAt(0) == ' ' || current.charAt(0) == '\t') && previous != null) {
                // we have continuation folded header
                // so append value
                int i = 0;
                while (i < current.length()) {
                    final char ch = current.charAt(i);
                    if (ch != ' ' && ch != '\t') {
                        break;
                    }
                    i++;
                }
                if (maxLineLen > 0
                        && previous.length() + 1 + current.length() - i > maxLineLen) {
                    throw new MessageConstraintException("Maximum line length limit exceeded");
                }
                previous.append(' ');
                previous.append(current, i, current.length() - i);
            } else {
                headerLines.add(current);
                previous = current;
                current = null;
            }
            if (maxHeaderCount > 0 && headerLines.size() >= maxHeaderCount) {
                throw new MessageConstraintException("Maximum header count exceeded");
            }
        }
        final Header[] headers = new Header[headerLines.size()];
        for (int i = 0; i < headerLines.size(); i++) {
            final CharArrayBuffer buffer = headerLines.get(i);
            headers[i] = parser.parseHeader(buffer);
        }
        return headers;
    }

    /**
     * Subclasses must override this method to generate an instance of
     * {@link HttpMessage} based on the initial input from the session buffer.
     * <p>
     * Usually this method is expected to read just the very first line or
     * the very first valid from the data stream and based on the input generate
     * an appropriate instance of {@link HttpMessage}.
     *
     * @param buffer the session input buffer.
     * @return HTTP message based on the input from the session buffer.
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation.
     *
     * @since 5.0
     */
    protected abstract T createMessage(CharArrayBuffer buffer) throws IOException, HttpException;

    /**
     * Subclasses must override this method to generate an appropriate exception
     * in case of unexpected connection termination by the peer endpoint.
     *
     * @since 5.0
     */
    protected abstract IOException createConnectionClosedException();

    @Override
    public T parse(final SessionInputBuffer buffer, final InputStream inputStream) throws IOException, HttpException {
        Args.notNull(buffer, "Session input buffer");
        Args.notNull(inputStream, "Input stream");
        final int st = this.state;
        switch (st) {
        case HEAD_LINE:
            for (int n = 0; n < this.http1Config.getMaxEmptyLineCount(); n++) {
                this.headLine.clear();
                final int i = buffer.readLine(this.headLine, inputStream);
                if (i == -1) {
                    throw createConnectionClosedException();
                }
                if (this.headLine.length() > 0) {
                    this.message = createMessage(this.headLine);
                    if (this.message != null) {
                        break;
                    }
                }
            }
            if (this.message == null) {
                throw new MessageConstraintException("Maximum empty line limit exceeded");
            }
            this.state = HEADERS;
            //$FALL-THROUGH$
        case HEADERS:
            final Header[] headers = AbstractMessageParser.parseHeaders(
                    buffer,
                    inputStream,
                    this.http1Config.getMaxHeaderCount(),
                    this.http1Config.getMaxLineLength(),
                    this.lineParser,
                    this.headerLines);
            this.message.setHeaders(headers);
            final T result = this.message;
            this.message = null;
            this.headerLines.clear();
            this.state = HEAD_LINE;
            return result;
        default:
            throw new IllegalStateException("Inconsistent parser state");
        }
    }

}
