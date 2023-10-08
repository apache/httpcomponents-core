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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Generates a date in the format required by the HTTP protocol.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class HttpDateGenerator {

    private static final int GRANULARITY_MILLIS = 1000;

    /**
     * @deprecated Use {@link #INTERNET_MESSAGE_FORMAT}
     */
    @Deprecated
    public static final String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String INTERNET_MESSAGE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    /**
     * @deprecated This attribute is no longer supported as a part of the public API.
     * The time zone to use in the date header.
     */
    @Deprecated
    public static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    public static final ZoneId GMT_ID = ZoneId.of("GMT");

    /** Singleton instance. */
    public static final HttpDateGenerator INSTANCE = new HttpDateGenerator(INTERNET_MESSAGE_FORMAT, GMT_ID);

    private final DateTimeFormatter dateTimeFormatter;
    private long dateAsMillis;
    private String dateAsText;
    private ZoneId zoneId;

    private final ReentrantLock lock;

    private HttpDateGenerator(final String pattern, final ZoneId zoneId) {
        dateTimeFormatter = new DateTimeFormatterBuilder()
                .parseLenient()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter();
        this.zoneId =  zoneId;
        this.lock = new ReentrantLock();
    }

    public String getCurrentDate() {
        lock.lock();
        try {
            final long now = System.currentTimeMillis();
            if (now - this.dateAsMillis > GRANULARITY_MILLIS) {
                // Generate new date string
                dateAsText = dateTimeFormatter.format(Instant.now().atZone(zoneId));
                dateAsMillis = now;
            }
            return dateAsText;
        } finally {
            lock.unlock();
        }
    }

}
