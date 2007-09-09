/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.message;

import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.ParseException;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.Header;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;



/**
 * Basic parser for lines in the head section of an HTTP message.
 * There are individual methods for parsing a request line, a
 * status line, or a header line.
 * The lines to parse are passed in memory, the parser does not depend
 * on any specific IO mechanism.
 * Instances of this class are stateless and thread-safe.
 * Derived classes MUST maintain these properties.
 *
 * <p>
 * Note: This class was created by refactoring parsing code located in
 * various other classes. The author tags from those other classes have
 * been replicated here, although the association with the parsing code
 * taken from there has not been traced.
 * </p>
 *
 * @author <a href="mailto:jsdever@apache.org">Jeff Dever</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:oleg@ural.ru">Oleg Kalnichevski</a>
 * @author and others
 */
public class BasicLineParser implements LineParser {

    /**
     * A default instance of this class, for use as default or fallback.
     * Note that {@link BasicLineParser} is not a singleton, there can
     * be many instances of the class itself and of derived classes.
     * The instance here provides non-customized, default behavior.
     */
    public final static BasicLineParser DEFAULT = new BasicLineParser();


    /**
     * A version of the protocol to parse.
     * The version is typically not relevant, but the protocol name.
     */
    protected final ProtocolVersion protocol;


    /**
     * Creates a new line parser for the given HTTP-like protocol.
     *
     * @param proto     a version of the protocol to parse, or
     *                  <code>null</code> for HTTP. The actual version
     *                  is not relevant, only the protocol name.
     */
    public BasicLineParser(ProtocolVersion proto) {
        if (proto == null) {
            proto = HttpVersion.HTTP_1_1;
        }
        this.protocol = proto;
    }


    /**
     * Creates a new line parser for HTTP.
     */
    public BasicLineParser() {
        this(null);
    }



    public final static
        ProtocolVersion parseProtocolVersion(String value,
                                             LineParser parser)
        throws ParseException {

        if (value == null) {
            throw new IllegalArgumentException
                ("Value to parse may not be null.");
        }

        if (parser == null)
            parser = BasicLineParser.DEFAULT;

        CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        return parser.parseProtocolVersion(buffer, 0, buffer.length());
    }


