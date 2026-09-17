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
package org.apache.hc.core5.http.structured;

import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestStructuredFieldHeaders {

    @Test
    void testDictionaryEntriesPreserveOrderAndDuplicates() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Example", "a=1, b=2");
        request.addHeader("Example", "a=3");

        final List<Map.Entry<String, StructuredFieldMember>> entries =
                StructuredFieldHeaders.parseDictionaryEntries(request, "Example");

        Assertions.assertEquals(3, entries.size());
        Assertions.assertEquals("a", entries.get(0).getKey());
        Assertions.assertEquals(1L, ((StructuredFieldItem) entries.get(0).getValue()).getBareItem().getLongValue());
        Assertions.assertEquals("b", entries.get(1).getKey());
        Assertions.assertEquals(2L, ((StructuredFieldItem) entries.get(1).getValue()).getBareItem().getLongValue());
        Assertions.assertEquals("a", entries.get(2).getKey());
        Assertions.assertEquals(3L, ((StructuredFieldItem) entries.get(2).getValue()).getBareItem().getLongValue());
    }

    @Test
    void testDictionaryStillUsesLastValueWinsSemantics() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Example", "a=1, b=2, a=3");

        final StructuredFieldDictionary dictionary = StructuredFieldHeaders.parseDictionary(request, "Example");

        Assertions.assertEquals(2, dictionary.size());
        Assertions.assertEquals("a", dictionary.getName(0));
        Assertions.assertEquals(3L, ((StructuredFieldItem) dictionary.get("a")).getBareItem().getLongValue());
        Assertions.assertEquals("b", dictionary.getName(1));
    }

}
