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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class NIOSocketHttpDataReceiver extends NIOHttpDataReceiver {

    private final SocketChannel channel;
    private final Selector selector;
    
    private long readTimeout = 0;
    
    protected NIOSocketHttpDataReceiver(final Socket socket) throws IOException {
        super();
        if (socket == null) {
            throw new IllegalArgumentException("Socket may not be null");
        }
        if (socket.getChannel() == null) {
            throw new IllegalArgumentException("Socket does not implement NIO channel");
        }
        this.channel = socket.getChannel();
        this.channel.configureBlocking(false);
        this.selector = Selector.open();
        this.channel.register(this.selector, SelectionKey.OP_READ);
        initBuffer(socket.getReceiveBufferSize());
    }
    
    public void reset(final HttpParams params) {
        this.readTimeout = HttpConnectionParams.getSoTimeout(params);
        super.reset(params); 
    }

    protected int readFromChannel(final ByteBuffer dst) throws IOException {
        if (dst == null) {
            throw new IllegalArgumentException("Byte buffer may not be null");
        }
        this.selector.select(this.readTimeout);
        int result = this.channel.read(dst);
        if (result == 0) {
            throw new SocketTimeoutException("Socket read timeout after " 
                    + this.readTimeout + " ms");
        }
        return result;
    }
  
    public boolean isDataAvailable(int timeout) throws IOException {
        if (hasDataInBuffer()) {
            return true;
        } else {
            this.selector.select(timeout);
            return !this.selector.selectedKeys().isEmpty();
        }
    }    
        
}