    // non-javadoc, see interface LineParser
    public ProtocolVersion parseProtocolVersion(final CharArrayBuffer buffer,
                                                final int indexFrom,
                                                final int indexTo) 
        throws ParseException {

        if (buffer == null) {
            throw new IllegalArgumentException
                ("Char array buffer may not be null");
        }
        if (indexFrom < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (indexTo > buffer.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (indexFrom > indexTo) {
            throw new IndexOutOfBoundsException();
        }


        final String protoname = this.protocol.getProtocol();
        final int protolength  = protoname.length();

        int i = skipWhitespace(buffer, indexFrom);
        // long enough for "HTTP/1.1"?
        if (i + protolength + 4 > indexTo) {
            throw new ParseException
                ("Not a valid protocol version: " +
                 buffer.substring(indexFrom, indexTo));
        }

        // check the protocol name and slash
        boolean ok = true;
        for (int j=0; ok && (j<protolength); j++) {
            ok = (buffer.charAt(i+j) == protoname.charAt(j));
        }
        if (ok) {
            ok = (buffer.charAt(i+protolength) == '/');
        }
        if (!ok) {
            throw new ParseException
                ("Not a valid protocol version: " +
                 buffer.substring(indexFrom, indexTo));
        }

        i += protolength+1;

        final int period = buffer.indexOf('.', i, indexTo);
        if (period == -1) {
            throw new ParseException
                ("Invalid protocol version number: " + 
                 buffer.substring(indexFrom, indexTo));
        }

        int major;
        try {
            major = Integer.parseInt(buffer.substringTrimmed(i, period)); 
        } catch (NumberFormatException e) {
            throw new ParseException
                ("Invalid protocol major version number: " + 
                 buffer.substring(indexFrom, indexTo));
        }

        int minor;
        try {
            minor = Integer.parseInt(buffer.substringTrimmed(period + 1,
                                                             indexTo)); 
        } catch (NumberFormatException e) {
            throw new ParseException(
                "Invalid protocol minor version number: " + 
                buffer.substring(indexFrom, indexTo));
        }

        return createProtocolVersion(major, minor);

    } // parseProtocolVersion


    /**
     * Creates a protocol version.
     * Called from {@link #parseProtocolVersion}.
     *
     * @param major     the major version number, for example 1 in HTTP/1.0
     * @param minor     the minor version number, for example 0 in HTTP/1.0
     *
     * @return  the protocol version
     */
    protected ProtocolVersion createProtocolVersion(int major, int minor) {
        return protocol.forVersion(major, minor);
    }



    // non-javadoc, see interface LineParser
    public boolean hasProtocolVersion(final CharArrayBuffer buffer,
                                      int index) {
        if (buffer == null) {
            throw new IllegalArgumentException
                ("Char array buffer may not be null");
        }
        if (index >= buffer.length()) {
            throw new IndexOutOfBoundsException();
        }


        final String protoname = this.protocol.getProtocol();
        final int  protolength = protoname.length();

        if (buffer.length() < protolength+4)
            return false; // not long enough for "HTTP/1.1"

        if (index < 0) {
            // end of line, no tolerance for trailing whitespace
            // this works only for single-digit major and minor version
            index = buffer.length() -4 -protolength;
        } else if (index == 0) {
            // beginning of line, tolerate leading whitespace
            index = skipWhitespace(buffer, index);

        } // else within line, don't tolerate whitespace


        if (index + protolength + 4 > buffer.length())
            return false;


        // just check protocol name and slash, no need to analyse the version
        boolean ok = true;
        for (int j=0; ok && (j<protolength); j++) {
            ok = (buffer.charAt(index+j) == protoname.charAt(j));
        }
        if (ok) {
            ok = (buffer.charAt(index+protolength) == '/');
        }

        return ok;
    }



    public final static
        RequestLine parseRequestLine(String value,
                                     LineParser parser)
        throws ParseException {

        if (value == null) {
            throw new IllegalArgumentException
                ("Value to parse may not be null.");
        }

        if (parser == null)
            parser = BasicLineParser.DEFAULT;

        CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        return parser.parseRequestLine(buffer, 0, buffer.length());
    }


    /**
     * Parses a request line.
     *
     * @param buffer    a buffer holding the line to parse
     *
     * @return  the parsed request line
     *
     * @throws ParseException        in case of a parse error
     */
    public RequestLine parseRequestLine(final CharArrayBuffer buffer,
                                        final int indexFrom,
                                        final int indexTo)
        throws ParseException {

        if (buffer == null) {
            throw new IllegalArgumentException
                ("Char array buffer may not be null");
        }
        if (indexFrom < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (indexTo > buffer.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (indexFrom > indexTo) {
            throw new IndexOutOfBoundsException();
        }

        try {
            int i = skipWhitespace(buffer, indexFrom);
            int blank = buffer.indexOf(' ', i, indexTo);
            if (blank < 0) {
                throw new ParseException("Invalid request line: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            String method = buffer.substringTrimmed(i, blank);

            i = skipWhitespace(buffer, blank);
            blank = buffer.indexOf(' ', i, indexTo);
            if (blank < 0) {
                throw new ParseException("Invalid request line: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            String uri = buffer.substringTrimmed(i, blank);
            ProtocolVersion ver = parseProtocolVersion(buffer, blank, indexTo);
            return createRequestLine(method, uri, ver);
        } catch (IndexOutOfBoundsException e) {
            throw new ParseException("Invalid request line: " + 
                                     buffer.substring(indexFrom, indexTo)); 
        }
    } // parseRequestLine


    /**
     * Instantiates a new request line.
     * Called from {@link #parseRequestLine}.
     *
     * @param method    the request method
     * @param uri       the requested URI
     * @param ver       the protocol version
     *
     * @return  a new status line with the given data
     */
    protected RequestLine createRequestLine(String method,
                                            String uri,
                                            ProtocolVersion ver) {
        return new BasicRequestLine(method, uri, ver);
    }



    public final static
        StatusLine parseStatusLine(String value,
                                   LineParser parser)
        throws ParseException {

        if (value == null) {
            throw new IllegalArgumentException
                ("Value to parse may not be null.");
        }

        if (parser == null)
            parser = BasicLineParser.DEFAULT;

        CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        return parser.parseStatusLine(buffer, 0, buffer.length());
    }


    // non-javadoc, see interface LineParser
    public StatusLine parseStatusLine(final CharArrayBuffer buffer,
                                      final int indexFrom,
                                      final int indexTo) 
        throws ParseException {

        if (buffer == null) {
            throw new IllegalArgumentException
                ("Char array buffer may not be null");
        }
        if (indexFrom < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (indexTo > buffer.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (indexFrom > indexTo) {
            throw new IndexOutOfBoundsException();
        }

        try {
            // handle the HTTP-Version
            int i = skipWhitespace(buffer, indexFrom);
            int blank = buffer.indexOf(' ', i, indexTo);
            if (blank <= 0) {
                throw new ParseException(
                        "Unable to parse HTTP-Version from the status line: "
                        + buffer.substring(indexFrom, indexTo));
            }
            ProtocolVersion ver = parseProtocolVersion(buffer, i, blank);

            // handle the Status-Code
            i = skipWhitespace(buffer, blank);
            blank = buffer.indexOf(' ', i, indexTo);
            if (blank < 0) {
                blank = indexTo;
            }
            int statusCode = 0;
            try {
                statusCode =
                    Integer.parseInt(buffer.substringTrimmed(i, blank));
            } catch (NumberFormatException e) {
                throw new ParseException(
                    "Unable to parse status code from status line: " 
                    + buffer.substring(indexFrom, indexTo));
            }
            //handle the Reason-Phrase
            i = blank;
            String reasonPhrase = null;
            if (i < indexTo) {
                reasonPhrase = buffer.substringTrimmed(i, indexTo);
            } else {
                reasonPhrase = "";
            }
            return createStatusLine(ver, statusCode, reasonPhrase);

        } catch (IndexOutOfBoundsException e) {
            throw new ParseException("Invalid status line: " + 
                                     buffer.substring(indexFrom, indexTo)); 
        }
    } // parseStatusLine


    /**
     * Instantiates a new status line.
     * Called from {@link #parseStatusLine}.
     *
     * @param ver       the protocol version
     * @param status    the status code
     * @param reason    the reason phrase
     *
     * @return  a new status line with the given data
     */
    protected StatusLine createStatusLine(ProtocolVersion ver,
                                          int status, String reason) {
        return new BasicStatusLine(ver, status, reason);
    }



    public final static
        Header parseHeader(String value, 
                           LineParser parser)
        throws ParseException {

        if (value == null) {
            throw new IllegalArgumentException
                ("Value to parse may not be null.");
        }

        if (parser == null)
            parser = BasicLineParser.DEFAULT;

        CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        return parser.parseHeader(buffer);
    }


    // non-javadoc, see interface LineParser
    public Header parseHeader(CharArrayBuffer buffer)
        throws ParseException {

        // the actual parser code is in the constructor of BufferedHeader
        return new BufferedHeader(buffer, getHeaderValueParser());
    }


    /**
     * Obtains the header value parser to use.
     * Called by {@link #parseHeader}.
     *
     * @return  the header value parser, or
     *          <code>null</code> for the default
     */
    protected HeaderValueParser getHeaderValueParser() {
        return null;
    }


    /**
     * Helper to skip whitespace.
     *
     * @param buffer    the buffer in which to skip whitespace
     * @param index     the index at which to start skipping
     *
     * @return  the index after the whitespace. This is the argument index
     *          if there was no whitespace. It is the end of the buffer if
     *          the rest of the line is whitespace.
     */
    protected int skipWhitespace(CharArrayBuffer buffer, int index) {
        while ((index < buffer.length()) &&
               HTTP.isWhitespace(buffer.charAt(index))) {
            index++;
        }
        return index;
    }


} // class BasicLineParser
