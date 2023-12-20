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
package org.apache.hc.core5.net;

import static org.hamcrest.MatcherAssert.assertThat;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.NameValuePairListMatcher;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class TestURIBuilder {

    private static final String CH_HELLO = "\u0047\u0072\u00FC\u0065\u007A\u0069\u005F\u007A\u00E4\u006D\u00E4";
    private static final String RU_HELLO = "\u0412\u0441\u0435\u043C\u005F\u043F\u0440\u0438\u0432\u0435\u0442";

    static List<String> parsePath(final CharSequence s) {
        return URIBuilder.parsePath(s, null);
    }

    @Test
    public void testParseSegments() throws Exception {
        assertThat(parsePath("/this/that"), CoreMatchers.equalTo(Arrays.asList("this", "that")));
        assertThat(parsePath("this/that"), CoreMatchers.equalTo(Arrays.asList("this", "that")));
        assertThat(parsePath("this//that"), CoreMatchers.equalTo(Arrays.asList("this", "", "that")));
        assertThat(parsePath("this//that/"), CoreMatchers.equalTo(Arrays.asList("this", "", "that", "")));
        assertThat(parsePath("this//that/%2fthis%20and%20that"),
                CoreMatchers.equalTo(Arrays.asList("this", "", "that", "/this and that")));
        assertThat(parsePath("this///that//"),
                CoreMatchers.equalTo(Arrays.asList("this", "", "", "that", "", "")));
        assertThat(parsePath("/"), CoreMatchers.equalTo(Collections.singletonList("")));
        assertThat(parsePath(""), CoreMatchers.equalTo(Collections.<String>emptyList()));
    }

    static String formatPath(final String... pathSegments) {
        final StringBuilder buf = new StringBuilder();
        URIBuilder.formatPath(buf, Arrays.asList(pathSegments), false, null);
        return buf.toString();
    }

    @Test
    public void testFormatSegments() throws Exception {
        assertThat(formatPath("this", "that"), CoreMatchers.equalTo("/this/that"));
        assertThat(formatPath("this", "", "that"), CoreMatchers.equalTo("/this//that"));
        assertThat(formatPath("this", "", "that", "/this and that"),
                CoreMatchers.equalTo("/this//that/%2Fthis%20and%20that"));
        assertThat(formatPath("this", "", "", "that", "", ""),
                CoreMatchers.equalTo("/this///that//"));
        assertThat(formatPath(""), CoreMatchers.equalTo("/"));
        assertThat(formatPath(), CoreMatchers.equalTo(""));
    }

    static List<NameValuePair> parseQuery(final CharSequence s) {
        return URIBuilder.parseQuery(s, null, false);
    }

    @Test
    public void testParseQuery() throws Exception {
        assertThat(parseQuery(""), NameValuePairListMatcher.isEmpty());
        assertThat(parseQuery("Name0"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name0", null)));
        assertThat(parseQuery("Name1=Value1"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name1", "Value1")));
        assertThat(parseQuery("Name2="),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name2", "")));
        assertThat(parseQuery(" Name3  "),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name3", null)));
        assertThat(parseQuery("Name4=Value%204%21"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name4", "Value 4!")));
        assertThat(parseQuery("Name4=Value%2B4%21"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name4", "Value+4!")));
        assertThat(parseQuery("Name4=Value%204%21%20%214"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name4", "Value 4! !4")));
        assertThat(parseQuery("Name5=aaa&Name6=bbb"),
                NameValuePairListMatcher.equalsTo(
                        new BasicNameValuePair("Name5", "aaa"),
                        new BasicNameValuePair("Name6", "bbb")));
        assertThat(parseQuery("Name7=aaa&Name7=b%2Cb&Name7=ccc"),
                NameValuePairListMatcher.equalsTo(
                        new BasicNameValuePair("Name7", "aaa"),
                        new BasicNameValuePair("Name7", "b,b"),
                        new BasicNameValuePair("Name7", "ccc")));
        assertThat(parseQuery("Name8=xx%2C%20%20yy%20%20%2Czz"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name8", "xx,  yy  ,zz")));
        assertThat(parseQuery("price=10%20%E2%82%AC"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("price", "10 \u20AC")));
        assertThat(parseQuery("a=b\"c&d=e"),
                NameValuePairListMatcher.equalsTo(
                        new BasicNameValuePair("a", "b\"c"),
                        new BasicNameValuePair("d", "e")));
        assertThat(parseQuery("russian=" + PercentCodec.encode(RU_HELLO, StandardCharsets.UTF_8) +
                        "&swiss=" + PercentCodec.encode(CH_HELLO, StandardCharsets.UTF_8)),
                NameValuePairListMatcher.equalsTo(
                        new BasicNameValuePair("russian", RU_HELLO),
                        new BasicNameValuePair("swiss", CH_HELLO)));
    }

    static String formatQuery(final NameValuePair... params) {
        final StringBuilder buf = new StringBuilder();
        URIBuilder.formatQuery(buf, Arrays.asList(params), null, false);
        return buf.toString();
    }

    @Test
    public void testFormatQuery() throws Exception {
        assertThat(formatQuery(new BasicNameValuePair("Name0", null)), CoreMatchers.equalTo("Name0"));
        assertThat(formatQuery(new BasicNameValuePair("Name1", "Value1")), CoreMatchers.equalTo("Name1=Value1"));
        assertThat(formatQuery(new BasicNameValuePair("Name2", "")), CoreMatchers.equalTo("Name2="));
        assertThat(formatQuery(new BasicNameValuePair("Name4", "Value 4&")),
                CoreMatchers.equalTo("Name4=Value%204%26"));
        assertThat(formatQuery(new BasicNameValuePair("Name4", "Value+4&")),
                CoreMatchers.equalTo("Name4=Value%2B4%26"));
        assertThat(formatQuery(new BasicNameValuePair("Name4", "Value 4& =4")),
                CoreMatchers.equalTo("Name4=Value%204%26%20%3D4"));
        assertThat(formatQuery(
                new BasicNameValuePair("Name5", "aaa"),
                new BasicNameValuePair("Name6", "bbb")), CoreMatchers.equalTo("Name5=aaa&Name6=bbb"));
        assertThat(formatQuery(
                new BasicNameValuePair("Name7", "aaa"),
                new BasicNameValuePair("Name7", "b,b"),
                new BasicNameValuePair("Name7", "ccc")
        ), CoreMatchers.equalTo("Name7=aaa&Name7=b%2Cb&Name7=ccc"));
        assertThat(formatQuery(new BasicNameValuePair("Name8", "xx,  yy  ,zz")),
                CoreMatchers.equalTo("Name8=xx%2C%20%20yy%20%20%2Czz"));
        assertThat(formatQuery(
                new BasicNameValuePair("russian", RU_HELLO),
                new BasicNameValuePair("swiss", CH_HELLO)),
                CoreMatchers.equalTo("russian=" + PercentCodec.encode(RU_HELLO, StandardCharsets.UTF_8) +
                        "&swiss=" + PercentCodec.encode(CH_HELLO, StandardCharsets.UTF_8)));
    }

    @Test
    public void testHierarchicalUri() throws Exception {
        final URI uri = new URI("http", "stuff", "localhost", 80, "/some stuff", "param=stuff", "fragment");
        final URIBuilder uribuilder = new URIBuilder(uri);
        final URI result = uribuilder.build();
        Assertions.assertEquals(new URI("http://stuff@localhost:80/some%20stuff?param=stuff#fragment"), result);
    }

    @Test
    public void testMutationRemoveFragment() throws Exception {
        final URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        final URI result = new URIBuilder(uri).setFragment(null).build();
        Assertions.assertEquals(new URI("http://stuff@localhost:80/stuff?param=stuff"), result);
    }

    @Test
    public void testMutationRemoveUserInfo() throws Exception {
        final URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        final URI result = new URIBuilder(uri).setUserInfo(null).build();
        Assertions.assertEquals(new URI("http://localhost:80/stuff?param=stuff#fragment"), result);
    }

    @Test
    public void testMutationRemovePort() throws Exception {
        final URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        final URI result = new URIBuilder(uri).setPort(-1).build();
        Assertions.assertEquals(new URI("http://stuff@localhost/stuff?param=stuff#fragment"), result);
    }

    @Test
    public void testOpaqueUri() throws Exception {
        final URI uri = new URI("stuff", "some-stuff", "fragment");
        final URIBuilder uribuilder = new URIBuilder(uri);
        final URI result = uribuilder.build();
        Assertions.assertEquals(uri, result);
    }

    @Test
    public void testOpaqueUriMutation() throws Exception {
        final URI uri = new URI("stuff", "some-stuff", "fragment");
        final URIBuilder uribuilder = new URIBuilder(uri).setCustomQuery("param1&param2=stuff").setFragment(null);
        Assertions.assertEquals(new URI("stuff:?param1&param2=stuff"), uribuilder.build());
    }

    @Test
    public void testHierarchicalUriMutation() throws Exception {
        final URIBuilder uribuilder = new URIBuilder("/").setScheme("http").setHost("localhost").setPort(80).setPath("/stuff");
        Assertions.assertEquals(new URI("http://localhost:80/stuff"), uribuilder.build());
    }

    @Test
   public void testLocalhost() throws Exception {
       // Check that the URI generated by URI builder agrees with that generated by using URI directly
       final String scheme="https";
       final InetAddress host=InetAddress.getLocalHost();
       final String specials="/abcd!$&*()_-+.,=:;'~@[]?<>|#^%\"{}\\\u00a3`\u00ac\u00a6xyz"; // N.B. excludes space
       final URI uri = new URI(scheme, specials, host.getHostAddress(), 80, specials, specials, specials);

       final URI bld = URIBuilder.localhost()
               .setScheme(scheme)
               .setUserInfo(specials)
               .setPath(specials)
               .setCustomQuery(specials)
               .setFragment(specials)
               .build();

       Assertions.assertEquals(uri.getHost(), bld.getHost());

       Assertions.assertEquals(uri.getUserInfo(), bld.getUserInfo());

       Assertions.assertEquals(uri.getPath(), bld.getPath());

       Assertions.assertEquals(uri.getQuery(), bld.getQuery());

       Assertions.assertEquals(uri.getFragment(), bld.getFragment());
   }

    @Test
   public void testLoopbackAddress() throws Exception {
       // Check that the URI generated by URI builder agrees with that generated by using URI directly
       final String scheme="https";
       final InetAddress host=InetAddress.getLoopbackAddress();
       final String specials="/abcd!$&*()_-+.,=:;'~@[]?<>|#^%\"{}\\\u00a3`\u00ac\u00a6xyz"; // N.B. excludes space
       final URI uri = new URI(scheme, specials, host.getHostAddress(), 80, specials, specials, specials);

       final URI bld = URIBuilder.loopbackAddress()
               .setScheme(scheme)
               .setUserInfo(specials)
               .setPath(specials)
               .setCustomQuery(specials)
               .setFragment(specials)
               .build();

       Assertions.assertEquals(uri.getHost(), bld.getHost());

       Assertions.assertEquals(uri.getUserInfo(), bld.getUserInfo());

       Assertions.assertEquals(uri.getPath(), bld.getPath());

       Assertions.assertEquals(uri.getQuery(), bld.getQuery());

       Assertions.assertEquals(uri.getFragment(), bld.getFragment());
   }

    @Test
    public void testEmpty() throws Exception {
        final URIBuilder uribuilder = new URIBuilder();
        final URI result = uribuilder.build();
        Assertions.assertEquals(new URI(""), result);
    }

    @Test
    public void testEmptyPath() throws Exception {
        final URIBuilder uribuilder = new URIBuilder("http://thathost");
        Assertions.assertTrue(uribuilder.isPathEmpty());
    }

    @Test
    public void testRemoveParameter() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff&blah&blah", null);
        final URIBuilder uribuilder = new URIBuilder(uri);
        Assertions.assertFalse(uribuilder.isQueryEmpty());

        Assertions.assertThrows(NullPointerException.class, () -> uribuilder.removeParameter(null));

        uribuilder.removeParameter("DoesNotExist");
        Assertions.assertEquals("stuff", uribuilder.getFirstQueryParam("param").getValue());
        Assertions.assertNull(uribuilder.getFirstQueryParam("blah").getValue());

        uribuilder.removeParameter("blah");
        Assertions.assertEquals("stuff", uribuilder.getFirstQueryParam("param").getValue());
        Assertions.assertNull(uribuilder.getFirstQueryParam("blah"));

        uribuilder.removeParameter("param");
        Assertions.assertNull(uribuilder.getFirstQueryParam("param"));
        Assertions.assertTrue(uribuilder.isQueryEmpty());

        uribuilder.removeParameter("AlreadyEmpty");
        Assertions.assertTrue(uribuilder.isQueryEmpty());
        Assertions.assertEquals(new URI("http://localhost:80/"), uribuilder.build());
    }

    @Test
    public void testRemoveQuery() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff", null);
        final URIBuilder uribuilder = new URIBuilder(uri).removeQuery();
        final URI result = uribuilder.build();
        Assertions.assertEquals(new URI("http://localhost:80/"), result);
    }

    @Test
    public void testSetAuthorityFromNamedEndpointHost() throws Exception {
        final Host host = Host.create("localhost:88");
        final URIBuilder uribuilder = new URIBuilder().setScheme(URIScheme.HTTP.id).setAuthority(host);
        // Check builder
        Assertions.assertNull(uribuilder.getUserInfo());
        Assertions.assertEquals(host.getHostName(), uribuilder.getAuthority().getHostName());
        Assertions.assertEquals(host.getHostName(), uribuilder.getHost());
        // Check result
        final URI result = uribuilder.build();
        Assertions.assertEquals(host.getHostName(), result.getHost());
        Assertions.assertEquals(host.getPort(), result.getPort());
        Assertions.assertEquals(new URI("http://localhost:88"), result);
    }

    @Test
    public void testSetAuthorityFromNamedEndpointHttpHost() throws Exception {
        final HttpHost httpHost = HttpHost.create("localhost:88");
        final URIBuilder uribuilder = new URIBuilder().setScheme(URIScheme.HTTP.id).setAuthority(httpHost);
        // Check builder
        Assertions.assertNull(uribuilder.getUserInfo());
        Assertions.assertEquals(httpHost.getHostName(), uribuilder.getAuthority().getHostName());
        Assertions.assertEquals(httpHost.getHostName(), uribuilder.getHost());
        // Check result
        final URI result = uribuilder.build();
        Assertions.assertEquals(httpHost.getHostName(), result.getHost());
        Assertions.assertEquals(httpHost.getPort(), result.getPort());
        Assertions.assertEquals(new URI("http://localhost:88"), result);
    }

    @Test
    public void testSetAuthorityFromURIAuthority() throws Exception {
        final URIAuthority authority = URIAuthority.create("u:p@localhost:88");
        final URIBuilder uribuilder = new URIBuilder().setScheme(URIScheme.HTTP.id).setAuthority(authority);
        // Check builder
        Assertions.assertEquals(authority.getUserInfo(), uribuilder.getAuthority().getUserInfo());
        Assertions.assertEquals(authority.getHostName(), uribuilder.getAuthority().getHostName());
        Assertions.assertEquals(authority.getHostName(), uribuilder.getHost());
        // Check result
        final URI result = uribuilder.build();
        Assertions.assertEquals(authority.getUserInfo(), result.getUserInfo());
        Assertions.assertEquals(authority.getHostName(), result.getHost());
        Assertions.assertEquals(authority.getPort(), result.getPort());
        Assertions.assertEquals(authority.toString(), result.getAuthority());
        Assertions.assertEquals(new URI("http://u:p@localhost:88"), result);
    }

    @Test
    public void testSetParameter() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff&blah&blah", null);
        final URIBuilder uribuilder = new URIBuilder(uri).setParameter("param", "some other stuff")
                .setParameter("blah", "blah")
                .setParameter("blah", "blah2");
        final URI result = uribuilder.build();
        Assertions.assertEquals(new URI("http://localhost:80/?param=some%20other%20stuff&blah=blah2"), result);
    }

    @Test
    public void testGetFirstNamedParameter() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff&blah&blah", null);
        URIBuilder uribuilder = new URIBuilder(uri).setParameter("param", "some other stuff")
            .setParameter("blah", "blah");
        Assertions.assertEquals("some other stuff", uribuilder.getFirstQueryParam("param").getValue());
        Assertions.assertEquals("blah", uribuilder.getFirstQueryParam("blah").getValue());
        Assertions.assertNull(uribuilder.getFirstQueryParam("DoesNotExist"));
        //
        uribuilder = new URIBuilder("http://localhost:80/?param=some%20other%20stuff&blah=blah&blah=blah2");
        Assertions.assertEquals("blah", uribuilder.getFirstQueryParam("blah").getValue());
        uribuilder.removeQuery();
        Assertions.assertNull(uribuilder.getFirstQueryParam("param"));
    }

    @Test
    public void testSetParametersWithEmptyArrayArg() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/test", "param=test", null);
        final URIBuilder uribuilder = new URIBuilder(uri).setParameters();
        final URI result = uribuilder.build();
        Assertions.assertEquals(new URI("http://localhost:80/test"), result);
    }

    @Test
    public void testSetParametersWithNullArrayArg() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/test", "param=test", null);
        final URIBuilder uribuilder = new URIBuilder(uri).setParameters((NameValuePair[]) null);
        final URI result = uribuilder.build();
        Assertions.assertEquals(new URI("http://localhost:80/test"), result);
    }

    @Test
    public void testSetParametersWithEmptyList() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/test", "param=test", null);
        final URIBuilder uribuilder = new URIBuilder(uri).setParameters(Collections.emptyList());
        final URI result = uribuilder.build();
        Assertions.assertEquals(new URI("http://localhost:80/test"), result);
    }

    @Test
    public void testSetParametersWithNullList() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/test", "param=test", null);
        final URIBuilder uribuilder = new URIBuilder(uri).setParameters((List<NameValuePair>) null);
        final URI result = uribuilder.build();
        Assertions.assertEquals(new URI("http://localhost:80/test"), result);
    }

    @Test
    public void testParameterWithSpecialChar() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff", null);
        final URIBuilder uribuilder = new URIBuilder(uri).addParameter("param", "1 + 1 = 2")
            .addParameter("param", "blah&blah");
        final URI result = uribuilder.build();
        Assertions.assertEquals(new URI("http://localhost:80/?param=stuff&param=1%20%2B%201%20%3D%202&" +
                "param=blah%26blah"), result);
    }

    @Test
    public void testAddParameter() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff&blah&blah", null);
        final URIBuilder uribuilder = new URIBuilder(uri).addParameter("param", "some other stuff")
            .addParameter("blah", "blah");
        final URI result = uribuilder.build();
        Assertions.assertEquals(new URI("http://localhost:80/?param=stuff&blah&blah&" +
                "param=some%20other%20stuff&blah=blah"), result);
    }

    @Test
    public void testQueryEncoding() throws Exception {
        final URI uri1 = new URI("https://somehost.com/stuff?client_id=1234567890" +
                "&redirect_uri=https%3A%2F%2Fsomehost.com%2Fblah%20blah%2F");
        final URI uri2 = new URIBuilder("https://somehost.com/stuff")
            .addParameter("client_id","1234567890")
            .addParameter("redirect_uri","https://somehost.com/blah blah/").build();
        Assertions.assertEquals(uri1, uri2);
    }

    @Test
    public void testQueryAndParameterEncoding() throws Exception {
        final URI uri1 = new URI("https://somehost.com/stuff?param1=12345&param2=67890");
        final URI uri2 = new URIBuilder("https://somehost.com/stuff")
            .setCustomQuery("this&that")
            .addParameter("param1","12345")
            .addParameter("param2","67890").build();
        Assertions.assertEquals(uri1, uri2);
    }

    @Test
    public void testPathEncoding() throws Exception {
        final URI uri1 = new URI("https://somehost.com/some%20path%20with%20blanks/");
        final URI uri2 = new URIBuilder()
            .setScheme("https")
            .setHost("somehost.com")
            .setPath("/some path with blanks/")
            .build();
        Assertions.assertEquals(uri1, uri2);
    }

    @Test
    public void testAgainstURI() throws Exception {
        // Check that the URI generated by URI builder agrees with that generated by using URI directly
        final String scheme="https";
        final String host="localhost";
        final String specials="/abcd!$&*()_-+.,=:;'~@[]?<>|#^%\"{}\\\u00a3`\u00ac\u00a6xyz"; // N.B. excludes space
        final URI uri = new URI(scheme, specials, host, 80, specials, specials, specials);

        final URI bld = new URIBuilder()
                .setScheme(scheme)
                .setHost(host)
                .setUserInfo(specials)
                .setPath(specials)
                .setCustomQuery(specials)
                .setFragment(specials)
                .build();

        Assertions.assertEquals(uri.getHost(), bld.getHost());

        Assertions.assertEquals(uri.getUserInfo(), bld.getUserInfo());

        Assertions.assertEquals(uri.getPath(), bld.getPath());

        Assertions.assertEquals(uri.getQuery(), bld.getQuery());

        Assertions.assertEquals(uri.getFragment(), bld.getFragment());

    }

    @Test
    public void testBuildAddParametersUTF8() throws Exception {
        assertAddParameters(StandardCharsets.UTF_8);
    }

    @Test
    public void testBuildAddParametersISO88591() throws Exception {
        assertAddParameters(StandardCharsets.ISO_8859_1);
    }

    public void assertAddParameters(final Charset charset) throws Exception {
        final URI uri = new URIBuilder("https://somehost.com/stuff")
                .setCharset(charset)
                .addParameters(createParameterList()).build();

        assertBuild(charset, uri);
        // null addParameters
        final URI uri2 = new URIBuilder("https://somehost.com/stuff")
                .setCharset(charset)
                .addParameters(null).build();

        Assertions.assertEquals("https://somehost.com/stuff", uri2.toString());
    }

    @Test
    public void testBuildSetParametersUTF8() throws Exception {
        assertSetParameters(StandardCharsets.UTF_8);
    }

    @Test
    public void testBuildSetParametersISO88591() throws Exception {
        assertSetParameters(StandardCharsets.ISO_8859_1);
    }

    public void assertSetParameters(final Charset charset) throws Exception {
        final URI uri = new URIBuilder("https://somehost.com/stuff")
                .setCharset(charset)
                .setParameters(createParameterList()).build();

        assertBuild(charset, uri);
    }

    public void assertBuild(final Charset charset, final URI uri) throws Exception {
        final String encodedData1 = PercentCodec.encode("\"1\u00aa position\"", charset);
        final String encodedData2 = PercentCodec.encode("Jos\u00e9 Abra\u00e3o", charset);

        final String uriExpected = String.format("https://somehost.com/stuff?parameter1=value1&parameter2=%s&parameter3=%s", encodedData1, encodedData2);

        Assertions.assertEquals(uriExpected, uri.toString());
    }

    private List<NameValuePair> createParameterList() {
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("parameter1", "value1"));
        parameters.add(new BasicNameValuePair("parameter2", "\"1\u00aa position\""));
        parameters.add(new BasicNameValuePair("parameter3", "Jos\u00e9 Abra\u00e3o"));
        return parameters;
    }

    @Test
    public void testMalformedPath() throws Exception {
        final String path = "@notexample.com/mypath";
        final URI uri = new URIBuilder(path).setHost("example.com").build();
        Assertions.assertEquals("example.com", uri.getHost());
    }

    @Test
    public void testRelativePath() throws Exception {
        final URI uri = new URIBuilder("./mypath").build();
        Assertions.assertEquals(new URI("./mypath"), uri);
    }

    @Test
    public void testRelativePathWithAuthority() throws Exception {
        final URI uri = new URIBuilder("./mypath").setHost("somehost").setScheme("http").build();
        Assertions.assertEquals(new URI("http://somehost/./mypath"), uri);
    }

    @Test
    public void testTolerateNullInput() throws Exception {
        assertThat(new URIBuilder()
                        .setScheme(null)
                        .setHost("localhost")
                        .setUserInfo(null)
                        .setPort(8443)
                        .setPath(null)
                        .setCustomQuery(null)
                        .setFragment(null)
                        .build(),
                CoreMatchers.equalTo(URI.create("//localhost:8443")));
    }

    @Test
    public void testTolerateBlankInput() throws Exception {
        assertThat(new URIBuilder()
                        .setScheme("")
                        .setHost("localhost")
                        .setUserInfo("")
                        .setPort(8443)
                        .setPath("")
                        .setPath("")
                        .setCustomQuery("")
                        .setFragment("")
                        .build(),
                CoreMatchers.equalTo(URI.create("//localhost:8443")));
    }

    @Test
    public void testHttpHost() throws Exception {
        final HttpHost httpHost = new HttpHost("http", "example.com", 1234);
        final URIBuilder uribuilder = new URIBuilder();
        uribuilder.setHttpHost(httpHost);
        Assertions.assertEquals(URI.create("http://example.com:1234"), uribuilder.build());
    }

    @Test
    public void testSetHostWithReservedChars() throws Exception {
        final URIBuilder uribuilder = new URIBuilder();
        uribuilder.setScheme("http").setHost("!example!.com");
        Assertions.assertEquals(URI.create("http://%21example%21.com"), uribuilder.build());
    }

    @Test
    public void testGetHostWithReservedChars() throws Exception {
        final URIBuilder uribuilder = new URIBuilder("http://someuser%21@%21example%21.com/");
        Assertions.assertEquals("!example!.com", uribuilder.getHost());
        Assertions.assertEquals("someuser!", uribuilder.getUserInfo());
    }

    @Test
    public void testMultipleLeadingPathSlashes() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("ftp")
                .setHost("somehost")
                .setPath("//blah//blah")
                .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("ftp://somehost//blah//blah")));
    }

    @Test
    public void testNoAuthorityAndPath() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("file")
                .setPath("/blah")
                .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("file:/blah")));
    }

    @Test
    public void testSetPathSegmentList() throws Exception {
        final URI uri = new URIBuilder()
            .setScheme("https")
            .setHost("somehost")
            .setPathSegments(Arrays.asList("api", "products"))
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("https://somehost/api/products")));
    }

    @Test
    public void testSetPathSegmentsVarargs() throws Exception {
        final URI uri = new URIBuilder()
            .setScheme("https")
            .setHost("somehost")
            .setPathSegments("api", "products")
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("https://somehost/api/products")));
    }

    @Test
    public void testSetPathSegmentsRootlessList() throws Exception {
        final URI uri = new URIBuilder()
            .setScheme("file")
            .setPathSegmentsRootless(Arrays.asList("dir", "foo"))
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("file:dir/foo")));
    }

    @Test
    public void testSetPathSegmentsRootlessVarargs() throws Exception {
        final URI uri = new URIBuilder()
            .setScheme("file")
            .setPathSegmentsRootless("dir", "foo")
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("file:dir/foo")));
    }

    @Test
    public void testAppendToExistingPath() throws Exception {
        final URI uri = new URIBuilder()
            .setScheme("https")
            .setHost("somehost")
            .setPath("api")
            .appendPath("v1/resources")
            .appendPath("idA")
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("https://somehost/api/v1/resources/idA")));
    }

    @Test
    public void testAppendToNonExistingPath() throws Exception {
        final URI uri = new URIBuilder()
            .setScheme("https")
            .setHost("somehost")
            .appendPath("api/v2/customers")
            .appendPath("idA")
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("https://somehost/api/v2/customers/idA")));
    }

    @Test
    public void testAppendNullToExistingPath() throws Exception {
        final URI uri = new URIBuilder()
            .setScheme("https")
            .setHost("somehost")
            .setPath("api")
            .appendPath(null)
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("https://somehost/api")));
    }

    @Test
    public void testAppendNullToNonExistingPath() throws Exception {
        final URI uri = new URIBuilder()
            .setScheme("https")
            .setHost("somehost")
            .appendPath(null)
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("https://somehost")));
    }

    @Test
    public void testAppendSegmentsVarargsToExistingPath() throws Exception {
        final URI uri = new URIBuilder()
            .setScheme("https")
            .setHost("myhost")
            .setPath("api")
            .appendPathSegments("v3", "products")
            .appendPathSegments("idA")
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("https://myhost/api/v3/products/idA")));
    }

    @Test
    public void testAppendSegmentsVarargsToNonExistingPath() throws Exception {
        final URI uri = new URIBuilder()
            .setScheme("https")
            .setHost("somehost")
            .appendPathSegments("api", "v2", "customers")
            .appendPathSegments("idA")
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("https://somehost/api/v2/customers/idA")));
    }

    @Test
    public void testAppendNullSegmentsVarargs() throws Exception {
        final String pathSegment = null;
        final URI uri = new URIBuilder()
            .setScheme("https")
            .setHost("somehost")
            .appendPathSegments(pathSegment)
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("https://somehost/")));
    }

    @Test
    public void testAppendSegmentsListToExistingPath() throws Exception {
        final URI uri = new URIBuilder()
            .setScheme("http")
            .setHost("myhost")
            .setPath("api")
            .appendPathSegments(Arrays.asList("v3", "products"))
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("http://myhost/api/v3/products")));
    }

    @Test
    public void testAppendSegmentsListToNonExistingPath() throws Exception {
        final URI uri = new URIBuilder()
            .setScheme("http")
            .setHost("myhost")
            .appendPathSegments(Arrays.asList("api", "v3", "customers"))
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("http://myhost/api/v3/customers")));
    }

    @Test
    public void testAppendNullSegmentsList() throws Exception {
        final List<String> pathSegments = null;
        final URI uri = new URIBuilder()
            .setScheme("http")
            .setHost("myhost")
            .appendPathSegments(pathSegments)
            .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("http://myhost")));
    }

    @Test
    public void testNoAuthorityAndPathSegments() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("file")
                .setPathSegments("this", "that")
                .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("file:/this/that")));
    }

    @Test
    public void testNoAuthorityAndRootlessPath() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("file")
                .setPath("blah")
                .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("file:blah")));
    }

    @Test
    public void testNoAuthorityAndRootlessPathSegments() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("file")
                .setPathSegmentsRootless("this", "that")
                .build();
        assertThat(uri, CoreMatchers.equalTo(URI.create("file:this/that")));
    }

    @Test
    public void testOpaque() throws Exception {
        final URIBuilder uriBuilder = new URIBuilder("http://host.com");
        final URI uri = uriBuilder.build();
        assertThat(uriBuilder.isOpaque(), CoreMatchers.equalTo(uri.isOpaque()));
    }

    @Test
    public void testAddParameterEncodingEquivalence() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/",
                "param=stuff with spaces", null);
        final URIBuilder uribuilder = new URIBuilder().setScheme("http").setHost("localhost").setPort(80).setPath("/").addParameter(
                "param", "stuff with spaces");
        final URI result = uribuilder.build();
        Assertions.assertEquals(uri, result);
    }

    @Test
    public void testSchemeSpecificPartParametersNull() throws Exception {
       final URIBuilder uribuilder = new URIBuilder("http://host.com").setParameter("par", "parvalue")
               .setSchemeSpecificPart("", (NameValuePair)null);
       Assertions.assertEquals(new URI("http://host.com?par=parvalue"), uribuilder.build());
    }

    @Test
    public void testSchemeSpecificPartSetGet() throws Exception {
       final URIBuilder uribuilder = new URIBuilder().setSchemeSpecificPart("specificpart");
       Assertions.assertEquals("specificpart", uribuilder.getSchemeSpecificPart());
    }

    /** Common use case: mailto: scheme. See https://tools.ietf.org/html/rfc6068#section-2 */
    @Test
    public void testSchemeSpecificPartNameValuePairByRFC6068Sample() throws Exception {
       final URIBuilder uribuilder = new URIBuilder().setScheme("mailto")
               .setSchemeSpecificPart("my@email.server", new BasicNameValuePair("subject", "mail subject"));
       final String result = uribuilder.build().toString();
       Assertions.assertTrue(result.contains("my@email.server"), "mail address as scheme specific part expected");
       Assertions.assertTrue(result.contains("mail%20subject"), "correct parameter encoding expected for that scheme");
    }

    /** Common use case: mailto: scheme. See https://tools.ietf.org/html/rfc6068#section-2 */
    @Test
    public void testSchemeSpecificPartNameValuePairListByRFC6068Sample() throws Exception {
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("subject", "mail subject"));

       final URIBuilder uribuilder = new URIBuilder().setScheme("mailto").setSchemeSpecificPart("my@email.server", parameters);
       final String result = uribuilder.build().toString();
       Assertions.assertTrue(result.contains("my@email.server"), "mail address as scheme specific part expected");
       Assertions.assertTrue(result.contains("mail%20subject"), "correct parameter encoding expected for that scheme");
    }

    @Test
    public void testOptimize() throws Exception {
        Assertions.assertEquals("example://a/b/c/%7Bfoo%7D",
                new URIBuilder("eXAMPLE://a/./b/../b/%63/%7bfoo%7d").optimize().build().toASCIIString());
        Assertions.assertEquals("http://www.example.com/%3C",
                new URIBuilder("http://www.example.com/%3c").optimize().build().toASCIIString());
        Assertions.assertEquals("http://www.example.com/",
                new URIBuilder("HTTP://www.EXAMPLE.com/").optimize().build().toASCIIString());
        Assertions.assertEquals("http://www.example.com/a%2F",
                new URIBuilder("http://www.example.com/a%2f").optimize().build().toASCIIString());
        Assertions.assertEquals("http://www.example.com/?a%2F",
                new URIBuilder("http://www.example.com/?a%2f").optimize().build().toASCIIString());
        Assertions.assertEquals("http://www.example.com/?q=%26",
                new URIBuilder("http://www.example.com/?q=%26").optimize().build().toASCIIString());
        Assertions.assertEquals("http://www.example.com/%23?q=%26",
                new URIBuilder("http://www.example.com/%23?q=%26").optimize().build().toASCIIString());
        Assertions.assertEquals("http://www.example.com/blah-%28%20-blah-%20%26%20-blah-%20%29-blah/",
                new URIBuilder("http://www.example.com/blah-%28%20-blah-%20&%20-blah-%20)-blah/").optimize().build().toASCIIString());
        Assertions.assertEquals("../../.././",
                new URIBuilder("../../.././").optimize().build().toASCIIString());
        Assertions.assertEquals("file:../../.././",
                new URIBuilder("file:../../.././").optimize().build().toASCIIString());
        Assertions.assertEquals("http://host/",
                new URIBuilder("http://host/../../.././").optimize().build().toASCIIString());
        Assertions.assertThrows(URISyntaxException.class, () -> new URIBuilder("http:///../../.././").optimize().build().toASCIIString());
    }

    @Test
    public void testIpv6Host() throws Exception {
        final URIBuilder builder = new URIBuilder("https://[::1]:432/path");
        final URI uri = builder.build();
        Assertions.assertEquals(432, builder.getPort());
        Assertions.assertEquals(432, uri.getPort());
        Assertions.assertEquals("https", builder.getScheme());
        Assertions.assertEquals("https", uri.getScheme());
        Assertions.assertEquals("::1", builder.getHost());
        Assertions.assertEquals("[::1]", uri.getHost());
        Assertions.assertEquals("/path", builder.getPath());
        Assertions.assertEquals("/path", uri.getPath());
    }

    @Test
    public void testIpv6HostWithPortUpdate() throws Exception {
        // Updating the port clears URIBuilder.encodedSchemeSpecificPart
        // and bypasses the fast/simple path which preserves input.
        final URIBuilder builder = new URIBuilder("https://[::1]:432/path").setPort(123);
        final URI uri = builder.build();
        Assertions.assertEquals(123, builder.getPort());
        Assertions.assertEquals(123, uri.getPort());
        Assertions.assertEquals("https", builder.getScheme());
        Assertions.assertEquals("https", uri.getScheme());
        Assertions.assertEquals("::1", builder.getHost());
        Assertions.assertEquals("[::1]", uri.getHost());
        Assertions.assertEquals("/path", builder.getPath());
        Assertions.assertEquals("/path", uri.getPath());
    }

    @Test
    public void testBuilderWithUnbracketedIpv6Host() throws Exception {
        final URIBuilder builder = new URIBuilder().setScheme("https").setHost("::1").setPort(443).setPath("/path");
        final URI uri = builder.build();
        Assertions.assertEquals("https", builder.getScheme());
        Assertions.assertEquals("https", uri.getScheme());
        Assertions.assertEquals(443, builder.getPort());
        Assertions.assertEquals(443, uri.getPort());
        Assertions.assertEquals("::1", builder.getHost());
        Assertions.assertEquals("[::1]", uri.getHost());
        Assertions.assertEquals("/path", builder.getPath());
        Assertions.assertEquals("/path", uri.getPath());
    }

    @Test
    public void testHttpsUriWithEmptyHost() {
        final URIBuilder uribuilder = new URIBuilder()
                .setScheme("https")
                .setUserInfo("stuff")
                .setHost("")
                .setPort(80)
                .setPath("/some stuff")
                .setParameter("param", "stuff")
                .setFragment("fragment");
        Assertions.assertThrows(URISyntaxException.class, uribuilder::build);
    }

    @Test
    public void testHttpUriWithEmptyHost() {
        final URIBuilder uribuilder = new URIBuilder()
                .setScheme("http")
                .setUserInfo("stuff")
                .setHost("")
                .setPort(80)
                .setPath("/some stuff")
                .setParameter("param", "stuff")
                .setFragment("fragment");
        Assertions.assertThrows(URISyntaxException.class, uribuilder::build);
    }
}
