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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import org.apache.hc.core5.util.Args;

/**
 * Builder for {@link javax.net.ssl.SSLContext} instances.
 * <p>
 * Please note: the default Oracle JSSE implementation of {@link SSLContext#init(KeyManager[], TrustManager[], SecureRandom)}
 * accepts multiple key and trust managers, however only only first matching type is ever used.
 * See for example:
 * <a href="http://docs.oracle.com/javase/7/docs/api/javax/net/ssl/SSLContext.html#init%28javax.net.ssl.KeyManager[],%20javax.net.ssl.TrustManager[],%20java.security.SecureRandom%29">
 * SSLContext.html#init
 * </a>
 *
 * @since 4.4
 */
public class SSLContextBuilder {

    static final String TLS   = "TLS";

    private String protocol;
    private final Set<KeyManager> keyManagers;
    private String keyManagerFactoryAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
    private String keyStoreType = KeyStore.getDefaultType();
    private final Set<TrustManager> trustManagers;
    private String trustManagerFactoryAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
    private SecureRandom secureRandom;
    private Provider provider;
    private Provider tsProvider;
    private Provider ksProvider;

    /**
     * An empty immutable {@code KeyManager} array.
     */
    private static final KeyManager[] EMPTY_KEY_MANAGER_ARRAY = new KeyManager[0];

    /**
     * An empty immutable {@code TrustManager} array.
     */
    private static final TrustManager[] EMPTY_TRUST_MANAGER_ARRAY = new TrustManager[0];



    public static SSLContextBuilder create() {
        return new SSLContextBuilder();
    }

    public SSLContextBuilder() {
        super();
        this.keyManagers = new LinkedHashSet<>();
        this.trustManagers = new LinkedHashSet<>();
    }

    /**
     * Sets the SSLContext algorithm name.
     *
     * @param protocol
     *            the SSLContext algorithm name of the requested protocol. See
     *            the SSLContext section in the <a href=
     *            "https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#SSLContext">Java
     *            Cryptography Architecture Standard Algorithm Name
     *            Documentation</a> for more information.
     * @return this builder
     * @see <a href=
     *      "https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#SSLContext">Java
     *      Cryptography Architecture Standard Algorithm Name Documentation</a>
     */
    public SSLContextBuilder setProtocol(final String protocol) {
        this.protocol = protocol;
        return this;
    }

    public SSLContextBuilder setProvider(final Provider provider) {
        this.provider = provider;
        return this;
    }

    public SSLContextBuilder setProvider(final String name) {
        this.provider = Security.getProvider(name);
        return this;
    }

    /**
     * Sets the JCA provider to use for creating trust stores.
     * @param provider provider to use for creating trust stores.
     * @return this builder
     * @since 5.2
     */
    public SSLContextBuilder setTrustStoreProvider(final Provider provider) {
        this.tsProvider = provider;
        return this;
    }

    /**
     * Sets the JCA provider name to use for creating trust stores.
     * @param name Name of the provider to use for creating trust stores, the provider must be registered with the JCA.
     * @return this builder
     * @since 5.2
     */
    public SSLContextBuilder setTrustStoreProvider(final String name) throws NoSuchProviderException {
        this.tsProvider = Security.getProvider(name);
        if (this.tsProvider == null) {
            throw new NoSuchProviderException(name);
        }
        return this;
    }

    /**
     * Sets the JCA provider to use for creating key stores.
     * @param provider provider to use for creating key stores.
     * @return this builder
     * @since 5.2
     */
    public SSLContextBuilder setKeyStoreProvider(final Provider provider) {
        this.ksProvider = provider;
        return this;
    }

    /**
     * Sets the JCA provider name to use for creating key stores.
     * @param name Name of the provider to use for creating key stores, the provider must be registered with the JCA.
     * @return this builder
     * @since 5.2
     */
    public SSLContextBuilder setKeyStoreProvider(final String name) throws NoSuchProviderException {
        this.ksProvider = Security.getProvider(name);
        if (this.ksProvider == null) {
            throw new NoSuchProviderException(name);
        }
        return this;
    }

    /**
     * Sets the key store type.
     *
     * @param keyStoreType
     *            the SSLkey store type. See
     *            the KeyStore section in the <a href=
     *            "https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#KeyStore">Java
     *            Cryptography Architecture Standard Algorithm Name
     *            Documentation</a> for more information.
     * @return this builder
     * @see <a href=
     *      "https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#KeyStore">Java
     *      Cryptography Architecture Standard Algorithm Name Documentation</a>
     * @since 4.4.7
     */
    public SSLContextBuilder setKeyStoreType(final String keyStoreType) {
        this.keyStoreType = keyStoreType;
        return this;
    }

