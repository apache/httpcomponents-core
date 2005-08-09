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
import org.apache.http.io.ChunkedOutputStream;
import org.apache.http.io.ContentLengthOutputStream;
import org.apache.http.io.HttpDataOutputStream;
import org.apache.http.io.HttpDataTransmitter;
import org.apache.http.io.IdentityOutputStream;
import org.apache.http.io.OutputStreamHttpDataTransmitter;

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
public class DefaultServerEntityWriter implements EntityWriter {

    public DefaultServerEntityWriter() {
        super();
    }

    private OutputStream getRawOutputStream(final HttpDataTransmitter datatransmitter) {
        // This is a (quite ugly) performance hack
        if (datatransmitter instanceof OutputStreamHttpDataTransmitter) {
            // If we are dealing with the compatibility wrapper
            // Get the original input stream
            return  ((OutputStreamHttpDataTransmitter)datatransmitter).getOutputStream();
        } else {
            return new HttpDataOutputStream(datatransmitter);
        }
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
        OutputStream outstream = getRawOutputStream(datatransmitter);
        long len = entity.getContentLength();
        if (entity.isChunked() && version.greaterEquals(HttpVersion.HTTP_1_1)) {
            outstream = new ChunkedOutputStream(outstream);
        } else if (len >= 0) {
            outstream = new ContentLengthOutputStream(outstream, len);
        } else {
            outstream = new IdentityOutputStream(outstream); 
        }
        if (entity.writeTo(outstream)) {
            outstream.close();
        }
    }
    
}
