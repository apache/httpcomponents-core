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
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class TestURIBuilder {

    private static final String CH_HELLO = "\u0047\u0072\u00FC\u0065\u007A\u0069\u005F\u007A\u00E4\u006D\u00E4";
    private static final String RU_HELLO = "\u0412\u0441\u0435\u043C\u005F\u043F\u0440\u0438\u0432\u0435\u0442";

    static List<String> parsePath(final CharSequence s) {
        return URIBuilder.parsePath(s, null);
    }

    @Test
    void testParseSegments() {
        Assertions.assertThat(parsePath("/this/that")).isEqualTo(Arrays.asList("this", "that"));
        Assertions.assertThat(parsePath("this/that")).isEqualTo(Arrays.asList("this", "that"));
        Assertions.assertThat(parsePath("this//that")).isEqualTo(Arrays.asList("this", "", "that"));
        Assertions.assertThat(parsePath("this//that/")).isEqualTo(Arrays.asList("this", "", "that", ""));
        Assertions.assertThat(parsePath("this//that/%2fthis%20and%20that")).isEqualTo(Arrays.asList("this", "", "that", "/this and that"));
        Assertions.assertThat(parsePath("this///that//")).isEqualTo(Arrays.asList("this", "", "", "that", "", ""));
        Assertions.assertThat(parsePath("/")).isEqualTo(Collections.singletonList(""));
        Assertions.assertThat(parsePath("")).isEqualTo(Collections.<String>emptyList());
    }

    static String formatPath(final String... pathSegments) {
        final StringBuilder buf = new StringBuilder();
        URIBuilder.formatPath(buf, Arrays.asList(pathSegments), false, null);
        return buf.toString();
    }

    @Test
    void testFormatSegments() {
        Assertions.assertThat(formatPath("this", "that")).isEqualTo("/this/that");
        Assertions.assertThat(formatPath("this", "", "that")).isEqualTo("/this//that");
        Assertions.assertThat(formatPath("this", "", "that", "/this and that")).isEqualTo("/this//that/%2Fthis%20and%20that");
        Assertions.assertThat(formatPath("this", "", "", "that", "", "")).isEqualTo("/this///that//");
        Assertions.assertThat(formatPath("")).isEqualTo("/");
        Assertions.assertThat(formatPath()).isEqualTo("");
    }

    static List<NameValuePair> parseQuery(final CharSequence s) {
        return URIBuilder.parseQuery(s, null, false);
    }

    @Test
    void testParseQuery() {
        Assertions.assertThat(parseQuery(""))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Collections.emptyList());
        Assertions.assertThat(parseQuery("Name0"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name0", null)));
        Assertions.assertThat(parseQuery("Name1=Value1"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name1", "Value1")));
        Assertions.assertThat(parseQuery("Name2="))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name2", "")));
        Assertions.assertThat(parseQuery(" Name3  "))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name3", null)));
        Assertions.assertThat(parseQuery("Name4=Value%204%21"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name4", "Value 4!")));
        Assertions.assertThat(parseQuery("Name4=Value%2B4%21"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name4", "Value+4!")));
        Assertions.assertThat(parseQuery("Name4=Value%204%21%20%214"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name4", "Value 4! !4")));
        Assertions.assertThat(parseQuery("Name5=aaa&Name6=bbb"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name5", "aaa"), Assertions.tuple("Name6", "bbb")));
        Assertions.assertThat(parseQuery("Name7=aaa&Name7=b%2Cb&Name7=ccc"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name7", "aaa"), Assertions.tuple("Name7", "b,b"), Assertions.tuple("Name7", "ccc")));
        Assertions.assertThat(parseQuery("Name8=xx%2C%20%20yy%20%20%2Czz"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name8", "xx,  yy  ,zz")));
        Assertions.assertThat(parseQuery("price=10%20%E2%82%AC"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("price", "10 \u20AC")));
        Assertions.assertThat(parseQuery("a=b\"c&d=e"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("a", "b\"c"), Assertions.tuple("d", "e")));
        Assertions.assertThat(parseQuery("russian=" + PercentCodec.encode(RU_HELLO, StandardCharsets.UTF_8) +
                        "&swiss=" + PercentCodec.encode(CH_HELLO, StandardCharsets.UTF_8)))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("russian", RU_HELLO), Assertions.tuple("swiss", CH_HELLO)));
    }

    static String formatQuery(final NameValuePair... params) {
        final StringBuilder buf = new StringBuilder();
        URIBuilder.formatQuery(buf, Arrays.asList(params), null, false);
        return buf.toString();
    }

    @Test
    void testFormatQuery() {
        Assertions.assertThat(formatQuery(new BasicNameValuePair("Name0", null))).isEqualTo("Name0");
        Assertions.assertThat(formatQuery(new BasicNameValuePair("Name1", "Value1"))).isEqualTo("Name1=Value1");
        Assertions.assertThat(formatQuery(new BasicNameValuePair("Name2", ""))).isEqualTo("Name2=");
        Assertions.assertThat(formatQuery(new BasicNameValuePair("Name4", "Value 4&"))).isEqualTo("Name4=Value%204%26");
        Assertions.assertThat(formatQuery(new BasicNameValuePair("Name4", "Value+4&"))).isEqualTo("Name4=Value%2B4%26");
        Assertions.assertThat(formatQuery(new BasicNameValuePair("Name4", "Value 4& =4"))).isEqualTo("Name4=Value%204%26%20%3D4");
        Assertions.assertThat(formatQuery(
                new BasicNameValuePair("Name5", "aaa"),
                new BasicNameValuePair("Name6", "bbb"))).isEqualTo("Name5=aaa&Name6=bbb");
        Assertions.assertThat(formatQuery(
                new BasicNameValuePair("Name7", "aaa"),
                new BasicNameValuePair("Name7", "b,b"),
                new BasicNameValuePair("Name7", "ccc")
        )).isEqualTo("Name7=aaa&Name7=b%2Cb&Name7=ccc");
        Assertions.assertThat(formatQuery(new BasicNameValuePair("Name8", "xx,  yy  ,zz"))).isEqualTo("Name8=xx%2C%20%20yy%20%20%2Czz");
        Assertions.assertThat(formatQuery(
                new BasicNameValuePair("russian", RU_HELLO),
                new BasicNameValuePair("swiss", CH_HELLO))).isEqualTo("russian=" + PercentCodec.encode(RU_HELLO, StandardCharsets.UTF_8) +
                "&swiss=" + PercentCodec.encode(CH_HELLO, StandardCharsets.UTF_8));
    }

    @Test
    void testHierarchicalUri() throws Exception {
        final URI uri = new URI("http", "stuff", "localhost", 80, "/some stuff", "param=stuff", "fragment");
        final URIBuilder uribuilder = new URIBuilder(uri).setEncodingPolicy(URIBuilder.EncodingPolicy.ALL_RESERVED);
        final URI result = uribuilder.build();
        Assertions.assertThat(result).isEqualTo(new URI("http://stuff@localhost:80/some%20stuff?param=stuff#fragment"));
    }

    @Test
    void testMutationRemoveFragment() throws Exception {
        final URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        final URI result = new URIBuilder(uri).setFragment(null).build();
        Assertions.assertThat(result).isEqualTo(new URI("http://stuff@localhost:80/stuff?param=stuff"));
    }

    @Test
    void testMutationRemoveUserInfo() throws Exception {
        final URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        final URI result = new URIBuilder(uri).setUserInfo(null).build();
        Assertions.assertThat(result).isEqualTo(new URI("http://localhost:80/stuff?param=stuff#fragment"));
    }

    @Test
    void testMutationRemovePort() throws Exception {
        final URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        final URI result = new URIBuilder(uri).setPort(-1).build();
        Assertions.assertThat(result).isEqualTo(new URI("http://stuff@localhost/stuff?param=stuff#fragment"));
    }

    @Test
    void testOpaqueUri() throws Exception {
        final URI uri = new URI("stuff", "some-stuff", "fragment");
        final URIBuilder uribuilder = new URIBuilder(uri);
        final URI result = uribuilder.build();
        Assertions.assertThat(result).isEqualTo(uri);
    }

    @Test
    void testOpaqueUriMutation() throws Exception {
        final URI uri = new URI("stuff", "some-stuff", "fragment");
        final URIBuilder uribuilder = new URIBuilder(uri).setCustomQuery("param1&param2=stuff").setFragment(null);
        Assertions.assertThat(uribuilder.build()).isEqualTo(new URI("stuff:?param1&param2=stuff"));
    }

    @Test
    void testHierarchicalUriMutation() throws Exception {
        final URIBuilder uribuilder = new URIBuilder("/").setScheme("http").setHost("localhost").setPort(80).setPath("/stuff");
        Assertions.assertThat(uribuilder.build()).isEqualTo(new URI("http://localhost:80/stuff"));
    }

    @Test
    void testLocalhost() throws Exception {
        // Check that the URI generated by URI builder agrees with that generated by using URI directly
        final String scheme = "https";
        final InetAddress host = InetAddress.getLocalHost();
        final String specials = "/abcd!$&*()_-+.,=:;'~@[]?<>|#^%\"{}\\\u00a3`\u00ac\u00a6xyz"; // N.B. excludes space
        final URI uri = new URI(scheme, specials, host.getHostAddress(), 80, specials, specials, specials);

        final URI bld = URIBuilder.localhost()
                .setScheme(scheme)
                .setUserInfo(specials)
                .setPath(specials)
                .setCustomQuery(specials)
                .setFragment(specials)
                .build();

        Assertions.assertThat(bld.getHost()).isEqualTo(uri.getHost());
        Assertions.assertThat(bld.getUserInfo()).isEqualTo(uri.getUserInfo());
        Assertions.assertThat(bld.getPath()).isEqualTo(uri.getPath());
        Assertions.assertThat(bld.getQuery()).isEqualTo(uri.getQuery());
        Assertions.assertThat(bld.getFragment()).isEqualTo(uri.getFragment());
    }

    @Test
    void testLoopbackAddress() throws Exception {
        // Check that the URI generated by URI builder agrees with that generated by using URI directly
        final String scheme = "https";
        final InetAddress host = InetAddress.getLoopbackAddress();
        final String specials = "/abcd!$&*()_-+.,=:;'~@[]?<>|#^%\"{}\\\u00a3`\u00ac\u00a6xyz"; // N.B. excludes space
        final URI uri = new URI(scheme, specials, host.getHostAddress(), 80, specials, specials, specials);

        final URI bld = URIBuilder.loopbackAddress()
                .setScheme(scheme)
                .setUserInfo(specials)
                .setPath(specials)
                .setCustomQuery(specials)
                .setFragment(specials)
                .build();

        Assertions.assertThat(bld.getHost()).isEqualTo(uri.getHost());
        Assertions.assertThat(bld.getUserInfo()).isEqualTo(uri.getUserInfo());
        Assertions.assertThat(bld.getPath()).isEqualTo(uri.getPath());
        Assertions.assertThat(bld.getQuery()).isEqualTo(uri.getQuery());
        Assertions.assertThat(bld.getFragment()).isEqualTo(uri.getFragment());
    }

    @Test
    void testEmpty() throws Exception {
        final URIBuilder uribuilder = new URIBuilder();
        final URI result = uribuilder.build();
        Assertions.assertThat(result).isEqualTo(new URI(""));
    }

    @Test
    void testEmptyPath() throws Exception {
        final URIBuilder uribuilder = new URIBuilder("http://thathost");
        Assertions.assertThat(uribuilder.isPathEmpty()).isTrue();
    }

    @Test
    void testRemoveParameter() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff&blah&blah", null);
        final URIBuilder uribuilder = new URIBuilder(uri);
        Assertions.assertThat(uribuilder.isQueryEmpty()).isFalse();

        Assertions.assertThatThrownBy(() ->
                        uribuilder.removeParameter(null))
                .isInstanceOf(NullPointerException.class);

        uribuilder.removeParameter("DoesNotExist");
        Assertions.assertThat(uribuilder.getFirstQueryParam("param").getValue()).isEqualTo("stuff");
        Assertions.assertThat(uribuilder.getFirstQueryParam("blah").getValue()).isNull();

        uribuilder.removeParameter("blah");
        Assertions.assertThat(uribuilder.getFirstQueryParam("param").getValue()).isEqualTo("stuff");
        Assertions.assertThat(uribuilder.getFirstQueryParam("blah")).isNull();

        uribuilder.removeParameter("param");
        Assertions.assertThat(uribuilder.getFirstQueryParam("param")).isNull();
        Assertions.assertThat(uribuilder.isQueryEmpty()).isTrue();

        uribuilder.removeParameter("AlreadyEmpty");
        Assertions.assertThat(uribuilder.isQueryEmpty()).isTrue();
        Assertions.assertThat(uribuilder.build()).isEqualTo(new URI("http://localhost:80/"));
    }

    @Test
    void testRemoveQuery() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff", null);
        final URIBuilder uribuilder = new URIBuilder(uri).removeQuery();
        final URI result = uribuilder.build();
        Assertions.assertThat(result).isEqualTo(new URI("http://localhost:80/"));
    }

    @Test
    void testSetAuthorityFromNamedEndpointHost() throws Exception {
        final Host host = Host.create("localhost:88");
        final URIBuilder uribuilder = new URIBuilder().setScheme(URIScheme.HTTP.id).setAuthority(host);
        // Check builder
        Assertions.assertThat(uribuilder.getUserInfo()).isNull();
        Assertions.assertThat(uribuilder.getAuthority().getHostName()).isEqualTo(host.getHostName());
        Assertions.assertThat(uribuilder.getHost()).isEqualTo(host.getHostName());
        // Check result
        final URI result = uribuilder.build();
        Assertions.assertThat(result.getHost()).isEqualTo(host.getHostName());
        Assertions.assertThat(result.getPort()).isEqualTo(host.getPort());
        Assertions.assertThat(result).isEqualTo(new URI("http://localhost:88"));
    }

    @Test
    void testSetAuthorityFromNamedEndpointHttpHost() throws Exception {
        final HttpHost httpHost = HttpHost.create("localhost:88");
        final URIBuilder uribuilder = new URIBuilder().setScheme(URIScheme.HTTP.id).setAuthority(httpHost);
        // Check builder
        Assertions.assertThat(uribuilder.getUserInfo()).isNull();
        Assertions.assertThat(uribuilder.getAuthority().getHostName()).isEqualTo(httpHost.getHostName());
        Assertions.assertThat(uribuilder.getHost()).isEqualTo(httpHost.getHostName());
        // Check result
        final URI result = uribuilder.build();
        Assertions.assertThat(result.getHost()).isEqualTo(httpHost.getHostName());
        Assertions.assertThat(result.getPort()).isEqualTo(httpHost.getPort());
        Assertions.assertThat(result).isEqualTo(new URI("http://localhost:88"));
    }

    @Test
    void testSetAuthorityFromURIAuthority() throws Exception {
        final URIAuthority authority = URIAuthority.create("u:p@localhost:88");
        final URIBuilder uribuilder = new URIBuilder().setScheme(URIScheme.HTTP.id).setAuthority(authority);
        // Check builder
        Assertions.assertThat(uribuilder.getAuthority().getUserInfo()).isEqualTo(authority.getUserInfo());
        Assertions.assertThat(uribuilder.getAuthority().getHostName()).isEqualTo(authority.getHostName());
        Assertions.assertThat(uribuilder.getHost()).isEqualTo(authority.getHostName());
        // Check result
        final URI result = uribuilder.build();
        Assertions.assertThat(result.getUserInfo()).isEqualTo(authority.getUserInfo());
        Assertions.assertThat(result.getHost()).isEqualTo(authority.getHostName());
        Assertions.assertThat(result.getPort()).isEqualTo(authority.getPort());
        Assertions.assertThat(result.getAuthority()).isEqualTo(authority.toString());
        Assertions.assertThat(result).isEqualTo(new URI("http://u:p@localhost:88"));
    }

    @Test
    void testSetParameter() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff&blah&blah", null);
        final URIBuilder uribuilder = new URIBuilder(uri).setParameter("param", "some other stuff")
                .setParameter("blah", "blah")
                .setParameter("blah", "blah2");
        final URI result = uribuilder.build();
        Assertions.assertThat(result).isEqualTo(new URI("http://localhost:80/?param=some%20other%20stuff&blah=blah2"));
    }

    @Test
    void testGetFirstNamedParameter() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff&blah&blah", null);
        URIBuilder uribuilder = new URIBuilder(uri).setParameter("param", "some other stuff")
                .setParameter("blah", "blah");
        Assertions.assertThat(uribuilder.getFirstQueryParam("param").getValue()).isEqualTo("some other stuff");
        Assertions.assertThat(uribuilder.getFirstQueryParam("blah").getValue()).isEqualTo("blah");
        Assertions.assertThat(uribuilder.getFirstQueryParam("DoesNotExist")).isNull();
        //
        uribuilder = new URIBuilder("http://localhost:80/?param=some%20other%20stuff&blah=blah&blah=blah2");
        Assertions.assertThat(uribuilder.getFirstQueryParam("blah").getValue()).isEqualTo("blah");
        uribuilder.removeQuery();
        Assertions.assertThat(uribuilder.getFirstQueryParam("param")).isNull();
    }

    @Test
    void testSetParametersWithEmptyArrayArg() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/test", "param=test", null);
        final URIBuilder uribuilder = new URIBuilder(uri).setParameters();
        final URI result = uribuilder.build();
        Assertions.assertThat(result).isEqualTo(new URI("http://localhost:80/test"));
    }

    @Test
    void testSetParametersWithNullArrayArg() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/test", "param=test", null);
        final URIBuilder uribuilder = new URIBuilder(uri).setParameters((NameValuePair[]) null);
        final URI result = uribuilder.build();
        Assertions.assertThat(result).isEqualTo(new URI("http://localhost:80/test"));
    }

    @Test
    void testSetParametersWithEmptyList() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/test", "param=test", null);
        final URIBuilder uribuilder = new URIBuilder(uri).setParameters(Collections.emptyList());
        final URI result = uribuilder.build();
        Assertions.assertThat(result).isEqualTo(new URI("http://localhost:80/test"));
    }

    @Test
    void testSetParametersWithNullList() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/test", "param=test", null);
        final URIBuilder uribuilder = new URIBuilder(uri).setParameters((List<NameValuePair>) null);
        final URI result = uribuilder.build();
        Assertions.assertThat(result).isEqualTo(new URI("http://localhost:80/test"));
    }

    @Test
    void testParameterWithSpecialChar() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff", null);
        final URIBuilder uribuilder = new URIBuilder(uri).addParameter("param", "1 + 1 = 2")
                .addParameter("param", "blah&blah");
        final URI result = uribuilder.build();
        Assertions.assertThat(result).isEqualTo(new URI("http://localhost:80/?param=stuff&param=1%20%2B%201%20%3D%202&" +
                "param=blah%26blah"));
    }

    @Test
    void testAddParameter() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff&blah&blah", null);
        final URIBuilder uribuilder = new URIBuilder(uri).addParameter("param", "some other stuff")
                .addParameter("blah", "blah");
        final URI result = uribuilder.build();
        Assertions.assertThat(result).isEqualTo(new URI("http://localhost:80/?param=stuff&blah&blah&" +
                "param=some%20other%20stuff&blah=blah"));
    }

    @Test
    void testQueryEncoding() throws Exception {
        final URI uri1 = new URI("https://somehost.com/stuff?client_id=1234567890" +
                "&redirect_uri=https%3A%2F%2Fsomehost.com%2Fblah%20blah%2F");
        final URI uri2 = new URIBuilder("https://somehost.com/stuff")
                .addParameter("client_id", "1234567890")
                .addParameter("redirect_uri", "https://somehost.com/blah blah/").build();
        Assertions.assertThat(uri2).isEqualTo(uri1);
    }

    @Test
    void testQueryAndParameterEncoding() throws Exception {
        final URI uri1 = new URI("https://somehost.com/stuff?param1=12345&param2=67890");
        final URI uri2 = new URIBuilder("https://somehost.com/stuff")
                .setCustomQuery("this&that")
                .addParameter("param1", "12345")
                .addParameter("param2", "67890").build();
        Assertions.assertThat(uri2).isEqualTo(uri1);
    }

    @Test
    void testPathEncoding() throws Exception {
        final URI uri1 = new URI("https://somehost.com/some%20path%20with%20blanks/");
        final URI uri2 = new URIBuilder()
                .setScheme("https")
                .setHost("somehost.com")
                .setPath("/some path with blanks/")
                .build();
        Assertions.assertThat(uri2).isEqualTo(uri1);
    }

    @Test
    void testFragmentEncoding() throws Exception {
        final URI uri1 = new URI("https://somehost.com#some%20fragment%20with%20all%20sorts%20of%20$tuff%20in%20it!!!");
        final URI uri2 = new URIBuilder()
                .setScheme("https")
                .setHost("somehost.com")
                .setFragment("some fragment with all sorts of $tuff in it!!!")
                .build();
        Assertions.assertThat(uri2).isEqualTo(uri1);
    }

    @Test
    void testAgainstURI() throws Exception {
        // Check that the URI generated by URI builder agrees with that generated by using URI directly
        final String scheme = "https";
        final String host = "localhost";
        final String specials = "/abcd!$&*()_-+.,=:;'~@[]?<>|#^%\"{}\\\u00a3`\u00ac\u00a6xyz"; // N.B. excludes space
        final URI uri = new URI(scheme, specials, host, 80, specials, specials, specials);

        final URI bld = new URIBuilder()
                .setScheme(scheme)
                .setHost(host)
                .setUserInfo(specials)
                .setPath(specials)
                .setCustomQuery(specials)
                .setFragment(specials)
                .build();

        Assertions.assertThat(bld.getHost()).isEqualTo(uri.getHost());
        Assertions.assertThat(bld.getUserInfo()).isEqualTo(uri.getUserInfo());
        Assertions.assertThat(bld.getPath()).isEqualTo(uri.getPath());
        Assertions.assertThat(bld.getQuery()).isEqualTo(uri.getQuery());
        Assertions.assertThat(bld.getFragment()).isEqualTo(uri.getFragment());
    }

    @Test
    void testBuildAddParametersUTF8() throws Exception {
        assertAddParameters(StandardCharsets.UTF_8);
    }

    @Test
    void testBuildAddParametersISO88591() throws Exception {
        assertAddParameters(StandardCharsets.ISO_8859_1);
    }

    void assertAddParameters(final Charset charset) throws Exception {
        final URI uri = new URIBuilder("https://somehost.com/stuff")
                .setCharset(charset)
                .addParameters(createParameterList()).build();

        assertBuild(charset, uri);
        // null addParameters
        final URI uri2 = new URIBuilder("https://somehost.com/stuff")
                .setCharset(charset)
                .addParameters(null).build();

        Assertions.assertThat(uri2.toString()).isEqualTo("https://somehost.com/stuff");
    }

    @Test
    void testBuildSetParametersUTF8() throws Exception {
        assertSetParameters(StandardCharsets.UTF_8);
    }

    @Test
    void testBuildSetParametersISO88591() throws Exception {
        assertSetParameters(StandardCharsets.ISO_8859_1);
    }

    void assertSetParameters(final Charset charset) throws Exception {
        final URI uri = new URIBuilder("https://somehost.com/stuff")
                .setCharset(charset)
                .setParameters(createParameterList()).build();

        assertBuild(charset, uri);
    }

    void assertBuild(final Charset charset, final URI uri) {
        final String encodedData1 = PercentCodec.encode("\"1\u00aa position\"", charset);
        final String encodedData2 = PercentCodec.encode("Jos\u00e9 Abra\u00e3o", charset);

        final String uriExpected = String.format("https://somehost.com/stuff?parameter1=value1&parameter2=%s&parameter3=%s", encodedData1, encodedData2);

        Assertions.assertThat(uri.toString()).isEqualTo(uriExpected);
    }

    private List<NameValuePair> createParameterList() {
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("parameter1", "value1"));
        parameters.add(new BasicNameValuePair("parameter2", "\"1\u00aa position\""));
        parameters.add(new BasicNameValuePair("parameter3", "Jos\u00e9 Abra\u00e3o"));
        return parameters;
    }

    @Test
    void testMalformedPath() throws Exception {
        final String path = "@notexample.com/mypath";
        final URI uri = new URIBuilder(path).setHost("example.com").build();
        Assertions.assertThat(uri.getHost()).isEqualTo("example.com");
    }

    @Test
    void testRelativePath() throws Exception {
        final URI uri = new URIBuilder("./mypath").build();
        Assertions.assertThat(uri).isEqualTo(new URI("./mypath"));
    }

    @Test
    void testRelativePathWithAuthority() throws Exception {
        final URI uri = new URIBuilder("./mypath").setHost("somehost").setScheme("http").build();
        Assertions.assertThat(uri).isEqualTo(new URI("http://somehost/./mypath"));
    }

    @Test
    void testTolerateNullInput() throws Exception {
        Assertions.assertThat(new URIBuilder()
                .setScheme(null)
                .setHost("localhost")
                .setUserInfo(null)
                .setPort(8443)
                .setPath(null)
                .setCustomQuery(null)
                .setFragment(null)
                .build()).isEqualTo(URI.create("//localhost:8443"));
    }

    @Test
    void testTolerateBlankInput() throws Exception {
        Assertions.assertThat(new URIBuilder()
                .setScheme("")
                .setHost("localhost")
                .setUserInfo("")
                .setPort(8443)
                .setPath("")
                .setPath("")
                .setCustomQuery("")
                .setFragment("")
                .build()).isEqualTo(URI.create("//localhost:8443"));
    }

    @Test
    void testHttpHost() throws Exception {
        final HttpHost httpHost = new HttpHost("http", "example.com", 1234);
        final URIBuilder uribuilder = new URIBuilder();
        uribuilder.setHttpHost(httpHost);
        Assertions.assertThat(uribuilder.build()).isEqualTo(URI.create("http://example.com:1234"));
    }

    @Test
    void testSetHostWithReservedChars() throws Exception {
        final URIBuilder uribuilder = new URIBuilder();
        uribuilder.setScheme("http").setHost("!example!.com");
        Assertions.assertThat(uribuilder.build()).isEqualTo(URI.create("http://%21example%21.com"));
    }

    @Test
    void testGetHostWithReservedChars() throws Exception {
        final URIBuilder uribuilder = new URIBuilder("http://someuser%21@%21example%21.com/");
        Assertions.assertThat(uribuilder.getHost()).isEqualTo("!example!.com");
        Assertions.assertThat(uribuilder.getUserInfo()).isEqualTo("someuser!");
    }

    @Test
    void testMultipleLeadingPathSlashes() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("ftp")
                .setHost("somehost")
                .setPath("//blah//blah")
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("ftp://somehost//blah//blah"));
    }

    @Test
    void testNoAuthorityAndPath() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("file")
                .setPath("/blah")
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("file:/blah"));
    }

    @Test
    void testSetPathSegmentList() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("https")
                .setHost("somehost")
                .setPathSegments(Arrays.asList("api", "products"))
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("https://somehost/api/products"));
    }

    @Test
    void testSetPathSegmentsVarargs() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("https")
                .setHost("somehost")
                .setPathSegments("api", "products")
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("https://somehost/api/products"));
    }

    @Test
    void testSetPathSegmentsRootlessList() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("file")
                .setPathSegmentsRootless(Arrays.asList("dir", "foo"))
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("file:dir/foo"));
    }

    @Test
    void testSetPathSegmentsRootlessVarargs() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("file")
                .setPathSegmentsRootless("dir", "foo")
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("file:dir/foo"));
    }

    @Test
    void testAppendToExistingPath() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("https")
                .setHost("somehost")
                .setPath("api")
                .appendPath("v1/resources")
                .appendPath("idA")
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("https://somehost/api/v1/resources/idA"));
    }

    @Test
    void testAppendToNonExistingPath() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("https")
                .setHost("somehost")
                .appendPath("api/v2/customers")
                .appendPath("idA")
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("https://somehost/api/v2/customers/idA"));
    }

    @Test
    void testAppendNullToExistingPath() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("https")
                .setHost("somehost")
                .setPath("api")
                .appendPath(null)
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("https://somehost/api"));
    }

    @Test
    void testAppendNullToNonExistingPath() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("https")
                .setHost("somehost")
                .appendPath(null)
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("https://somehost"));
    }

    @Test
    void testAppendSegmentsVarargsToExistingPath() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("https")
                .setHost("myhost")
                .setPath("api")
                .appendPathSegments("v3", "products")
                .appendPathSegments("idA")
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("https://myhost/api/v3/products/idA"));
    }

    @Test
    void testAppendSegmentsVarargsToNonExistingPath() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("https")
                .setHost("somehost")
                .appendPathSegments("api", "v2", "customers")
                .appendPathSegments("idA")
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("https://somehost/api/v2/customers/idA"));
    }

    @Test
    void testAppendNullSegmentsVarargs() throws Exception {
        final String pathSegment = null;
        final URI uri = new URIBuilder()
                .setScheme("https")
                .setHost("somehost")
                .appendPathSegments(pathSegment)
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("https://somehost/"));
    }

    @Test
    void testAppendSegmentsListToExistingPath() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("http")
                .setHost("myhost")
                .setPath("api")
                .appendPathSegments(Arrays.asList("v3", "products"))
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("http://myhost/api/v3/products"));
    }

    @Test
    void testAppendSegmentsListToNonExistingPath() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("http")
                .setHost("myhost")
                .appendPathSegments(Arrays.asList("api", "v3", "customers"))
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("http://myhost/api/v3/customers"));
    }

    @Test
    void testAppendNullSegmentsList() throws Exception {
        final List<String> pathSegments = null;
        final URI uri = new URIBuilder()
                .setScheme("http")
                .setHost("myhost")
                .appendPathSegments(pathSegments)
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("http://myhost"));
    }

    @Test
    void testNoAuthorityAndPathSegments() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("file")
                .setPathSegments("this", "that")
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("file:/this/that"));
    }

    @Test
    void testNoAuthorityAndRootlessPath() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("file")
                .setPath("blah")
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("file:blah"));
    }

    @Test
    void testNoAuthorityAndRootlessPathSegments() throws Exception {
        final URI uri = new URIBuilder()
                .setScheme("file")
                .setPathSegmentsRootless("this", "that")
                .build();
        Assertions.assertThat(uri).isEqualTo(URI.create("file:this/that"));
    }

    @Test
    void testOpaque() throws Exception {
        final URIBuilder uriBuilder = new URIBuilder("http://host.com");
        final URI uri = uriBuilder.build();
        Assertions.assertThat(uriBuilder.isOpaque()).isEqualTo(uri.isOpaque());
    }

    @Test
    void testAddParameterEncodingEquivalence() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/",
                "param=stuff with spaces", null);
        final URIBuilder uribuilder = new URIBuilder().setScheme("http").setHost("localhost").setPort(80).setPath("/").addParameter(
                "param", "stuff with spaces");
        final URI result = uribuilder.build();
        Assertions.assertThat(result).isEqualTo(uri);
    }

    @Test
    void testSchemeSpecificPartParametersNull() throws Exception {
        final URIBuilder uribuilder = new URIBuilder("http://host.com").setParameter("par", "parvalue")
                .setSchemeSpecificPart("", (NameValuePair) null);
        Assertions.assertThat(uribuilder.build()).isEqualTo(new URI("http://host.com?par=parvalue"));
    }

    @Test
    void testSchemeSpecificPartSetGet() {
        final URIBuilder uribuilder = new URIBuilder().setSchemeSpecificPart("specificpart");
        Assertions.assertThat(uribuilder.getSchemeSpecificPart()).isEqualTo("specificpart");
    }

    /**
     * Common use case: mailto: scheme. See https://tools.ietf.org/html/rfc6068#section-2
     */
    @Test
    void testSchemeSpecificPartNameValuePairByRFC6068Sample() throws Exception {
        final URIBuilder uribuilder = new URIBuilder().setScheme("mailto")
                .setSchemeSpecificPart("my@email.server", new BasicNameValuePair("subject", "mail subject"));
        final String result = uribuilder.build().toString();
        Assertions.assertThat(result.contains("my@email.server")).withFailMessage("mail address as scheme specific part expected").isTrue();
        Assertions.assertThat(result.contains("mail%20subject")).withFailMessage("correct parameter encoding expected for that scheme").isTrue();
    }

    /**
     * Common use case: mailto: scheme. See https://tools.ietf.org/html/rfc6068#section-2
     */
    @Test
    void testSchemeSpecificPartNameValuePairListByRFC6068Sample() throws Exception {
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("subject", "mail subject"));

        final URIBuilder uribuilder = new URIBuilder().setScheme("mailto").setSchemeSpecificPart("my@email.server", parameters);
        final String result = uribuilder.build().toString();
        Assertions.assertThat(result.contains("my@email.server")).withFailMessage("mail address as scheme specific part expected").isTrue();
        Assertions.assertThat(result.contains("mail%20subject")).withFailMessage("correct parameter encoding expected for that scheme").isTrue();
    }

    @Test
    void testOptimize() throws Exception {
        Assertions.assertThat(new URIBuilder("eXAMPLE://a/./b/../b/%63/%7bfoo%7d").optimize().build().toASCIIString()).isEqualTo("example://a/b/c/%7Bfoo%7D");
        Assertions.assertThat(new URIBuilder("http://www.example.com/%3c").optimize().build().toASCIIString()).isEqualTo("http://www.example.com/%3C");
        Assertions.assertThat(new URIBuilder("HTTP://www.EXAMPLE.com/").optimize().build().toASCIIString()).isEqualTo("http://www.example.com/");
        Assertions.assertThat(new URIBuilder("http://www.example.com/a%2f").optimize().build().toASCIIString()).isEqualTo("http://www.example.com/a%2F");
        Assertions.assertThat(new URIBuilder("http://www.example.com/?a%2f").optimize().build().toASCIIString()).isEqualTo("http://www.example.com/?a%2F");
        Assertions.assertThat(new URIBuilder("http://www.example.com/?q=%26").optimize().build().toASCIIString()).isEqualTo("http://www.example.com/?q=%26");
        Assertions.assertThat(new URIBuilder("http://www.example.com/%23?q=%26").optimize().build().toASCIIString()).isEqualTo("http://www.example.com/%23?q=%26");
        Assertions.assertThat(new URIBuilder("http://www.example.com/blah-%28%20-blah-%20&%20-blah-%20)-blah/").optimize().build().toASCIIString()).isEqualTo("http://www.example.com/blah-%28%20-blah-%20%26%20-blah-%20%29-blah/");
        Assertions.assertThat(new URIBuilder("../../.././").optimize().build().toASCIIString()).isEqualTo("../../.././");
        Assertions.assertThat(new URIBuilder("file:../../.././").optimize().build().toASCIIString()).isEqualTo("file:../../.././");
        Assertions.assertThat(new URIBuilder("http://host/../../.././").optimize().build().toASCIIString()).isEqualTo("http://host/");
        Assertions.assertThat(new URIBuilder("http://host/a/b/..").optimize().build().toASCIIString()).isEqualTo("http://host/a/");
        Assertions.assertThat(new URIBuilder("http://host/a/b/.").optimize().build().toASCIIString()).isEqualTo("http://host/a/b/");
        Assertions.assertThat(new URIBuilder("http://host/a/b/..../...").optimize().build().toASCIIString()).isEqualTo("http://host/a/b/..../...");
        Assertions.assertThatThrownBy(() ->
                        new URIBuilder("http:///../../.././").optimize().build().toASCIIString())
                .isInstanceOf(URISyntaxException.class);
    }

    @Test
    void testIpv6Host() throws Exception {
        final URIBuilder builder = new URIBuilder("https://[::1]:432/path");
        final URI uri = builder.build();
        Assertions.assertThat(builder.getPort()).isEqualTo(432);
        Assertions.assertThat(uri.getPort()).isEqualTo(432);
        Assertions.assertThat(builder.getScheme()).isEqualTo("https");
        Assertions.assertThat(uri.getScheme()).isEqualTo("https");
        Assertions.assertThat(builder.getHost()).isEqualTo("::1");
        Assertions.assertThat(uri.getHost()).isEqualTo("[::1]");
        Assertions.assertThat(builder.getPath()).isEqualTo("/path");
        Assertions.assertThat(uri.getPath()).isEqualTo("/path");
    }

    @Test
    void testIpv6HostWithPortUpdate() throws Exception {
        // Updating the port clears URIBuilder.encodedSchemeSpecificPart
        // and bypasses the fast/simple path which preserves input.
        final URIBuilder builder = new URIBuilder("https://[::1]:432/path").setPort(123);
        final URI uri = builder.build();
        Assertions.assertThat(builder.getPort()).isEqualTo(123);
        Assertions.assertThat(uri.getPort()).isEqualTo(123);
        Assertions.assertThat(builder.getScheme()).isEqualTo("https");
        Assertions.assertThat(uri.getScheme()).isEqualTo("https");
        Assertions.assertThat(builder.getHost()).isEqualTo("::1");
        Assertions.assertThat(uri.getHost()).isEqualTo("[::1]");
        Assertions.assertThat(builder.getPath()).isEqualTo("/path");
        Assertions.assertThat(uri.getPath()).isEqualTo("/path");
    }

    @Test
    void testBuilderWithUnbracketedIpv6Host() throws Exception {
        final URIBuilder builder = new URIBuilder().setScheme("https").setHost("::1").setPort(443).setPath("/path");
        final URI uri = builder.build();
        Assertions.assertThat(builder.getScheme()).isEqualTo("https");
        Assertions.assertThat(uri.getScheme()).isEqualTo("https");
        Assertions.assertThat(builder.getPort()).isEqualTo(443);
        Assertions.assertThat(uri.getPort()).isEqualTo(443);
        Assertions.assertThat(builder.getHost()).isEqualTo("::1");
        Assertions.assertThat(uri.getHost()).isEqualTo("[::1]");
        Assertions.assertThat(builder.getPath()).isEqualTo("/path");
        Assertions.assertThat(uri.getPath()).isEqualTo("/path");
    }

    @Test
    void testHttpsUriWithEmptyHost() {
        final URIBuilder uribuilder = new URIBuilder()
                .setScheme("https")
                .setUserInfo("stuff")
                .setHost("")
                .setPort(80)
                .setPath("/some stuff")
                .setParameter("param", "stuff")
                .setFragment("fragment");
        Assertions.assertThatThrownBy(uribuilder::build).isInstanceOf(URISyntaxException.class);
    }

    @Test
    void testHttpUriWithEmptyHost() {
        final URIBuilder uribuilder = new URIBuilder()
                .setScheme("http")
                .setUserInfo("stuff")
                .setHost("")
                .setPort(80)
                .setPath("/some stuff")
                .setParameter("param", "stuff")
                .setFragment("fragment");
        Assertions.assertThatThrownBy(uribuilder::build).isInstanceOf(URISyntaxException.class);
    }

    @Test
    void testSetPlusAsBlank() throws Exception {
        // Case 1: Plus as blank, "+" should be treated as space
        URIBuilder uriBuilder = new URIBuilder("http://localhost?param=hello+world")
                .setPlusAsBlank(true);
        List<NameValuePair> params = uriBuilder.getQueryParams();
        Assertions.assertThat(params.get(0).getValue()).isEqualTo("hello world");

        // Case 2: Plus as plus, "+" should remain "+"
        uriBuilder = new URIBuilder("http://localhost?param=hello+world")
                .setPlusAsBlank(false);
        params = uriBuilder.getQueryParams();
        Assertions.assertThat(params.get(0).getValue()).isEqualTo("hello+world");

        // Case 3: '%20' always interpreted as space
        uriBuilder = new URIBuilder("http://localhost?param=hello%20world")
                .setPlusAsBlank(true);
        params = uriBuilder.getQueryParams();
        Assertions.assertThat(params.get(0).getValue()).isEqualTo("hello world");

        uriBuilder = new URIBuilder("http://localhost?param=hello%20world")
                .setPlusAsBlank(false);
        params = uriBuilder.getQueryParams();
        Assertions.assertThat(params.get(0).getValue()).isEqualTo("hello world");
    }

    @Test
    void testDefaultEncodingPolicy() throws Exception {
        final URIBuilder builder = new URIBuilder()
                .setScheme("http")
                .setHost("example.com")
                .setPath("/a:b")
                .addParameter("key", "a/b")
                .setFragment("a/b");
        final URI expected = new URI("http://example.com/a%3Ab?key=a%2Fb#a%2Fb");

        Assertions.assertThat(builder.build()).isEqualTo(expected);
        builder.setEncodingPolicy(URIBuilder.EncodingPolicy.ALL_RESERVED);
        Assertions.assertThat(builder.build()).isEqualTo(expected);
        builder.setEncodingPolicy(null);
        Assertions.assertThat(builder.build()).isEqualTo(expected);
    }

    @Test
    void testResetEncodingPolicy() throws Exception {
        final URIBuilder builder = new URIBuilder()
                .setScheme("http")
                .setUserInfo("user!")
                .setHost("example.com")
                .setPath("/a:b")
                .setCustomQuery("a/b")
                .setFragment("a/b")
                .setEncodingPolicy(URIBuilder.EncodingPolicy.RFC_3986);

        Assertions.assertThat(builder.build()).isEqualTo(new URI("http://user!@example.com/a:b?a/b#a/b"));
        Assertions.assertThat(builder.setEncodingPolicy(null)).isSameAs(builder);
        Assertions.assertThat(builder.build()).isEqualTo(new URI("http://user%21@example.com/a%3Ab?a%2Fb#a%2Fb"));
    }

    @Test
    void testCustomQueryEncoding() throws Exception {
        final String query = "query param:!@/?\"";
        final String expectedEncodedQuery = "query%20param:!@/?%22";

        final URI uri = new URIBuilder()
                .setScheme("http")
                .setHost("example.com")
                .setCustomQuery(query)
                .setEncodingPolicy(URIBuilder.EncodingPolicy.RFC_3986)
                .build();

        Assertions.assertThat(uri.getRawQuery()).isEqualTo(expectedEncodedQuery);
    }

}
