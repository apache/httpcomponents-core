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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.apache.hc.core5.util.Timeout;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Unit tests for {@link SSLContextBuilder}.
 */
@Isolated
class TestSSLContextBuilder {

    static final String PROVIDER_SUN_JSSE = "SunJSSE";
    static final String PROVIDER_SUN_JCE = "SunJCE";

    private static boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    private static final Timeout TIMEOUT = Timeout.ofSeconds(5);
    private ExecutorService executorService;

    @AfterEach
    void cleanup() throws Exception {
        if (this.executorService != null) {
            this.executorService.shutdown();
            this.executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private URL getResource(final String name) {
        return getClass().getResource(name);
    }

    @Test
    void testBuildAllDefaults() throws Exception {
        final SSLContext sslContext = SSLContextBuilder.create()
                .setKeyStoreType(KeyStore.getDefaultType())
                .setKeyManagerFactoryAlgorithm(KeyManagerFactory.getDefaultAlgorithm())
                .setTrustManagerFactoryAlgorithm(TrustManagerFactory.getDefaultAlgorithm())
                .setProvider(PROVIDER_SUN_JSSE)
                .setProtocol("TLS")
                .setSecureRandom(null)
                .loadTrustMaterial((KeyStore) null, null)
                .loadKeyMaterial((KeyStore) null, null, null)
                .build();
        Assertions.assertThat(sslContext).isNotNull();
        Assertions.assertThat(sslContext.getProtocol()).isEqualTo("TLS");
        Assertions.assertThat(sslContext.getProvider().getName()).isEqualTo(PROVIDER_SUN_JSSE);
    }

    @Test
    void testBuildAllNull() throws Exception {
        final SSLContext sslContext = SSLContextBuilder.create()
                .setKeyStoreType(null)
                .setKeyManagerFactoryAlgorithm(null)
                .setTrustManagerFactoryAlgorithm(null)
                .setProtocol(null)
                .setProvider((String) null)
                .setSecureRandom(null)
                .loadTrustMaterial((KeyStore) null, null)
                .loadKeyMaterial((KeyStore) null, null, null)
                .build();
        Assertions.assertThat(sslContext).isNotNull();
        Assertions.assertThat(sslContext.getProtocol()).isEqualTo("TLS");
        Assertions.assertThat(sslContext.getProvider().getName()).isEqualTo(PROVIDER_SUN_JSSE);
    }

    @Test
    void testBuildAllNull_deprecated() throws Exception {
        final SSLContext sslContext = SSLContextBuilder.create()
                .setProtocol(null)
                .setSecureRandom(null)
                .loadTrustMaterial((KeyStore) null, null)
                .loadKeyMaterial((KeyStore) null, null, null)
                .build();
        Assertions.assertThat(sslContext).isNotNull();
        Assertions.assertThat(sslContext.getProtocol()).isEqualTo("TLS");
    }

    @Test
    void testBuildDefault() {
        Assertions.assertThatCode(() -> new SSLContextBuilder().build()).doesNotThrowAnyException();
    }

    @Test
    void testBuildNoSuchKeyManagerFactoryAlgorithm() {
        final URL resource1 = getResource("/test-keypasswd.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "password";
        Assertions.assertThatThrownBy(() ->
                SSLContextBuilder.create()
                        .setKeyManagerFactoryAlgorithm(" BAD ")
                        .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                        .build()).isInstanceOf(NoSuchAlgorithmException.class);
    }

    @Test
    void testBuildNoSuchKeyStoreType() {
        final URL resource1 = getResource("/test-keypasswd.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "password";
        Assertions.assertThatThrownBy(() ->
                SSLContextBuilder.create()
                        .setKeyStoreType(" BAD ")
                        .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                        .build()).isInstanceOf(KeyStoreException.class);
    }

    @Test
    void testBuildNoSuchTrustManagerFactoryAlgorithm() {
        final URL resource1 = getResource("/test-keypasswd.p12");
        final String storePassword = "nopassword";
        Assertions.assertThatThrownBy(() ->
                SSLContextBuilder.create()
                        .setTrustManagerFactoryAlgorithm(" BAD ")
                        .loadTrustMaterial(resource1, storePassword.toCharArray())
                        .build()).isInstanceOf(NoSuchAlgorithmException.class);
    }

    @Test
    void testBuildWithProvider() throws Exception {
        final URL resource1 = getResource("/test-server.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final DummyProvider provider = new DummyProvider();
        SSLContextBuilder.create()
                .setProvider(provider)
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assertions.assertThat(provider.hasBeenRequested("SSLContext")).isTrue();
    }

    @Test
    void testBuildWithProviderName() throws Exception {

        final DummyProvider provider = new DummyProvider();
        Security.insertProviderAt(provider, 1);
        try {

            final URL resource1 = getResource("/test-server.p12");
            final String storePassword = "nopassword";
            final String keyPassword = "nopassword";
            SSLContextBuilder.create()
                    .setProvider(DummyProvider.NAME)
                    .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                    .build();
            Assertions.assertThat(provider.hasBeenRequested("SSLContext")).isTrue();

        } finally {
            Security.removeProvider(DummyProvider.NAME);
        }
    }

    @Test
    void testBuildKSWithNoSuchProvider() {
        Assertions.assertThatThrownBy(() -> SSLContextBuilder.create()
                .setKeyStoreProvider("no-such-provider")
                .build()).isInstanceOf(NoSuchProviderException.class);
    }

    @Test
    void testBuildKSWithProvider() throws Exception {
        final URL resource1 = getResource("/test-server.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final DummyProvider provider = new DummyProvider();
        SSLContextBuilder.create()
                .setKeyStoreProvider(provider)
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assertions.assertThat(provider.hasBeenRequested("KeyManagerFactory")).isTrue();
    }

    @Test
    void testBuildKSWithProviderName() throws Exception {

        final DummyProvider provider = new DummyProvider();
        Security.insertProviderAt(provider, 1);
        try {

            final URL resource1 = getResource("/test-server.p12");
            final String storePassword = "nopassword";
            final String keyPassword = "nopassword";
            SSLContextBuilder.create()
                    .setKeyStoreProvider(DummyProvider.NAME)
                    .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                    .build();
            Assertions.assertThat(provider.hasBeenRequested("KeyManagerFactory")).isTrue();

        } finally {
            Security.removeProvider(DummyProvider.NAME);
        }
    }

    @Test
    void testBuildTSWithNoSuchProvider() {
        Assertions.assertThatThrownBy(() ->
                SSLContextBuilder.create()
                        .setTrustStoreProvider("no-such-provider")
                        .build()).isInstanceOf(NoSuchProviderException.class);
    }

    @Test
    void testBuildTSWithProvider() throws Exception {
        final DummyProvider provider = new DummyProvider();
        SSLContextBuilder.create()
                .setTrustStoreProvider(provider)
                .loadTrustMaterial((KeyStore) null, null)
                .build();
        Assertions.assertThat(provider.hasBeenRequested("TrustManagerFactory")).isTrue();
    }

    @Test
    void testBuildTSWithProviderName() throws Exception {

        final DummyProvider provider = new DummyProvider();
        Security.insertProviderAt(provider, 1);
        try {

            SSLContextBuilder.create()
                    .setTrustStoreProvider(DummyProvider.NAME)
                    .loadTrustMaterial((KeyStore) null, null)
                    .build();
            Assertions.assertThat(provider.hasBeenRequested("TrustManagerFactory")).isTrue();

        } finally {
            Security.removeProvider(DummyProvider.NAME);
        }
    }


    @Test
    void testKeyWithAlternatePasswordInvalid() {
        final URL resource1 = getResource("/test-keypasswd.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "!password";
        Assertions.assertThatThrownBy(() ->
                SSLContextBuilder.create()
                        .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                        .loadTrustMaterial(resource1, storePassword.toCharArray())
                        .build()).isInstanceOf(UnrecoverableKeyException.class);
    }

    @Test
    void testSSLHandshakeServerTrusted() throws Exception {
        final URL resource1 = getResource("/test.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assertions.assertThat(serverSslContext).isNotNull();
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource1, storePassword.toCharArray())
                .build();
        Assertions.assertThat(clientSslContext).isNotNull();
        final ServerSocket serverSocket = serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        this.executorService = Executors.newSingleThreadExecutor();
        final Future<Boolean> future = this.executorService.submit(() -> {
            try (Socket socket = serverSocket.accept()) {
                final OutputStream outputStream = socket.getOutputStream();
                outputStream.write(new byte[]{'H', 'i'});
                outputStream.flush();
            }
            return Boolean.TRUE;
        });

        final int localPort = serverSocket.getLocalPort();
        try (final Socket clientSocket = clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), TIMEOUT.toMillisecondsIntBound());
            clientSocket.setSoTimeout(TIMEOUT.toMillisecondsIntBound());
            final InputStream inputStream = clientSocket.getInputStream();
            Assertions.assertThat(inputStream.read()).isEqualTo('H');
            Assertions.assertThat(inputStream.read()).isEqualTo('i');
            Assertions.assertThat(inputStream.read()).isEqualTo(-1);
        }

        final Boolean result = future.get(5, TimeUnit.SECONDS);
        Assertions.assertThat(result).isNotNull();
    }

    @Test
    void testSSLHandshakeServerNotTrusted() throws Exception {
        final URL resource1 = getResource("/test-server.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assertions.assertThat(serverSslContext).isNotNull();
        final URL resource2 = getResource("/test.p12");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .build();
        Assertions.assertThat(clientSslContext).isNotNull();
        final ServerSocket serverSocket = serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        this.executorService = Executors.newSingleThreadExecutor();
        this.executorService.submit(() -> {
            try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                socket.getSession();
            }
            return Boolean.FALSE;
        });
        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), TIMEOUT.toMillisecondsIntBound());
            clientSocket.setSoTimeout(TIMEOUT.toMillisecondsIntBound());
            Assertions.assertThatThrownBy(clientSocket::startHandshake).isInstanceOf(IOException.class);
        }
    }

    @Test
    void testSSLHandshakeServerCustomTrustStrategy() throws Exception {
        final URL resource1 = getResource("/test-server.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assertions.assertThat(serverSslContext).isNotNull();

        final AtomicReference<X509Certificate[]> certChainRef = new AtomicReference<>();

        final TrustStrategy trustStrategy = (chain, authType) -> {
            certChainRef.set(chain);
            return true;
        };

        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(trustStrategy)
                .build();

        Assertions.assertThat(clientSslContext).isNotNull();
        final ServerSocket serverSocket = serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        this.executorService = Executors.newSingleThreadExecutor();
        final Future<Boolean> future = this.executorService.submit(() -> {
            try (Socket socket = serverSocket.accept()) {
                final OutputStream outputStream = socket.getOutputStream();
                outputStream.write(new byte[]{'H', 'i'});
                outputStream.flush();
            }
            return Boolean.TRUE;
        });

        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), TIMEOUT.toMillisecondsIntBound());
            clientSocket.setSoTimeout(TIMEOUT.toMillisecondsIntBound());
            final InputStream inputStream = clientSocket.getInputStream();
            Assertions.assertThat(inputStream.read()).isEqualTo('H');
            Assertions.assertThat(inputStream.read()).isEqualTo('i');
            Assertions.assertThat(inputStream.read()).isEqualTo(-1);
        }

        final Boolean result = future.get(5, TimeUnit.SECONDS);
        Assertions.assertThat(result).isNotNull();

        final X509Certificate[] certs = certChainRef.get();
        Assertions.assertThat(certs).isNotNull();
        Assertions.assertThat(certs.length).isEqualTo(2);
        final X509Certificate cert1 = certs[0];
        final Principal subjectDN1 = cert1.getSubjectDN();
        Assertions.assertThat(subjectDN1).isNotNull();
        Assertions.assertThat(subjectDN1.getName()).isEqualTo("CN=Test Server, OU=HttpComponents Project, O=Apache Software Foundation");
        final X509Certificate cert2 = certs[1];
        final Principal subjectDN2 = cert2.getSubjectDN();
        Assertions.assertThat(subjectDN2).isNotNull();
        Assertions.assertThat(subjectDN2.getName()).isEqualTo("EMAILADDRESS=dev@hc.apache.org, " +
                "CN=Test CA, OU=HttpComponents Project, O=Apache Software Foundation");
        final Principal issuerDN = cert2.getIssuerDN();
        Assertions.assertThat(issuerDN).isNotNull();
        Assertions.assertThat(issuerDN.getName()).isEqualTo("EMAILADDRESS=dev@hc.apache.org, " +
                "CN=Test CA, OU=HttpComponents Project, O=Apache Software Foundation");

    }

