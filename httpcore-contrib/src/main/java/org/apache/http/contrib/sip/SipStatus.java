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

/**
 * Constants enumerating the SIP status codes.
 * All status codes registered at
 * <a href="http://www.iana.org/assignments/sip-parameters">
 * http://www.iana.org/assignments/sip-parameters
 * </a>
 * on 2007-12-30 are listed.
 * The defining RFCs include RFC 3261 (SIP/2.0),
 * RFC 3265 (SIP-Specific Event Notification),
 * RFC 4412 (Communications Resource Priority for SIP),
 * RFC 4474 (Enhancements for Authenticated Identity Management in SIP),
 * and others.
 *
 *
 */
public interface SipStatus {

    // --- 1xx Informational ---

    /** <tt>100 Trying</tt>. RFC 3261, section 21.1.1. */
    public static final int SC_CONTINUE = 100;

    /** <tt>180 Ringing</tt>. RFC 3261, section 21.1.2. */
    public static final int SC_RINGING = 180;

    /** <tt>181 Call Is Being Forwarded</tt>. RFC 3261, section 21.1.3. */
    public static final int SC_CALL_IS_BEING_FORWARDED = 181;

    /** <tt>182 Queued</tt>. RFC 3261, section 21.1.4. */
    public static final int SC_QUEUED = 182;

    /** <tt>183 Session Progress</tt>. RFC 3261, section 21.1.5. */
    public static final int SC_SESSION_PROGRESS = 183;


    // --- 2xx Successful ---

    /** <tt>200 OK</tt>. RFC 3261, section 21.2.1. */
    public static final int SC_OK = 200;

    /** <tt>202 Accepted</tt>. RFC 3265, section 6.4. */
    public static final int SC_ACCEPTED = 202;


    // --- 3xx Redirection ---

    /** <tt>300 Multiple Choices</tt>. RFC 3261, section 21.3.1. */
    public static final int SC_MULTIPLE_CHOICES = 300;

    /** <tt>301 Moved Permanently</tt>. RFC 3261, section 21.3.2. */
    public static final int SC_MOVED_PERMANENTLY = 301;

    /** <tt>302 Moved Temporarily</tt>. RFC 3261, section 21.3.3. */
    public static final int SC_MOVED_TEMPORARILY = 302;

    /** <tt>305 Use Proxy</tt>. RFC 3261, section 21.3.4. */
    public static final int SC_USE_PROXY = 305;

    /** <tt>380 Alternative Service</tt>. RFC 3261, section 21.3.5. */
    public static final int SC_ALTERNATIVE_SERVICE = 380;


    // --- 4xx Request Failure ---


    /** <tt>400 Bad Request</tt>. RFC 3261, section 21.4.1. */
    public static final int SC_BAD_REQUEST = 400;

    /** <tt>401 Unauthorized</tt>. RFC 3261, section 21.4.2. */
    public static final int SC_UNAUTHORIZED = 401;

    /** <tt>402 Payment Required</tt>. RFC 3261, section 21.4.3. */
    public static final int SC_PAYMENT_REQUIRED = 402;

    /** <tt>403 Forbidden</tt>. RFC 3261, section 21.4.4. */
    public static final int SC_FORBIDDEN = 403;

    /** <tt>404 Not Found</tt>. RFC 3261, section 21.4.5. */
    public static final int SC_NOT_FOUND = 404;

    /** <tt>405 Method Not Allowed</tt>. RFC 3261, section 21.4.6. */
    public static final int SC_METHOD_NOT_ALLOWED = 405;

    /** <tt>406 Not Acceptable</tt>. RFC 3261, section 21.4.7. */
    public static final int SC_NOT_ACCEPTABLE = 406;

    /**
     * <tt>407 Proxy Authentication Required</tt>.
     * RFC 3261, section 21.4.8.
     */
    public static final int SC_PROXY_AUTHENTICATION_REQUIRED = 407;

    /** <tt>408 Request Timeout</tt>. RFC 3261, section 21.4.9. */
    public static final int SC_REQUEST_TIMEOUT = 408;

    /** <tt>410 Gone</tt>. RFC 3261, section 21.4.10. */
    public static final int SC_GONE = 410;

    /** <tt>412 Conditional Request Failed</tt>. RFC 3903, section 11.2.1. */
    public static final int SC_CONDITIONAL_REQUEST_FAILED = 412;

    /** <tt>413 Request Entity Too Large</tt>. RFC 3261, section 21.4.11. */
    public static final int SC_REQUEST_ENTITY_TOO_LARGE = 413;

    /** <tt>414 Request-URI Too Long</tt>. RFC 3261, section 21.4.12. */
    public static final int SC_REQUEST_URI_TOO_LONG = 414;

    /** <tt>415 Unsupported Media Type</tt>. RFC 3261, section 21.4.13. */
    public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;

    /** <tt>416 Unsupported URI Scheme</tt>. RFC 3261, section 21.4.14. */
    public static final int SC_UNSUPPORTED_URI_SCHEME = 416;

    /** <tt>417 Unknown Resource-Priority</tt>. RFC 4412, section 12.4. */
    public static final int SC_UNKNOWN_RESOURCE_PRIORITY = 417;

    /** <tt>420 Bad Extension</tt>. RFC 3261, section 21.4.15. */
    public static final int SC_BAD_EXTENSION = 420;

    /** <tt>421 Extension Required</tt>. RFC 3261, section 21.4.16. */
    public static final int SC_EXTENSION_REQUIRED = 421;

    /** <tt>422 Session Interval Too Small</tt>. RFC 4028, chapter 6. */
    public static final int SC_SESSION_INTERVAL_TOO_SMALL = 422;

