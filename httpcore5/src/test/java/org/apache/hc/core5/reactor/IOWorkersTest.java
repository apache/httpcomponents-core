package org.apache.hc.core5.reactor;

import org.junit.Test;

import static org.mockito.Mockito.mock;

public class IOWorkersTest {

    @Test
    public void testIndexOverflow() {
        final SingleCoreIOReactor reactor = new SingleCoreIOReactor(null, mock(IOEventHandlerFactory.class), IOReactorConfig.DEFAULT, null, null, null);
        final IOWorkers.Selector selector = IOWorkers.newSelector(new SingleCoreIOReactor[]{reactor, reactor, reactor});
        for (long i = 0; i < (long) Integer.MAX_VALUE + 10; i++) {
            selector.next();
        }
    }

}