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

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.testfixture.servlet.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link LayeredRequestMappingHandlerMapping}.
 *
 * @author Conney Joo
 */
@SuppressWarnings("unused")
public class LayeredHandlerMethodMappingTests {

	private LayeredRequestMappingHandlerMapping mapping;

	private MyHandler handler;

	private Method method1;

	private Method method2;


	@BeforeEach
	void setUp() throws Exception {
		this.mapping = new LayeredRequestMappingHandlerMapping();
		this.handler = new MyHandler();
		this.method1 = handler.getClass().getMethod("handlerMethod1");
		this.method2 = handler.getClass().getMethod("handlerMethod2");
	}

	@Test
	void registerMapping() throws Exception {
		String path1 = "/box/server/download";
		String path2 = "/box/server/{userId}/download";
		String path3 = "/box/system/info";
		RequestMappingInfo mapping1 = RequestMappingInfo.paths(path1).methods(RequestMethod.GET).build();
		RequestMappingInfo mapping2 = RequestMappingInfo.paths(path2).methods(RequestMethod.GET).build();
		RequestMappingInfo mapping3 = RequestMappingInfo.paths(path3).methods(RequestMethod.GET).build();
		HandlerMethod handlerMethod = new HandlerMethod(this.handler, this.method1);

		this.mapping.registerHandlerMethod(this.handler, this.method1, mapping1);
		this.mapping.registerHandlerMethod(this.handler, this.method1, mapping2);
		this.mapping.registerHandlerMethod(this.handler, this.method1, mapping3);
		assertThat(this.mapping.getHandler(new MockHttpServletRequest("GET", path1))).isNotNull();
		assertThat(this.mapping.getHandler(new MockHttpServletRequest("GET", path2))).isNotNull();
		assertThat(this.mapping.getHandler(new MockHttpServletRequest("GET", path3))).isNotNull();
	}

	@Test
	void unregisterMapping() throws Exception {
		String path1 = "/box/server/download";
		String path2 = "/box/server/{userId}/download";
		String path3 = "/box/system/info";
		RequestMappingInfo mapping1 = RequestMappingInfo.paths(path1).methods(RequestMethod.GET).build();
		RequestMappingInfo mapping2 = RequestMappingInfo.paths(path2).methods(RequestMethod.GET).build();
		RequestMappingInfo mapping3 = RequestMappingInfo.paths(path3).methods(RequestMethod.GET).build();
		HandlerMethod handlerMethod = new HandlerMethod(this.handler, this.method1);

		this.mapping.registerHandlerMethod(this.handler, this.method1, mapping1);
		this.mapping.registerHandlerMethod(this.handler, this.method1, mapping2);
		this.mapping.registerHandlerMethod(this.handler, this.method1, mapping3);
		assertThat(this.mapping.getHandler(new MockHttpServletRequest("GET", path2))).isNotNull();

		this.mapping.unregisterMapping(mapping1);
		assertThat(this.mapping.getHandler(new MockHttpServletRequest("GET", path1))).isNull();
		this.mapping.unregisterMapping(mapping2);
		assertThat(this.mapping.getHandler(new MockHttpServletRequest("GET", path2))).isNull();
		assertThat(this.mapping.getHandler(new MockHttpServletRequest("GET", path3))).isNotNull();
	}

	@Controller
	static class MyHandler {

		@RequestMapping
		public void handlerMethod1() {
		}

		@RequestMapping
		public void handlerMethod2() {
		}

		@RequestMapping
		@CrossOrigin(originPatterns = "*")
		public void corsHandlerMethod() {
		}
	}
}
