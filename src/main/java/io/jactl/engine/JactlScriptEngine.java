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

package io.jactl.engine;

import io.jactl.*;
import io.jactl.runtime.*;

import javax.script.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

/**
 * ScriptEngine for Jactl to support JSR 223 scripting language API
 * <p>We keep a cache of the compiled scripts to avoid a recompile if the same
 * script is used in eval() multiple times.</p>
 */
public class JactlScriptEngine extends AbstractScriptEngine implements Invocable, Compilable {
  
  private JactlContext             jactlContext;
  private JactlScriptEngineFactory factory;
  private Map<String, Object>      globals;
  private JactlScript              jactlScript;
  private boolean                  untypedGlobals;

  public static String JACTL_DEBUG_LEVEL              = "jactl.debug.level";
  public static String JACTL_ASYNC                    = "jactl.async";
  public static String JACTL_ALLOW_UNDECLARED_GLOBALS = "jactl.allowUndeclaredGlobals";
  public static String JACTL_UNTYPED_GLOBALS          = "jactl.untypedGlobals";
  public static String JACTL_ALLOW_HOST_ACCESS        = "jactl.allowHostAccess";
  public static String JACTL_ALLOW_HOST_CLASS_LOOKUP  = "jactl.allowHostClassLookup";
  public static String JACTL_DISABLE_PRINT            = "jactl.disablePrint";
  public static String JACTL_DISABLE_EVAL             = "jactl.disableEval";
  public static String JACTL_DISABLE_DIE              = "jactl.disableDie";
  public static String JACTL_MAX_LOOP_ITERATIONS      = "jactl.maxLoopIterations";
  public static String JACTL_MAX_EXECUTION_TIME       = "jactl.maxExecutionTime";
  
