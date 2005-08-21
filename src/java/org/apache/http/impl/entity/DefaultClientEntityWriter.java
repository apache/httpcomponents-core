/*
 * $HeadURL: $
 * $Revision: $
 * $Date: $
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

package org.apache.http.impl.entity;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.io.ChunkedOutputStream;
import org.apache.http.io.ContentLengthOutputStream;
import org.apache.http.io.HttpDataTransmitter;

/**
 * <p>
 * </p>
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision: $
 * 
 * @since 4.0
 */
public class DefaultClientEntityWriter implements EntityWriter {

    public DefaultClientEntityWriter() {
        super();
    }

    public void write(
            final HttpEntity entity,
            final HttpVersion version,
            final HttpDataTransmitter datatransmitter) throws HttpException, IOException {
        if (entity == null) {
            throw new IllegalArgumentException("HTTP entity may not be null");
        }
        if (version == null) {
            throw new IllegalArgumentException("HTTP version may not be null");
        }
        if (datatransmitter == null) {
            throw new IllegalArgumentException("HTTP data transmitter may not be null");
        }
        long len = entity.getContentLength();
        boolean chunked = entity.isChunked() || len < 0;  
        if (chunked && version.lessEquals(HttpVersion.HTTP_1_0)) {
            throw new ProtocolException(
                    "Chunked transfer encoding not supported by " + version);
        }
        OutputStream outstream = null;
        if (chunked) {
            outstream = new ChunkedOutputStream(datatransmitter);
        } else {
            outstream = new ContentLengthOutputStream(datatransmitter, len);
        }
        if (entity.writeTo(outstream)) {
            outstream.close();
        }
    }
    
}
