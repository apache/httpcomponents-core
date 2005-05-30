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

package org.apache.http.impl.io;

import java.io.IOException;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

import org.apache.http.HttpRuntime;
import org.apache.http.io.HttpDataTransmitter;
import org.apache.http.io.HttpDataTransmitterFactory;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class DefaultHttpDataTransmitterFactory implements HttpDataTransmitterFactory {
    
    public HttpDataTransmitter create(final Socket socket) throws IOException {
        if (socket == null) {
            throw new IllegalArgumentException("Socket may not be null");
        }
        if (socket instanceof SSLSocket) {
            if (HttpRuntime.isSSLNIOCapable()) {
                return new NIOSocketHttpDataTransmitter(socket); 
            } else {
                return new OldIOSocketHttpDataTransmitter(socket); 
            }
        } else {
            if (HttpRuntime.isNIOCapable()) {
                return new NIOSocketHttpDataTransmitter(socket); 
            } else {
                return new OldIOSocketHttpDataTransmitter(socket); 
            }            
        }
    }
    
}
