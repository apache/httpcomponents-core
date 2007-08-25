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

import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.StatusLine;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;



/**
 * Represents a status line as returned from a HTTP server.
 * See <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC2616</a> section 6.1.
 * This class is immutable and therefore inherently thread safe.
 *
 * @see HttpStatus
 * @author <a href="mailto:jsdever@apache.org">Jeff Dever</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 *
 * @version $Id$
 * 
 * @since 4.0
 */
public class BasicStatusLine implements StatusLine {

    // ----------------------------------------------------- Instance Variables

    /** The HTTP-Version. */
    private final HttpVersion httpVersion;

    /** The Status-Code. */
    private final int statusCode;

    /** The Reason-Phrase. */
    private final String reasonPhrase;

    // ----------------------------------------------------------- Constructors
    /**
     * Creates a new status line with the given version, status, and reason.
     *
     * @param httpVersion       the HTTP version of the response
     * @param statusCode        the status code of the response
     * @param reasonPhrase      the reason phrase to the status code, or
     *                          <code>null</code>
     */
    public BasicStatusLine(final HttpVersion httpVersion, int statusCode,
                           final String reasonPhrase) {
        super();
        if (httpVersion == null) {
            throw new IllegalArgumentException
                ("HTTP version may not be null.");
        }
        if (statusCode < 0) {
            throw new IllegalArgumentException
                ("Status code may not be negative.");
        }
        this.httpVersion = httpVersion;
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * @return the Status-Code
     */
    public int getStatusCode() {
        return this.statusCode;
    }

    /**
     * @return the HTTP-Version
     */
    public HttpVersion getHttpVersion() {
        return this.httpVersion;
    }

    /**
     * @return the Reason-Phrase
     */
    public String getReasonPhrase() {
        return this.reasonPhrase;
    }

    public String toString() {
    	CharArrayBuffer buffer = new CharArrayBuffer(64);        
        buffer.append(this.httpVersion);
        buffer.append(' ');
        buffer.append(Integer.toString(this.statusCode));
        if (this.reasonPhrase != null && this.reasonPhrase.length() > 0) {
            buffer.append(' ');
            buffer.append(this.reasonPhrase);
        }
        return buffer.toString();
    }
    
    /*public@@@*/ static void format(final CharArrayBuffer buffer, final StatusLine statusline) {
        if (buffer == null) {
            throw new IllegalArgumentException("String buffer may not be null");
        }
        if (statusline == null) {
            throw new IllegalArgumentException("Status line may not be null");
        }
        BasicHttpVersionFormat.format(buffer, statusline.getHttpVersion());
        buffer.append(' ');
        buffer.append(Integer.toString(statusline.getStatusCode()));
        buffer.append(' '); // keep whitespace even if reason phrase is empty
        if (statusline.getReasonPhrase() != null) {
            buffer.append(statusline.getReasonPhrase());
        }
    }
 
    /*public@@@*/ static String format(final StatusLine statusline) {
        CharArrayBuffer buffer = new CharArrayBuffer(32);
        format(buffer, statusline);
        return buffer.toString();
    }
    
}
