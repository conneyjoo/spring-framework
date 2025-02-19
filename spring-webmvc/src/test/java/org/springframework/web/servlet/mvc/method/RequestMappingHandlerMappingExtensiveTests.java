/*
 * Copyright 2002-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.mvc.method;

import java.util.stream.Stream;

import org.springframework.http.MediaType;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.handler.PathPatternsParameterizedTest;
import org.springframework.web.testfixture.servlet.MockHttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMappingTests.TestRequestMappingInfoHandlerMapping;

/**
 * Test with {@link RequestMappingInfoHandlerMapping}.
 * This unit test is conducted for extensive verification of various types of mappings.
 *
 * @author conney joo
 */
public class RequestMappingHandlerMappingExtensiveTests {

	@SuppressWarnings({"unused", "removal"})
	static Stream<?> pathPatternsArguments() {
		LayeredRequestMappingHandlerMappingExtensiveTests.TestController controller = new LayeredRequestMappingHandlerMappingExtensiveTests.TestController();

		TestRequestMappingInfoHandlerMapping mapping1 = new TestRequestMappingInfoHandlerMapping();

		UrlPathHelper pathHelper = new UrlPathHelper();
		pathHelper.setRemoveSemicolonContent(false);

		TestRequestMappingInfoHandlerMapping mapping2 = new TestRequestMappingInfoHandlerMapping();
		mapping2.setUrlPathHelper(pathHelper);

		return Stream.of(named("defaults", mapping1), named("setRemoveSemicolonContent(false)", mapping2))
				.peek(named -> {
					TestRequestMappingInfoHandlerMapping mapping = named.getPayload();
					mapping.setApplicationContext(new StaticWebApplicationContext());
					mapping.registerHandler(controller);
					mapping.afterPropertiesSet();
				});
	}

	@PathPatternsParameterizedTest
	void testMatchEmpty(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("empty");

		request = new MockHttpServletRequest("GET", "/");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("empty2");
	}

	@PathPatternsParameterizedTest
	void testMatchGreet(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/greet");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("greet");

		request = new MockHttpServletRequest("GET", "/greet");
		request.setContentType(MediaType.APPLICATION_JSON_VALUE);
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("greet2");

		request = new MockHttpServletRequest("GET", "/greet/");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("greet3");
	}

	@PathPatternsParameterizedTest
	void testMatchMessage(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/message");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("message");
	}

	@PathPatternsParameterizedTest
	void testMatchGetUserName(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1/name");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("getUserName");

		request = new MockHttpServletRequest("GET", "/1/name");
		request.setParameter("date", "2025");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("getUserName2");

		request = new MockHttpServletRequest("GET", "/1/age");
		request.setContentType(MediaType.APPLICATION_JSON_VALUE);
		request.setParameter("date", "2025");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("getUserAge");
	}

	@PathPatternsParameterizedTest
	void testMatchGroup(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1/2");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("group");

		request = new MockHttpServletRequest("GET", "/group/123456");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("group");
	}

	@PathPatternsParameterizedTest
	void testMatchXY(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x/y");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("xy");
	}

	@PathPatternsParameterizedTest
	void testMatchN1(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1/2/3/4/5/5/5/a/b/c");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("n1");

		request = new MockHttpServletRequest("GET", "/1/2/3/4/5/5/5/5/b/c");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("n1");
	}

	@PathPatternsParameterizedTest
	void testMatchN2(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1/2/3/4/5/5/5/a/b");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("n2");
	}

	@PathPatternsParameterizedTest
	void testMatchN3(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1/2/3/4/5/5/a/b/c/d/e");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("n3");
	}

	@PathPatternsParameterizedTest
	void testMatchN4(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1/2/3/4/5/5/5/6/b/c");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("n4");
	}

	@PathPatternsParameterizedTest
	void testMatchN5(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1/2/3/4/5/5/5/a/b/c/d");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("n5");

		request = new MockHttpServletRequest("GET", "/1/2/3/4/5/5/5/6/b/c/d");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("n5");
	}

	@PathPatternsParameterizedTest
	void testMatchN6(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1/2/3/4/5/5/6");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("n6");

		request = new MockHttpServletRequest("GET", "/1/2/3/4/5/5/a");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("n6");
	}

	@PathPatternsParameterizedTest
	void testMatchN7(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1/2/3/4/5/5/x/x/x/x");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("n7");

		request = new MockHttpServletRequest("GET", "/1/2/3/4/5/5/5/a");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("n7");

		request = new MockHttpServletRequest("GET", "/1/2/3/4/5/5/5/6/a/b/c/d");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("n7");
	}

