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

package org.apache.http.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/**
 * A utility class for parsing http header values according to
 * RFC-2616 Section 4 and 19.3.
 * 
 * @author Michael Becke
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class HttpLineParser {

    /**
     * Constructor for HttpParser.
     */
    private HttpLineParser() {
        super();
    }

    /**
     * Return byte array from an (unchunked) input stream.
     * Stop reading when <tt>"\n"</tt> terminator encountered 
     * If the stream ends before the line terminator is found,
     * the last part of the string will still be returned. 
     * If no input data available, <code>null</code> is returned.
     *
     * @param inputStream the stream to read from
     *
     * @throws IOException if an I/O problem occurs
     * @return a byte array from the stream
     */
    public static byte[] readRawLine(final InputStream inputStream) 
            throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int ch;
        while ((ch = inputStream.read()) >= 0) {
            buf.write(ch);
            if (ch == '\n') { // be tolerant (RFC-2616 Section 19.3)
                break;
            }
        }
        if (buf.size() == 0) {
            return null;
        }
        return buf.toByteArray();
    }

    /**
     * Read up to <tt>"\n"</tt> from an (unchunked) input stream.
     * If the stream ends before the line terminator is found,
     * the last part of the string will still be returned.
     * If no input data available, <code>null</code> is returned.
     *
     * @param inputStream the stream to read from
     * @param charset charset of HTTP protocol elements
     *
     * @throws IOException if an I/O problem occurs
     * @return a line from the stream
     * 
     * @since 3.0
     */
    public static String readLine(final InputStream inputStream, final String charset) 
            throws IOException {
        byte[] rawdata = readRawLine(inputStream);
        if (rawdata == null) {
            return null;
        }
        // strip CR and LF from the end
        int len = rawdata.length;
        int offset = 0;
        if (len > 0) {
            if (rawdata[len - 1] == '\n') {
                offset++;
                if (len > 1) {
                    if (rawdata[len - 2] == '\r') {
                        offset++;
                    }
                }
            }
        }
        return EncodingUtil.getString(rawdata, 0, len - offset, charset);
    }
    
}
