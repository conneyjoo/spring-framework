# LayeredRequestMappingHandlerMapping

### 1. Overview

`LayeredRequestMappingHandlerMapping` is a subclass of `RequestMappingHandlerMapping`, designed to improve request mapping lookup efficiency through a layered path matching mechanism while preserving its original functionality.



### 2. Background and Purpose

In the current project, the commonly used variable style API in `RequestMappingHandlerMapping` matches request paths by iterating through all registered mappings and selecting the best match. As the number of mappings increases, this linear search approach leads to a gradual decline in performance.

`LayeredRequestMappingHandlerMapping` optimizes the lookup process by splitting paths into multiple layers using the '/' symbol and leveraging a hierarchical structure. This layered approach significantly reduces the search scope during matching, thereby improving efficiency.



### 3. Design Plan

The traditional path-style API can be efficiently looked up using a `HashMap`, which provides sufficient performance. However, the variable-style API differs because its path contains dynamic variables, making it impossible to locate using a `HashMap`. This design primarily focuses on optimizing and improving the performance of variable-style API matching. RESTful APIs are currently the mainstream interface approach and are widely used.

**Note**: If the URI mapping contains a `?`, `LayeredRequestMappingHandlerMapping` will not support it and will delegate the request to the parent class for processing.

- **Core Concepts**

  - **Layered Path**: The request path is split into multiple hierarchical levels based on the `/` separator, where each level is connected to the one above and below. The mapping path is divided into multiple levels, each called a **LayerPath**. For example, the path `/user/{id}/profile` is decomposed into three layers: `user`, `{id}`, and `profile`.
  - **Path Tree**: A hierarchical data structure used to store and manage layered paths.
  - **Match Layer**: Different matching strategies, such as static path matching, variable matching, and wildcard matching. When matching a request path, the process proceeds layer by layer. Each level can be a static path, a variable path, or a wildcard path. This layered matching mechanism makes path matching more flexible and efficient.

  **Main Classes and Methods**

  - `LayeredRequestMappingHandlerMapping`: The main class that extends `RequestMappingHandlerMapping`, responsible for registering and locating request mappings.
  - `PatternLayerRegistry`: A registry for the path tree, responsible for building and managing the path tree.
  - `PatternLayer`: A node in the path tree that contains path information, mapping details, and child nodes.
  - `LayerPath`: Represents the hierarchical structure of a path, supporting path splitting and concatenation.
  - `MatchLayer`: The interface for matching layers, defining various matching strategies.
  - `LayerPathMatch`, `LayerVariableMatcher`, `LayerWildcardMatcher`, `LayerWildcard2Matcher`: Specific implementations of the matching layers.

  **Lookup and Matching**

  - **Lookup**: In the `lookupHandlerMethod` method, the request path and method are combined into a layered path, which is then searched within the path tree. If a matching `PatternLayer` is found, further validation is performed.
  - **Matching**: During each layer traversal, different matchers (`LayerPathMatcher`, `LayerVariableMatcher`, `LayerWildcardMatcher`, `LayerWildcard2Matcher`) are used for matching. The matching results are stored in the request attributes for subsequent processing.

  **Optimization and Performance**

  - **Layered Storage**: By storing paths in a hierarchical manner, the number of lookups is reduced, improving search efficiency.
  - **Matching Strategies**: Different matching layers support various strategies, such as static path matching, variable matching, and wildcard matching, ensuring flexibility and accuracy.
  - **Caching and Reuse**: Matching results are cached, and deep traversal is terminated early whenever possible, further improving performance.

  **Reliability**

  - **Compatibility**: Since it extends `RequestMappingHandlerMapping`, it maintains compatibility with existing functionality. If no match is found, the request is ultimately handed over to the parent class for processing.



#### 3.1. Path Tree

![image-20250213193206193](./image-20250213193206193.png)

The figure above shows the construction of a path tree by splitting all mappings in the project into multiple layers according to the `/` delimiter. For example, with the mappings `/box/server/{}/download` and `/box/server/download`, the process of building the path tree is as follows:

