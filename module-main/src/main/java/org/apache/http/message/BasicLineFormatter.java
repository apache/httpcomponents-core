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
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.Header;
import org.apache.http.util.CharArrayBuffer;


/**
 * Interface for formatting elements of the HEAD section of an HTTP message.
 * This is the complement to {@link LineParser}.
 * There are individual methods for formatting a request line, a
 * status line, or a header line. The formatting does <i>not</i> include the
 * trailing line break sequence CR-LF.
 * The formatted lines are returned in memory, the formatter does not depend
 * on any specific IO mechanism.
 * Instances of this interface are expected to be stateless and thread-safe.
 *
 * @author <a href="mailto:rolandw AT apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$ $Date$
 *
 * @since 4.0
 */
public class BasicLineFormatter implements LineFormatter {

    /**
     * A default instance of this class, for use as default or fallback.
     * Note that {@link BasicLineFormatter} is not a singleton, there can
     * be many instances of the class itself and of derived classes.
     * The instance here provides non-customized, default behavior.
     */
    public final static BasicLineFormatter DEFAULT = new BasicLineFormatter();



    // public default constructor


    /**
     * Obtains a buffer for formatting.
     *
     * @param buffer    a buffer already available, or <code>null</code>
     *
     * @return  the cleared argument buffer if there is one, or
     *          a new empty buffer that can be used for formatting
     */
    protected CharArrayBuffer initBuffer(CharArrayBuffer buffer) {
        if (buffer != null) {
            buffer.clear();
        } else {
            buffer = new CharArrayBuffer(64);
        }
        return buffer;
    }


    // non-javadoc, see interface LineFormatter
    public CharArrayBuffer formatRequestLine(RequestLine reqline,
                                             CharArrayBuffer buffer) {

        CharArrayBuffer result = initBuffer(buffer);
        BasicRequestLine.format(result, reqline); //@@@ move code here
        return result;
    }



    // non-javadoc, see interface LineFormatter
    public CharArrayBuffer formatStatusLine(StatusLine statline,
                                            CharArrayBuffer buffer) {
        CharArrayBuffer result = initBuffer(buffer);
        BasicStatusLine.format(result, statline); //@@@ move code here
        return result;
    }



    // non-javadoc, see interface LineFormatter
    public CharArrayBuffer formatHeader(Header header,
                                        CharArrayBuffer buffer) {
        if (header == null) {
            throw new IllegalArgumentException
                ("Header must not be null.");
        }
        CharArrayBuffer result = null;

        if (header instanceof BufferedHeader) {
            // If the header is backed by a buffer, re-use the buffer
            result = ((BufferedHeader)header).getBuffer();
        } else {
            result = initBuffer(buffer);
            BasicHeader.format(result, header); //@@@ move code here
        }
        return result;

    } // formatHeader


} // class BasicLineFormatter
