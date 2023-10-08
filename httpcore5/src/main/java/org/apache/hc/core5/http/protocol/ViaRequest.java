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
package org.apache.hc.core5.http.protocol;

import java.io.IOException;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;


/**
 * This request interceptor can be used by an HTTP proxy or intemediary to add the
 * {@link HttpHeaders#VIA} HTTP header to outgoing request messages.
 * <p>The {@link  HttpHeaders#VIA} header is used to indicate intermediate protocols and recipients
 * between the user agent and the server (on requests) or between the origin server and the client
 * (on responses). It can be used for tracking message forwards, avoiding request loops, and
 * identifying the protocol capabilities of senders along the request/response chain. Each member of
 * the {@link HttpHeaders#VIA} header field value represents a proxy or gateway that has forwarded
 * the message.
 * <p>A proxy <b>MUST</b> send an appropriate {@link  HttpHeaders#VIA} header field, as described
 * in
 * the HTTP specification, in each message that it forwards. An HTTP-to-HTTP gateway <b>MUST</b>
 * send an appropriate {@link HttpHeaders#VIA} header field in each inbound request message and
 * <b>MAY</b> send a {@link HttpHeaders#VIA} header field in forwarded response messages.
 * <p>This interceptor ensures that the {@link  HttpHeaders#VIA} header is added to the request
 * only
 * if it has not been added previously, as per the HTTP specification. Additionally, it updates the
 * values in the {@link HttpHeaders#VIA} header correctly in case of multiple intermediate protocols
 * or recipients, by appending its own information about how the message was received to the end of
 * the header field value.
 *
 * @since 5.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class ViaRequest implements HttpRequestInterceptor {


    /**
     * Singleton instance.
     */
    public static final HttpRequestInterceptor INSTANCE = new ViaRequest();

    /**
     * Constructs a new {@code ViaRequest}.
     */
    public ViaRequest() {
    }

    /**
     * Adds the HTTP {@link  HttpHeaders#VIA} header to the request if it does not already exist.
     *
     * <p>This method ensures that the version of the request is HTTP/1.1 or higher, and adds the
     * Via header in the format {@code <protocol> <version> <host>}, where {@code <protocol>} is the protocol name,
     * {@code <version>} is the major and minor version of the request, and {@code <host>} is the value of the Host header.
     *
     * <p>In case the {@link  HttpHeaders#VIA} header already exists, this method updates its value by appending
     * the new protocol information in the same format.
     *
     * <p>If the version of the request is lower than {@code HTTP/1.1} or the request authority not being specified,
     * this method throws a {@link ProtocolException}.
     *
     * @param request the request object to modify
     * @param entity the entity for the request, may be {@code null}
     * @param context the context for the request
     * @throws ProtocolException if there was a protocol error, such as the request version being lower than {@code HTTP/1.1},
     *         or the request authority not being specified
     * @throws IOException if there was an I/O error
     */
    @Override
    public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context) throws ProtocolException, IOException {
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");
        final ProtocolVersion ver = context.getProtocolVersion() != null ? context.getProtocolVersion() : HttpVersion.HTTP_1_1;

        final URIAuthority authority = request.getAuthority();
        if (authority == null) {
            throw new ProtocolException("Request authority not specified");
        }


        if (!ver.greaterEquals(HttpVersion.HTTP_1_1)) {
            throw new ProtocolException("Invalid protocol version: %s", ver);
        }
        if (!request.containsHeader(HttpHeaders.VIA)) {
            String viaHeaderValue = ver.getProtocol() + " " + ver.getMajor() + "." + ver.getMinor() + " " + authority.getHostName();
            final int port = authority.getPort();
            if (port != -1) {
                viaHeaderValue += ":" + port;
            }
            request.addHeader(HttpHeaders.VIA, viaHeaderValue);
        }
    }
}

