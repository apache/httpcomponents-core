/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.entity.EntitySerializer;
import org.apache.http.io.ChunkedOutputStream;
import org.apache.http.io.ContentLengthOutputStream;
import org.apache.http.io.HttpDataTransmitter;
import org.apache.http.io.IdentityOutputStream;
import org.apache.http.protocol.HTTP;

/**
 * Default implementation of an entity writer on the client side.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class DefaultEntitySerializer implements EntitySerializer {

    public DefaultEntitySerializer() {
        super();
    }
    
    protected OutputStream doSerialize(
            final HttpDataTransmitter datatransmitter,
            final HttpMessage message) throws HttpException, IOException {
        Header transferEncodingHeader = message.getFirstHeader(HTTP.TRANSFER_ENCODING);
        Header contentLengthHeader = message.getFirstHeader(HTTP.CONTENT_LEN);
        if (transferEncodingHeader != null) {
            String s = transferEncodingHeader.getValue();
            if (HTTP.CHUNK_CODING.equalsIgnoreCase(s)) {
                if (message.getHttpVersion().lessEquals(HttpVersion.HTTP_1_0)) {
                    throw new ProtocolException(
                            "Chunked transfer encoding not allowed for " + 
                            message.getHttpVersion());
                }
                return new ChunkedOutputStream(datatransmitter);
            } else if (HTTP.IDENTITY_CODING.equalsIgnoreCase(s)) {
                return new IdentityOutputStream(datatransmitter);
            } else {
                throw new ProtocolException(
                        "Unsupported transfer encoding: " + s);
            }
        } else if (contentLengthHeader != null) {
            String s = contentLengthHeader.getValue();
            try {
                long len = Long.parseLong(s);
                return new ContentLengthOutputStream(datatransmitter, len);
            } catch (NumberFormatException e) {
                throw new ProtocolException("Invalid content length: " + s);
            }
        } else {
            return new IdentityOutputStream(datatransmitter); 
        }
    }

    public void serialize(
            final HttpDataTransmitter datatransmitter,
            final HttpMessage message,
            final HttpEntity entity) throws HttpException, IOException {
        if (datatransmitter == null) {
            throw new IllegalArgumentException("HTTP data transmitter may not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("HTTP message may not be null");
        }
        if (entity == null) {
            throw new IllegalArgumentException("HTTP entity may not be null");
        }
        OutputStream outstream = doSerialize(datatransmitter, message);
        entity.writeTo(outstream);
        outstream.close();
    }
    
}
