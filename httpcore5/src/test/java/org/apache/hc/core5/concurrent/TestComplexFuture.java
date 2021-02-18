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
package org.apache.hc.core5.concurrent;

import java.util.concurrent.Future;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

public class TestComplexFuture {

    @Test
    public void testCancelled() throws Exception {
        final ComplexFuture<Object> future = new ComplexFuture<>(null);

        final Future<Object> dependency1 = new BasicFuture<>(null);
        future.setDependency(dependency1);

        Assert.assertFalse(future.isDone());

        future.cancel();
        MatcherAssert.assertThat(future.isCancelled(), CoreMatchers.is(true));
        MatcherAssert.assertThat(dependency1.isCancelled(), CoreMatchers.is(true));

        final Future<Object> dependency2 = new BasicFuture<>(null);
        future.setDependency(dependency2);
        MatcherAssert.assertThat(dependency2.isCancelled(), CoreMatchers.is(true));
    }

    @Test
    public void testCompleted() throws Exception {
        final ComplexFuture<Object> future = new ComplexFuture<>(null);

        final Future<Object> dependency1 = new BasicFuture<>(null);
        future.setDependency(dependency1);

        Assert.assertFalse(future.isDone());

        future.completed(Boolean.TRUE);
        MatcherAssert.assertThat(future.isCancelled(), CoreMatchers.is(false));
        MatcherAssert.assertThat(dependency1.isCancelled(), CoreMatchers.is(false));

        final Future<Object> dependency2 = new BasicFuture<>(null);
        future.setDependency(dependency2);
        MatcherAssert.assertThat(dependency2.isCancelled(), CoreMatchers.is(true));
    }

    @Test
    public void testCancelledWithCallback() throws Exception {
        final ComplexFuture<Object> future = new ComplexFuture<>(null);

        final Future<Object> dependency1 = new BasicFuture<>(new FutureContribution<Object>(future) {

            @Override
            public void completed(final Object result) {
            }

        });
        future.setDependency(dependency1);

        Assert.assertFalse(future.isDone());

        future.cancel();
        MatcherAssert.assertThat(future.isCancelled(), CoreMatchers.is(true));
        MatcherAssert.assertThat(dependency1.isCancelled(), CoreMatchers.is(true));

        final Future<Object> dependency2 = new BasicFuture<>(null);
        future.setDependency(dependency2);
        MatcherAssert.assertThat(dependency2.isCancelled(), CoreMatchers.is(true));
    }

}
