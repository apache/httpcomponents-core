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
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;

/**
 * This request interceptor can be used by an HTTP proxy or an intermediary to add a Forwarded header
 * to outgoing request messages.
 * The Forwarded header is used to capture information about the intermediate nodes that a request
 * has passed through. This information can be useful for security purposes or for debugging purposes.
 * <p>
 * The Forwarded header consists of a list of key-value pairs separated by semicolons. The keys that
 * can be used in the Forwarded header include "host", "port", "proto", "for", and "by". The host
 * key is used to specify the host name or IP address of the request authority. The port key is used
 * to specify the port number of the request authority. The proto key is used to specify the
 * protocol version of the request. The for key is used to specify the IP address of the client
 * making the request. The by key is used to specify the IP address of the node adding the Forwarded
 * header.
 * <p>
 * When multiple proxy servers are involved in forwarding a request, each proxy can add its own
 * Forwarded header to the request. This allows for the capture of information about each
 * intermediate node that the request passes through.
 * <p>
 * The ForwardedRequest class adds the Forwarded header to the request by implementing the process()
 * method of the HttpRequestInterceptor interface. The method retrieves the ProtocolVersion and
 * {@link URIAuthority} from the {@link HttpContext}. The ProtocolVersion is used to determine the
 * proto key value and the {@link URIAuthority} is used to determine the host and port key values.
 * The method also retrieves the {@link EndpointDetails} from the {@link HttpContext}, if it exists.
 * The {@link EndpointDetails} is used to determine the by and for key values. If a Forwarded header
 * already exists in the request, the existing header is not overwritten; instead, the new header
 * value is appended to the existing header value, with a comma separator.
 * <p>
 *
 * @since 5.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class ForwardedRequest implements HttpRequestInterceptor {

    /**
     * The name of the header to set in the HTTP request.
     */
    private static final String FORWARDED_HEADER_NAME = "Forwarded";

    /**
     * Singleton instance.
     */
    public static final HttpRequestInterceptor INSTANCE = new ForwardedRequest();


    /**
     * The process method adds a Forwarded header to an HTTP request containing information about
     * the intermediate nodes that the request has passed through. If a Forwarded header already
     * exists in the request, the new header value is appended to the existing header value, with a
     * comma separator.
     * <p>
     * The method retrieves the {@link ProtocolVersion} and {@link URIAuthority} from the
     * HttpContext. The ProtocolVersion is used to determine the proto key value and the
     * URIAuthority is used to determine the host and port key values. The method also retrieves the
     * EndpointDetails from the {@link HttpContext}, if it exists. The {@link EndpointDetails} is
     * used to determine the by key value.
     *
     * @param request the HTTP request to which the Forwarded header is to be added (not
     *                {@code null})
     * @param entity  the entity details of the request (can be {@code null})
     * @param context the HTTP context in which the request is being executed (not {@code null})
     * @throws HttpException     if there is an error processing the request
     * @throws IOException       if an IO error occurs while processing the request
     * @throws ProtocolException if the request authority is not specified
     */
    @Override
    public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context) throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");

        final ProtocolVersion ver = context.getProtocolVersion() != null ? context.getProtocolVersion() : HttpVersion.HTTP_1_1;

        final URIAuthority authority = request.getAuthority();
        if (authority == null) {
            throw new ProtocolException("Request authority not specified");
        }

        final int port = authority.getPort();
        final StringBuilder valueBuilder = new StringBuilder();

        final Header forwardedHeader = request.getFirstHeader(FORWARDED_HEADER_NAME);
        final boolean forwardedHeaderExists = forwardedHeader != null;
        if (forwardedHeaderExists) {
            // Forwarded header already exists, add current hop details to the end of the existing value
            valueBuilder.append(forwardedHeader.getValue());
            valueBuilder.append(", ");
        }

        // Add the current hop details
        final EndpointDetails endpointDetails = (EndpointDetails) context.getAttribute(HttpCoreContext.CONNECTION_ENDPOINT);

        // Add the "by" parameter
        if (endpointDetails != null) {
            final SocketAddress remoteAddress = endpointDetails.getRemoteAddress();
            if (remoteAddress instanceof InetSocketAddress) {
                final InetSocketAddress inetAddress = (InetSocketAddress) remoteAddress;
                final String byValue = inetAddress.getHostString() + ":" + inetAddress.getPort();
                if (inetAddress.getAddress() instanceof Inet6Address) {
                    valueBuilder.append("by=\"").append(byValue).append("\"");
                } else {
                    valueBuilder.append("by=").append(byValue);
                }
            }
            // Add the "for" parameter
            final SocketAddress localAddress = endpointDetails.getLocalAddress();
            if (localAddress instanceof InetSocketAddress) {
                final InetSocketAddress inetAddress = (InetSocketAddress) localAddress;
                final String forValue = inetAddress.getHostString() + ":" + inetAddress.getPort();
                if (inetAddress.getAddress() instanceof Inet6Address) {
                    valueBuilder.append(";for=\"").append(forValue).append("\"");
                } else {
                    valueBuilder.append(";for=").append(forValue);
                }
            }

        }
        // Add the "host" parameter
        if (valueBuilder.length() > 0 && !forwardedHeaderExists) {
            valueBuilder.append(";");
        }
        valueBuilder.append("host=\"").append(authority.getHostName()).append("\"");

        // Add the "port" parameter
        if (port != -1) {
            valueBuilder.append(";port=").append(port);
        }

        // Add the "proto" parameter
        final String protoValue = ver.getProtocol();
        if (protoValue != null) {
            valueBuilder.append(";proto=").append(protoValue);
        }

        final String value = valueBuilder.toString();
        request.setHeader(FORWARDED_HEADER_NAME, value);
    }

}