	@PathPatternsParameterizedTest
	void testMatchDownload(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/1/download");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("download");
	}

	@PathPatternsParameterizedTest
	void testMatchDownload2(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/download");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("download2");
	}

	@PathPatternsParameterizedTest
	void testMatchDownload3(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		// Expected to match /box/server/*/file/download, but matched /box/server/{userId}/{schoolId}/{appId}
		// based on matching order rules: variable > wildcard
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/x/file/download");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("school");
	}

	@PathPatternsParameterizedTest
	void testMatchUpload(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/1/upload");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("upload");
	}

	@PathPatternsParameterizedTest
	void testMatchSave(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/1/save");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("save");

		// Expected to match /box/server/*/save - params = {a, b}, but matched /box/server/{userId}/save
		// based on matching order rules: variable > wildcard
		request = new MockHttpServletRequest("GET", "/box/server/x/save");
		request.setParameter("a", "1");
		request.setParameter("b", "2");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("save");
	}

	@PathPatternsParameterizedTest
	void testMatchFindOne(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		// Expected to match /box/server/*/{userId}, but matched /box/server/{appId}/{userId}
		// based on matching order rules: variable > wildcard
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/x/1");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("findOne2");

		request = new MockHttpServletRequest("GET", "/box/server/1/1");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("findOne2");
	}

	@PathPatternsParameterizedTest
	void testMatchValue(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/1");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("value");
	}

	@PathPatternsParameterizedTest
	void testMatchDownloadTest(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/x/file/download/test");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("downloadTest");
	}

	@PathPatternsParameterizedTest
	void testMatchDownloadTest2(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/aaa/file/x/download/test");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("downloadTest2");
	}

	@PathPatternsParameterizedTest
	void testMatchDownloadPath(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/x/file/download/1/a/b/c");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("downloadPath");

		request = new MockHttpServletRequest("GET", "/box/server/x/file/download/1/a/b/c/d");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("downloadPath2");
	}

	@PathPatternsParameterizedTest
	void testMatchDownloadAny(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/x/file/download/1/x/x/x/x/x");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("downloadAny");

		request = new MockHttpServletRequest("GET", "/box/server/x/file/download/1/x");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("downloadAny");
	}

	@PathPatternsParameterizedTest
	void testMatchDownloadAppId(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/x/file/download/1/x/com.app");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("downloadAppId");

		// Expected to match /box/server/*/file/download/{userId}/*/ss/{appId}, but matched /box/server/*/file/download/{userId}/{a}/{b}/{c}
		// based on matching order rules: variable > wildcard
		request = new MockHttpServletRequest("GET", "/box/server/x/file/download/1/x/ss/com.app");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("downloadPath");
	}

	@PathPatternsParameterizedTest
	void testMatchDownloadSchool(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/1/1001/com.app");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("school");
	}

	@PathPatternsParameterizedTest
	void testMatchDownloadSchool2(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/x/1001/x/com.app");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("school2");
	}

	@PathPatternsParameterizedTest
	void testMatchDownloadRequest(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/1/request");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("request");

		request = new MockHttpServletRequest("GET", "/box/server/1/request2");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("request2");

		request = new MockHttpServletRequest("GET", "/box/server/request3");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("request3");
	}

	@PathPatternsParameterizedTest
	void testMatchDownloadJson(TestRequestMappingInfoHandlerMapping mapping) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/box/server/json");
		HandlerMethod handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("json");

		request = new MockHttpServletRequest("GET", "/box/server/json2");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("json2");

		request = new MockHttpServletRequest("GET", "/box/server/json3");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("json3");

		request = new MockHttpServletRequest("GET", "/box/server/json4");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("json4");

		request = new MockHttpServletRequest("GET", "/box/server/json5");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("json5");

		request = new MockHttpServletRequest("GET", "/box/server/json6");
		handlerMethod = getHandler(mapping, request);
		assertThat(handlerMethod.getMethod().getName()).isEqualTo("json6");
	}

	private HandlerMethod getHandler(
			RequestMappingInfoHandlerMapping mapping, MockHttpServletRequest request) throws Exception {
		HandlerExecutionChain chain = mapping.getHandler(request);
		assertThat(chain).isNotNull();
		return (HandlerMethod) chain.getHandler();
	}
}
