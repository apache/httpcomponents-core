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

package tests.performance;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class NIOListenerThread extends Thread {

    private final int port; 
    private final TestDataProcessor dataprocessor;
    private ServerSocketChannel serverchannel;
    
    public NIOListenerThread(final TestDataProcessor dataprocessor, int port) {
        super();
        this.port = port;
        this.dataprocessor = dataprocessor;
    }
    
    public void run() {
        try {
            this.serverchannel = ServerSocketChannel.open();
            try {
                this.serverchannel.socket().bind(new InetSocketAddress(this.port));
                while (!Thread.interrupted()) {
                    SocketChannel channel = this.serverchannel.accept();
                    try {
                        Socket socket = channel.socket();
                        this.dataprocessor.process(socket);
                    } finally {
                        channel.close();
                    }
                }
            } finally {
                this.serverchannel.close();
            }
        } catch (IOException ex) {
            if (!isInterrupted()) {
                ex.printStackTrace();
            }
        }
    }
    
    public void destroy() {
        interrupt();
        try {
            this.serverchannel.close();
        } catch (IOException ignore) {
        }
    }
        
}