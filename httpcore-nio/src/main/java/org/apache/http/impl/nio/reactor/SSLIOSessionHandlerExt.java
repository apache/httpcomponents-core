package org.apache.http.impl.nio.reactor;

import org.apache.http.nio.reactor.IOSession;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.net.SocketAddress;

/**
 * This is an extended interface of the SSLIOSessionHandler - to maintain backwards compatibility but yet solve HTTPCORE-217
 */
public interface SSLIOSessionHandlerExt extends SSLIOSessionHandler {
    /**
     * Triggered when the SSL connection has been established and initial SSL
     * handshake has been successfully completed. Custom handlers can use
     * this callback to verify properties of the {@link javax.net.ssl.SSLSession}
     * and optionally set properties on the IOSession to be processed later.
     * For instance this would be the right place to enforce SSL cipher
     * strength, validate certificate chain and do hostname checks, and to optionally
     * set the client DN as an IOSession attribute
     *
     * @param remoteAddress the remote address of the connection.
     * @param session newly created SSL session.
     * @param iosession the underlying IOSession for the SSL connection.
     * @throws javax.net.ssl.SSLException if case of SSL protocol error.
     */
    void verify(SocketAddress remoteAddress, SSLSession session, IOSession iosession)
        throws SSLException;
}
