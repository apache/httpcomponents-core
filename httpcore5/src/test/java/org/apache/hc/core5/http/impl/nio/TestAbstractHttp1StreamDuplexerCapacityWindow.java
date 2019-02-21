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

package org.apache.hc.core5.http.impl.nio;

import org.apache.hc.core5.http.impl.nio.AbstractHttp1StreamDuplexer.CapacityWindow;
import org.apache.hc.core5.reactor.IOSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.nio.channels.SelectionKey;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

public class TestAbstractHttp1StreamDuplexerCapacityWindow {
    @Mock private IOSession ioSession;

    @Before
    public void before() {
        initMocks(this);
    }

    @Test
    public void testWindowUpdate() throws IOException {
        final CapacityWindow window = new CapacityWindow(0, ioSession);
        window.update(1);
        assertEquals(1, window.getWindow());
        verify(ioSession).setEvent(eq(SelectionKey.OP_READ));
        verifyNoMoreInteractions(ioSession);
    }

    @Test
    public void testRemoveCapacity() {
        final CapacityWindow window = new CapacityWindow(1, ioSession);
        window.removeCapacity(1);
        assertEquals(0, window.getWindow());
        verify(ioSession).clearEvent(eq(SelectionKey.OP_READ));
        verifyNoMoreInteractions(ioSession);
    }

    @Test
    public void noReadsSetAfterWindowIsClosed() throws IOException {
        final CapacityWindow window = new CapacityWindow(1, ioSession);
        window.close();
        window.update(1);
        verifyZeroInteractions(ioSession);
    }

    @Test
    public void windowCannotUnderflow() {
        final CapacityWindow window = new CapacityWindow(Integer.MIN_VALUE, ioSession);
        window.removeCapacity(1);
        assertEquals(Integer.MIN_VALUE, window.getWindow());
    }

    @Test
    public void windowCannotOverflow() throws IOException{
        final CapacityWindow window = new CapacityWindow(Integer.MAX_VALUE, ioSession);
        window.update(1);
        assertEquals(Integer.MAX_VALUE, window.getWindow());
    }

    @Test
    public void noChangesIfUpdateIsNonPositive() throws IOException {
        final CapacityWindow window = new CapacityWindow(1, ioSession);
        window.update(0);
        window.update(-1);
        assertEquals(1, window.getWindow());
        verifyZeroInteractions(ioSession);
    }
}