- The first two segments of both mappings are the same (`/box/server`), so there is only one top-level node named `box`, under which is `server`.
- The third segment is `{}` and `download`, so under `server`, there are two branches: `{}` and `download`.
- The fourth segment of the first mapping is `download`, so under the `{}` branch, there is a `download` node. However, the second mapping ends with `download` and has no fourth segment, making `download` a leaf node.
- All mappings in the project are processed in this manner to ultimately construct a path tree.



#### 3.2. Path Lookup

The primary purpose of converting mappings into a path tree is to utilize its hierarchical structure to narrow down the search scope, avoiding the need to iterate through all mappings for matching. When a request is made, the request path is split into multiple layers according to the `/` delimiter. The first layer is matched with the root node of the path tree. If successful, the next layer continues to match with the child nodes, and so on. If the current layer node contains uncertain nodes such as `{}`, `*`, or `**`, the search proceeds into these nodes to find the best match. If a match is found in this branch, the search exits early to avoid performance impacts. This process is repeated until the last layer of the path is reached, and if the corresponding node is a leaf node, the associated mapping is returned. This entire process significantly reduces the search scope during matching, thereby improving efficiency.

**RequestPath layered and searched in the path tree code:**

Code

```
/**
 * First, convert the request path into LayerPath,
 * Then perform a depth search on the path-tree to find the best PatternLayer.
 * The best PatternLayer found will be stored in request.attribute[MATCHES_ATTRIBUTE].
 * @see #MATCHES_ATTRIBUTE
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
```

**Layer-by-layer matching**:

- A `for` loop is used to traverse each `LayerPath` layer until `lp` becomes `null`.
- In the current layer of the path tree, search for a matching `PatternLayer` by calling the `matching` method.
- If a matching `PatternLayer` is found, update `layer` and `layers`, and continue to the next layer.
- If `lp` is a leaf node (i.e., `lp.isLeaf()` returns `true`) or a `LayeredMatch` has already been found, return the current `layer`.



**Matching a layer of RequestPath in the path tree node code:**

Code

```
/**
 * From a certain layer of the path-tree, find the PatternLayer that matches the request path.
 * Use four types of matchers for matching, and the matching order is: path > {} > * > **
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
```

**Path matching:**

- `matches` is a collection of multiple `LayerMatcher` objects, which are arranged in the order of `path > {} > * > **`. The order of matchers determines the matching priority, with the `path` matcher having the highest priority and the `**` matcher having the lowest.
- Iterate through each matcher `m` and call its `match` method to attempt to match the current path segment `path`.
- If a matcher returns a non-`null` `PatternLayer`, it is immediately returned.



**Implementations of Matchers**

1. **Path Matching (`LayerPathMatcher`)**:

   Code

   ```
   /**
    * A pure path style matching tool
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
      * @return
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
   ```

   - **Lookup**: Search for a `PatternLayer` in the current layer of the path tree that matches the `LayerPath`.
   - **Explore**: If a matching `PatternLayer` is found and the layer contains uncertain factors (such as variables or wildcards), continue to explore downward until a matching leaf node is found.
   - **Validation**: If a matching leaf node is found, call the `matchingCondition` method for further validation.

2. **Variable Matching (`LayerVariableMatcher`)**:

   Code

   ```
   /**
    * A variable path style matching tool
    */
   class LayerVariableMatcher extends LayerPathMatcher {
   
      /**
       * The search method is almost the same as super match, except that the parameter for lookup is '{}'."
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
   ```

   - **Lookup**: Search for a `PatternLayer` in the current layer of the path tree that matches the variable path (e.g., `{id}`).
   - **Explore**: Continue to explore downward until a matching leaf node is found.
   - **Validation**: Call the `matchingCondition` method for further validation.

3. **Wildcard Matching (`LayerWildcardMatcher`)**:

   Code

   ```
   /**
    * A wildcard path style matching tool
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
   ```

   - **Lookup**: Search for a `PatternLayer` in the current layer of the path tree that matches the wildcard path (e.g., `*`).
   - **Explore**: Continue to explore downward until a matching leaf node is found.
   - **Validation**: Call the `matchingCondition` method for further validation.

4. **Multi-level Wildcard Matching (`LayerWildcard2Matcher`)**:

   Code

   ```
   /**
    * A any path style matching tool
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
   ```

   - **Lookup**: Search for a `PatternLayer` in the current layer of the path tree that matches the multi-level wildcard path (e.g., `**`).
   - **Explore**: Support skipping intermediate paths until a matching leaf node is found.
   - **Validation**: Call the `matchingCondition` method for further validation.