    /**
     * Sets the key manager factory algorithm name.
     *
     * @param keyManagerFactoryAlgorithm
     *            the key manager factory algorithm name of the requested protocol. See
     *            the KeyManagerFactory section in the <a href=
     *            "https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#KeyManagerFactory">Java
     *            Cryptography Architecture Standard Algorithm Name
     *            Documentation</a> for more information.
     * @return this builder
     * @see <a href=
     *      "https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#KeyManagerFactory">Java
     *      Cryptography Architecture Standard Algorithm Name Documentation</a>
     * @since 4.4.7
     */
    public SSLContextBuilder setKeyManagerFactoryAlgorithm(final String keyManagerFactoryAlgorithm) {
        this.keyManagerFactoryAlgorithm = keyManagerFactoryAlgorithm;
        return this;
    }

    /**
     * Sets the trust manager factory algorithm name.
     *
     * @param trustManagerFactoryAlgorithm
     *            the trust manager algorithm name of the requested protocol. See
     *            the TrustManagerFactory section in the <a href=
     *            "https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#TrustManagerFactory">Java
     *            Cryptography Architecture Standard Algorithm Name
     *            Documentation</a> for more information.
     * @return this builder
     * @see <a href=
     *      "https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#TrustManagerFactory">Java
     *      Cryptography Architecture Standard Algorithm Name Documentation</a>
     * @since 4.4.7
     */
    public SSLContextBuilder setTrustManagerFactoryAlgorithm(final String trustManagerFactoryAlgorithm) {
        this.trustManagerFactoryAlgorithm = trustManagerFactoryAlgorithm;
        return this;
    }

    public SSLContextBuilder setSecureRandom(final SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
        return this;
    }

    public SSLContextBuilder loadTrustMaterial(
            final KeyStore truststore,
            final TrustStrategy trustStrategy) throws NoSuchAlgorithmException, KeyStoreException {

        final String alg = trustManagerFactoryAlgorithm == null ?
                TrustManagerFactory.getDefaultAlgorithm() : trustManagerFactoryAlgorithm;

        final TrustManagerFactory tmFactory = tsProvider == null ?
                TrustManagerFactory.getInstance(alg) : TrustManagerFactory.getInstance(alg, tsProvider);

        tmFactory.init(truststore);
        final TrustManager[] tms = tmFactory.getTrustManagers();
        if (tms != null) {
            if (trustStrategy != null) {
                for (int i = 0; i < tms.length; i++) {
                    final TrustManager tm = tms[i];
                    if (tm instanceof X509TrustManager) {
                        tms[i] = new TrustManagerDelegate(
                                (X509TrustManager) tm, trustStrategy);
                    }
                }
            }
            Collections.addAll(this.trustManagers, tms);
        }
        return this;
    }

    public SSLContextBuilder loadTrustMaterial(
            final TrustStrategy trustStrategy) throws NoSuchAlgorithmException, KeyStoreException {
        return loadTrustMaterial(null, trustStrategy);
    }

