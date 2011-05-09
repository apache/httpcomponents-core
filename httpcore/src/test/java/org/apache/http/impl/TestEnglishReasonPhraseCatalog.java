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

package org.apache.http.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * Unit tests for {@link EnglishReasonPhraseCatalog}
 *
 */
public class TestEnglishReasonPhraseCatalog {

    @Test
    public void testReasonPhrases() throws IllegalAccessException {
    Field[] publicFields = HttpStatus.class.getFields();

    Assert.assertNotNull( publicFields );

    Assert.assertTrue( publicFields.length > 0 );

    for (int i = 0; i < publicFields.length; i++)
    {
        final Field f = publicFields[i];

        final int modifiers = f.getModifiers();

        if ( (f.getType() == int.class)
            && Modifier.isPublic(modifiers)
            && Modifier.isFinal(modifiers)
            && Modifier.isStatic(modifiers) )
        {
            final int iValue = f.getInt(null);
            final String text = EnglishReasonPhraseCatalog.
                            INSTANCE.getReason(iValue, null);
            Assert.assertNotNull("text is null for HttpStatus."+f.getName(), text);
            Assert.assertTrue(text.length() > 0);
        }
    }
    }


    @Test
    public void testStatusInvalid() throws Exception {
        try {
            EnglishReasonPhraseCatalog.INSTANCE.getReason(-1, null);
            Assert.fail("IllegalArgumentException must have been thrown (-1)");
        } catch (IllegalArgumentException expected) {
        }
        try {
            EnglishReasonPhraseCatalog.INSTANCE.getReason(99, null);
            Assert.fail("IllegalArgumentException must have been thrown (99)");
        } catch (IllegalArgumentException expected) {
        }
        try {
            EnglishReasonPhraseCatalog.INSTANCE.getReason(600, null);
            Assert.fail("IllegalArgumentException must have been thrown (600)");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testStatusAll() throws Exception {
        for (int i = 100; i < 600; i++) {
            EnglishReasonPhraseCatalog.INSTANCE.getReason(i, null);
        }
    }
}
