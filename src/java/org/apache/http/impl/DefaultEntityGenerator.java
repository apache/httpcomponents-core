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

package org.apache.http.impl;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.HttpMutableEntity;
import org.apache.http.ProtocolException;
import org.apache.http.io.ChunkedInputStream;
import org.apache.http.io.ContentLengthInputStream;
import org.apache.http.io.HttpDataInputStream;
import org.apache.http.io.HttpDataReceiver;
import org.apache.http.io.InputStreamHttpDataReceiver;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class DefaultEntityGenerator implements EntityGenerator {

    private static final String TRANSFER_ENCODING = "Transfer-Encoding";
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String CONTENT_TYPE = "Content-Type";
    
    private static final String CHUNKED_ENCODING = "chunked";
    private static final String IDENTITY_ENCODING = "identity";
    
    public DefaultEntityGenerator() {
        super();
    }

    private InputStream getRawInputStream(final HttpDataReceiver datareceiver) {
        // This is a (quite ugly) performance hack
        if (datareceiver instanceof InputStreamHttpDataReceiver) {
            // If we are dealing with the compatibility wrapper
            // Get the original input stream
            return  ((InputStreamHttpDataReceiver)datareceiver).getInputStream();
        } else {
            return new HttpDataInputStream(datareceiver);
        }
    }
    
    public HttpMutableEntity generate(
            final HttpDataReceiver datareceiver,
            final HttpMessage message) throws HttpException, IOException {
        if (datareceiver == null) {
            throw new IllegalArgumentException("HTTP data receiver may not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("HTTP message may not be null");
        }

        HttpMutableEntity entity = new BasicHttpEntity();
        HttpParams params = message.getParams(); 
        
        Header contentTypeHeader = message.getFirstHeader(CONTENT_TYPE);
        Header transferEncodingHeader = message.getFirstHeader(TRANSFER_ENCODING);
        Header contentLengthHeader = message.getFirstHeader(CONTENT_LENGTH);
        // We use Transfer-Encoding if present and ignore Content-Length.
        // RFC2616, 4.4 item number 3
        if (transferEncodingHeader != null) {
            HeaderElement[] encodings = transferEncodingHeader.getElements();
            if (params.isParameterTrue(HttpProtocolParams.STRICT_TRANSFER_ENCODING)) {
                // Currently only chunk and identity are supported
                for (int i = 0; i < encodings.length; i++) {
                    String encoding = encodings[i].getValue();
                    if (encoding != null && !encoding.equals("") 
                        && !encoding.equalsIgnoreCase(CHUNKED_ENCODING)
                        && !encoding.equalsIgnoreCase(IDENTITY_ENCODING)) {
                        throw new ProtocolException("Unsupported transfer encoding: " + encoding);
                    }
                }
            }
            // The chunked encoding must be the last one applied
            // RFC2616, 14.41
            int len = encodings.length;            
            if ((len > 0) && (CHUNKED_ENCODING.equalsIgnoreCase(encodings[len - 1].getName()))) { 
                entity.setChunked(true);
                entity.setContentLength(-1);
                // if response body is empty
                HttpConnectionParams connparams = new HttpConnectionParams(params); 
                if (datareceiver.isDataAvailable(connparams.getSoTimeout())) {
                    entity.setInputStream(new ChunkedInputStream(datareceiver));
                } else {
                    if (params.isParameterTrue(HttpProtocolParams.STRICT_TRANSFER_ENCODING)) {
                        throw new ProtocolException("Chunk-encoded body declared but not sent");
                    }
                }
            }
        } else if (contentLengthHeader != null) {
            long contentlen = -1;
            Header[] headers = message.getHeaders(CONTENT_LENGTH);
            for (int i = headers.length - 1; i >= 0; i--) {
                Header header = headers[i];
                try {
                    contentlen = Long.parseLong(header.getValue());
                    break;
                } catch (NumberFormatException e) {
                    // No option but to ignore it
                }
                // See if we can have better luck with another header, if present
            }
            entity.setChunked(false);
            entity.setContentLength(contentlen);
            InputStream instream = getRawInputStream(datareceiver);            
            if (contentlen >= 0) {
                instream = new ContentLengthInputStream(instream, contentlen);
            }
            entity.setInputStream(instream);
        }
        if (contentTypeHeader != null) {
            entity.setContentType(contentTypeHeader.getValue());    
        }
        return entity;
    }
        
}
