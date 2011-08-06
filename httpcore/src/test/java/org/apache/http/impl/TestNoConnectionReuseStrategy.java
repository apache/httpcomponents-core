package org.apache.http.impl;

import junit.framework.Assert;

import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


public class TestNoConnectionReuseStrategy {

    private NoConnectionReuseStrategy strat = new NoConnectionReuseStrategy();
    @Mock private HttpResponse response;
    @Mock private HttpContext context;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullResponse() throws Exception {
        strat.keepAlive(null, context);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullContext() throws Exception {
        strat.keepAlive(response, null);
    }

    @Test
    public void testGoodcall() throws Exception {
        Assert.assertFalse(strat.keepAlive(response, context));
    }

}
