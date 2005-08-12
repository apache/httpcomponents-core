/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http;

/**
 * Represents a Status-Line as returned from a HTTP server.
 *
 * <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC2616</a> states
 * the following regarding the Status-Line:
 * <pre>
 * 6.1 Status-Line
 *
 *  The first line of a Response message is the Status-Line, consisting
 *  of the protocol version followed by a numeric status code and its
 *  associated textual phrase, with each element separated by SP
 *  characters. No CR or LF is allowed except in the final CRLF sequence.
 *
 *      Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
 * </pre>
 * <p>
 * This class is immutable and is inherently thread safe.
 *
 * @see HttpStatus
 * @author <a href="mailto:jsdever@apache.org">Jeff Dever</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @version $Id$
 * 
 * @since 2.0
 */
public class StatusLine {

    // ----------------------------------------------------- Instance Variables

    /** The HTTP-Version. */
    private final HttpVersion httpVersion;

    /** The Status-Code. */
    private final int statusCode;

    /** The Reason-Phrase. */
    private final String reasonPhrase;

    // ----------------------------------------------------------- Constructors
    /**
     * Default constructor
     */
    public StatusLine(final HttpVersion httpVersion, int statusCode, final String reasonPhrase) {
        super();
        if (httpVersion == null) {
            throw new IllegalArgumentException("HTTP version may not be null");
        }
        if (statusCode < 0) {
            throw new IllegalArgumentException("Status code may not be negative");
        }
        this.httpVersion = httpVersion;
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
    }

    public StatusLine(final HttpVersion httpVersion, int statusCode) {
        this(httpVersion, statusCode, HttpStatus.getStatusText(statusCode));
    }

    /**
     * Parses the status line returned from the HTTP server.
     *
     * @param statusLine the status line to be parsed
     * 
     * @throws HttpException if the status line is invalid
     * 
     * @since 4.0 
     */
    public static StatusLine parse(final String statusLine) throws HttpException {
        if (statusLine == null) {
            throw new IllegalArgumentException("Status line string may not be null");
        }
        HttpVersion httpVersion = null;
        int statusCode = 0;
        String reasonPhrase = null;
        int length = statusLine.length();
        int at = 0;
        int start = 0;
        try {
            while (Character.isWhitespace(statusLine.charAt(at))) {
                ++at;
                ++start;
            }
            if (!"HTTP".equals(statusLine.substring(at, at += 4))) {
                throw new HttpException("Status-Line '" + statusLine 
                    + "' does not start with HTTP");
            }
            //handle the HTTP-Version
            at = statusLine.indexOf(" ", at);
            if (at <= 0) {
                throw new ProtocolException(
                        "Unable to parse HTTP-Version from the status line: '"
                        + statusLine + "'");
            }
            httpVersion = HttpVersion.parse(statusLine.substring(start, at));

            //advance through spaces
            while (statusLine.charAt(at) == ' ') {
                at++;
            }

            //handle the Status-Code
            int to = statusLine.indexOf(" ", at);
            if (to < 0) {
                to = length;
            }
            try {
                statusCode = Integer.parseInt(statusLine.substring(at, to));
            } catch (NumberFormatException e) {
                throw new ProtocolException(
                    "Unable to parse status code from status line: '" 
                    + statusLine + "'");
            }
            //handle the Reason-Phrase
            at = to + 1;
            if (at < length) {
                reasonPhrase = statusLine.substring(at).trim();
            } else {
                reasonPhrase = "";
            }
        } catch (StringIndexOutOfBoundsException e) {
            throw new HttpException("Status-Line '" + statusLine + "' is not valid"); 
        }
        return new StatusLine(httpVersion, statusCode, reasonPhrase);
    }

    // --------------------------------------------------------- Public Methods

    /**
     * @return the Status-Code
     */
    public final int getStatusCode() {
        return this.statusCode;
    }

    /**
     * @return the HTTP-Version
     */
    public final HttpVersion getHttpVersion() {
        return this.httpVersion;
    }

    /**
     * @return the Reason-Phrase
     */
    public final String getReasonPhrase() {
        return this.reasonPhrase;
    }

    public final String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(this.httpVersion);
        buffer.append(' ');
        buffer.append(this.statusCode);
        if (this.reasonPhrase != null && !this.reasonPhrase.equals("")) {
            buffer.append(' ');
            buffer.append(this.reasonPhrase);
        }
        return buffer.toString();
    }

    /**
     * Tests if the string starts with 'HTTP' signature.
     * @param s string to test
     * @return <tt>true</tt> if the line starts with 'HTTP' 
     *   signature, <tt>false</tt> otherwise.
     */
    public static boolean startsWithHTTP(final String s) {
        try {
            int at = 0;
            while (Character.isWhitespace(s.charAt(at))) {
                ++at;
            }
            return ("HTTP".equals(s.substring(at, at + 4)));
        } catch (StringIndexOutOfBoundsException e) {
            return false;
        }
    }
}
