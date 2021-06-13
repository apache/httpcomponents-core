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
package org.apache.hc.core5.http2.frame;

import org.apache.hc.core5.http2.config.H2Param;
import org.apache.hc.core5.http2.config.H2Setting;
import org.junit.Assert;
import org.junit.Test;

public class TestH2Settings {

    @Test
    public void testH2ParamBasics() throws Exception {
        for (final H2Param param: H2Param.values()) {
            Assert.assertEquals(param, H2Param.valueOf(param.getCode()));
            Assert.assertEquals(param.name(), H2Param.toString(param.getCode()));
        }
        Assert.assertNull(H2Param.valueOf(0));
        Assert.assertNull(H2Param.valueOf(10));
        Assert.assertEquals("0", H2Param.toString(0));
        Assert.assertEquals("10", H2Param.toString(10));
    }

    @Test
    public void testH2SettingBasics() throws Exception {

        final H2Setting setting1 = new H2Setting(H2Param.ENABLE_PUSH, 0);
        final H2Setting setting2 = new H2Setting(H2Param.INITIAL_WINDOW_SIZE, 1024);

        Assert.assertEquals("ENABLE_PUSH: 0", setting1.toString());
        Assert.assertEquals("INITIAL_WINDOW_SIZE: 1024", setting2.toString());
    }

}
