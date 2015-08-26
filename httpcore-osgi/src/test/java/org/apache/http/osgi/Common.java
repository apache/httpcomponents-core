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

package org.apache.http.osgi;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemPackages;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;

/**
 * Test inherit from this.
 */
public class Common {

    public static String getDependencyVersion(final String groupId, final String artifactId) {
        final URL depPropsUrl = BasicIT.class.getResource("META-INF/maven/dependencies.properties");
        final Properties depProps = new Properties();
        try {
            depProps.load(depPropsUrl.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String ver = (String) depProps.get(String.format("%s/%s/version", groupId, artifactId));
        if (ver == null) {
            throw new RuntimeException(String.format("No version available for %s:%s", groupId, artifactId));
        }
        ver = ver.replace("-SNAPSHOT", ".SNAPSHOT");
        return ver;
    }


    @Configuration
    public Option[] config() {
        final String projectVersion = System.getProperty("project.version");
        final String buildDir = System.getProperty("project.build.directory", "target");
        final String paxLoggingLevel = System.getProperty("bt.osgi.pax.logging.level", "WARN");

        return options(
                bundle(String.format("%s/org.apache.httpcomponents.httpcore_%s",
                        buildDir,
                        projectVersion)),
                wrappedBundle(mavenBundle().groupId("org.apache.httpcomponents")
                  .artifactId("httpcore")
                  .version(projectVersion)
                  .type("test-jar")),
                mavenBundle("org.mockito", "mockito-core", getDependencyVersion("org.mockito", "mockito-core")),
                systemPackages(
                        String.format("org.slf4j;version=\"%s\"", getDependencyVersion("org.slf4j", "slf4j-api"))
                ),
                junitBundles(),
                systemProperty("pax.exam.osgi.unresolved.fail").value("true"),
                systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value(paxLoggingLevel)
        );
    }
}
