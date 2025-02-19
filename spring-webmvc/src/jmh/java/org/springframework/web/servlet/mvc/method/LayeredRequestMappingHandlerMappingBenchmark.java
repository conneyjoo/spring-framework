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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.RequestPath;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.testfixture.servlet.MockHttpServletRequest;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Benchmark for {@link LayeredRequestMappingHandlerMapping}.
 *
 * @author conney joo
 */
@BenchmarkMode(Mode.Throughput)
public class LayeredRequestMappingHandlerMappingBenchmark {

	@Benchmark
	public Object matchPathStyle(LayeredRequestMappingHandlerMappingJob job) {
		return job.matchPathStyle();
	}

	@Benchmark
	public Object matchVariableStyle(LayeredRequestMappingHandlerMappingJob job) {
		return job.matchVariableStyle();
	}

	@Benchmark
	public Object matchWildcardStyle(LayeredRequestMappingHandlerMappingJob job) {
		return job.matchWildcardStyle();
	}

	@State(Scope.Benchmark)
	public static class LayeredRequestMappingHandlerMappingJob {

		TestLayeredRequestMappingHandlerMapping mapping;

		MockHttpServletRequest request1;
		MockHttpServletRequest request2;
		MockHttpServletRequest request3;

		@Setup(Level.Trial)
		public void setup() {
			this.mapping = new TestLayeredRequestMappingHandlerMapping();
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

	@RestController
	@RequestMapping("/test1")
	static class Test1Controller {

		@RequestMapping(value = "/box/system/info", method = RequestMethod.GET)
		public Long path(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{userId}/download", method = RequestMethod.GET)
		public Long download(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{userId}/download1", method = RequestMethod.GET)
		public Long download1(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{userId}/download2", method = RequestMethod.GET)
		public Long download2(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/*/download1", params = {"aaa", "bbb"}, method = RequestMethod.GET)
		public String download1(HttpServletRequest request) {
			return "*";
		}

		@RequestMapping(value = "/box/server/*/{userId}", method = RequestMethod.GET)
		public Long download2(@PathVariable Long userId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{appId}/{userId}", method = RequestMethod.GET)
		public Long download21(@PathVariable Long appId, @PathVariable Long userId, HttpServletRequest request) {
			return appId + userId;
		}

		@RequestMapping(value = "/box/server/download", method = RequestMethod.POST)
		public String download3(HttpServletRequest request) {
			return String.valueOf(new Random().nextInt(100));
		}

		@RequestMapping(value = "/box/server/download", method = RequestMethod.GET)
		public String download3a() {
			return "3a";
		}

		@RequestMapping(value = "/box/server/{value}", method = RequestMethod.GET)
		public String download4(HttpServletRequest request) {
			return "4";
		}

		@RequestMapping(value = "/box/server/*/file/download", method = RequestMethod.GET)
		public String download5(HttpServletRequest request) {
			return "5";
		}

		@RequestMapping(value = "/box/server/*/file/download/test", method = RequestMethod.GET)
		public String download6(HttpServletRequest request) {
			return "6";
		}

		@RequestMapping("/box/server/*/file/download/{userId}/*/{appId}")
		public Long download66(@PathVariable Long userId, @PathVariable String appId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping("/box/server/*/file/download/{userId}/*/ss/{appId}")
		public String download666(@PathVariable Long userId, @PathVariable String appId, HttpServletRequest request) {
			return appId;
		}

		@RequestMapping(value = "/box/server/*/file/download/{userId}/**", method = RequestMethod.GET)
		public Long download6666(@PathVariable Long userId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping("/box/server/aaa/file/*/download/test")
		public String download7(HttpServletRequest request) {
			return "7";
		}

		@RequestMapping("/box/server/aaa/file/**")
		public String download8(HttpServletRequest request) {
			return "8";
		}

		@RequestMapping("/box/server/{userId}/{schoolId}/{appId}")
		public String download9(HttpServletRequest request) {
			return "9";
		}

		@RequestMapping("/box/server/*/{schoolId}/*/{appId}")
		public String download10(HttpServletRequest request) {
			return "10";
		}

		@RequestMapping(value = "/box/server/*/file/download/{userId}/{a}/{b}/{c}", method = RequestMethod.GET)
		public String download11(@PathVariable Long userId, HttpServletRequest request) {
			return "11";
		}

		@RequestMapping("/box/server/{userId}/request")
		public String request(@PathVariable Long userId, @RequestParam String url, HttpServletRequest request) {
			return url;
		}

		@RequestMapping("/box/server/{userId}/request2")
		public String request2(@PathVariable Long userId, @RequestParam String url, HttpServletRequest request) {
			return url;
		}

		@RequestMapping("/box/server/test")
		public ResponseEntity<String> test(@RequestParam String url, HttpServletRequest request) {
			if (url.equals("1")) {
				throw new IllegalArgumentException();
			}
			return ResponseEntity.ok(url);
		}

		@RequestMapping("/box/server/json")
		public @ResponseBody String json(@RequestParam String url) {
			return url;
		}

		@RequestMapping("/box/server/json2")
		public void json2() {
		}

		@RequestMapping("/box/server/json3")
		public void json3() {
		}

		@RequestMapping("/box/server/json4")
		public void json4() {
		}

		@RequestMapping("/box/server/json5")
		public void json5() {
		}

		@RequestMapping("/box/server/json6")
		public ResponseEntity<Void> json6() throws URISyntaxException {
			return ResponseEntity.created(new URI("/test/box/server/json6")).build();
		}

		@RequestMapping("/box/server/e1")
		public ResponseEntity<byte[]> e1() {
			return ResponseEntity.ok(new byte[]{1, 2, 3, 4, 5});
		}

		@RequestMapping("/box/server/e2")
		public byte[] e2() {
			return new byte[]{1, 2, 3, 4, 5};
		}

		@RequestMapping("/box/server/e3")
		public byte[] e3() {
			return new byte[]{1, 2, 3, 4, 5};
		}

		@RequestMapping("/box/server/void")
		public void vd(HttpServletRequest request, HttpServletResponse response) throws IOException {
			response.setContentType("text/html");
			response.setStatus(HttpStatus.OK.value());
			ServletOutputStream os = response.getOutputStream();
			os.write(1);
			os.flush();
			os.close();
		}
	}

	@RestController
	@RequestMapping("/test2")
	static class Test2Controller {

		@RequestMapping(value = "/box/server/{userId}/download", method = RequestMethod.GET)
		public Long download(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{userId}/download1", method = RequestMethod.GET)
		public Long download1(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{userId}/download2", method = RequestMethod.GET)
		public Long download2(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/*/download1", params = {"aaa", "bbb"}, method = RequestMethod.GET)
		public String download1(HttpServletRequest request) {
			return "*";
		}

		@RequestMapping(value = "/box/server/*/{userId}", method = RequestMethod.GET)
		public Long download2(@PathVariable Long userId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{appId}/{userId}", method = RequestMethod.GET)
		public Long download21(@PathVariable Long appId, @PathVariable Long userId, HttpServletRequest request) {
			return appId + userId;
		}

		@RequestMapping(value = "/box/server/download", method = RequestMethod.POST)
		public String download3(HttpServletRequest request) {
			return String.valueOf(new Random().nextInt(100));
		}

		@RequestMapping(value = "/box/server/download", method = RequestMethod.GET)
		public String download3a() {
			return "3a";
		}

		@RequestMapping(value = "/box/server/{value}", method = RequestMethod.GET)
		public String download4(HttpServletRequest request) {
			return "4";
		}

		@RequestMapping(value = "/box/server/*/file/download", method = RequestMethod.GET)
		public String download5(HttpServletRequest request) {
			return "5";
		}

		@RequestMapping(value = "/box/server/*/file/download/test", method = RequestMethod.GET)
		public String download6(HttpServletRequest request) {
			return "6";
		}

		@RequestMapping(value = "/box/server/*/file/download/{userId}/**", method = RequestMethod.GET)
		public Long download6(@PathVariable Long userId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping("/box/server/*/file/download/{userId}/*/{appId}")
		public Long download66(@PathVariable Long userId, @PathVariable String appId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping("/box/server/*/file/download/{userId}/*/ss/{appId}")
		public String download666(@PathVariable Long userId, @PathVariable String appId, HttpServletRequest request) {
			return appId;
		}

		@RequestMapping("/box/server/aaa/file/*/download/test")
		public String download7(HttpServletRequest request) {
			return "7";
		}

		@RequestMapping("/box/server/aaa/file/**")
		public String download8(HttpServletRequest request) {
			return "8";
		}

		@RequestMapping("/box/server/{userId}/{schoolId}/{appId}")
		public String download9(HttpServletRequest request) {
			return "9";
		}

		@RequestMapping("/box/server/*/{schoolId}/*/{appId}")
		public String download10(HttpServletRequest request) {
			return "10";
		}

		@RequestMapping("/box/server/{userId}/request")
		public String request(@PathVariable Long userId, @RequestParam String url, HttpServletRequest request) {
			return url;
		}

		@RequestMapping("/box/server/{userId}/request2")
		public String request2(@PathVariable Long userId, @RequestParam String url, HttpServletRequest request) {
			return url;
		}

		@RequestMapping("/box/server/test")
		public ResponseEntity<String> test(@RequestParam String url, HttpServletRequest request) {
			if (url.equals("1")) {
				throw new IllegalArgumentException();
			}
			return ResponseEntity.ok(url);
		}

		@RequestMapping("/box/server/json")
		public @ResponseBody String json(@RequestParam String url) {
			return url;
		}

		@RequestMapping("/box/server/json2")
		public void json2() {
		}

		@RequestMapping("/box/server/json3")
		public void json3() {
		}

		@RequestMapping("/box/server/json4")
		public void json4() {
		}

		@RequestMapping("/box/server/json5")
		public void json5() {
		}

		@RequestMapping("/box/server/json6")
		public ResponseEntity<Void> json6() throws URISyntaxException {
			return ResponseEntity.created(new URI("/test/box/server/json6")).build();
		}

		@RequestMapping("/box/server/e1")
		public ResponseEntity<byte[]> e1() {
			return ResponseEntity.ok(new byte[]{1, 2, 3, 4, 5});
		}

		@RequestMapping("/box/server/e2")
		public byte[] e2() {
			return new byte[]{1, 2, 3, 4, 5};
		}

		@RequestMapping("/box/server/e3")
		public byte[] e3() {
			return new byte[]{1, 2, 3, 4, 5};
		}

		@RequestMapping("/box/server/void")
		public void vd(HttpServletRequest request, HttpServletResponse response) throws IOException {
			response.setContentType("text/html");
			response.setStatus(HttpStatus.OK.value());
			ServletOutputStream os = response.getOutputStream();
			os.write(1);
			os.flush();
			os.close();
		}
	}

	@RestController
	@RequestMapping("/test3")
	static class Test3Controller {

		@RequestMapping(value = "/box/server/{userId}/download", method = RequestMethod.GET)
		public Long download(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{userId}/download1", method = RequestMethod.GET)
		public Long download1(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{userId}/download2", method = RequestMethod.GET)
		public Long download2(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/*/download1", params = {"aaa", "bbb"}, method = RequestMethod.GET)
		public String download1(HttpServletRequest request) {
			return "*";
		}

		@RequestMapping(value = "/box/server/*/{userId}", method = RequestMethod.GET)
		public Long download2(@PathVariable Long userId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{appId}/{userId}", method = RequestMethod.GET)
		public Long download21(@PathVariable Long appId, @PathVariable Long userId, HttpServletRequest request) {
			return appId + userId;
		}

		@RequestMapping(value = "/box/server/download", method = RequestMethod.POST)
		public String download3(HttpServletRequest request) {
			return String.valueOf(new Random().nextInt(100));
		}

		@RequestMapping(value = "/box/server/download", method = RequestMethod.GET)
		public String download3a() {
			return "3a";
		}

		@RequestMapping(value = "/box/server/{value}", method = RequestMethod.GET)
		public String download4(HttpServletRequest request) {
			return "4";
		}

		@RequestMapping(value = "/box/server/*/file/download", method = RequestMethod.GET)
		public String download5(HttpServletRequest request) {
			return "5";
		}

		@RequestMapping(value = "/box/server/*/file/download/test", method = RequestMethod.GET)
		public String download6(HttpServletRequest request) {
			return "6";
		}

		@RequestMapping(value = "/box/server/*/file/download/{userId}/**", method = RequestMethod.GET)
		public Long download6(@PathVariable Long userId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping("/box/server/*/file/download/{userId}/*/{appId}")
		public Long download66(@PathVariable Long userId, @PathVariable String appId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping("/box/server/*/file/download/{userId}/*/ss/{appId}")
		public String download666(@PathVariable Long userId, @PathVariable String appId, HttpServletRequest request) {
			return appId;
		}

		@RequestMapping("/box/server/aaa/file/*/download/test")
		public String download7(HttpServletRequest request) {
			return "7";
		}

		@RequestMapping("/box/server/aaa/file/**")
		public String download8(HttpServletRequest request) {
			return "8";
		}

		@RequestMapping("/box/server/{userId}/{schoolId}/{appId}")
		public String download9(HttpServletRequest request) {
			return "9";
		}

		@RequestMapping("/box/server/*/{schoolId}/*/{appId}")
		public String download10(HttpServletRequest request) {
			return "10";
		}

		@RequestMapping("/box/server/{userId}/request")
		public String request(@PathVariable Long userId, @RequestParam String url, HttpServletRequest request) {
			return url;
		}

		@RequestMapping("/box/server/{userId}/request2")
		public String request2(@PathVariable Long userId, @RequestParam String url, HttpServletRequest request) {
			return url;
		}

		@RequestMapping("/box/server/test")
		public ResponseEntity<String> test(@RequestParam String url, HttpServletRequest request) {
			if (url.equals("1")) {
				throw new IllegalArgumentException();
			}
			return ResponseEntity.ok(url);
		}

		@RequestMapping("/box/server/json")
		public @ResponseBody String json(@RequestParam String url) {
			return url;
		}

		@RequestMapping("/box/server/json2")
		public void json2() {
		}

		@RequestMapping("/box/server/json3")
		public void json3() {
		}

		@RequestMapping("/box/server/json4")
		public void json4() {
		}

		@RequestMapping("/box/server/json5")
		public void json5() {
		}

		@RequestMapping("/box/server/json6")
		public ResponseEntity<Void> json6() throws URISyntaxException {
			return ResponseEntity.created(new URI("/test/box/server/json6")).build();
		}

		@RequestMapping("/box/server/e1")
		public ResponseEntity<byte[]> e1() {
			return ResponseEntity.ok(new byte[]{1, 2, 3, 4, 5});
		}

		@RequestMapping("/box/server/e2")
		public byte[] e2() {
			return new byte[]{1, 2, 3, 4, 5};
		}

		@RequestMapping("/box/server/e3")
		public byte[] e3() {
			return new byte[]{1, 2, 3, 4, 5};
		}

		@RequestMapping("/box/server/void")
		public void vd(HttpServletRequest request, HttpServletResponse response) throws IOException {
			response.setContentType("text/html");
			response.setStatus(HttpStatus.OK.value());
			ServletOutputStream os = response.getOutputStream();
			os.write(1);
			os.flush();
			os.close();
		}
	}

	@RestController
	@RequestMapping("/test4")
	static class Test4Controller {

		@RequestMapping(value = "/box/server/{userId}/download", method = RequestMethod.GET)
		public Long download(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{userId}/download1", method = RequestMethod.GET)
		public Long download1(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{userId}/download2", method = RequestMethod.GET)
		public Long download2(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/*/download1", params = {"aaa", "bbb"}, method = RequestMethod.GET)
		public String download1(HttpServletRequest request) {
			return "*";
		}

		@RequestMapping(value = "/box/server/*/{userId}", method = RequestMethod.GET)
		public Long download2(@PathVariable Long userId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{appId}/{userId}", method = RequestMethod.GET)
		public Long download21(@PathVariable Long appId, @PathVariable Long userId, HttpServletRequest request) {
			return appId + userId;
		}

		@RequestMapping(value = "/box/server/download", method = RequestMethod.POST)
		public String download3(HttpServletRequest request) {
			return String.valueOf(new Random().nextInt(100));
		}

		@RequestMapping(value = "/box/server/download", method = RequestMethod.GET)
		public String download3a() {
			return "3a";
		}

		@RequestMapping(value = "/box/server/{value}", method = RequestMethod.GET)
		public String download4(HttpServletRequest request) {
			return "4";
		}

		@RequestMapping(value = "/box/server/*/file/download", method = RequestMethod.GET)
		public String download5(HttpServletRequest request) {
			return "5";
		}

		@RequestMapping(value = "/box/server/*/file/download/test", method = RequestMethod.GET)
		public String download6(HttpServletRequest request) {
			return "6";
		}

		@RequestMapping(value = "/box/server/*/file/download/{userId}/**", method = RequestMethod.GET)
		public Long download6(@PathVariable Long userId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping("/box/server/*/file/download/{userId}/*/{appId}")
		public Long download66(@PathVariable Long userId, @PathVariable String appId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping("/box/server/*/file/download/{userId}/*/ss/{appId}")
		public String download666(@PathVariable Long userId, @PathVariable String appId, HttpServletRequest request) {
			return appId;
		}

		@RequestMapping("/box/server/aaa/file/*/download/test")
		public String download7(HttpServletRequest request) {
			return "7";
		}

		@RequestMapping("/box/server/aaa/file/**")
		public String download8(HttpServletRequest request) {
			return "8";
		}

		@RequestMapping("/box/server/{userId}/{schoolId}/{appId}")
		public String download9(HttpServletRequest request) {
			return "9";
		}

		@RequestMapping("/box/server/*/{schoolId}/*/{appId}")
		public String download10(HttpServletRequest request) {
			return "10";
		}

		@RequestMapping("/box/server/{userId}/request")
		public String request(@PathVariable Long userId, @RequestParam String url, HttpServletRequest request) {
			return url;
		}

		@RequestMapping("/box/server/{userId}/request2")
		public String request2(@PathVariable Long userId, @RequestParam String url, HttpServletRequest request) {
			return url;
		}

		@RequestMapping("/box/server/test")
		public ResponseEntity<String> test(@RequestParam String url, HttpServletRequest request) {
			if (url.equals("1")) {
				throw new IllegalArgumentException();
			}
			return ResponseEntity.ok(url);
		}

		@RequestMapping("/box/server/json")
		public @ResponseBody String json(@RequestParam String url) {
			return url;
		}

		@RequestMapping("/box/server/json2")
		public void json2() {
		}

		@RequestMapping("/box/server/json3")
		public void json3() {
		}

		@RequestMapping("/box/server/json4")
		public void json4() {
		}

		@RequestMapping("/box/server/json5")
		public void json5() {
		}

		@RequestMapping("/box/server/json6")
		public ResponseEntity<Void> json6() throws URISyntaxException {
			return ResponseEntity.created(new URI("/test/box/server/json6")).build();
		}

		@RequestMapping("/box/server/e1")
		public ResponseEntity<byte[]> e1() {
			return ResponseEntity.ok(new byte[]{1, 2, 3, 4, 5});
		}

		@RequestMapping("/box/server/e2")
		public byte[] e2() {
			return new byte[]{1, 2, 3, 4, 5};
		}

		@RequestMapping("/box/server/e3")
		public byte[] e3() {
			return new byte[]{1, 2, 3, 4, 5};
		}

		@RequestMapping("/box/server/void")
		public void vd(HttpServletRequest request, HttpServletResponse response) throws IOException {
			response.setContentType("text/html");
			response.setStatus(HttpStatus.OK.value());
			ServletOutputStream os = response.getOutputStream();
			os.write(1);
			os.flush();
			os.close();
		}
	}

	@RestController
	@RequestMapping("/test5")
	static class Test5Controller {

		@RequestMapping(value = "/box/server/{userId}/download", method = RequestMethod.GET)
		public Long download(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{userId}/download1", method = RequestMethod.GET)
		public Long download1(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{userId}/download2", method = RequestMethod.GET)
		public Long download2(@PathVariable("userId") Long userId) {
			return userId;
		}

		@RequestMapping(value = "/box/server/*/download1", params = {"aaa", "bbb"}, method = RequestMethod.GET)
		public String download1(HttpServletRequest request) {
			return "*";
		}

		@RequestMapping(value = "/box/server/*/{userId}", method = RequestMethod.GET)
		public Long download2(@PathVariable Long userId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping(value = "/box/server/{appId}/{userId}", method = RequestMethod.GET)
		public Long download21(@PathVariable Long appId, @PathVariable Long userId, HttpServletRequest request) {
			return appId + userId;
		}

		@RequestMapping(value = "/box/server/download", method = RequestMethod.POST)
		public String download3(HttpServletRequest request) {
			return String.valueOf(new Random().nextInt(100));
		}

		@RequestMapping(value = "/box/server/download", method = RequestMethod.GET)
		public String download3a() {
			return "3a";
		}

		@RequestMapping(value = "/box/server/{value}", method = RequestMethod.GET)
		public String download4(HttpServletRequest request) {
			return "4";
		}

		@RequestMapping(value = "/box/server/*/file/download", method = RequestMethod.GET)
		public String download5(HttpServletRequest request) {
			return "5";
		}

		@RequestMapping(value = "/box/server/*/file/download/test", method = RequestMethod.GET)
		public String download6(HttpServletRequest request) {
			return "6";
		}

		@RequestMapping(value = "/box/server/*/file/download/{userId}/**", method = RequestMethod.GET)
		public Long download6(@PathVariable Long userId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping("/box/server/*/file/download/{userId}/*/{appId}")
		public Long download66(@PathVariable Long userId, @PathVariable String appId, HttpServletRequest request) {
			return userId;
		}

		@RequestMapping("/box/server/*/file/download/{userId}/*/ss/{appId}")
		public String download666(@PathVariable Long userId, @PathVariable String appId, HttpServletRequest request) {
			return appId;
		}

		@RequestMapping("/box/server/aaa/file/*/download/test")
		public String download7(HttpServletRequest request) {
			return "7";
		}

		@RequestMapping("/box/server/aaa/file/**")
		public String download8(HttpServletRequest request) {
			return "8";
		}

		@RequestMapping("/box/server/{userId}/{schoolId}/{appId}")
		public String download9(HttpServletRequest request) {
			return "9";
		}

		@RequestMapping("/box/server/*/{schoolId}/*/{appId}")
		public String download10(HttpServletRequest request) {
			return "10";
		}

		@RequestMapping("/box/server/{userId}/request")
		public String request(@PathVariable Long userId, @RequestParam String url, HttpServletRequest request) {
			return url;
		}

		@RequestMapping("/box/server/{userId}/request2")
		public String request2(@PathVariable Long userId, @RequestParam String url, HttpServletRequest request) {
			return url;
		}

		@RequestMapping("/box/server/test")
		public ResponseEntity<String> test(@RequestParam String url, HttpServletRequest request) {
			if (url.equals("1")) {
				throw new IllegalArgumentException();
			}
			return ResponseEntity.ok(url);
		}

		@RequestMapping("/box/server/json")
		public @ResponseBody String json(@RequestParam String url) {
			return url;
		}

		@RequestMapping("/box/server/json2")
		public void json2() {
		}

		@RequestMapping("/box/server/json3")
		public void json3() {
		}

		@RequestMapping("/box/server/json4")
		public void json4() {
		}

		@RequestMapping("/box/server/json5")
		public void json5() {
		}

		@RequestMapping("/box/server/json6")
		public ResponseEntity<Void> json6() throws URISyntaxException {
			return ResponseEntity.created(new URI("/test/box/server/json6")).build();
		}

		@RequestMapping("/box/server/e1")
		public ResponseEntity<byte[]> e1() {
			return ResponseEntity.ok(new byte[]{1, 2, 3, 4, 5});
		}

		@RequestMapping("/box/server/e2")
		public byte[] e2() {
			return new byte[]{1, 2, 3, 4, 5};
		}

		@RequestMapping("/box/server/e3")
		public byte[] e3() {
			return new byte[]{1, 2, 3, 4, 5};
		}

		@RequestMapping("/box/server/void")
		public void vd(HttpServletRequest request, HttpServletResponse response) throws IOException {
			response.setContentType("text/html");
			response.setStatus(HttpStatus.OK.value());
			ServletOutputStream os = response.getOutputStream();
			os.write(1);
			os.flush();
			os.close();
		}
	}

	public static class TestLayeredRequestMappingHandlerMapping extends LayeredRequestMappingHandlerMapping {

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
