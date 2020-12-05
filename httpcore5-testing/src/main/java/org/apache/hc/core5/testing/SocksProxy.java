/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.core5.testing;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.util.TimeValue;

/**
 * Cheap and nasty SOCKS protocol version 5 proxy, recommended for use in unit tests only so we can test our SOCKS client code.
 */
public class SocksProxy {

    private static class SocksProxyHandler {

        public static final int VERSION_5 = 5;
        public static final int COMMAND_CONNECT = 1;
        public static final int ATYP_DOMAINNAME = 3;

        private final SocksProxy parent;
        private final Socket socket;
        private volatile Socket remote;

        public SocksProxyHandler(final SocksProxy parent, final Socket socket) {
            this.parent = parent;
            this.socket = socket;
        }

        public void start() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final DataInputStream input = new DataInputStream(socket.getInputStream());
                        final DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                        final Socket target = establishConnection(input, output);
                        remote = target;

                        final Thread t1 = pumpStream(input, target.getOutputStream());
                        final Thread t2 = pumpStream(target.getInputStream(), output);
                        try {
                            t1.join();
                        } catch (final InterruptedException e) {
                        }
                        try {
                            t2.join();
                        } catch (final InterruptedException e) {
                        }
                    } catch (final IOException e) {
                    } finally {
                        parent.cleanupSocksProxyHandler(SocksProxyHandler.this);
                    }
                }

                private Socket establishConnection(final DataInputStream input, final DataOutputStream output) throws IOException {
                    final int clientVersion = input.readUnsignedByte();
                    if (clientVersion != VERSION_5) {
                        throw new IOException("SOCKS implementation only supports version 5");
                    }
                    final int nMethods = input.readUnsignedByte();
                    for (int i = 0; i < nMethods; i++) {
                        input.readUnsignedByte(); // auth method
                    }
                    // response
                    output.writeByte(VERSION_5);
                    output.writeByte(0); // no auth method
                    output.flush();

                    input.readUnsignedByte(); // client version again
                    final int command = input.readUnsignedByte();
                    if (command != COMMAND_CONNECT) {
                        throw new IOException("SOCKS implementation only supports CONNECT command");
                    }
                    input.readUnsignedByte(); // reserved

                    final String targetHost;
                    final byte[] targetAddress;
                    final int addressType = input.readUnsignedByte();
                    switch (addressType) {
                        case InetAddressUtils.IPV4:
                            targetHost = null;
                            targetAddress = new byte[4];
                            for (int i = 0; i < targetAddress.length; i++) {
                                targetAddress[i] = input.readByte();
                            }
                            break;
                        case InetAddressUtils.IPV6:
                            targetHost = null;
                            targetAddress = new byte[16];
                            for (int i = 0; i < targetAddress.length; i++) {
                                targetAddress[i] = input.readByte();
                            }
                            break;
                        case ATYP_DOMAINNAME:
                            final int length = input.readUnsignedByte();
                            final StringBuilder domainname = new StringBuilder();
                            for (int i = 0; i < length; i++) {
                                domainname.append((char) input.readUnsignedByte());
                            }
                            targetHost = domainname.toString();
                            targetAddress = null;
                            break;
                        default:
                            throw new IOException("Unsupported address type: " + addressType);
                    }

                    final int targetPort = input.readUnsignedShort();
                    final Socket target;
                    if (targetHost != null) {
                        target = new Socket(targetHost, targetPort);
                    } else {
                        target = new Socket(InetAddress.getByAddress(targetAddress), targetPort);
                    }

                    output.writeByte(VERSION_5);
                    output.writeByte(0); /* success */
                    output.writeByte(0); /* reserved */
                    final byte[] localAddress = target.getLocalAddress().getAddress();
                    if (localAddress.length == 4) {
                        output.writeByte(InetAddressUtils.IPV4);
                    } else if (localAddress.length == 16) {
                        output.writeByte(InetAddressUtils.IPV6);
                    } else {
                        throw new IOException("Unsupported localAddress byte length: " + localAddress.length);
                    }
                    output.write(localAddress);
                    output.writeShort(target.getLocalPort());
                    output.flush();

                    return target;
                }

                private Thread pumpStream(final InputStream input, final OutputStream output) {
                    final Thread t = new Thread(() -> {
                        final byte[] buffer = new byte[1024 * 8];
                        try {
                            while (true) {
                                final int read = input.read(buffer);
                                if (read < 0) {
                                    break;
                                }
                                output.write(buffer, 0, read);
                                output.flush();
                            }
                        } catch (final IOException e) {
                        } finally {
                            shutdown();
                        }
                    });
                    t.start();
                    return t;
                }

            }).start();
        }

        public void shutdown() {
            try {
                this.socket.close();
            } catch (final IOException e) {
            }
            if (this.remote != null) {
                try {
                    this.remote.close();
                } catch (final IOException e) {
                }
            }
        }

    }

    private final int port;

    private final List<SocksProxyHandler> handlers = new ArrayList<>();
    private ServerSocket server;
    private Thread serverThread;

    public SocksProxy() {
        this(0);
    }

    public SocksProxy(final int port) {
        this.port = port;
    }

    public synchronized void start() throws IOException {
        if (this.server == null) {
            this.server = new ServerSocket(this.port);
            this.serverThread = new Thread(() -> {
                try {
                    while (true) {
                        final Socket socket = server.accept();
                        startSocksProxyHandler(socket);
                    }
                } catch (final IOException e) {
                } finally {
                    if (server != null) {
                        try {
                            server.close();
                        } catch (final IOException e) {
                        }
                        server = null;
                    }
                }
            });
            this.serverThread.start();
        }
    }

    public void shutdown(final TimeValue timeout) throws InterruptedException {
        final long waitUntil = System.currentTimeMillis() + timeout.toMilliseconds();
        Thread t = null;
        synchronized (this) {
            if (this.server != null) {
                try {
                    this.server.close();
                } catch (final IOException e) {
                } finally {
                    this.server = null;
                }
                t = this.serverThread;
                this.serverThread = null;
            }
            for (final SocksProxyHandler handler : this.handlers) {
                handler.shutdown();
            }
            while (!this.handlers.isEmpty()) {
                final long waitTime = waitUntil - System.currentTimeMillis();
                if (waitTime > 0) {
                    wait(waitTime);
                }
            }
        }
        if (t != null) {
            final long waitTime = waitUntil - System.currentTimeMillis();
            if (waitTime > 0) {
                t.join(waitTime);
            }
        }
    }

    protected void startSocksProxyHandler(final Socket socket) {
        final SocksProxyHandler handler = new SocksProxyHandler(this, socket);
        synchronized (this) {
            this.handlers.add(handler);
        }
        handler.start();
    }

    protected synchronized void cleanupSocksProxyHandler(final SocksProxyHandler handler) {
        this.handlers.remove(handler);
    }

    public SocketAddress getProxyAddress() {
        return this.server.getLocalSocketAddress();
    }

}
