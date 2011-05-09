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

package org.apache.http.params;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link BasicHttpParams}.
 *
 */
public class TestDefaultedHttpParams {

    @Test
    public void testAddRemoveParam() {
        DefaultedHttpParams deflt = new DefaultedHttpParams(new BasicHttpParams(), new BasicHttpParams());
        Assert.assertFalse("The parameter should not be removed successfully", deflt.removeParameter("param1"));
        deflt.setParameter("param1", "paramValue1");
        Assert.assertEquals(0, deflt.getDefaultNames().size());
        Assert.assertEquals(1, deflt.getNames().size());
        Assert.assertEquals(1, deflt.getLocalNames().size());
        Assert.assertTrue("The parameter should be removed successfully", deflt.removeParameter("param1"));
        Assert.assertFalse("The parameter should not be present", deflt.removeParameter("param1"));
        Assert.assertEquals(0, deflt.getDefaultNames().size());
        Assert.assertEquals(0, deflt.getNames().size());
        Assert.assertEquals(0, deflt.getLocalNames().size());
    }

    @Test
    public void testEmptyParams() {
        DefaultedHttpParams deflt = new DefaultedHttpParams(new BasicHttpParams(), new BasicHttpParams());
        Assert.assertNull("The parameter should not be present", deflt.getParameter("param1"));
        //try a remove from an empty params
        Assert.assertFalse("The parameter should not be present", deflt.removeParameter("param1"));

        Assert.assertEquals(0, deflt.getNames().size());
        Assert.assertEquals(0, deflt.getLocalNames().size());
        Assert.assertEquals(0, deflt.getDefaultNames().size());
    }

    private HttpParams addParams(String name){
        BasicHttpParams params = new BasicHttpParams();
        params.setParameter("common","both");
        params.setParameter(name,"value");
        return params;
    }

    @Test
    public void testgetNames() {
        DefaultedHttpParams params = new DefaultedHttpParams(addParams("local"), addParams("default"));

        Set<String> nameSet = params.getNames();
        Assert.assertEquals(3, nameSet.size());
        Set<String> localnameSet = params.getLocalNames();
        Assert.assertEquals(2, localnameSet.size());
        Set<String> defaultnameSet = params.getDefaultNames();
        Assert.assertEquals(2, defaultnameSet.size());

        params.setParameter("new", null);
        Assert.assertEquals(3, nameSet.size()); // Name set not yet updated
        Assert.assertEquals(2, localnameSet.size());
        Assert.assertEquals(2, defaultnameSet.size());

        nameSet = params.getNames();
        localnameSet = params.getLocalNames();
        defaultnameSet = params.getDefaultNames();
        Assert.assertEquals(4, nameSet.size());
        Assert.assertEquals(3, localnameSet.size());
        Assert.assertEquals(2, defaultnameSet.size());
    }
}
