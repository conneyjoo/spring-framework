/*
 * Copyright 2002-2024 the original author or authors.
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

import jakarta.servlet.http.HttpServletRequest;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.server.RequestPath;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.testfixture.servlet.MockHttpServletRequest;
import org.springframework.web.util.ServletRequestPathUtils;

import static org.springframework.web.servlet.mvc.method.LayeredRequestMappingHandlerMappingBenchmark.Test1Controller;
import static org.springframework.web.servlet.mvc.method.LayeredRequestMappingHandlerMappingBenchmark.Test2Controller;
import static org.springframework.web.servlet.mvc.method.LayeredRequestMappingHandlerMappingBenchmark.Test3Controller;
import static org.springframework.web.servlet.mvc.method.LayeredRequestMappingHandlerMappingBenchmark.Test4Controller;
import static org.springframework.web.servlet.mvc.method.LayeredRequestMappingHandlerMappingBenchmark.Test5Controller;

/**
 * Benchmark for {@link RequestMappingHandlerMapping}.
 *
 * @author conney joo
 */
@BenchmarkMode(Mode.Throughput)
public class RequestMappingHandlerMappingBenchmark {

	@Benchmark
	public Object matchPathStyle(RequestMappingHandlerMappingBenchmarkJob job) {
		return job.matchPathStyle();
	}

	@Benchmark
	public Object matchVariableStyle(RequestMappingHandlerMappingBenchmarkJob job) {
		return job.matchVariableStyle();
	}

	@Benchmark
	public Object matchWildcardStyle(RequestMappingHandlerMappingBenchmarkJob job) {
		return job.matchWildcardStyle();
	}

	@State(Scope.Benchmark)
	public static class RequestMappingHandlerMappingBenchmarkJob {

		TestRequestMappingHandlerMapping mapping;

		MockHttpServletRequest request1;
		MockHttpServletRequest request2;
		MockHttpServletRequest request3;

		@Setup(Level.Trial)
		public void setup() {
			this.mapping = new TestRequestMappingHandlerMapping();
			this.mapping.setApplicationContext(new StaticWebApplicationContext());
			this.mapping.registerHandler(new Test1Controller());
			this.mapping.registerHandler(new Test2Controller());
			this.mapping.registerHandler(new Test3Controller());
			this.mapping.registerHandler(new Test4Controller());
			this.mapping.registerHandler(new Test5Controller());
			this.mapping.afterPropertiesSet();

			this.request1 = new MockHttpServletRequest("GET", "/test1/box/system/info");
			this.request2 = new MockHttpServletRequest("GET", "/test1/box/server/1/download");
			this.request3 = new MockHttpServletRequest("GET", "/test1/box/server/x/file/download/1/a/b/c/d");
		}

		public Object matchPathStyle() {
			return getHandler(this.request1);
		}

		public Object matchVariableStyle() {
			return getHandler(this.request2);
		}

		public Object matchWildcardStyle() {
			return getHandler(this.request3);
		}

		public Object getHandler(MockHttpServletRequest request) {
			try {
				HandlerExecutionChain chain = this.mapping.getHandler(request);
				Object handler = chain.getHandler();
				request.clearAttributes();
				return handler;
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

	}

	private static class TestRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

		void registerHandler(Object handler) {
			super.detectHandlerMethods(handler);
		}

		@Override
		protected boolean isHandler(Class<?> beanType) {
			return AnnotationUtils.findAnnotation(beanType, RequestMapping.class) != null;
		}

		@SuppressWarnings("removal")
		private RequestMappingInfo.BuilderConfiguration getBuilderConfig() {
			RequestMappingInfo.BuilderConfiguration config = new RequestMappingInfo.BuilderConfiguration();
			if (getPatternParser() != null) {
				config.setPatternParser(getPatternParser());
			}
			else {
				config.setPathMatcher(getPathMatcher());
			}
			return config;
		}

		RequestMappingInfo createInfo(String... patterns) {
			return RequestMappingInfo.paths(patterns).options(getBuilderConfig()).build();
		}

		@Override
		protected String initLookupPath(HttpServletRequest request) {
			// At runtime this is done by the DispatcherServlet
			if (getPatternParser() != null) {
				RequestPath requestPath = ServletRequestPathUtils.parseAndCache(request);
				return requestPath.pathWithinApplication().value();
			}
			return super.initLookupPath(request);
		}
	}

}