### 4. Performance Testing

**Test Specifications:**

A: 2 controllers, 64 mappings

B: 5 controllers, 156 mappings

**Test Method:**

Use JHM for testing.

**Performance Test Data (OPS):**

**Path Style**

@GetMapping(value = "/test1/box/system/info")

Request URL: /test1/box/system/info

|               handler               | 64 mappings                                   | 156 mappings                                  |
| :---------------------------------: | --------------------------------------------- | :-------------------------------------------- |
|    RequestMappingHandlerMapping     | (min, avg, max) = (1703491, 2443469, 3012852) | (min, avg, max) = (1702488, 2063486, 2727431) |
| LayeredRequestMappingHandlerMapping | (min, avg, max) = (2079133, 2653335, 2972061) | (min, avg, max) = (1562584, 2273202, 2801109) |

Why the test data is the same is because it handles the method consistently with `RequestMappingHandlerMapping`, both first using `MappingRegistry.getMappingsByDirectPath(lookupPath)` to look up once. If not found, then proceed with their respective subsequent processing. The `getMappingsByDirectPath` method uses path to look up in a `HashMap`.



**Variable Style**

@GetMapping(value = "/test1/box/server/{userId}/download")

Request URL: /test1/box/server/1/download

| handler                             | 64 mappings                                   | 156 mappings                                 |
| :---------------------------------- | --------------------------------------------- | :------------------------------------------- |
| RequestMappingHandlerMapping        | (min, avg, max) = (198084, 265895, 311141)    | (min, avg, max) = (114361, 129306, 140919)   |
| LayeredRequestMappingHandlerMapping | (min, avg, max) = (1108880, 1232683, 1313233) | (min, avg, max) = (929322, 1150678, 1424788) |



**Wildcard Style**

@GetMapping(value = "/test1/box/server/*/file/download/{userId}/**")

Request URL: /test1/box/server/x/file/download/1/a/b/c/d

| handler                             | 64 mappings                                | 156 mappings                               |
| :---------------------------------- | ------------------------------------------ | :----------------------------------------- |
| RequestMappingHandlerMapping        | (min, avg, max) = (248597, 277619, 322315) | (min, avg, max) = (90514, 120986, 136340)  |
| LayeredRequestMappingHandlerMapping | (min, avg, max) = (571164, 608704, 635916) | (min, avg, max) = (496474, 591531, 625612) |



**Benchmark Test Code:**

```
LayeredRequestMappingHandlerMappingBenchmark.java
RequestMappingHandlerMappingBenchmark.java
```



### 5. Integration Method

Its integration method is the same as that of `RequestMappingHandlerMapping`, both being added to the `DispatcherServlet.handlerMappings` collection during initialization. When handling a web request, `DispatcherServlet` processes the request by iterating through the `handlerMappings` collection in sequence, with each `HandlerMapping` implementation class looking up the current request. The search exits upon the first successful match. `LayeredRequestMappingHandlerMapping` is prioritized by adjusting its position in the `handlerMappings` collection to be ahead of `RequestMappingHandlerMapping`. Thus, web requests are first processed by `LayeredRequestMappingHandlerMapping`, and if no match is found, it is then handed over to the next `RequestMappingHandlerMapping` for processing.

**Order of `HandlerMapping` in `handlerMappings`:**

- LayeredRequestMappingHandlerMapping.order = -1
- RequestMappingHandlerMapping.order = 0



### 6. Summary

`LayeredRequestMappingHandlerMapping` is a subclass of `RequestMappingHandlerMapping`, designed to optimize the efficiency of request mapping through a hierarchical path matching mechanism. It reduces the search scope during matching by storing paths hierarchically, thereby improving matching efficiency. Particularly in handling variable style APIs, the performance improvement is significant. This class inherits from `RequestMappingHandlerMapping`, maintaining compatibility with the original functionality, and by adjusting its position in the `DispatcherServlet`'s `handlerMappings` collection, it prioritizes request processing. Performance test data shows that `LayeredRequestMappingHandlerMapping` has a slight edge in handling variable style and wildcard style paths.

Thanks