    /** <tt>423 Interval Too Brief</tt>. RFC 3261, section 21.4.17. */
    public static final int SC_INTERVAL_TOO_BRIEF = 423;

    /** <tt>428 Use Identity Header</tt>. RFC 4474, section 14.2. */
    public static final int SC_USE_IDENTITY_HEADER = 428;

    /** <tt>429 Provide Referrer Identity</tt>. RFC 3892, chapter 5. */
    public static final int SC_PROVIDE_REFERRER_IDENTITY = 429;

    /** <tt>433 Anonymity Disallowed</tt>. RFC 5079, chapter 5. */
    public static final int SC_ANONYMITY_DISALLOWED = 433;

    /** <tt>436 Bad Identity-Info</tt>. RFC 4474, section 14.3. */
    public static final int SC_BAD_IDENTITY_INFO = 436;

    /** <tt>437 Unsupported Certificate</tt>. RFC 4474, section 14.4. */
    public static final int SC_UNSUPPORTED_CERTIFICATE = 437;

    /** <tt>438 Invalid Identity Header</tt>. RFC 4474, section 14.5. */
    public static final int SC_INVALID_IDENTITY_HEADER = 438;

    /** <tt>480 Temporarily Unavailable</tt>. RFC 3261, section 21.4.18. */
    public static final int SC_TEMPORARILY_UNAVAILABLE = 480;

    /**
     * <tt>481 Call/Transaction Does Not Exist</tt>.
     * RFC 3261, section 21.4.19.
     */
    public static final int SC_CALL_TRANSACTION_DOES_NOT_EXIST = 481;

    /** <tt>482 Loop Detected</tt>. RFC 3261, section 21.4.20. */
    public static final int SC_LOOP_DETECTED = 482;

    /** <tt>483 Too Many Hops</tt>. RFC 3261, section 21.4.21. */
    public static final int SC_TOO_MANY_HOPS = 483;

    /** <tt>484 Address Incomplete</tt>. RFC 3261, section 21.4.22. */
    public static final int SC_ADDRESS_INCOMPLETE = 484;

    /** <tt>485 Ambiguous</tt>. RFC 3261, section 21.4.23. */
    public static final int SC_AMBIGUOUS = 485;

    /** <tt>486 Busy Here</tt>. RFC 3261, section 21.4.24. */
    public static final int SC_BUSY_HERE = 486;

    /** <tt>487 Request Terminated</tt>. RFC 3261, section 21.4.25. */
    public static final int SC_REQUEST_TERMINATED = 487;

    /** <tt>488 Not Acceptable Here</tt>. RFC 3261, section 21.4.26. */
    public static final int SC_NOT_ACCEPTABLE_HERE = 488;

    /** <tt>489 Bad Event</tt>. RFC 3265, section 6.4. */
    public static final int SC_BAD_EVENT = 489;

    /** <tt>491 Request Pending</tt>. RFC 3261, section 21.4.27. */
    public static final int SC_REQUEST_PENDING = 491;

    /** <tt>493 Undecipherable</tt>. RFC 3261, section 21.4.28. */
    public static final int SC_UNDECIPHERABLE = 493;

    /** <tt>494 Security Agreement Required</tt>. RFC 3329, section 6.4. */
    public static final int SC_SECURITY_AGREEMENT_REQUIRED = 494;


    // --- 5xx Server Failure ---

    /** <tt>500 Server Internal Error</tt>. RFC 3261, section 21.5.1. */
    public static final int SC_SERVER_INTERNAL_ERROR = 500;

    /** <tt>501 Not Implemented</tt>. RFC 3261, section 21.5.2. */
    public static final int SC_NOT_IMPLEMENTED = 501;

    /** <tt>502 Bad Gateway</tt>. RFC 3261, section 21.5.3. */
    public static final int SC_BAD_GATEWAY = 502;

    /** <tt>503 Service Unavailable</tt>. RFC 3261, section 21.5.4. */
    public static final int SC_SERVICE_UNAVAILABLE = 503;

    /** <tt>504 Server Time-out</tt>. RFC 3261, section 21.5.5. */
    public static final int SC_SERVER_TIMEOUT = 504;

    /** <tt>505 Version Not Supported</tt>. RFC 3261, section 21.5.6. */
    public static final int SC_VERSION_NOT_SUPPORTED = 505;

    /** <tt>513 Message Too Large</tt>. RFC 3261, section 21.5.7. */
    public static final int SC_MESSAGE_TOO_LARGE = 513;

    /** <tt>580 Precondition Failure</tt>. RFC 3312, chapter 8. */
    public static final int SC_PRECONDITION_FAILURE = 580;


    // --- 6xx Global Failures ---

    /** <tt>600 Busy Everywhere</tt>. RFC 3261, section 21.6.1. */
    public static final int SC_BUSY_EVERYWHERE = 600;

    /** <tt>603 Decline</tt>. RFC 3261, section 21.6.2. */
    public static final int SC_DECLINE = 603;

    /** <tt>604 Does Not Exist Anywhere</tt>. RFC 3261, section 21.6.3. */
    public static final int SC_DOES_NOT_EXIST_ANYWHERE = 604;

    /**
     * <tt>606 Not Acceptable</tt>. RFC 3261, section 21.6.4.
     * Status codes 606 and 406 both indicate "Not Acceptable",
     * but we can only define one constant with that name in this interface.
     * 406 is specific to an endpoint, while 606 is global and
     * indicates that the callee will not find the request acceptable
     * on any endpoint.
     */
    public static final int SC_NOT_ACCEPTABLE_ANYWHERE = 606;

}
