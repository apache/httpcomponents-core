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

package org.apache.hc.core5.http.message;

import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Extension of {@link org.apache.hc.core5.http.message.BasicLineParser} that defers parsing of
 * header values. Header value is parsed only if accessed with
 * {@link org.apache.hc.core5.http.Header#getValue()}.
 * <p>
 * This parser should be used to parse request messages on the server server.
 * </p>
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class LazyLineParser extends BasicLineParser {

    public final static LazyLineParser INSTANCE = new LazyLineParser();

    @Override
    public Header parseHeader(final CharArrayBuffer buffer) throws ParseException {
        Objects.requireNonNull(buffer, "Char array buffer");

        return new BufferedHeader(buffer, true);
    }

}
