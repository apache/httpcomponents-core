package org.apache.http.nio.protocol;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import junit.framework.Assert;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.entity.ProducingNHttpEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TestBasicAsyncRequestProducer {

    private BasicAsyncRequestProducer producer;
    private HttpHost target;
    @Mock private ProducingNHttpEntity entity;
    @Mock private HttpEntityEnclosingRequest request;
    @Mock private ContentEncoder encoder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(request.getEntity()).thenReturn(entity);

        target = new HttpHost("localhost");

        producer = new BasicAsyncRequestProducer(target, request, entity);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullTarget3ArgConstructor() throws Exception {
        producer = new BasicAsyncRequestProducer(null, request, entity);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullRequest3ArgConstructor() throws Exception {
        producer = new BasicAsyncRequestProducer(target, null, entity);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullTarget2ArgConstructor() throws Exception {
        producer = new BasicAsyncRequestProducer(null, request);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullRequest2ArgConstructor() throws Exception {
        producer = new BasicAsyncRequestProducer(target, null);
    }

    @Test
    public void testGenerateRequest() {
        HttpRequest res = producer.generateRequest();

        Assert.assertEquals(request, res);
    }

    @Test
    public void testGetTarget() {
        HttpHost res = producer.getTarget();

        Assert.assertEquals(target, res);
    }

    @Test
    public void testProduceContentEncoderCompleted() throws Exception {
        when(encoder.isCompleted()).thenReturn(true);

        producer.produceContent(encoder,  null);

        verify(entity, times(1)).finish();
    }

    @Test
    public void testProduceContentEncoderNotCompleted() throws Exception {
        when(encoder.isCompleted()).thenReturn(false);

        producer.produceContent(encoder,  null);

        verify(entity, times(0)).finish();
    }

    @Test
    public void testResetRequest() throws Exception {
        producer.resetRequest();
        verify(entity, times(1)).finish();
    }

    @Test
    public void testClose() throws Exception {
        producer.close();
        verify(entity, times(1)).finish();
    }

}
