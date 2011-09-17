package org.apache.http.nio.protocol;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import junit.framework.Assert;

import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TestBasicAsyncResponseConsumer {

    private BasicAsyncResponseConsumer consumer;
    @Mock private HttpResponse response;
    @Mock private HttpContext context;
    @Mock private ContentDecoder decoder;
    @Mock private IOControl ioctrl;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        consumer = Mockito.spy(new BasicAsyncResponseConsumer());
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullTargetArgConstructor() throws Exception {
        new BasicAsyncResponseConsumer(null);
    }

    @Test
    public void testResponseProcessing() throws Exception {
        when(response.getEntity()).thenReturn(new StringEntity("stuff"));

        consumer.responseReceived(response);
        consumer.consumeContent(decoder, ioctrl);
        consumer.responseCompleted(context);

        verify(consumer).releaseResources();
        verify(consumer).buildResult(context);
        Assert.assertTrue(consumer.isDone());
        Assert.assertSame(response, consumer.getResult());

        consumer.responseCompleted(context);
        verify(consumer, times(1)).releaseResources();
        verify(consumer, times(1)).buildResult(context);
    }

    @Test
    public void testResponseProcessingWithException() throws Exception {
        when(response.getEntity()).thenReturn(new StringEntity("stuff"));
        RuntimeException ooopsie = new RuntimeException();
        when(consumer.buildResult(context)).thenThrow(ooopsie);

        consumer.responseReceived(response);
        consumer.consumeContent(decoder, ioctrl);
        consumer.responseCompleted(context);

        verify(consumer).releaseResources();
        Assert.assertTrue(consumer.isDone());
        Assert.assertSame(ooopsie, consumer.getException());
    }

    @Test
    public void testCancel() throws Exception {
        Assert.assertTrue(consumer.cancel());

        verify(consumer).releaseResources();
        Assert.assertTrue(consumer.isDone());

        Assert.assertFalse(consumer.cancel());
        verify(consumer, times(1)).releaseResources();
    }

    @Test
    public void testFailed() throws Exception {
        RuntimeException ooopsie = new RuntimeException();

        consumer.failed(ooopsie);

        verify(consumer).releaseResources();
        Assert.assertTrue(consumer.isDone());
        Assert.assertSame(ooopsie, consumer.getException());
    }

    @Test
    public void testFailedAfterDone() throws Exception {
        RuntimeException ooopsie = new RuntimeException();

        consumer.cancel();
        consumer.failed(ooopsie);

        verify(consumer, times(1)).releaseResources();
        Assert.assertTrue(consumer.isDone());
        Assert.assertNull(consumer.getException());
    }

    @Test
    public void testClose() throws Exception {
        consumer.close();

        verify(consumer).releaseResources();
        Assert.assertTrue(consumer.isDone());

        consumer.close();

        verify(consumer, times(1)).releaseResources();
    }

}
