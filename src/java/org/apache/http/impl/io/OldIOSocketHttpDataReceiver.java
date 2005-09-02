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
import java.io.InterruptedIOException;
import java.net.Socket;

import org.apache.http.io.InputStreamHttpDataReceiver;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class OldIOSocketHttpDataReceiver extends InputStreamHttpDataReceiver {

    static private final Class SOCKET_TIMEOUT_CLASS = SocketTimeoutExceptionClass();

    /**
     * Returns <code>SocketTimeoutExceptionClass<code> or <code>null</code> if the class
     * does not exist.
     * 
     * @return <code>SocketTimeoutExceptionClass<code>, or <code>null</code> if unavailable.
     */ 
    static private Class SocketTimeoutExceptionClass() {
        try {
            return Class.forName("java.net.SocketTimeoutException");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static boolean isSocketTimeoutException(final InterruptedIOException e) {
        if (SOCKET_TIMEOUT_CLASS != null) {
            return SOCKET_TIMEOUT_CLASS.isInstance(e);
        } else {
            return true;
        }
    }
    
    private final Socket socket;
    
    public OldIOSocketHttpDataReceiver(final Socket socket) throws IOException {
        super(socket.getInputStream());
        this.socket = socket;
        int buffersize = socket.getReceiveBufferSize();
        if (buffersize < 2048) {
            buffersize = 2048;
        }
        initBuffer(buffersize);
    }
    
    public boolean isDataAvailable(int timeout) throws IOException {
        boolean result = hasBufferedData();
        if (!result) {
            int oldtimeout = this.socket.getSoTimeout();
            try {
                this.socket.setSoTimeout(timeout);
                fillBuffer();
                result = hasBufferedData();
            } catch (InterruptedIOException e) {
                if (!isSocketTimeoutException(e)) {
                    throw e;
                }
            } finally {
                socket.setSoTimeout(oldtimeout);
            }
        }
        return result;
    }    
        
}
