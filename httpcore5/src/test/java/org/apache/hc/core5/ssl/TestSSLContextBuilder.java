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

package org.apache.hc.core5.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.security.KeyStore;
import java.security.Principal;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Unit tests for {@link SSLContextBuilder}.
 */
public class TestSSLContextBuilder {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private ExecutorService executorService;

    @After
    public void cleanup() throws Exception {
        if (this.executorService != null) {
            this.executorService.shutdown();
            this.executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }


    @Test
    public void testBuildDefault() throws Exception {
        new SSLContextBuilder().build();
    }

    @Test
    public void testBuildAllNull() throws Exception {
        final SSLContext sslContext = SSLContextBuilder.create()
                .setProtocol(null)
                .setSecureRandom(null)
                .loadTrustMaterial((KeyStore) null, null)
                .loadKeyMaterial((KeyStore) null, null, null)
                .build();
        Assert.assertNotNull(sslContext);
        Assert.assertEquals("TLS", sslContext.getProtocol());
    }

    @Test
    public void testKeyWithAlternatePassword() throws Exception {
        final URL resource1 = getClass().getResource("/test-keypasswd.keystore");
        final String storePassword = "nopassword";
        final String keyPassword = "password";
        final SSLContext sslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .loadTrustMaterial(resource1, storePassword.toCharArray())
                .build();
        Assert.assertNotNull(sslContext);
        final SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        Assert.assertNotNull(socketFactory);
    }

    @Test
    public void testKeyWithAlternatePasswordInvalid() throws Exception {

        thrown.expect(UnrecoverableKeyException.class);

        final URL resource1 = getClass().getResource("/test-keypasswd.keystore");
        final String storePassword = "nopassword";
        final String keyPassword = "!password";
        SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .loadTrustMaterial(resource1, storePassword.toCharArray())
                .build();
    }

    @Test
    public void testSSLHanskshakeServerTrusted() throws Exception {
        final URL resource1 = getClass().getResource("/test.keystore");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assert.assertNotNull(serverSslContext);
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource1, storePassword.toCharArray())
                .build();
        Assert.assertNotNull(clientSslContext);
        final ServerSocket serverSocket = serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.bind(new InetSocketAddress(0));

        this.executorService = Executors.newSingleThreadExecutor();
        final Future<Boolean> future = this.executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final Socket socket = serverSocket.accept();
                try {
                    final OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(new byte[]{'H', 'i'});
                    outputStream.flush();
                } finally {
                    socket.close();
                }
                return Boolean.TRUE;
            }
        });

        final int localPort = serverSocket.getLocalPort();
        try (final Socket clientSocket = clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), 5000);
            final InputStream inputStream = clientSocket.getInputStream();
            Assert.assertEquals('H', inputStream.read());
            Assert.assertEquals('i', inputStream.read());
            Assert.assertEquals(-1, inputStream.read());
        }

        final Boolean result = future.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result);
    }

    @Test
    public void testSSLHanskshakeServerNotTrusted() throws Exception {

        thrown.expect(SSLHandshakeException.class);

        final URL resource1 = getClass().getResource("/test-server.keystore");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assert.assertNotNull(serverSslContext);
        final URL resource2 = getClass().getResource("/test.keystore");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .build();
        Assert.assertNotNull(clientSslContext);
        final ServerSocket serverSocket = serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.bind(new InetSocketAddress(0));

        this.executorService = Executors.newSingleThreadExecutor();
        this.executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final SSLSocket socket = (SSLSocket) serverSocket.accept();
                try {
                    socket.getSession();
                } finally {
                    socket.close();
                }
                return Boolean.FALSE;
            }
        });
        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), 5000);
            clientSocket.startHandshake();
        }
    }

    @Test
    public void testSSLHanskshakeServerCustomTrustStrategy() throws Exception {
        final URL resource1 = getClass().getResource("/test-server.keystore");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assert.assertNotNull(serverSslContext);

        final AtomicReference<X509Certificate[]> certChainRef = new AtomicReference<>();

        final TrustStrategy trustStrategy = new TrustStrategy() {

            @Override
            public boolean isTrusted(
                    final X509Certificate[] chain, final String authType) throws CertificateException {
                certChainRef.set(chain);
                return true;
            }

        };

        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(trustStrategy)
                .build();

        Assert.assertNotNull(clientSslContext);
        final ServerSocket serverSocket = serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.bind(new InetSocketAddress(0));

        this.executorService = Executors.newSingleThreadExecutor();
        final Future<Boolean> future = this.executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final Socket socket = serverSocket.accept();
                try {
                    final OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(new byte[]{'H', 'i'});
                    outputStream.flush();
                } finally {
                    socket.close();
                }
                return Boolean.TRUE;
            }
        });

        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), 5000);
            final InputStream inputStream = clientSocket.getInputStream();
            Assert.assertEquals('H', inputStream.read());
            Assert.assertEquals('i', inputStream.read());
            Assert.assertEquals(-1, inputStream.read());
        }

        final Boolean result = future.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result);

        final X509Certificate[] certs = certChainRef.get();
        Assert.assertNotNull(certs);
        Assert.assertEquals(2, certs.length);
        final X509Certificate cert1 = certs[0];
        final Principal subjectDN1 = cert1.getSubjectDN();
        Assert.assertNotNull(subjectDN1);
        Assert.assertEquals("CN=Test Server, OU=HttpComponents Project, O=Apache Software Foundation, " +
                "L=Unknown, ST=Unknown, C=Unknown", subjectDN1.getName());
        final X509Certificate cert2 = certs[1];
        final Principal subjectDN2 = cert2.getSubjectDN();
        Assert.assertNotNull(subjectDN2);
        Assert.assertEquals("EMAILADDRESS=dev@hc.apache.org, " +
                "CN=Test CA, OU=HttpComponents Project, O=Apache Software Foundation", subjectDN2.getName());
        final Principal issuerDN = cert2.getIssuerDN();
        Assert.assertNotNull(issuerDN);
        Assert.assertEquals("EMAILADDRESS=dev@hc.apache.org, " +
                "CN=Test CA, OU=HttpComponents Project, O=Apache Software Foundation", issuerDN.getName());

    }

    @Test
    public void testSSLHanskshakeClientUnauthenticated() throws Exception {
        final URL resource1 = getClass().getResource("/test-server.keystore");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assert.assertNotNull(serverSslContext);
        final URL resource2 = getClass().getResource("/test-client.keystore");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .build();
        Assert.assertNotNull(clientSslContext);
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.setWantClientAuth(true);
        serverSocket.bind(new InetSocketAddress(0));

        this.executorService = Executors.newSingleThreadExecutor();
        final Future<Principal> future = this.executorService.submit(new Callable<Principal>() {
            @Override
            public Principal call() throws Exception {
                final SSLSocket socket = (SSLSocket) serverSocket.accept();
                Principal clientPrincipal = null;
                try {
                    final SSLSession session = socket.getSession();
                    try {
                        clientPrincipal = session.getPeerPrincipal();
                    } catch (final SSLPeerUnverifiedException ignore) {
                    }
                    final OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(new byte [] {'H', 'i'});
                    outputStream.flush();
                } finally {
                    socket.close();
                }
                return clientPrincipal;
            }
        });

        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), 5000);
            clientSocket.startHandshake();
            final InputStream inputStream = clientSocket.getInputStream();
            Assert.assertEquals('H', inputStream.read());
            Assert.assertEquals('i', inputStream.read());
            Assert.assertEquals(-1, inputStream.read());
        }

        final Principal clientPrincipal = future.get(5, TimeUnit.SECONDS);
        Assert.assertNull(clientPrincipal);
    }

    @Test
    public void testSSLHanskshakeClientUnauthenticatedError() throws Exception {

        thrown.expect(IOException.class);

        final URL resource1 = getClass().getResource("/test-server.keystore");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assert.assertNotNull(serverSslContext);
        final URL resource2 = getClass().getResource("/test-client.keystore");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .build();
        Assert.assertNotNull(clientSslContext);
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.setNeedClientAuth(true);
        serverSocket.bind(new InetSocketAddress(0));

        this.executorService = Executors.newSingleThreadExecutor();
        this.executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final SSLSocket socket = (SSLSocket) serverSocket.accept();
                try {
                    socket.getSession();
                } finally {
                    socket.close();
                }
                return Boolean.FALSE;
            }
        });

        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), 5000);
            clientSocket.startHandshake();
        }
    }

    @Test
    public void testSSLHanskshakeClientAuthenticated() throws Exception {
        final URL resource1 = getClass().getResource("/test-server.keystore");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource1, storePassword.toCharArray())
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assert.assertNotNull(serverSslContext);
        final URL resource2 = getClass().getResource("/test-client.keystore");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .loadKeyMaterial(resource2, storePassword.toCharArray(), storePassword.toCharArray())
                .build();
        Assert.assertNotNull(clientSslContext);
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.setNeedClientAuth(true);
        serverSocket.bind(new InetSocketAddress(0));

        this.executorService = Executors.newSingleThreadExecutor();
        final Future<Principal> future = this.executorService.submit(new Callable<Principal>() {
            @Override
            public Principal call() throws Exception {
                final SSLSocket socket = (SSLSocket) serverSocket.accept();
                try {
                    final SSLSession session = socket.getSession();
                    final Principal clientPrincipal = session.getPeerPrincipal();
                    final OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(new byte[]{'H', 'i'});
                    outputStream.flush();
                    return clientPrincipal;
                } finally {
                    socket.close();
                }
            }
        });
        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), 5000);
            clientSocket.startHandshake();
            final InputStream inputStream = clientSocket.getInputStream();
            Assert.assertEquals('H', inputStream.read());
            Assert.assertEquals('i', inputStream.read());
            Assert.assertEquals(-1, inputStream.read());
        }

        final Principal clientPrincipal = future.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(clientPrincipal);
    }

    @Test
    public void testSSLHanskshakeClientAuthenticatedPrivateKeyStrategy() throws Exception {
        final URL resource1 = getClass().getResource("/test-server.keystore");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource1, storePassword.toCharArray())
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assert.assertNotNull(serverSslContext);

        final PrivateKeyStrategy privateKeyStrategy = new PrivateKeyStrategy() {
            @Override
            public String chooseAlias(final Map<String, PrivateKeyDetails> aliases, final SSLParameters sslParameters) {
                if (aliases.keySet().contains("client2")) {
                    return "client2";
                } else {
                    return null;
                }
            }
        };

        final URL resource2 = getClass().getResource("/test-client.keystore");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .loadKeyMaterial(resource2, storePassword.toCharArray(), storePassword.toCharArray(), privateKeyStrategy)
                .build();
        Assert.assertNotNull(clientSslContext);
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.setNeedClientAuth(true);
        serverSocket.bind(new InetSocketAddress(0));

        this.executorService = Executors.newSingleThreadExecutor();
        final Future<Principal> future = this.executorService.submit(new Callable<Principal>() {
            @Override
            public Principal call() throws Exception {
                final SSLSocket socket = (SSLSocket) serverSocket.accept();
                try {
                    final SSLSession session = socket.getSession();
                    final Principal clientPrincipal = session.getPeerPrincipal();
                    final OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(new byte[]{'H', 'i'});
                    outputStream.flush();
                    return clientPrincipal;
                } finally {
                    socket.close();
                }
            }
        });
        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), 5000);
            clientSocket.startHandshake();
            final InputStream inputStream = clientSocket.getInputStream();
            Assert.assertEquals('H', inputStream.read());
            Assert.assertEquals('i', inputStream.read());
            Assert.assertEquals(-1, inputStream.read());
        }

        final Principal clientPrincipal = future.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(clientPrincipal);
        Assert.assertEquals("CN=Test Client 2,OU=HttpComponents Project,O=Apache Software Foundation," +
                "L=Unknown,ST=Unknown,C=Unknown", clientPrincipal.getName());
    }


    @Test
    public void testSSLHanskshakeProtocolMismatch1() throws Exception {

        thrown.expect(IOException.class);

        final URL resource1 = getClass().getResource("/test-server.keystore");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assert.assertNotNull(serverSslContext);
        final URL resource2 = getClass().getResource("/test-client.keystore");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .build();
        Assert.assertNotNull(clientSslContext);
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        final Set<String> supportedServerProtocols = new LinkedHashSet<>(Arrays.asList(serverSocket.getSupportedProtocols()));
        Assert.assertTrue(supportedServerProtocols.contains("TLSv1"));
        serverSocket.setEnabledProtocols(new String[] {"TLSv1"});
        serverSocket.bind(new InetSocketAddress(0));

        this.executorService = Executors.newSingleThreadExecutor();
        this.executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final SSLSocket socket = (SSLSocket) serverSocket.accept();
                try {
                    socket.getSession();
                } finally {
                    socket.close();
                }
                return Boolean.FALSE;
            }
        });

        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            final Set<String> supportedClientProtocols = new LinkedHashSet<>(Arrays.asList(clientSocket.getSupportedProtocols()));
            Assert.assertTrue(supportedClientProtocols.contains("SSLv3"));
            clientSocket.setEnabledProtocols(new String[] {"SSLv3"} );
            clientSocket.connect(new InetSocketAddress("localhost", localPort), 5000);
            clientSocket.startHandshake();
        }
    }

    @Test
    public void testSSLHanskshakeProtocolMismatch2() throws Exception {

        final double javaVersion = Double.parseDouble(System.getProperty("java.specification.version"));
        final boolean isWindows = System.getProperty("os.name").contains("Windows");
        if (isWindows && javaVersion < 1.8) {
            thrown.expect(IOException.class);
        } else if (isWindows && javaVersion < 10) {
            thrown.expect(SocketException.class);
        } else {
            thrown.expect(SSLHandshakeException.class);
        }

        final URL resource1 = getClass().getResource("/test-server.keystore");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assert.assertNotNull(serverSslContext);
        final URL resource2 = getClass().getResource("/test-client.keystore");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .build();
        Assert.assertNotNull(clientSslContext);
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        final Set<String> supportedServerProtocols = new LinkedHashSet<>(Arrays.asList(serverSocket.getSupportedProtocols()));
        Assert.assertTrue(supportedServerProtocols.contains("SSLv3"));
        serverSocket.setEnabledProtocols(new String[] {"SSLv3"});
        serverSocket.bind(new InetSocketAddress(0));

        this.executorService = Executors.newSingleThreadExecutor();
        this.executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final SSLSocket socket = (SSLSocket) serverSocket.accept();
                try {
                    socket.getSession();
                } finally {
                    socket.close();
                }
                return Boolean.FALSE;
            }
        });

        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            final Set<String> supportedClientProtocols = new LinkedHashSet<>(
                    Arrays.asList(clientSocket.getSupportedProtocols()));
            Assert.assertTrue(supportedClientProtocols.contains("TLSv1"));
            clientSocket.setEnabledProtocols(new String[] { "TLSv1" });
            clientSocket.connect(new InetSocketAddress("localhost", localPort), 5000);
            clientSocket.startHandshake();
        }
    }

    @Test
    public void testBuildWithProvider() throws Exception {
        final URL resource1 = getClass().getResource("/test-server.keystore");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext sslContext=SSLContextBuilder.create()
                .setProvider(Security.getProvider("SunJSSE"))
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assert.assertTrue(sslContext.getProvider().getName().equals("SunJSSE"));
    }

}