  private final Map<String, JactlScript> scriptCache = Collections.synchronizedMap(
    new LinkedHashMap(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() > scriptCacheSize;
      }
    });

  // Size of cache of compiled scripts
  public static int scriptCacheSize = Integer.getInteger("jactl.engine.cache-size", 100);

  JactlScriptEngine(JactlScriptEngineFactory factory) {
    super();
    this.factory = factory;
  }

  @Override
  public Object eval(String script) throws ScriptException {
    return evalScript(script, globals, context.getReader(), context.getWriter());
  }

  @Override
  public Object eval(Reader reader) throws ScriptException {
    try {
      return eval(Utils.readAllChars(reader));
    }
    catch (IOException e) {
      throw new ScriptException("Error reading script: " + e);
    }
  }

  @Override public Object eval(String script, ScriptContext scriptContext) throws ScriptException {
    Map ctxGlobals = extractGlobals(scriptContext);
    return evalScript(script, ctxGlobals, scriptContext.getReader(), scriptContext.getWriter());
  }

  private Object evalScript(String script, Map ctxGlobals, Reader reader, Writer writer) throws ScriptException {
    JactlScript jactlScript = scriptCache.get(script);
    if (jactlScript == null) {
      jactlScript = compileScript(script, ctxGlobals);
      scriptCache.put(script, jactlScript);
    }
    this.jactlScript = jactlScript;
    try {
      return jactlScript.eval(ctxGlobals, reader, writer);
    }
    catch (JactlError e) {
      ScriptException scriptException = new ScriptException("Script error: " + e.getMessage());
      scriptException.initCause(e);
      throw scriptException;
    }
  }

  @Override public Object eval(Reader reader, ScriptContext context) throws ScriptException {
    String script;
    try {
      script = Utils.readAllChars(reader);
    }
    catch (IOException e) {
      throw new ScriptException("Error reading script: " + e.getMessage());
    }
    return eval(script, context);
  }

  @Override public Bindings createBindings() {
    return new SimpleBindings();
  }

  @Override public ScriptEngineFactory getFactory() {
    return factory;
  }

  @Override
  public Object invokeMethod(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException {
    Objects.requireNonNull(thiz);
    Objects.requireNonNull(name);
    if (thiz instanceof JactlObject) {
      JactlObject       jactlObject   = (JactlObject) thiz;
      Object            fieldOrHandle = jactlObject._$j$getFieldsAndMethods().get(name);
      if (fieldOrHandle == null) {
        throw new NoSuchMethodException("No such method '" + name + "'");
      }
      JactlMethodHandle handle;
      if (fieldOrHandle instanceof JactlMethodHandle) {
        handle = (JactlMethodHandle) fieldOrHandle;
        handle = handle.bindTo(thiz);
      }
      else {
        Field  field = (Field) fieldOrHandle;
        Object value;
        try {
          value = field.get(thiz);
        }
        catch (IllegalAccessException e) {
          throw new RuntimeException("Error getting value of field '" + name + "': " + e.getMessage(), e);
        }
        if (value instanceof JactlMethodHandle) {
          handle = (JactlMethodHandle) value;
        }
        else {
          throw new NoSuchElementException("Field " + name + " is not a method handle (is '" + RuntimeUtils.className(value) + "')");
        }
      }
      try {
        RuntimeState.setState(getJactlContext(), globals, context.getReader(), context.getWriter());
        return handle.invoke( null, "unknown", -1, args);
      }
      catch (Continuation c) {
        // Method called an async function so now we need to wait for it to finish
        CompletableFuture<Object> future = new CompletableFuture<>();
        getJactlContext().asyncWork(future::complete, c, null);
        try {
          return future.get();
        }
        catch (InterruptedException | ExecutionException e) {
          throw new ScriptException("Error invoking method '" + name + "': " + e);
        }
      }
      catch (Throwable e) {
        throw new ScriptException("Error invoking method '" + name + "': " + e);
      }
    }
    throw new ScriptException("Target object is not a JactlObject (is '" + RuntimeUtils.className(thiz) + "')");
  }

  @Override
  public Object invokeFunction(String name, Object... args) throws ScriptException {
    Objects.requireNonNull(name);
    if (this.jactlScript == null) {
      throw new ScriptException("No script found: JactlScriptEngine instance has not used eval() yet");
    }
    try {
      // Get the method handle to the wrapper of the function we want to invoke as it
      // handles invocation with Object[] args
      Class<?> compiledClass = jactlScript.getCompiledClass();
      String   fnHandleName  = Utils.JACTL_SCRIPT_MAIN + '$' + Utils.staticHandleName(Utils.wrapperName(name));
      Field    field  = compiledClass.getField(fnHandleName);
      JactlMethodHandle handle = (JactlMethodHandle)field.get(null);
      if (handle == null) {
        throw new IllegalStateException("Internal error: value of field " + field.getName() + " is null");
      }
      
      // Create an instance of the script and set its globals field
      JactlScriptObject instance = (JactlScriptObject)compiledClass.getDeclaredConstructor().newInstance();
      compiledClass.getField(Utils.JACTL_GLOBALS_NAME).set(instance, globals);
      
      // Bind handle to the instance and invoke it 
      handle = handle.bindTo(instance);
      RuntimeState.setState(getJactlContext(), globals, context.getReader(), context.getWriter());
      return handle.invoke(null, "unknown", 0, args == null ? new Object[0] : args);
    }
    catch (Continuation c) {
      // Method called an async function, so now we need to wait for it to finish
      CompletableFuture<Object> future = new CompletableFuture<>();
      getJactlContext().asyncWork(future::complete, c, null);
      try {
        return future.get();
      }
      catch (InterruptedException | ExecutionException e) {
        throw new ScriptException("Error invoking function '" + name + "': " + e);
      }
    }
    catch (NoSuchFieldException e) {
      throw new ScriptException("Unknown function '" + name + "'");
    }
    catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  private Object invokeFunctionAndCatch(String name, Object[] args) {
    try {
      return invokeFunction(name, args);
    }
    catch (ScriptException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public <T> T getInterface(Class<T> clasz) {
    Objects.requireNonNull(clasz);
    return (T) Proxy.newProxyInstance(jactlScript.getCompiledClass().getClassLoader(),
                                      new Class<?>[] { clasz },
                                      (proxyObj, method, args) -> invokeFunctionAndCatch(method.getName(), args));
  }

  private Object invokeMethodAndCatch(Object thiz, String name, Object[] args) {
    try {
      return invokeMethod(thiz, name, args);
    }
    catch (ScriptException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public <T> T getInterface(Object thiz, Class<T> clasz) {
    Objects.requireNonNull(clasz);
    return (T) Proxy.newProxyInstance(jactlScript.getCompiledClass().getClassLoader(),
                                      new Class<?>[] { clasz },
                                      (proxyObj, method, args) -> invokeMethodAndCatch(thiz, method.getName(), args));
  }

  private synchronized JactlContext getJactlContext() {
    if (jactlContext == null) {
      Object debugLevelStr = getProperty(JACTL_DEBUG_LEVEL);
      if (debugLevelStr == null) {
        debugLevelStr = System.getProperty(JACTL_DEBUG_LEVEL);
      }
      int debugLevel = 0;
      try {
        debugLevel = debugLevelStr == null ? 0 : Integer.parseInt(debugLevelStr.toString());
      }
      catch (NumberFormatException ignored) {}
      
      // Default to true to avoid issues
      this.untypedGlobals = getBoolean(JACTL_UNTYPED_GLOBALS, true);
      
      // Value for this should be a Predicate<String> or a Boolean 
      Object            allowHostClassLookupObj = getProperty(JACTL_ALLOW_HOST_CLASS_LOOKUP);
      Predicate<String> allowHostClassLookup    = allowHostClassLookupObj instanceof Predicate ? (Predicate<String>) allowHostClassLookupObj 
                                                                                               : allowHostClassLookupObj instanceof Boolean ? s -> (boolean)allowHostClassLookupObj : null;

      jactlContext = JactlContext.create()
                                 .debug(debugLevel)
                                 .async(getBoolean(JACTL_ASYNC, false))
                                 .classAccessToGlobals(true)
                                 .allowUndeclaredGlobals(getBoolean(JACTL_ALLOW_UNDECLARED_GLOBALS))
                                 .allowHostAccess(getBoolean(JACTL_ALLOW_HOST_ACCESS))
                                 .allowHostClassLookup(allowHostClassLookup)
                                 .disablePrint(getBoolean(JACTL_DISABLE_PRINT))
                                 .disableEval(getBoolean(JACTL_DISABLE_EVAL))
                                 .disableDie(getBoolean(JACTL_DISABLE_DIE))
                                 .maxLoopIterations(getInt(JACTL_MAX_LOOP_ITERATIONS, -1))
                                 .maxExecutionTime(getInt(JACTL_MAX_EXECUTION_TIME, -1))
                                 .build();
    }
    return jactlContext;
  }
  
  private int getInt(String propertyName, int defaultValue) {
    Object property = getProperty(propertyName);
    if (property == null) {
      return defaultValue; 
    }
    return Integer.parseInt(property.toString());
  }
  
  private boolean getBoolean(String propertyName) {
    return getBoolean(propertyName, false);
  }
  
  private boolean getBoolean(String propertyName, boolean defaultValue) {
    Object value = getProperty(propertyName);
    return value instanceof Boolean ? (Boolean) value : defaultValue;
  }
  
  private Object getProperty(String name) {
    // Look up ENGINE_SCOPE first
    Object value = this.get(name);
    if (value != null) {
      return value;
    }
    return this.getBindings(ScriptContext.GLOBAL_SCOPE).get(name);
  }

  private class JactlCompiledScript extends CompiledScript {
    JactlScript jactlScript;
    public JactlCompiledScript(JactlScript jactlScript) { this.jactlScript = jactlScript; }
    @Override public Object eval(ScriptContext context) throws ScriptException {
      try {
        return jactlScript.eval(extractGlobals(context), context.getReader(), context.getWriter());
      }
      catch (JactlError e) {
        ScriptException scriptException = new ScriptException("Script error: " + e.getMessage());
        scriptException.initCause(e);
        throw scriptException;
      }
    }
    @Override public Object eval() throws ScriptException {
      return eval(JactlScriptEngine.this.context);
    }
    @Override public ScriptEngine getEngine() {
      return JactlScriptEngine.this;
    }
  }
  
  @Override
  public CompiledScript compile(String script) throws ScriptException {
    jactlScript = compileScript(script, globals);
    return new JactlCompiledScript(jactlScript);
  }

  private JactlScript compileScript(String script, Map bindings) throws ScriptException {
    JactlContext jactlContext = getJactlContext();
    if (untypedGlobals) {
      // Use erased types (i.e. ANY) to avoid strange errors if bindings type changes
      // after compilation. This way compiled script won't get NullPointerException,
      // for example, if type was Integer at compile-time but then changes to String
      // when script is invoked.
      Map newBindings = new HashMap();
      bindings.keySet().forEach(k -> newBindings.put(k, null));
      bindings = newBindings;
    }
    try {
      return Jactl.compileScript(script, bindings, jactlContext);
    }
    catch (JactlError e) {
      ScriptException scriptException = new ScriptException("Compilation error: " + e.getMessage());
      scriptException.initCause(e);
      throw scriptException;
    }
  }

  @Override
  public CompiledScript compile(Reader script) throws ScriptException {
    try {
      return compile(Utils.readAllChars(script));
    }
    catch (IOException e) {
      throw new ScriptException("Error reading script: " + e.getMessage());
    }
  }
  
  private Map<String,Object> extractGlobals(ScriptContext context) {
    Bindings engineBindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
    Bindings globalBindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
    Map<String, Object> globals;
    if (globalBindings == null) {
      globals = engineBindings;
    }
    else {
      // Build a pretend Map that combines engine and global bindings with
      // engine bindings searched first
      globals = new AbstractMap<String, Object>() {
        @Override public Object get(Object key) {
          if (engineBindings.containsKey(key)) { return engineBindings.get(key); }
          return globalBindings.get(key);
        }
        @Override public boolean containsKey(Object key) { return engineBindings.containsKey(key) || globalBindings.containsKey(key); }
        @Override public Object put(String key, Object value) {
          if (engineBindings.containsKey(key)) { return engineBindings.put(key, value); }
          if (globalBindings.containsKey(key)) { return globalBindings.put(key, value); }
          return engineBindings.put(key, value);
        }
        @Override public Object remove(Object key) {
          if (engineBindings.containsKey(key)) { return engineBindings.remove(key); }
          return globalBindings.remove(key);
        }
        @Override public Set<String> keySet() { return new HashSet(globalBindings.keySet()) {{ addAll(engineBindings.keySet()); }}; }
        @Override public Set<Entry<String, Object>> entrySet() { return new HashSet(globalBindings.entrySet()) {{ addAll(engineBindings.entrySet()); }}; }
      };
    }
    return globals;
  }

  @Override
  public void setBindings(Bindings bindings, int scope) {
    super.setBindings(bindings, scope);
    globals = extractGlobals(context);
  }
}