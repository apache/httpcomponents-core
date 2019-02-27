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

/**
 * Core HTTP transport APIs based on the asynchronous, event driven I/O model.
 * <p>
 * The application programming interface is based on the concept
 * of channels and event handlers. The channels act as conduits
 * for asynchronous data output. They are generally expected to
 * be thread-safe and could be used by multiple threads concurrently.
 * The event handlers react to asynchronous signals or events and
 * communicate with the opposite endpoint through available channels.
 * Event handlers can be specialized as data producers,
 * data consumers or can be both. Generally event handlers can only
 * be used by a single thread at a time and do not require synchronization
 * as long as they do not interact with event handlers run by separate
 * threads.
 * </p>
 */
package org.apache.hc.core5.http.nio;
