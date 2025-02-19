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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.springframework.util.AntPathMatcher.DEFAULT_PATH_SEPARATOR;

/**
 * {@link LayeredRequestMappingHandlerMapping} is a subclass of {@link RequestMappingHandlerMapping}
 * that enhances the efficiency of mapping lookups while preserving its original functionality.
 * The core idea is to align paths hierarchically based on the / separator and then perform a step-by-step search to improve lookup performance.
 * The key implementation is in the {@link PatternLayerRegistry#lookupPattern(String, HttpServletRequest)} method of the {@link PatternLayerRegistry}.
 *
 * @author Conney Joo
 */
@SuppressWarnings("removal")
public class LayeredRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

	private static final String MATCHES_ATTRIBUTE = LayeredRequestMappingHandlerMapping.class.getName() + ".conditionMatched";

	private final PatternLayerRegistry patternLayerRegistry = new PatternLayerRegistry();

	/**
	 * After completing the super method, perform the initialization settings for PatternLayerRegistry.
	 * @param handlerMethods a read-only map with handler methods and mappings.
	 */
	@Override
	protected void handlerMethodsInitialized(Map<RequestMappingInfo, HandlerMethod> handlerMethods) {
		super.handlerMethodsInitialized(handlerMethods);
		this.patternLayerRegistry.afterPatternLayersSet();
	}

	/**
	 * After completing the super method, proceed with the registration of PatternLayerRegistry.
	 * @param handler the bean name of the handler or the handler instance
	 * @param method the method to register
	 * @param mapping the mapping conditions associated with the handler method
	 */
	@Override
	protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
		super.registerHandlerMethod(handler, method, mapping);

		MappingRegistration<RequestMappingInfo> mappingRegistration = getMappingRegistry().getRegistrations().get(mapping);
		if (mappingRegistration != null) {
			registerPatternLayer(mapping, mappingRegistration.getHandlerMethod());
		}
	}

	/**
	 * Register the mapping into the path-tree registry.
	 * If the method of the mapping is null, it will be assigned all RequestMethod values.
	 * If the path pattern is empty, it will be assigned a default value of "/".
	 * @param mapping the mapping conditions associated with the handler method
	 * @param handlerMethod the handlerMethod
	 */
	public void registerPatternLayer(RequestMappingInfo mapping, HandlerMethod handlerMethod) {
		Set<RequestMethod> methods = getMappingMethods(mapping);
		boolean isEmptyPatter = checkEmptyPattern(mapping.getPatternValues());
		for (RequestMethod m : methods) {
			if (isEmptyPatter) {
				String requestPattern = combinePath(m.name(), "/");
				this.patternLayerRegistry.register(requestPattern, mapping, handlerMethod);
			}
			else {
				for (String pattern : mapping.getPatternValues()) {
					if (StringUtils.hasText(pattern) && notQuantifier(pattern)) {
						String requestPattern = combinePath(m.name(), pattern);
						this.patternLayerRegistry.register(requestPattern, mapping, handlerMethod);
					}
				}
			}
		}
	}

	/**
	 * Unregister the mapping into the path-tree registry.
	 * If the method of the mapping is null, it will be assigned all RequestMethod values.
	 * If the path pattern is empty, it will be assigned a default value of "/".
	 * @param mapping the mapping conditions associated with the handler method
	 */
	protected void unregisterPatternLayer(RequestMappingInfo mapping) {
		Set<RequestMethod> methods = getMappingMethods(mapping);
		boolean isEmptyPatter = checkEmptyPattern(mapping.getPatternValues());
		for (RequestMethod m : methods) {
			if (isEmptyPatter) {
				String requestPattern = combinePath(m.name(), "/");
				this.patternLayerRegistry.unregister(requestPattern, mapping);
			}
			else {
				for (String pattern : mapping.getPatternValues()) {
					if (StringUtils.hasText(pattern)) {
						String requestPattern = combinePath(m.name(), pattern);
						this.patternLayerRegistry.unregister(requestPattern, mapping);
					}
				}
			}
		}
	}

	/**
	 * Check if all patternValues are empty.
	 * @param patternValues the mapping pattern
	 * @return patternValues whether it is empty.
	 */
	private boolean checkEmptyPattern(Set<String> patternValues) {
		for (String pattern : patternValues) {
			if (StringUtils.hasText(pattern)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks whether the given string contains the quantifier `?`.
	 * @param pattern the string to check
	 * @return true if the pattern does not contain the `?` quantifier; otherwise, false
	 */
	private boolean notQuantifier(String pattern) {
		return pattern.indexOf("?") == -1;
	}

	private Set<RequestMethod> getMappingMethods(RequestMappingInfo mapping) {
		Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
		if (methods == null || methods.isEmpty()) {
			methods = new HashSet<>();
			for (RequestMethod rm : RequestMethod.values()) {
				methods.add(rm);
			}
		}
		return methods;
	}

	@Override
	public void unregisterMapping(RequestMappingInfo mapping) {
		super.unregisterMapping(mapping);
		unregisterPatternLayer(mapping);
	}

	/**
	 * Using the hierarchical alignment approach,
	 * find the most matching PatternLayer in the path-tree for the given request path.
	 * If found, proceed with the subsequent handling in the same way as the parent class;
	 * otherwise, delegate the entire process to the parent class for further handling.
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 * @return the best-matching handler method, or {@code null} if no match
	 * @see #handleNoMatch(String, HttpServletRequest)
	 */
	@Override
	protected @Nullable HandlerMethod lookupHandlerMethod(String lookupPath, HttpServletRequest request) throws Exception {
		List<Match> matches = new ArrayList<>();
		List<RequestMappingInfo> directPathMatches = getMappingRegistry().getMappingsByDirectPath(lookupPath);
		if (directPathMatches != null) {
			addMatchingMappings(directPathMatches, matches, request);
		}
		if (matches.isEmpty()) {
			String requestPath = combinePath(request.getMethod(), lookupPath);
			PatternLayerRegistry.PatternLayer patternLayer = this.patternLayerRegistry.lookupPattern(requestPath, request);
			if (patternLayer != null && patternLayer.mapping != null) {
				matches = getFoundMatches(request);
			}
		}
		if (matches != null && !matches.isEmpty()) {
			Match bestMatch;
			if (matches.size() == 1) {
				bestMatch = matches.get(0);
			}
			else {
				Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
				matches.sort(comparator);
				bestMatch = matches.get(0);
				if (logger.isTraceEnabled()) {
					logger.trace(matches.size() + " matching mappings: " + matches);
				}
				if (CorsUtils.isPreFlightRequest(request)) {
					for (Match match : matches) {
						if (match.hasCorsConfig()) {
							return PREFLIGHT_AMBIGUOUS_MATCH;
						}
					}
				}
				else {
					Match firstBestMatch = matches.get(0);
					Match secondBestMatch = matches.get(1);
					if (comparator.compare(firstBestMatch, secondBestMatch) == 0) {
						Method m1 = firstBestMatch.getHandlerMethod().getMethod();
						Method m2 = secondBestMatch.getHandlerMethod().getMethod();
						String uri = request.getRequestURI();
						throw new IllegalStateException(
								"Ambiguous handler methods mapped for '" + uri + "': {" + m1 + ", " + m2 + "}");
					}
				}
			}
			if (bestMatch instanceof LayeredMatch) {
				PatternLayerRegistry.PatternLayer bestPattern = ((LayeredMatch) bestMatch).patternLayer;
				if (bestPattern != null && bestPattern.mapping != null) {
					request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, bestPattern.handlerMethod);
					handleMatch(bestPattern.mapping, lookupPath, request);
					return bestPattern.handlerMethod;
				}
			}
			else {
				request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, bestMatch.getHandlerMethod());
				handleMatch(bestMatch.getMapping(), lookupPath, request);
				return bestMatch.getHandlerMethod();
			}
		}

		return handleNoMatch(lookupPath, request);
	}

	/**
	 * Same functionality as the parent class {@link #getMatchingMapping(RequestMappingInfo, HttpServletRequest)},
	 * except that the returned type is LayeredMatch.
	 * @param patternLayers patternLayer matched from the path-tree
	 * @param request the current request
	 * @return an info in case of a match; or {@code null} otherwise.
	 */
	protected List<LayeredMatch> getMatchingMappings(List<PatternLayerRegistry.PatternLayer> patternLayers, HttpServletRequest request) {
		List<LayeredMatch> matches = new ArrayList<>();
		for (PatternLayerRegistry.PatternLayer patternLayer : patternLayers) {
			if (patternLayer.mapping != null) {
				RequestMappingInfo match = getMatchingMapping(patternLayer.mapping, request);
				MappingRegistration<RequestMappingInfo> registration = getMappingRegistry().getRegistrations().get(patternLayer.mapping);
				if (match != null && registration != null) {
					matches.add(new LayeredMatch(match, registration, patternLayer));
				}
			}
		}
		return matches;
	}

	/**
	 * Call getMatchingMapping with patternLayer and similar together,
	 * and then save the returned result in request.attribute[MATCHES_ATTRIBUTE].
	 * @param patternLayer patternLayer matched from the path-tree
	 * @param request the current request
	 * @return patternLayer matched from the path-tree
	 */
	public PatternLayerRegistry.@Nullable PatternLayer matchingCondition(
			PatternLayerRegistry.PatternLayer patternLayer, HttpServletRequest request) {
		if (patternLayer.mapping == null || getFoundMatches(request) != null) {
			return patternLayer;
		}
		List<PatternLayerRegistry.PatternLayer> patternLayers = new ArrayList<>();
		patternLayers.add(patternLayer);
		if (!CollectionUtils.isEmpty(patternLayer.getSimilarItems())) {
			patternLayers.addAll(patternLayer.getSimilarItems());
		}
		List<LayeredMatch> matches = getMatchingMappings(patternLayers, request);
		if (!matches.isEmpty()) {
			request.setAttribute(MATCHES_ATTRIBUTE, matches);
			return patternLayer;
		}
		else {
			return null;
		}
	}

	/**
	 * Retrieve the LayerMatch that matches from request.attribute[MATCHES_ATTRIBUTE].
	 * @param request the current request
	 * @return the final matching mappings
	 */
	@SuppressWarnings("unchecked")
	public @Nullable List<Match> getFoundMatches(HttpServletRequest request) {
		return (List<Match>) request.getAttribute(MATCHES_ATTRIBUTE);
	}

	/**
	 * Delegate to the parent class for further processing. if no match
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 * @return return best-matching handler method by the super.
	 */
	protected @Nullable HandlerMethod handleNoMatch(String lookupPath, HttpServletRequest request) throws Exception {
		return super.lookupHandlerMethod(lookupPath, request);
	}

	/**
	 * Combine Method and path together.
	 * @param method the http method
	 * @param path the current request path
	 * @return the string combining method and path
	 */
	private String combinePath(String method, String path) {
		path = path.length() > 0 && path.charAt(0) == '/' ? path : DEFAULT_PATH_SEPARATOR + path;
		return DEFAULT_PATH_SEPARATOR + method.toUpperCase(Locale.ROOT) + path;
	}

	/**
	 * An extended class of Match, primarily designed to store patternLayer.
	 */
	class LayeredMatch extends Match {

		PatternLayerRegistry.PatternLayer patternLayer;

		public LayeredMatch(RequestMappingInfo mapping,
							MappingRegistration<RequestMappingInfo> registration,
							PatternLayerRegistry.PatternLayer patternLayer) {
			super(mapping, registration);
			this.patternLayer = patternLayer;
		}
	}

	/**
	 * A registry that converts all mapping path patterns into a hierarchical path-tree by splitting them with the "/" delimiter,
	 * providing lookup methods and supporting concurrent access.
	 */
	class PatternLayerRegistry {

		private static final Pattern VARIABLES_PATTERN = Pattern.compile("\\{([\\w\\-.]+.)\\}");

		private final Map<LayerPath, PatternLayer> patternLayers = new LinkedHashMap<>();

		private final List<LayerMatcher> matches = new LinkedList<>();

		/**
		 *  Perform some settings after PatternLayerRegistry is created.
		 */
		public void afterPatternLayersSet() {
			setAllUncertain(this.patternLayers);
		}

		/**
		 * Initialize and add four types of matchers.
		 */
		public PatternLayerRegistry() {
			this.matches.add(new LayerPathMatcher());
			this.matches.add(new LayerVariableMatcher());
			this.matches.add(new LayerWildcardMatcher());
			this.matches.add(new LayerWildcard2Matcher());
		}

		/**
		 * Layer the pattern first, then add it sequentially to the path-tree.
		 * If multiple patterns have the same layered path, they will be added to the similarItems field.
		 * @param pattern the combined string of method and mapping.path
		 * @param mapping the mapping conditions associated with the handler method
		 * @param handlerMethod handlerMethod
		 */
		public void register(String pattern, RequestMappingInfo mapping, HandlerMethod handlerMethod) {
			PatternLayer patternLayer;
			Map<LayerPath, PatternLayer> root = this.patternLayers;
			LayerPath layer = LayerPath.create(pattern);

			for (; layer != null; layer = layer.next()) {
				patternLayer = root.computeIfAbsent(layer, k -> new PatternLayer(k));
				if (layer.isLeaf()) {
					if (patternLayer.similarItems == null) {
						patternLayer.init(pattern, mapping, handlerMethod, new LinkedList<>());
					}
					else {
						PatternLayer pl = new PatternLayer(layer);
						pl.init(pattern, mapping, handlerMethod, null);
						patternLayer.similarItems.add(pl);
					}
				}
				root = patternLayer.subLayers;
			}
		}

		/**
		 * Layer the pattern first, then remove it sequentially from the path-tree.
		 * @param pattern the combined string of method and mapping.path
		 * @param mapping the mapping conditions associated with the handler method
		 */
		public void unregister(String pattern, RequestMappingInfo mapping) {
			LayerPath layer = LayerPath.create(pattern);
			unregister(this.patternLayers, layer, mapping);
		}

		private void unregister(Map<LayerPath, PatternLayer> root, LayerPath layer, RequestMappingInfo mapping) {
			if (CollectionUtils.isEmpty(root)) {
				return;
			}
			PatternLayer patternLayer = root.get(layer);
			if (patternLayer == null) {
				return;
			}
			if (layer.isLeaf() && patternLayer.mapping == mapping) {
				root.remove(layer);
			}
			else {
				if (layer.next != null) {
					unregister(patternLayer.subLayers, layer.next, mapping);
					for (Map.Entry<LayerPath, PatternLayer> entry : patternLayer.subLayers.entrySet()) {
						if (entry.getValue().getMapping() != null || entry.getValue().subLayers.size() > 0) {
							return;
						}
					}
					root.remove(layer);
				}
			}
		}

		/**
		 * First, convert the request path into LayerPath,
		 * Then perform a depth search on the path-tree to find the best PatternLayer.
		 * The best PatternLayer found will be stored in request.attribute[MATCHES_ATTRIBUTE].
		 * @param requestPath mapping lookup path within the current servlet mapping
		 * @param request the current request
		 * @return the patternLayer found in the search.
		 */
		public @Nullable PatternLayer lookupPattern(String requestPath, HttpServletRequest request) {
			LayerPath lookupPath = LayerPath.create(requestPath);
			LayerPath lp = lookupPath;
			PatternLayer layer = null;
			Map<LayerPath, PatternLayer> layers = this.patternLayers;
			for (; lp != null; lp = lp.next(), layers = (layer != null ? layer.subLayers : null)) {
				if (layers == null || (layer = matching(layers, lp, request)) == null) {
					return null;
				}
				if (getFoundMatches(request) != null || lp.isLeaf()) {
					return layer.mapping != null ? layer : layer.subLayers.get(lp.wildcard2());
				}
			}
			return layer;
		}

		/**
		 * After the path-tree is created, apply some optimization settings to its search.
		 * @param patternLayers the path-tree
		 */
		private void setAllUncertain(Map<LayerPath, PatternLayer> patternLayers) {
			if (patternLayers == null || patternLayers.size() == 0) {
				return;
			}
			boolean varied = hasUncertainPath(patternLayers.keySet());
			for (PatternLayer patternLayer : patternLayers.values()) {
				patternLayer.uncertain = varied;
				setAllUncertain(patternLayer.subLayers);
			}
		}

		/**
		 * Determine whether there are any uncertain paths in layerPaths, example ('{}', '*', '**').
		 * @param layerPaths all paths at a certain level of the path-tree
		 * @return does there exist any non-path style in layerPaths
		 */
		private boolean hasUncertainPath(Collection<LayerPath> layerPaths) {
			for (LayerPath layerPath : layerPaths) {
				if (layerPath.style > 0) {
					return true;
				}
			}
			return false;
		}

		/**
		 * Extract the variable parts from the mapping pattern.
		 * @param pattern the mapping pattern
		 * @param variables collect variable names.
		 */
		private void extractPatternVariables(String pattern, List<String> variables) {
			Matcher matcher = VARIABLES_PATTERN.matcher(pattern);
			String name;

			while (matcher.find()) {
				name = matcher.group();
				variables.add(name.substring(1, name.length() - 1));
			}
		}

		/**
		 * Extract the variable parts from the uriTemplate.
		 * @param variables variable names in mapping pattern.
		 * @param patternPath the mapping pattern
		 * @param path the request path
		 * @param uriVariables collect variable values.
		 * @return collect variable values.
		 */
		@Deprecated
		private @Nullable Map<String, String> extractUriTemplateVariables(
				@Nullable List<String> variables,
				LayerPath patternPath,
				String path,
				Map<String, String> uriVariables) {
			LayerPath lookupPath = LayerPath.create(path);
			if (variables != null && variables.size() > 0) {
				LayerPath lp = patternPath.first();
				int index = 0;
				for (lp = lp.first(); lp != null && lookupPath != null; lp = lp.next(), lookupPath = lookupPath.next()) {
					if (lp.isVariable()) {
						uriVariables.put(variables.get(index++), lookupPath.getPattern());
					}
				}
				return uriVariables;
			}
			return null;
		}

		/**
		 * From a certain layer of the path-tree, find the PatternLayer that matches the request path.
		 * Use four types of matchers for matching, and the order of matching is: path > {} > * > **
		 * This process is a deep recursion
		 * @param patternLayers the path-tree
		 * @param path a segment in the request path
		 * @param request the current request
		 * @return the patternLayer found in the search
		 */
		private @Nullable PatternLayer matching(Map<LayerPath, PatternLayer> patternLayers, LayerPath path, HttpServletRequest request) {
			if (patternLayers.size() == 0) {
				return null;
			}
			PatternLayer patternLayer;
			for (LayerMatcher m : this.matches) {
				if ((patternLayer = m.match(patternLayers, path, request)) != null) {
					return patternLayer;
				}
			}
			return null;
		}

		/**
		 * Split the mapping pattern into hierarchical data, that is,
		 * the node objects in the path-tree.
		 */
		class PatternLayer {

			final LayerPath path;

			final Map<LayerPath, PatternLayer> subLayers;

			@Nullable String pattern;

			@Nullable List<String> variables;

			@Nullable RequestMappingInfo mapping;

			@Nullable HandlerMethod handlerMethod;

			@Nullable List<PatternLayer> similarItems;

			boolean uncertain = false;

			public PatternLayer(LayerPath path) {
				Assert.notNull(path, "Mapping must not be null");
				this.path = path;
				this.subLayers = new HashMap<>();
			}

			public void init(String pattern, RequestMappingInfo mapping, HandlerMethod handlerMethod, @Nullable List<PatternLayer> similarPatternLayers) {
				this.pattern = pattern;
				this.mapping = mapping;
				this.handlerMethod = handlerMethod;
				this.similarItems = similarPatternLayers;
				this.variables = new LinkedList<>();
				extractPatternVariables(pattern, this.variables);
			}

			@Deprecated
			Map<String, String> getUriTemplateVariables(String requestPath) {
				LayerPath lookupPath = LayerPath.create(requestPath);
				if (this.variables != null && this.variables.size() > 0) {
					Map<String, String> uriVariables = new LinkedHashMap<>();
					LayerPath lp = this.path.first();
					int index = 0;
					for (; lp != null && lookupPath != null; lp = lp.next(), lookupPath = lookupPath.next()) {
						if (lp.isVariable()) {
							uriVariables.put(this.variables.get(index++), lookupPath.getPattern());
						}
					}
					return uriVariables;
				}
				return Collections.emptyMap();
			}

			public @Nullable String getPattern() {
				return this.pattern;
			}

			public @Nullable RequestMappingInfo getMapping() {
				return this.mapping;
			}

			public @Nullable HandlerMethod getHandlerMethod() {
				return this.handlerMethod;
			}

			public @Nullable List<PatternLayer> getSimilarItems() {
				return this.similarItems;
			}
		}

		/**
		 * Interface of Layer Matcher.
		 */
		interface LayerMatcher {

			@Nullable PatternLayer match(Map<LayerPath, PatternLayer> patternLookup, LayerPath path, HttpServletRequest request);
		}

		/**
		 * A pure path style matching tool.
		 */
		class LayerPathMatcher implements LayerMatcher {

			/**
			 * Search for a PatternLayer in the current path-tree layer that matches request path.
			 * If the current layer has uncertain factors, continue exploring downward (repeating the matching process);
			 * otherwise, return directly.
			 * @param patternLookup the path-tree
			 * @param path a segment in the request path
			 * @param request the current request
			 * @return the patternLayer found in the search
			 */
			@Override
			public @Nullable PatternLayer match(Map<LayerPath, PatternLayer> patternLookup, LayerPath path, HttpServletRequest request) {
				PatternLayer patternLayer = lookup(patternLookup, path);
				if (patternLayer != null) {
					if (patternLayer.uncertain) {
						PatternLayer finalPatternLayer = explore(path, patternLayer, request);
						if (finalPatternLayer == null) {
							return null;
						}
						if (finalPatternLayer.mapping != null && getFoundMatches(request) != null) {
							return finalPatternLayer;
						}
					}
					else if (path.isLeaf()) {
						if (patternLayer.mapping == null) {
							return null;
						}
						return matchingCondition(patternLayer, request);
					}
				}
				return patternLayer;
			}

			/**
			 * Returns the PatternLayer to which the specified path is mapped
			 * Find PatternLayer in the path tree by path.
			 * @param patternLookup the path-tree
			 * @param path a segment in the request path.
			 * @return the PatternLayer to which the specified path is mapped
			 */
			public @Nullable PatternLayer lookup(Map<LayerPath, PatternLayer> patternLookup, LayerPath path) {
				return patternLookup.get(path);
			}

			/**
			 * Continuously explore downward in the path tree until a PatternLayer matching the path is found;
			 * otherwise, return null. If a matching PatternLayer is found at the last layer (leaf node),
			 * perform the matchingCondition method for validation. If the validation passes,
			 * notify the external search that the desired result has been found and can be terminated.
			 * @param path a segment in the request path.
			 * @param pl the matched node in the path tree.
			 * @param request the current request
			 * @return the patternLayer found in the search
			 */
			public @Nullable PatternLayer explore(LayerPath path, PatternLayer pl, HttpServletRequest request) {
				if ((path.isLeaf() && pl.mapping != null)) {
					return matchingCondition(pl, request);
				}
				LayerPath next = path.next;
				PatternLayer patternLayer;
				if ((next != null && pl.subLayers.size() > 0 && (patternLayer = matching(pl.subLayers, next, request)) != null)) {
					return patternLayer;
				}
				else {
					return null;
				}
			}
		}

		/**
		 * A variable path style matching tool.
		 */
		class LayerVariableMatcher extends LayerPathMatcher {

			/**
			 * The search method is almost the same as super match, except that the parameter for lookup is '{}'.
			 * @param patternLookup the path-tree
			 * @param path a segment in the request path
			 * @param request the current request
			 * @return the patternLayer found in the search
			 */
			@Override
			public @Nullable PatternLayer match(Map<LayerPath, PatternLayer> patternLookup, LayerPath path, HttpServletRequest request) {
				PatternLayer patternLayer = lookup(patternLookup, path);
				if (patternLayer != null) {
					PatternLayer finalPatternLayer = explore(path, patternLayer, request);
					if (finalPatternLayer == null) {
						return null;
					}
					else if (finalPatternLayer.mapping != null) {
						return finalPatternLayer;
					}
					else {
						return patternLayer;
					}
				}
				return null;
			}

			/**
			 * Returns the PatternLayer to which the specified '{}' is mapped
			 * Find PatternLayer in the path tree by path.
			 * @param patternLookup the path-tree
			 * @param path a segment in the request path.
			 * @return the PatternLayer to which the specified path is mapped
			 */
			@Override
			public @Nullable PatternLayer lookup(Map<LayerPath, PatternLayer> patternLookup, LayerPath path) {
				return patternLookup.get(path.variable());
			}
		}

		/**
		 * A wildcard path style matching tool.
		 */
		class LayerWildcardMatcher extends LayerVariableMatcher {

			/**
			 * Returns the PatternLayer to which the specified '*' is mapped
			 * Find PatternLayer in the path tree by path.
			 * @param patternLookup the path-tree
			 * @param path a segment in the request path.
			 * @return the PatternLayer to which the specified path is mapped
			 */
			@Override
			public @Nullable PatternLayer lookup(Map<LayerPath, PatternLayer> patternLookup, LayerPath path) {
				return patternLookup.get(path.wildcard());
			}
		}

		/**
		 * A any path style matching tool.
		 */
		class LayerWildcard2Matcher extends LayerPathMatcher {

			/**
			 * If a '**' path exists at this layer,
			 * it supports skipping any intermediate paths until it matches the next segment of the path.
			 * For example:
			 *  /a/b/'**'/f/g, /a/b/c/d/e/f/g match success
			 *  /a/b/c/d/'**', /a/b/c/d/e/f/g match success
			 * @param patternLookup the path-tree
			 * @param path a segment in the request path
			 * @param request the current request
			 * @return the patternLayer found in the search
			 */
			@Override
			public @Nullable PatternLayer match(Map<LayerPath, PatternLayer> patternLookup, LayerPath path, HttpServletRequest request) {
				PatternLayer patternLayer = lookup(patternLookup, path);
				LayerPath lp = path;

				if (patternLayer != null) {
					if (lp.isLeaf()) {
						return matchingCondition(patternLayer, request);
					}
					for (; lp != null; lp = lp.next()) {
						if (matching(patternLayer.subLayers, lp, request) != null) {
							lp.next(lp.clone());
							return patternLayer;
						}
					}
					path.end();
					return matchingCondition(patternLayer, request);
				}

				return null;
			}

			/**
			 * Returns the PatternLayer to which the specified '**' is mapped
			 * Find PatternLayer in the path tree by path.
			 * @param patternLookup the path-tree
			 * @param path a segment in the request path.
			 * @return the PatternLayer to which the specified path is mapped
			 */
			@Override
			public @Nullable PatternLayer lookup(Map<LayerPath, PatternLayer> patternLookup, LayerPath path) {
				return patternLookup.get(path.wildcard2());
			}
		}

		/**
		 * Convert the URI into layered short paths based on the '/' separator,
		 * with each path connected sequentially through next and prev.
		 */
		public static final class LayerPath {

			private static final String VARIABLE_LAYER_NAME = "{}";
			private static final String WILDCARD_LAYER_NAME = "*";
			private static final String WILDCARD2_LAYER_NAME = "**";

			private static final int PATH_STYLE = 0;
			private static final int VARIABLE_STYLE = 1;
			private static final int WILDCARD_STYLE = 2;
			private static final int WILDCARD2_STYLE = 3;

			private String pattern = "";

			private int style = -1;

			@Nullable private LayerPath prev;
			@Nullable private LayerPath next;

			private LayerPath(String path) {
				if (path.equals(DEFAULT_PATH_SEPARATOR)) {
					this.pattern = path;
					return;
				}
				if (path.charAt(0) == '/') {
					path = path.substring(1);
				}
				int start = path.indexOf(DEFAULT_PATH_SEPARATOR);
				if (start == -1) {
					this.pattern = path;
					return;
				}
				this.pattern = path.substring(0, start);
				String nextPattern = path.substring(start);
				if (StringUtils.hasText(nextPattern)) {
					this.next = new LayerPath(nextPattern).layer();
					this.next.prev = this;
				}
			}

			private LayerPath(String pattern, int style, @Nullable LayerPath prev, @Nullable LayerPath next) {
				this.pattern = pattern;
				this.style = style;
				this.prev = prev;
				this.next = next;
			}

			public String getPattern() {
				return this.pattern;
			}

			public LayerPath next(LayerPath lp) {
				this.next = lp;
				lp.prev = this;
				return this.next;
			}

			public @Nullable LayerPath next() {
				return this.next;
			}

			public LayerPath prev(LayerPath lp) {
				this.prev = lp;
				lp.next = this;
				return this.prev;
			}

			public @Nullable LayerPath prev() {
				return this.prev;
			}

			public LayerPath first() {
				LayerPath lp = this;
				while (lp.prev != null) {
					lp = lp.prev;
				}
				return lp;
			}

			public LayerPath last() {
				LayerPath lp = this;
				while (lp.next != null) {
					lp = lp.next;
				}
				return lp;
			}

			public void end() {
				this.next = null;
			}

			public int getStyle() {
				if (this.style == -1) {
					if (this.pattern.startsWith("{") && this.pattern.endsWith("}")) {
						this.style = VARIABLE_STYLE;
					}
					else if (this.pattern.equals(WILDCARD_LAYER_NAME)) {
						this.style = WILDCARD_STYLE;
					}
					else if (this.pattern.equals(WILDCARD2_LAYER_NAME)) {
						this.style = WILDCARD2_STYLE;
					}
					else {
						this.style = PATH_STYLE;
					}
				}
				return this.style;
			}

			public boolean isLeaf() {
				return this.next == null;
			}

			public boolean isVariable() {
				return this.style == VARIABLE_STYLE;
			}

			public LayerPath layer() {
				this.style = getStyle();
				switch (this.style) {
					case VARIABLE_STYLE:
						this.pattern = VARIABLE_LAYER_NAME;
						break;
					case WILDCARD_STYLE:
						this.pattern = WILDCARD_LAYER_NAME;
						break;
					case WILDCARD2_STYLE:
						this.pattern = WILDCARD2_LAYER_NAME;
						break;
				}
				return this;
			}

			public String trimSeparator(String value) {
				int len = value.length();
				int st = 0;
				while ((st < len) && (value.charAt(st) == '/')) {
					st++;
				}
				while ((st < len) && (value.charAt(len - 1) == '/')) {
					len--;
				}
				return ((st > 0) || (len < value.length())) ? value.substring(st, len) : value;
			}

			public LayerPath wildcard() {
				return new LayerPath(WILDCARD_LAYER_NAME);
			}

			public LayerPath wildcard2() {
				return new LayerPath(WILDCARD2_LAYER_NAME);
			}

			public LayerPath variable() {
				return new LayerPath(VARIABLE_LAYER_NAME);
			}

			public static LayerPath create(String path) {
				return new LayerPath(path);
			}

			@Override
			public LayerPath clone() {
				return new LayerPath(this.pattern, this.style, this.next, this.prev);
			}

			@Override
			public boolean equals(Object other) {
				if (this == other) {
					return true;
				}
				if (!(other instanceof LayerPath)) {
					return false;
				}
				LayerPath otherInfo = (LayerPath) other;
				return (this.pattern.equals(otherInfo.pattern));
			}

			@Override
			public int hashCode() {
				return this.pattern.hashCode() * 31;
			}

			@Override
			public String toString() {
				return this.pattern;
			}
		}
	}
}
