/*
 * Copyright © 2022-2026 James Crawford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.jactl;

/**
 * Class that captures values of system properties that control behaviour of Jactl
 */
public class Properties {

  // Whether to enforce limits for array size and list size
  public static final boolean ENFORCE_LIMITS = Boolean.parseBoolean(System.getProperty("jactl.enforceLimits", "true"));
  
  // Maximum size for a list/array
  public static final int MAX_LIST_SIZE = Integer.getInteger("jactl.list.maxSize", 100_000_000);
  
  // Maximum size of internal buffer for skip()/limit() when used with negative offsets
  public static final int SKIP_LIMIT_MAX_BUFFER = Integer.getInteger("jactl.skipLimit.buffer.maxSize", 100_000_000);
  
  // Maximum depth of invokeDynamic chain to support multiple argument types at call site
  public static final int INVOKE_DYNAMIC_MAX_DEPTH = Integer.getInteger("jactl.invokeDynamic.maxDepth", 8);
  
  // Check for script timeout every N loop iterations 
  public static final int TIMEOUT_FREQ_CHECK = Integer.getInteger("jactl.loop.timeoutFreqCheck", 1000);
  
  // Total number of compiled scripts we keep for use by eval() function
  public static final int EVAL_SCRIPT_CACHE_SIZE = Integer.getInteger("jactl.eval.cacheSize", 100);
  
  // Size of thread local cache of DateTimeFormatter objects
  public static final int DATE_TIME_FORMATTER_CACHE_SIZE = Integer.getInteger("jactl.datetime.formatter.cache", 10);
  
  // Size of thread local cache of compiled regex Pattern objects
  public static final int PATTERN_CACHE_SIZE = Integer.getInteger("jactl.regex.cache.size", 100); // Per thread cache size
  
  // Whether to create local aliases for global binding variables in scripts
  public static final boolean LOCAL_ALIASES_FOR_GLOBALS  = Boolean.parseBoolean(System.getProperty("jactl.localAliasesForGlobals", "true"));
  
  // Whether to enable support for all of the date/time classes in Jactl
  public static final boolean DATE_TIME_CLASSES_ENABLED = Boolean.parseBoolean(System.getProperty("io.jactl.dateTimeClasses.enabled", "true"));
  
  // Whether to auto import date/time classes in every script
  public static final boolean AUTO_IMPORT_DATE_TIME_CLASSES = Boolean.parseBoolean(System.getProperty("io.jactl.dateTimeClasses.autoImport", "true"));
}
