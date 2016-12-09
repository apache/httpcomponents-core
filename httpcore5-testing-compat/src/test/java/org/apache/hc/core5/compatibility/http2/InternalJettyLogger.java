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

package org.apache.hc.core5.compatibility.http2;

import org.apache.logging.log4j.LogManager;
import org.eclipse.jetty.util.log.AbstractLogger;
import org.eclipse.jetty.util.log.Logger;

class InternalJettyLogger extends AbstractLogger {

    private final String name;
    private final org.apache.logging.log4j.Logger logger;

    InternalJettyLogger(final String name) {
        this.name = name;
        this.logger = LogManager.getLogger(name);
    }

    @Override
    protected Logger newLogger(final String fullname) {
        return new InternalJettyLogger(fullname);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void warn(final String msg, final Object... args) {
        logger.warn(msg, args);
    }

    @Override
    public void warn(final Throwable thrown) {
        logger.warn(thrown);
    }

    @Override
    public void warn(final String msg, final Throwable thrown) {
        logger.warn(msg, thrown);
    }

    @Override
    public void info(final String msg, final Object... args) {
        logger.info(msg, args);
    }

    @Override
    public void info(final Throwable thrown) {
        logger.info(thrown);
    }

    @Override
    public void info(final String msg, final Throwable thrown) {
        logger.info(msg, thrown);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public void setDebugEnabled(final boolean enabled) {
    }

    @Override
    public void debug(final String msg, final Object... args) {
        logger.debug(msg, args);
    }

    @Override
    public void debug(final Throwable thrown) {
        logger.debug(thrown);
    }

    @Override
    public void debug(final String msg, final Throwable thrown) {
        logger.debug(msg, thrown);
    }

    @Override
    public void ignore(final Throwable ignored) {
        logger.trace(ignored);
    }

}