    public SSLContextBuilder loadTrustMaterial(
            final File file,
            final char[] storePassword,
            final TrustStrategy trustStrategy) throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        Args.notNull(file, "Truststore file");
        final KeyStore trustStore = KeyStore.getInstance(keyStoreType);
        try (final FileInputStream inStream = new FileInputStream(file)) {
            trustStore.load(inStream, storePassword);
        }
        return loadTrustMaterial(trustStore, trustStrategy);
    }

    public SSLContextBuilder loadTrustMaterial(
            final File file,
            final char[] storePassword) throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        return loadTrustMaterial(file, storePassword, null);
    }

    public SSLContextBuilder loadTrustMaterial(
            final File file) throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        return loadTrustMaterial(file, null);
    }

    public SSLContextBuilder loadTrustMaterial(
            final URL url,
            final char[] storePassword,
            final TrustStrategy trustStrategy) throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        Args.notNull(url, "Truststore URL");
        final KeyStore trustStore = KeyStore.getInstance(keyStoreType);
        try (final InputStream inStream = url.openStream()) {
            trustStore.load(inStream, storePassword);
        }
        return loadTrustMaterial(trustStore, trustStrategy);
    }

    public SSLContextBuilder loadTrustMaterial(
            final URL url,
            final char[] storePassword) throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        return loadTrustMaterial(url, storePassword, null);
    }

    public SSLContextBuilder loadKeyMaterial(
            final KeyStore keystore,
            final char[] keyPassword,
            final PrivateKeyStrategy aliasStrategy)
            throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {

        final String alg = keyManagerFactoryAlgorithm == null ?
                KeyManagerFactory.getDefaultAlgorithm() : keyManagerFactoryAlgorithm;

        final KeyManagerFactory kmFactory = ksProvider == null ?
                KeyManagerFactory.getInstance(alg) : KeyManagerFactory.getInstance(alg, ksProvider);

        kmFactory.init(keystore, keyPassword);
        final KeyManager[] kms = kmFactory.getKeyManagers();
        if (kms != null) {
            if (aliasStrategy != null) {
                for (int i = 0; i < kms.length; i++) {
                    final KeyManager km = kms[i];
                    if (km instanceof X509ExtendedKeyManager) {
                        kms[i] = new KeyManagerDelegate((X509ExtendedKeyManager) km, aliasStrategy);
                    }
                }
            }
            Collections.addAll(keyManagers, kms);
        }
        return this;
    }

    public SSLContextBuilder loadKeyMaterial(
            final KeyStore keystore,
            final char[] keyPassword) throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
        return loadKeyMaterial(keystore, keyPassword, null);
    }

    public SSLContextBuilder loadKeyMaterial(
            final File file,
            final char[] storePassword,
            final char[] keyPassword,
            final PrivateKeyStrategy aliasStrategy) throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException, CertificateException, IOException {
        Args.notNull(file, "Keystore file");
        final KeyStore identityStore = KeyStore.getInstance(keyStoreType);
        try (final FileInputStream inStream = new FileInputStream(file)) {
            identityStore.load(inStream, storePassword);
        }
        return loadKeyMaterial(identityStore, keyPassword, aliasStrategy);
    }

    public SSLContextBuilder loadKeyMaterial(
            final File file,
            final char[] storePassword,
            final char[] keyPassword) throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException, CertificateException, IOException {
        return loadKeyMaterial(file, storePassword, keyPassword, null);
    }

    public SSLContextBuilder loadKeyMaterial(
            final URL url,
            final char[] storePassword,
            final char[] keyPassword,
            final PrivateKeyStrategy aliasStrategy) throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException, CertificateException, IOException {
        Args.notNull(url, "Keystore URL");
        final KeyStore identityStore = KeyStore.getInstance(keyStoreType);
        try (final InputStream inStream = url.openStream()) {
            identityStore.load(inStream, storePassword);
        }
        return loadKeyMaterial(identityStore, keyPassword, aliasStrategy);
    }

    public SSLContextBuilder loadKeyMaterial(
            final URL url,
            final char[] storePassword,
            final char[] keyPassword) throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException, CertificateException, IOException {
        return loadKeyMaterial(url, storePassword, keyPassword, null);
    }

    protected void initSSLContext(
            final SSLContext sslContext,
            final Collection<KeyManager> keyManagers,
            final Collection<TrustManager> trustManagers,
            final SecureRandom secureRandom) throws KeyManagementException {
        sslContext.init(
                !keyManagers.isEmpty() ? keyManagers.toArray(EMPTY_KEY_MANAGER_ARRAY) : null,
                !trustManagers.isEmpty() ? trustManagers.toArray(EMPTY_TRUST_MANAGER_ARRAY) : null,
                secureRandom);
    }

    public SSLContext build() throws NoSuchAlgorithmException, KeyManagementException {
        final SSLContext sslContext;
        final String protocolStr = this.protocol != null ? this.protocol : TLS;
        if (this.provider != null) {
            sslContext = SSLContext.getInstance(protocolStr, this.provider);
        } else {
            sslContext = SSLContext.getInstance(protocolStr);
        }
        initSSLContext(sslContext, keyManagers, trustManagers, secureRandom);
        return sslContext;
    }

    static class TrustManagerDelegate implements X509TrustManager {

        private final X509TrustManager trustManager;
        private final TrustStrategy trustStrategy;

        TrustManagerDelegate(final X509TrustManager trustManager, final TrustStrategy trustStrategy) {
            super();
            this.trustManager = trustManager;
            this.trustStrategy = trustStrategy;
        }

        @Override
        public void checkClientTrusted(
                final X509Certificate[] chain, final String authType) throws CertificateException {
            this.trustManager.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(
                final X509Certificate[] chain, final String authType) throws CertificateException {
            if (!this.trustStrategy.isTrusted(chain, authType)) {
                this.trustManager.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return this.trustManager.getAcceptedIssuers();
        }

    }

    static class KeyManagerDelegate extends X509ExtendedKeyManager {

        private final X509ExtendedKeyManager keyManager;
        private final PrivateKeyStrategy aliasStrategy;

        KeyManagerDelegate(final X509ExtendedKeyManager keyManager, final PrivateKeyStrategy aliasStrategy) {
            super();
            this.keyManager = keyManager;
            this.aliasStrategy = aliasStrategy;
        }

        @Override
        public String[] getClientAliases(
                final String keyType, final Principal[] issuers) {
            return this.keyManager.getClientAliases(keyType, issuers);
        }

        public Map<String, PrivateKeyDetails> getClientAliasMap(
                final String[] keyTypes, final Principal[] issuers) {
            final Map<String, PrivateKeyDetails> validAliases = new HashMap<>();
            for (final String keyType: keyTypes) {
                final String[] aliases = this.keyManager.getClientAliases(keyType, issuers);
                if (aliases != null) {
                    for (final String alias: aliases) {
                        validAliases.put(alias,
                                new PrivateKeyDetails(keyType, this.keyManager.getCertificateChain(alias)));
                    }
                }
            }
            return validAliases;
        }

        public Map<String, PrivateKeyDetails> getServerAliasMap(
                final String keyType, final Principal[] issuers) {
            final Map<String, PrivateKeyDetails> validAliases = new HashMap<>();
            final String[] aliases = this.keyManager.getServerAliases(keyType, issuers);
            if (aliases != null) {
                for (final String alias: aliases) {
                    validAliases.put(alias,
                            new PrivateKeyDetails(keyType, this.keyManager.getCertificateChain(alias)));
                }
            }
            return validAliases;
        }

        @Override
        public String chooseClientAlias(
                final String[] keyTypes, final Principal[] issuers, final Socket socket) {
            final Map<String, PrivateKeyDetails> validAliases = getClientAliasMap(keyTypes, issuers);
            return this.aliasStrategy.chooseAlias(validAliases,
                    socket instanceof SSLSocket ? ((SSLSocket) socket).getSSLParameters() : null);
        }

        @Override
        public String[] getServerAliases(
                final String keyType, final Principal[] issuers) {
            return this.keyManager.getServerAliases(keyType, issuers);
        }

        @Override
        public String chooseServerAlias(
                final String keyType, final Principal[] issuers, final Socket socket) {
            final Map<String, PrivateKeyDetails> validAliases = getServerAliasMap(keyType, issuers);
            return this.aliasStrategy.chooseAlias(validAliases,
                    socket instanceof SSLSocket ? ((SSLSocket) socket).getSSLParameters() : null);
        }

        @Override
        public X509Certificate[] getCertificateChain(final String alias) {
            return this.keyManager.getCertificateChain(alias);
        }

        @Override
        public PrivateKey getPrivateKey(final String alias) {
            return this.keyManager.getPrivateKey(alias);
        }

        @Override
        public String chooseEngineClientAlias(
                final String[] keyTypes, final Principal[] issuers, final SSLEngine sslEngine) {
            final Map<String, PrivateKeyDetails> validAliases = getClientAliasMap(keyTypes, issuers);
            return this.aliasStrategy.chooseAlias(validAliases, sslEngine.getSSLParameters());
        }

        @Override
        public String chooseEngineServerAlias(
                final String keyType, final Principal[] issuers, final SSLEngine sslEngine) {
            final Map<String, PrivateKeyDetails> validAliases = getServerAliasMap(keyType, issuers);
            return this.aliasStrategy.chooseAlias(validAliases, sslEngine.getSSLParameters());
        }

    }

    @Override
    public String toString() {
        return "[provider=" + provider + ", protocol=" + protocol + ", keyStoreType=" + keyStoreType
                + ", keyManagerFactoryAlgorithm=" + keyManagerFactoryAlgorithm + ", keyManagers=" + keyManagers
                + ", trustManagerFactoryAlgorithm=" + trustManagerFactoryAlgorithm + ", trustManagers=" + trustManagers
                + ", secureRandom=" + secureRandom + "]";
    }
}
