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

package org.apache.http.nio;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class NIOSocketHttpDataTransmitter extends NIOHttpDataTransmitter {

    private final SocketChannel channel;
    
    public NIOSocketHttpDataTransmitter(final Socket socket, int buffersize) throws SocketException {
        super();
        if (socket == null) {
            throw new IllegalArgumentException("Socket may not be null");
        }
        if (socket.getChannel() == null) {
            throw new IllegalArgumentException("Socket does not implement NIO channel");
        }
        this.channel = socket.getChannel();
        if (buffersize < 0) {
            buffersize = socket.getReceiveBufferSize();
        }
        if (buffersize < 1024) {
            buffersize = 1024;
        }
        initBuffer(buffersize);
    }

    protected void flushBuffer(final ByteBuffer src) throws IOException {
        this.channel.write(src);
    }
        
}
