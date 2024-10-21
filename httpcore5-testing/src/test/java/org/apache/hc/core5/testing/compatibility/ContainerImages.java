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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

public final class ContainerImages {

    private static final Logger LOG = LoggerFactory.getLogger(ContainerImages.class);

    public static final String AAA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    public static final String BBB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    public static final String CCC = "ccccccccccccccccccccccccccccccccccccccccccccccccc";
    public static final String PUSHY = "I am being very pushy";

    public static final String APACHE_HTTPD = "test-apache";
    public static final String NGINX = "test-nginx";
    public static final String HTTPBIN = "test-httpbin";
    public static final String DANTE = "test-dante";

    public static int HTTP_PORT = 80;
    public static int H2C_PORT = 81;
    public static int HTTPS_PORT = 443;
    public static int SOCKS_PORT = 1080;

    public static GenericContainer<?> httpBin(final Network network) {
        return new GenericContainer<>(DockerImageName.parse("kennethreitz/httpbin:latest"))
                .withNetwork(network)
                .withNetworkAliases(HTTPBIN)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .withExposedPorts(HTTP_PORT);
    }

    public static GenericContainer<?> apacheHttpD(final Network network) {
        return new GenericContainer<>(new ImageFromDockerfile()
                .withFileFromClasspath("httpd-default.conf", "docker/httpd/httpd-default.conf")
                .withFileFromClasspath("httpd-h2c.conf", "docker/httpd/httpd-h2c.conf")
                .withFileFromClasspath("httpd-ssl.conf", "docker/httpd/httpd-ssl.conf")
                .withFileFromClasspath("server-cert.pem", "docker/server-cert.pem")
                .withFileFromClasspath("server-key.pem", "docker/server-key.pem")
                .withFileFromString("pushy", PUSHY)
                .withFileFromString("aaa", AAA)
                .withFileFromString("bbb", BBB)
                .withFileFromString("ccc", CCC)
                .withDockerfileFromBuilder(builder -> builder
                        .from("httpd:2.4")
                        .env("var_dir", "/var/httpd")
                        .env("www_dir", "${var_dir}/www")
                        .run("mkdir -p ${var_dir}")
                        .run("mkdir -p ${www_dir}")
                        .run("echo '\\n" +
                                "LoadModule http2_module modules/mod_http2.so\\n" +
                                "LoadModule ssl_module modules/mod_ssl.so\\n" +
                                "Include conf/extra/httpd-default.conf\\n" +
                                "Include conf/extra/httpd-h2c.conf\\n" +
                                "Include conf/extra/httpd-ssl.conf\\n" +
                                "'" +
                                " >> /usr/local/apache2/conf/httpd.conf")
                        .copy("httpd-default.conf", "/usr/local/apache2/conf/extra/httpd-default.conf")
                        .copy("httpd-h2c.conf", "/usr/local/apache2/conf/extra/httpd-h2c.conf")
                        .copy("httpd-ssl.conf", "/usr/local/apache2/conf/extra/httpd-ssl.conf")
                        .copy("server-cert.pem", "/usr/local/apache2/conf/server-cert.pem")
                        .copy("server-key.pem", "/usr/local/apache2/conf/server-key.pem")
                        .copy("pushy", "${www_dir}/")
                        .copy("aaa", "${www_dir}/")
                        .copy("bbb", "${www_dir}/")
                        .copy("ccc", "${www_dir}/")
                        .build()))
                .withNetwork(network)
                .withNetworkAliases(APACHE_HTTPD)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .withExposedPorts(HTTP_PORT, H2C_PORT, HTTPS_PORT);
    }

    public static GenericContainer<?> nginx(final Network network) {
        return new GenericContainer<>(new ImageFromDockerfile()
                .withFileFromClasspath("default.conf", "docker/nginx/default.conf")
                .withFileFromClasspath("h2c.conf", "docker/nginx/h2c.conf")
                .withFileFromClasspath("ssl.conf", "docker/nginx/ssl.conf")
                .withFileFromClasspath("server-cert.pem", "docker/server-cert.pem")
                .withFileFromClasspath("server-key.pem", "docker/server-key.pem")
                .withFileFromString("pushy", PUSHY)
                .withFileFromString("aaa", AAA)
                .withFileFromString("bbb", BBB)
                .withFileFromString("ccc", CCC)
                .withDockerfileFromBuilder(builder -> builder
                        .from("nginx:1.23")
                        .env("var_dir", "/var/nginx")
                        .env("www_dir", "${var_dir}/www")
                        .run("mkdir -p ${var_dir}")
                        .copy("default.conf", "/etc/nginx/conf.d/default.conf")
                        .copy("h2c.conf", "/etc/nginx/conf.d/h2c.conf")
                        .copy("ssl.conf", "/etc/nginx/conf.d/ssl.conf")
                        .copy("server-cert.pem", "/etc/nginx/server-cert.pem")
                        .copy("server-key.pem", "/etc/nginx/server-key.pem")
                        .copy("pushy", "${www_dir}/")
                        .copy("aaa", "${www_dir}/")
                        .copy("bbb", "${www_dir}/")
                        .copy("ccc", "${www_dir}/")
                        .build()))
                .withNetwork(network)
                .withNetworkAliases(NGINX)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .withExposedPorts(HTTP_PORT, H2C_PORT, HTTPS_PORT);
    }

    public static GenericContainer<?> dante(final Network network) {
        return new GenericContainer<>(new ImageFromDockerfile()
                .withDockerfileFromBuilder(builder -> builder
                        .from("vimagick/dante:latest")
                        .run("useradd socks")
                        .run("echo socks:nopassword | chpasswd")
                        .build()))
                .withNetwork(network)
                .withNetworkAliases(DANTE)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .withExposedPorts(SOCKS_PORT);
    }

}
