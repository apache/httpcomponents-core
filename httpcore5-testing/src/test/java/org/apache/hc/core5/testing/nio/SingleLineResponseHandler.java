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
package org.apache.hc.core5.testing.nio;

import java.io.IOException;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.BasicResponseProducer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.RequestConsumerSupplier;
import org.apache.hc.core5.http.nio.support.ResponseHandler;
import org.apache.hc.core5.http.nio.support.ResponseTrigger;
import org.apache.hc.core5.http.protocol.HttpContext;

public class SingleLineResponseHandler extends BasicServerExchangeHandler<Message<HttpRequest, String>> {

    public SingleLineResponseHandler(final String message) {
        super(new RequestConsumerSupplier<Message<HttpRequest, String>>() {

                  @Override
                  public AsyncRequestConsumer<Message<HttpRequest, String>> get(
                          final HttpRequest request,
                          final HttpContext context) throws HttpException {
                      return new BasicRequestConsumer<>(new StringAsyncEntityConsumer());
                  }

              }, new ResponseHandler<Message<HttpRequest, String>>() {

                  @Override
                  public void handle(
                          final Message<HttpRequest, String> requestMessage,
                          final ResponseTrigger responseTrigger,
                          final HttpContext context) throws HttpException, IOException {
                      responseTrigger.submitResponse(new BasicResponseProducer(
                              HttpStatus.SC_OK, new BasicAsyncEntityProducer(message)));
                  }
              }
        );
    }

}
