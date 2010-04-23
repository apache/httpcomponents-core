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

package org.apache.http.contrib.sip;

import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

import org.apache.http.ReasonPhraseCatalog;


/**
 * English reason phrases for SIP status codes.
 * All status codes defined in {@link SipStatus} are supported.
 * See <a href="http://www.iana.org/assignments/sip-parameters">
 * http://www.iana.org/assignments/sip-parameters
 * </a>
 * for a full list of registered SIP status codes and the defining RFCs.
 *
 *
 */
public class EnglishSipReasonPhraseCatalog
    implements ReasonPhraseCatalog {

    // static map with english reason phrases defined below

    /**
     * The default instance of this catalog.
     * This catalog is thread safe, so there typically
     * is no need to create other instances.
     */
    public final static EnglishSipReasonPhraseCatalog INSTANCE =
        new EnglishSipReasonPhraseCatalog();


    /**
     * Restricted default constructor, for derived classes.
     * If you need an instance of this class, use {@link #INSTANCE INSTANCE}.
     */
    protected EnglishSipReasonPhraseCatalog() {
        // no body
    }


    /**
     * Obtains the reason phrase for a status code.
     *
     * @param status    the status code, in the range 100-699
     * @param loc       ignored
     *
     * @return  the reason phrase, or <code>null</code>
     */
    public String getReason(int status, Locale loc) {
        if ((status < 100) || (status >= 700)) {
            throw new IllegalArgumentException
                ("Unknown category for status code " + status + ".");
        }

        // Unlike HTTP status codes, those for SIP are not compact
        // in each category. We therefore use a map for lookup.

        return REASON_PHRASES.get(Integer.valueOf(status));
    }



    /**
     * Reason phrases lookup table.
     * Since this attribute is private and never exposed,
     * we don't need to turn it into an unmodifiable map.
     */
    // see below for static initialization
    private static final
        Map<Integer,String> REASON_PHRASES = new HashMap<Integer,String>();



    /**
     * Stores the given reason phrase, by status code.
     * Helper method to initialize the static lookup map.
     *
     * @param status    the status code for which to define the phrase
     * @param reason    the reason phrase for this status code
     */
    private static void setReason(int status, String reason) {
        REASON_PHRASES.put(Integer.valueOf(status), reason);
    }


    // ----------------------------------------------------- Static Initializer

    /** Set up status code to "reason phrase" map. */
    static {

        // --- 1xx Informational ---
        setReason(SipStatus.SC_CONTINUE,
                  "Trying");
        setReason(SipStatus.SC_RINGING,
                  "Ringing");
        setReason(SipStatus.SC_CALL_IS_BEING_FORWARDED,
                  "Call Is Being Forwarded");
        setReason(SipStatus.SC_QUEUED,
                  "Queued");
        setReason(SipStatus.SC_SESSION_PROGRESS,
                  "Session Progress");


        // --- 2xx Successful ---
        setReason(SipStatus.SC_OK,
                  "OK");
        setReason(SipStatus.SC_ACCEPTED,
                  "Accepted");


        // --- 3xx Redirection ---
        setReason(SipStatus.SC_MULTIPLE_CHOICES,
                  "Multiple Choices");
        setReason(SipStatus.SC_MOVED_PERMANENTLY,
                  "Moved Permanently");
        setReason(SipStatus.SC_MOVED_TEMPORARILY,
                  "Moved Temporarily");
        setReason(SipStatus.SC_USE_PROXY,
                  "Use Proxy");
        setReason(SipStatus.SC_ALTERNATIVE_SERVICE,
                  "Alternative Service");


        // --- 4xx Request Failure ---
        setReason(SipStatus.SC_BAD_REQUEST,
                  "Bad Request");
        setReason(SipStatus.SC_UNAUTHORIZED,
                  "Unauthorized");
        setReason(SipStatus.SC_PAYMENT_REQUIRED,
                  "Payment Required");
        setReason(SipStatus.SC_FORBIDDEN,
                  "Forbidden");
        setReason(SipStatus.SC_NOT_FOUND,
                  "Not Found");
        setReason(SipStatus.SC_METHOD_NOT_ALLOWED,
                  "Method Not Allowed");
        setReason(SipStatus.SC_NOT_ACCEPTABLE,
                  "Not Acceptable");
        setReason(SipStatus.SC_PROXY_AUTHENTICATION_REQUIRED,
                  "Proxy Authentication Required");
        setReason(SipStatus.SC_REQUEST_TIMEOUT,
                  "Request Timeout");
        setReason(SipStatus.SC_GONE,
                  "Gone");
        setReason(SipStatus.SC_CONDITIONAL_REQUEST_FAILED,
                  "Conditional Request Failed");
        setReason(SipStatus.SC_REQUEST_ENTITY_TOO_LARGE,
                  "Request Entity Too Large");
        setReason(SipStatus.SC_REQUEST_URI_TOO_LONG,
                  "Request-URI Too Long");
        setReason(SipStatus.SC_UNSUPPORTED_MEDIA_TYPE,
                  "Unsupported Media Type");
        setReason(SipStatus.SC_UNSUPPORTED_URI_SCHEME,
                  "Unsupported URI Scheme");
        setReason(SipStatus.SC_UNKNOWN_RESOURCE_PRIORITY,
                  "Unknown Resource-Priority");
        setReason(SipStatus.SC_BAD_EXTENSION,
                  "Bad Extension");
        setReason(SipStatus.SC_EXTENSION_REQUIRED,
                  "Extension Required");
        setReason(SipStatus.SC_SESSION_INTERVAL_TOO_SMALL,
                  "Session Interval Too Small");
        setReason(SipStatus.SC_INTERVAL_TOO_BRIEF,
                  "Interval Too Brief");
        setReason(SipStatus.SC_USE_IDENTITY_HEADER,
                  "Use Identity Header");
        setReason(SipStatus.SC_PROVIDE_REFERRER_IDENTITY,
                  "Provide Referrer Identity");
        setReason(SipStatus.SC_ANONYMITY_DISALLOWED,
                  "Anonymity Disallowed");
        setReason(SipStatus.SC_BAD_IDENTITY_INFO,
                  "Bad Identity-Info");
        setReason(SipStatus.SC_UNSUPPORTED_CERTIFICATE,
                  "Unsupported Certificate");
        setReason(SipStatus.SC_INVALID_IDENTITY_HEADER,
                  "Invalid Identity Header");
        setReason(SipStatus.SC_TEMPORARILY_UNAVAILABLE,
                  "Temporarily Unavailable");
        setReason(SipStatus.SC_CALL_TRANSACTION_DOES_NOT_EXIST,
                  "Call/Transaction Does Not Exist");
        setReason(SipStatus.SC_LOOP_DETECTED,
                  "Loop Detected");
        setReason(SipStatus.SC_TOO_MANY_HOPS,
                  "Too Many Hops");
        setReason(SipStatus.SC_ADDRESS_INCOMPLETE,
                  "Address Incomplete");
        setReason(SipStatus.SC_AMBIGUOUS,
                  "Ambiguous");
        setReason(SipStatus.SC_BUSY_HERE,
                  "Busy Here");
        setReason(SipStatus.SC_REQUEST_TERMINATED,
                  "Request Terminated");
        setReason(SipStatus.SC_NOT_ACCEPTABLE_HERE,
                  "Not Acceptable Here");
        setReason(SipStatus.SC_BAD_EVENT,
                  "Bad Event");
        setReason(SipStatus.SC_REQUEST_PENDING,
                  "Request Pending");
        setReason(SipStatus.SC_UNDECIPHERABLE,
                  "Undecipherable");
        setReason(SipStatus.SC_SECURITY_AGREEMENT_REQUIRED,
                  "Security Agreement Required");


        // --- 5xx Server Failure ---
        setReason(SipStatus.SC_SERVER_INTERNAL_ERROR,
                  "Server Internal Error");
        setReason(SipStatus.SC_NOT_IMPLEMENTED,
                  "Not Implemented");
        setReason(SipStatus.SC_BAD_GATEWAY,
                  "Bad Gateway");
        setReason(SipStatus.SC_SERVICE_UNAVAILABLE,
                  "Service Unavailable");
        setReason(SipStatus.SC_SERVER_TIMEOUT,
                  "Server Time-out");
        setReason(SipStatus.SC_VERSION_NOT_SUPPORTED,
                  "Version Not Supported");
        setReason(SipStatus.SC_MESSAGE_TOO_LARGE,
                  "Message Too Large");
        setReason(SipStatus.SC_PRECONDITION_FAILURE,
                  "Precondition Failure");


        // --- 6xx Global Failures ---
        setReason(SipStatus.SC_BUSY_EVERYWHERE,
                  "Busy Everywhere");
        setReason(SipStatus.SC_DECLINE,
                  "Decline");
        setReason(SipStatus.SC_DOES_NOT_EXIST_ANYWHERE,
                  "Does Not Exist Anywhere");
        setReason(SipStatus.SC_NOT_ACCEPTABLE_ANYWHERE,
                  "Not Acceptable");

    } // static initializer


}
