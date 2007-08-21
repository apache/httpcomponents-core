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
import org.apache.http.ParseException;
import org.apache.http.ProtocolException;
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



    // public default constructor


    // non-javadoc, see interface LineParser
    public HttpVersion parseProtocolVersion(final CharArrayBuffer buffer,
                                            final int indexFrom,
                                            final int indexTo) 
        throws ProtocolException {

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
            int major, minor;

            int i = indexFrom;
            while (HTTP.isWhitespace(buffer.charAt(i))) {
                i++;
            }            
            if (buffer.charAt(i    ) != 'H' 
             || buffer.charAt(i + 1) != 'T'
             || buffer.charAt(i + 2) != 'T'
             || buffer.charAt(i + 3) != 'P'
             || buffer.charAt(i + 4) != '/') {
                throw new ProtocolException("Not a valid HTTP version string: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            i += 5;
            int period = buffer.indexOf('.', i, indexTo);
            if (period == -1) {
                throw new ProtocolException("Invalid HTTP version number: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            try {
                major = Integer.parseInt(buffer.substringTrimmed(i, period)); 
            } catch (NumberFormatException e) {
                throw new ProtocolException("Invalid HTTP major version number: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            try {
                minor = Integer.parseInt(buffer.substringTrimmed(period + 1, indexTo)); 
            } catch (NumberFormatException e) {
                throw new ProtocolException("Invalid HTTP minor version number: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            return createProtocolVersion(major, minor);
            
        } catch (IndexOutOfBoundsException e) {
            throw new ProtocolException("Invalid HTTP version string: " + 
                    buffer.substring(indexFrom, indexTo)); 
        }
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
    protected HttpVersion createProtocolVersion(int major, int minor) {
        return new HttpVersion(major, minor);
    }


    /**
     * Parses a request line.
     *
     * @param buffer    a buffer holding the line to parse
     *
     * @return  the parsed request line
     *
     * @throws ProtocolException        in case of a parse error
     */
    public RequestLine parseRequestLine(final CharArrayBuffer buffer,
                                        final int indexFrom,
                                        final int indexTo)
        throws ProtocolException {

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
            int i = indexFrom;
            while (HTTP.isWhitespace(buffer.charAt(i))) {
                i++;
            }
            int blank = buffer.indexOf(' ', i, indexTo);
            if (blank < 0) {
                throw new ProtocolException("Invalid request line: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            String method = buffer.substringTrimmed(i, blank);
            i = blank;
            while (HTTP.isWhitespace(buffer.charAt(i))) {
                i++;
            }
            blank = buffer.indexOf(' ', i, indexTo);
            if (blank < 0) {
                throw new ProtocolException("Invalid request line: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            String uri = buffer.substringTrimmed(i, blank);
            HttpVersion ver = parseProtocolVersion(buffer, blank, indexTo);
            return createRequestLine(method, uri, ver);
        } catch (IndexOutOfBoundsException e) {
            throw new ProtocolException("Invalid request line: " + 
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
                                            HttpVersion ver) {
        return new BasicRequestLine(method, uri, ver);
    }


    // non-javadoc, see interface LineParser
    public StatusLine parseStatusLine(final CharArrayBuffer buffer,
                                      final int indexFrom,
                                      final int indexTo) 
        throws ProtocolException {

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
            int i = indexFrom;
            //handle the HTTP-Version
            while (HTTP.isWhitespace(buffer.charAt(i))) {
                i++;
            }            
            int blank = buffer.indexOf(' ', i, indexTo);
            if (blank <= 0) {
                throw new ProtocolException(
                        "Unable to parse HTTP-Version from the status line: "
                        + buffer.substring(indexFrom, indexTo));
            }
            HttpVersion ver = parseProtocolVersion(buffer, i, blank);

            i = blank;
            //advance through spaces
            while (HTTP.isWhitespace(buffer.charAt(i))) {
                i++;
            }            

            //handle the Status-Code
            blank = buffer.indexOf(' ', i, indexTo);
            if (blank < 0) {
                blank = indexTo;
            }
            int statusCode = 0;
            try {
                statusCode =
                    Integer.parseInt(buffer.substringTrimmed(i, blank));
            } catch (NumberFormatException e) {
                throw new ProtocolException(
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
            throw new ProtocolException("Invalid status line: " + 
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
    protected StatusLine createStatusLine(HttpVersion ver,
                                          int status, String reason) {
        return new BasicStatusLine(ver, status, reason);
    }


    // non-javadoc, see interface LineParser
    public Header parseHeader(CharArrayBuffer buffer)
        throws ProtocolException {

        Header result = null;
        try {
            // the actual parser code is in the constructor of BufferedHeader
            result = new BufferedHeader(buffer, getHeaderValueParser());
        } catch (ParseException px) {
            throw new ProtocolException(px.getMessage(), px);
        }
        return result;
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


} // class BasicLineParser