    @Test
    void testSSLHandshakeClientUnauthenticated() throws Exception {
        final URL resource1 = getResource("/test-server.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assertions.assertThat(serverSslContext).isNotNull();
        final URL resource2 = getResource("/test-client.p12");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .build();
        Assertions.assertThat(clientSslContext).isNotNull();
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.setWantClientAuth(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        this.executorService = Executors.newSingleThreadExecutor();
        final Future<Principal> future = this.executorService.submit(() -> {
            Principal clientPrincipal = null;
            try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                final SSLSession session = socket.getSession();
                try {
                    clientPrincipal = session.getPeerPrincipal();
                } catch (final SSLPeerUnverifiedException ignore) {
                }
                final OutputStream outputStream = socket.getOutputStream();
                outputStream.write(new byte [] {'H', 'i'});
                outputStream.flush();
            }
            return clientPrincipal;
        });

        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), TIMEOUT.toMillisecondsIntBound());
            clientSocket.setSoTimeout(TIMEOUT.toMillisecondsIntBound());
            clientSocket.startHandshake();
            final InputStream inputStream = clientSocket.getInputStream();
            Assertions.assertThat(inputStream.read()).isEqualTo('H');
            Assertions.assertThat(inputStream.read()).isEqualTo('i');
            Assertions.assertThat(inputStream.read()).isEqualTo(-1);
        }

        final Principal clientPrincipal = future.get(5, TimeUnit.SECONDS);
        Assertions.assertThat(clientPrincipal).isNull();
    }

    @Test
    void testSSLHandshakeClientUnauthenticatedError() throws Exception {
        final URL resource1 = getResource("/test-server.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assertions.assertThat(serverSslContext).isNotNull();
        final URL resource2 = getResource("/test-client.p12");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .build();
        Assertions.assertThat(clientSslContext).isNotNull();
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.setNeedClientAuth(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        this.executorService = Executors.newSingleThreadExecutor();
        this.executorService.submit(() -> {
            try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                socket.getSession();
            }
            return Boolean.FALSE;
        });

        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), TIMEOUT.toMillisecondsIntBound());
            clientSocket.setSoTimeout(TIMEOUT.toMillisecondsIntBound());
            Assertions.assertThatThrownBy(() -> {
                clientSocket.startHandshake();
                final InputStream inputStream = clientSocket.getInputStream();
                inputStream.read();
            }).isInstanceOf(IOException.class);
        }
    }

    @Test
    void testSSLHandshakeClientAuthenticated() throws Exception {
        final URL resource1 = getResource("/test-server.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource1, storePassword.toCharArray())
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assertions.assertThat(serverSslContext).isNotNull();
        final URL resource2 = getResource("/test-client.p12");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .loadKeyMaterial(resource2, storePassword.toCharArray(), storePassword.toCharArray())
                .build();
        Assertions.assertThat(clientSslContext).isNotNull();
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.setNeedClientAuth(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        this.executorService = Executors.newSingleThreadExecutor();
        final Future<Principal> future = this.executorService.submit(() -> {
            try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                final SSLSession session = socket.getSession();
                final Principal clientPrincipal = session.getPeerPrincipal();
                final OutputStream outputStream = socket.getOutputStream();
                outputStream.write(new byte[]{'H', 'i'});
                outputStream.flush();
                return clientPrincipal;
            }
        });
        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), TIMEOUT.toMillisecondsIntBound());
            clientSocket.setSoTimeout(TIMEOUT.toMillisecondsIntBound());
            clientSocket.startHandshake();
            final InputStream inputStream = clientSocket.getInputStream();
            Assertions.assertThat(inputStream.read()).isEqualTo('H');
            Assertions.assertThat(inputStream.read()).isEqualTo('i');
            Assertions.assertThat(inputStream.read()).isEqualTo(-1);
        }

        final Principal clientPrincipal = future.get(5, TimeUnit.SECONDS);
        Assertions.assertThat(clientPrincipal).isNotNull();
    }

    @Test
    void testSSLHandshakeClientAuthenticatedPrivateKeyStrategy() throws Exception {
        final URL resource1 = getResource("/test-server.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource1, storePassword.toCharArray())
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assertions.assertThat(serverSslContext).isNotNull();

        final PrivateKeyStrategy privateKeyStrategy = (aliases, sslParameters) -> aliases.containsKey("client2") ? "client2" : null;

        final URL resource2 = getResource("/test-client.p12");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .loadKeyMaterial(resource2, storePassword.toCharArray(), storePassword.toCharArray(), privateKeyStrategy)
                .build();
        Assertions.assertThat(clientSslContext).isNotNull();
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.setNeedClientAuth(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        this.executorService = Executors.newSingleThreadExecutor();
        final Future<Principal> future = this.executorService.submit(() -> {
            try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                final SSLSession session = socket.getSession();
                final Principal clientPrincipal = session.getPeerPrincipal();
                final OutputStream outputStream = socket.getOutputStream();
                outputStream.write(new byte[]{'H', 'i'});
                outputStream.flush();
                return clientPrincipal;
            }
        });
        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort), TIMEOUT.toMillisecondsIntBound());
            clientSocket.setSoTimeout(TIMEOUT.toMillisecondsIntBound());
            clientSocket.startHandshake();
            final InputStream inputStream = clientSocket.getInputStream();
            Assertions.assertThat(inputStream.read()).isEqualTo('H');
            Assertions.assertThat(inputStream.read()).isEqualTo('i');
            Assertions.assertThat(inputStream.read()).isEqualTo(-1);
        }

        final Principal clientPrincipal = future.get(5, TimeUnit.SECONDS);
        Assertions.assertThat(clientPrincipal).isNotNull();
        Assertions.assertThat(clientPrincipal.getName()).isEqualTo("CN=Test Client 2,OU=HttpComponents Project,O=Apache Software Foundation");
    }


    @Test
    void testSSLHandshakeProtocolMismatch1() throws Exception {
        final URL resource1 = getResource("/test-server.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assertions.assertThat(serverSslContext).isNotNull();
        final URL resource2 = getResource("/test-client.p12");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .build();
        Assertions.assertThat(clientSslContext).isNotNull();
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        final Set<String> supportedServerProtocols = new LinkedHashSet<>(Arrays.asList(serverSocket.getSupportedProtocols()));
        Assertions.assertThat(supportedServerProtocols.contains("TLSv1")).isTrue();
        serverSocket.setEnabledProtocols(new String[] {"TLSv1"});
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        this.executorService = Executors.newSingleThreadExecutor();
        this.executorService.submit(() -> {
            try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                socket.getSession();
            }
            return Boolean.FALSE;
        });

        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            final Set<String> supportedClientProtocols = new LinkedHashSet<>(Arrays.asList(clientSocket.getSupportedProtocols()));
            Assertions.assertThat(supportedClientProtocols.contains("SSLv3")).isTrue();
            clientSocket.setEnabledProtocols(new String[] {"SSLv3"} );
            clientSocket.connect(new InetSocketAddress("localhost", localPort), TIMEOUT.toMillisecondsIntBound());
            clientSocket.setSoTimeout(TIMEOUT.toMillisecondsIntBound());
            if (isWindows()) {
                Assertions.assertThatThrownBy(clientSocket::startHandshake).isInstanceOf(IOException.class);
            } else {
                Assertions.assertThatThrownBy(clientSocket::startHandshake).isInstanceOf(SSLException.class);
            }
        }
    }

    @Test
    void testSSLHandshakeProtocolMismatch2() throws Exception {
        final URL resource1 = getResource("/test-server.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assertions.assertThat(serverSslContext).isNotNull();
        final URL resource2 = getResource("/test-client.p12");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .build();
        Assertions.assertThat(clientSslContext).isNotNull();
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        final Set<String> supportedServerProtocols = new LinkedHashSet<>(Arrays.asList(serverSocket.getSupportedProtocols()));
        Assertions.assertThat(supportedServerProtocols.contains("SSLv3")).isTrue();
        serverSocket.setEnabledProtocols(new String[] {"SSLv3"});
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        this.executorService = Executors.newSingleThreadExecutor();
        this.executorService.submit(() -> {
            try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                socket.getSession();
            }
            return Boolean.FALSE;
        });

        final int localPort = serverSocket.getLocalPort();
        try (final SSLSocket clientSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket()) {
            final Set<String> supportedClientProtocols = new LinkedHashSet<>(
                    Arrays.asList(clientSocket.getSupportedProtocols()));
            Assertions.assertThat(supportedClientProtocols.contains("TLSv1")).isTrue();
            clientSocket.setEnabledProtocols(new String[]{"TLSv1"});
            clientSocket.connect(new InetSocketAddress("localhost", localPort), TIMEOUT.toMillisecondsIntBound());
            clientSocket.setSoTimeout(TIMEOUT.toMillisecondsIntBound());
            if (isWindows()) {
                Assertions.assertThatThrownBy(clientSocket::startHandshake).isInstanceOf(IOException.class);
            } else {
                Assertions.assertThatThrownBy(clientSocket::startHandshake).isInstanceOf(SSLException.class);
            }
        }
    }

    @Test
    void testJSSEEndpointIdentification() throws Exception {
        final URL resource1 = getResource("/test-server.p12");
        final String storePassword = "nopassword";
        final String keyPassword = "nopassword";
        final SSLContext serverSslContext = SSLContextBuilder.create()
                .loadKeyMaterial(resource1, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
        Assertions.assertThat(serverSslContext).isNotNull();
        final URL resource2 = getResource("/test-client.p12");
        final SSLContext clientSslContext = SSLContextBuilder.create()
                .loadTrustMaterial(resource2, storePassword.toCharArray())
                .build();
        Assertions.assertThat(clientSslContext).isNotNull();
        final SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory().createServerSocket();
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        this.executorService = Executors.newSingleThreadExecutor();
        this.executorService.submit(() -> {
            for (;;) {
                try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                    socket.getSession();
                    socket.shutdownOutput();
                } catch (final IOException ex) {
                    return Boolean.FALSE;
                }
            }
        });

        final int localPort1 = serverSocket.getLocalPort();
        try (final Socket clientSocket = new Socket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort1));
            try (SSLSocket sslSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket(clientSocket, "localhost", -1, true)) {
                final SSLParameters sslParameters = sslSocket.getSSLParameters();
                sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                sslSocket.setSSLParameters(sslParameters);
                sslSocket.startHandshake();
            }
        }
        final int localPort2 = serverSocket.getLocalPort();
        try (final Socket clientSocket = new Socket()) {
            clientSocket.connect(new InetSocketAddress("localhost", localPort2));
            try (SSLSocket sslSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket(clientSocket, "otherhost", -1, true)) {
                final SSLParameters sslParameters = sslSocket.getSSLParameters();
                sslParameters.setEndpointIdentificationAlgorithm(null);
                sslSocket.setSSLParameters(sslParameters);
                sslSocket.startHandshake();
            }
        }
        final int localPort3 = serverSocket.getLocalPort();

        Assertions.assertThatThrownBy(() -> {
            try (final Socket clientSocket = new Socket()) {
                clientSocket.connect(new InetSocketAddress("localhost", localPort3));
                try (SSLSocket sslSocket = (SSLSocket) clientSslContext.getSocketFactory().createSocket(clientSocket, "otherhost", -1, true)) {
                    final SSLParameters sslParameters = sslSocket.getSSLParameters();
                    sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                    sslSocket.setSSLParameters(sslParameters);
                    sslSocket.startHandshake();
                }
            }
        }).isInstanceOf(SSLException.class);
    }

}
