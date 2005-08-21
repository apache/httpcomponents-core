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
import java.net.ServerSocket;
import java.net.Socket;

public class OldIOListenerThread extends Thread {

    private final int port; 
    private final TestDataProcessor dataprocessor;
    private ServerSocket serversocket;
    
    public OldIOListenerThread(final TestDataProcessor dataprocessor, int port) {
        super();
        this.port = port;
        this.dataprocessor = dataprocessor;
    }
    
    public void run() {
        try {
            try {
                this.serversocket = new ServerSocket(this.port);
                while (!Thread.interrupted()) {
                    Socket socket = this.serversocket.accept();
                    try {
                        this.dataprocessor.process(socket);
                    } finally {
                        socket.close();
                    }
                }
            } finally {
                this.serversocket.close();
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
            this.serversocket.close();
        } catch (IOException ignore) {
        }
    }
	

}