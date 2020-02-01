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

package org.apache.hc.core5.http2.hpack;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.hc.core5.http2.H2PseudoRequestHeaders;
import org.apache.hc.core5.http2.H2PseudoResponseHeaders;

final class StaticTable {

    static final HPackHeader[] STANDARD_HEADERS = {
            new HPackHeader(H2PseudoRequestHeaders.AUTHORITY, ""),
            new HPackHeader(H2PseudoRequestHeaders.METHOD, "GET"),
            new HPackHeader(H2PseudoRequestHeaders.METHOD, "POST"),
            new HPackHeader(H2PseudoRequestHeaders.PATH, "/"),
            new HPackHeader(H2PseudoRequestHeaders.PATH, "/index.html"),
            new HPackHeader(H2PseudoRequestHeaders.SCHEME, "http"),
            new HPackHeader(H2PseudoRequestHeaders.SCHEME, "https"),
            new HPackHeader(H2PseudoResponseHeaders.STATUS, "200"),
            new HPackHeader(H2PseudoResponseHeaders.STATUS, "204"),
            new HPackHeader(H2PseudoResponseHeaders.STATUS, "206"),
            new HPackHeader(H2PseudoResponseHeaders.STATUS, "304"),
            new HPackHeader(H2PseudoResponseHeaders.STATUS, "400"),
            new HPackHeader(H2PseudoResponseHeaders.STATUS, "404"),
            new HPackHeader(H2PseudoResponseHeaders.STATUS, "500"),
            new HPackHeader("accept-charset", ""),
            new HPackHeader("accept-encoding", "gzip, deflate"),
            new HPackHeader("accept-language", ""),
            new HPackHeader("accept-ranges", ""),
            new HPackHeader("accept", ""),
            new HPackHeader("access-control-allow-origin", ""),
            new HPackHeader("age", ""),
            new HPackHeader("allow", ""),
            new HPackHeader("authorization", ""),
            new HPackHeader("cache-control", ""),
            new HPackHeader("content-disposition", ""),
            new HPackHeader("content-encoding", ""),
            new HPackHeader("content-language", ""),
            new HPackHeader("content-length", ""),
            new HPackHeader("content-location", ""),
            new HPackHeader("content-range", ""),
            new HPackHeader("content-type", ""),
            new HPackHeader("cookie", ""),
            new HPackHeader("date", ""),
            new HPackHeader("etag", ""),
            new HPackHeader("expect", ""),
            new HPackHeader("expires", ""),
            new HPackHeader("from", ""),
            new HPackHeader("host", ""),
            new HPackHeader("if-match", ""),
            new HPackHeader("if-modified-since", ""),
            new HPackHeader("if-none-match", ""),
            new HPackHeader("if-range", ""),
            new HPackHeader("if-unmodified-since", ""),
            new HPackHeader("last-modified", ""),
            new HPackHeader("link", ""),
            new HPackHeader("location", ""),
            new HPackHeader("max-forwards", ""),
            new HPackHeader("proxy-authenticate", ""),
            new HPackHeader("proxy-authorization", ""),
            new HPackHeader("range", ""),
            new HPackHeader("referer", ""),
            new HPackHeader("refresh", ""),
            new HPackHeader("retry-after", ""),
            new HPackHeader("server", ""),
            new HPackHeader("set-cookie", ""),
            new HPackHeader("strict-transport-security", ""),
            new HPackHeader("transfer-encoding", ""),
            new HPackHeader("user-agent", ""),
            new HPackHeader("vary", ""),
            new HPackHeader("via", ""),
            new HPackHeader("www-authenticate", "")
    };

    final static StaticTable INSTANCE = new StaticTable(STANDARD_HEADERS);

    private final HPackHeader[] headers;
    private final ConcurrentMap<String, CopyOnWriteArrayList<HPackEntry>> mapByName;

    StaticTable(final HPackHeader... headers) {
        this.headers = headers;
        this.mapByName = new ConcurrentHashMap<>();

        for (int i = 0; i < headers.length; i++) {
            final HPackHeader header = headers[i];

            final String key = header.getName();
            CopyOnWriteArrayList<HPackEntry> entries = this.mapByName.get(key);
            if (entries == null) {
                entries = new CopyOnWriteArrayList<>(new HPackEntry[] { new InternalEntry(header, i) });
                this.mapByName.put(key, entries);
            } else {
                entries.add(new InternalEntry(header, i));
            }
        }
    }

    public int length() {
        return this.headers.length;
    }

    public HPackHeader get(final int index) {
        return this.headers[index - 1];
    }

    public List<HPackEntry> getByName(final String key) {
        return this.mapByName.get(key);
    }

    static class InternalEntry implements HPackEntry {

        private final HPackHeader header;
        private final int index;

        InternalEntry(final HPackHeader header, final int index) {
            this.header = header;
            this.index = index;
        }

        @Override
        public int getIndex() {
            return index + 1;
        }

        @Override
        public HPackHeader getHeader() {
            return header;
        }

    }

}
