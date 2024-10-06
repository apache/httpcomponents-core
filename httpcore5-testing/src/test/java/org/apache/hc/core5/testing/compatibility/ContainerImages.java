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
package org.apache.hc.core5.testing.compatibility;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

public final class ContainerImages {

    public static final String AAA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    public static final String BBB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    public static final String CCC = "ccccccccccccccccccccccccccccccccccccccccccccccccc";
    public static final String PUSHY = "I am being very pushy";

    public static int HTTP_PORT = 80;

    public static GenericContainer<?> httpBin() {
        return new GenericContainer<>(DockerImageName.parse("kennethreitz/httpbin:latest"))
                .withExposedPorts(HTTP_PORT);
    }

    public static GenericContainer<?> apacheHttpD() {
        return new GenericContainer<>(new ImageFromDockerfile()
                .withFileFromClasspath("httpd-vhosts.conf", "docker/httpd/httpd-vhosts.conf")
                .withFileFromString("pushy", PUSHY)
                .withFileFromString("aaa", AAA)
                .withFileFromString("bbb", BBB)
                .withFileFromString("ccc", CCC)
                .withDockerfileFromBuilder(builder ->
                        builder
                                .from("httpd:2.4")
                                .env("var_dir", "/var/httpd")
                                .env("www_dir", "${var_dir}/www")
                                .run("mkdir -p ${var_dir}")
                                .run("sed -i -E 's/^\\s*#\\s*(LoadModule\\s+http2_module\\s+modules\\/mod_http2.so)/\\1/' conf/httpd.conf")
                                .run("sed -i -E 's/^\\s*ServerAdmin.*$/ServerAdmin dev@hc.apache.org/' conf/httpd.conf")
                                .run("sed -i -E 's/^\\s*#\\s*(Include\\s+conf\\/extra\\/httpd-vhosts.conf)/\\1/' conf/httpd.conf")
                                .copy("httpd-vhosts.conf", "/usr/local/apache2/conf/extra/httpd-vhosts.conf")
                                .copy("pushy", "${www_dir}/")
                                .copy("aaa", "${www_dir}/")
                                .copy("bbb", "${www_dir}/")
                                .copy("ccc", "${www_dir}/")
                                .build()))
                .withExposedPorts(HTTP_PORT);
    }

    public static GenericContainer<?> ngnix() {
        return new GenericContainer<>(new ImageFromDockerfile()
                .withFileFromClasspath("default.conf", "docker/ngnix/default.conf")
                .withFileFromString("pushy", PUSHY)
                .withFileFromString("aaa", AAA)
                .withFileFromString("bbb", BBB)
                .withFileFromString("ccc", CCC)
                .withDockerfileFromBuilder(builder ->
                        builder
                                .from("nginx:1.22")
                                .env("var_dir", "/var/nginx")
                                .env("www_dir", "${var_dir}/www")
                                .run("mkdir -p ${var_dir}")
                                .copy("default.conf", "/etc/nginx/conf.d/default.conf")
                                .copy("pushy", "${www_dir}/")
                                .copy("aaa", "${www_dir}/")
                                .copy("bbb", "${www_dir}/")
                                .copy("ccc", "${www_dir}/")
                                .build()))
                .withExposedPorts(HTTP_PORT);
    }

}
