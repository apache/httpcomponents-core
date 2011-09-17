package org.apache.http.nio.protocol;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import junit.framework.Assert;

import org.apache.http.HttpResponse;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.entity.ProducingNHttpEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TestBasicAsyncResponseProducer {

    private BasicAsyncResponseProducer producer;
    @Mock private ProducingNHttpEntity entity;
    @Mock private HttpResponse response;
    @Mock private ContentEncoder encoder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(response.getEntity()).thenReturn(entity);

        producer = new BasicAsyncResponseProducer(response);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullTargetArgConstructor() throws Exception {
        producer = new BasicAsyncResponseProducer(null);
    }

    @Test
    public void testGenerateRequest() {
        HttpResponse res = producer.generateResponse();

        Assert.assertSame(response, res);
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

        verify(entity, never()).finish();
    }

    @Test
    public void testClose() throws Exception {
        producer.close();
        verify(entity, times(1)).finish();
    }

}
