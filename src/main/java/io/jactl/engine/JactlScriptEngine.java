/*
 * Copyright © 2022,2023,2024  James Crawford
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
    this.factory = factory;
    jactlContext = JactlContext.create()
                               .debug(0)
                               .classAccessToGlobals(true)
                               .build();
  }
  
  @Override public Object eval(String script, ScriptContext context) throws ScriptException {
    this.context = context;
    this.globals = extractGlobals(context);
    JactlScript jactlScript = scriptCache.get(script);
    if (jactlScript == null) {
      // Erase types of bindings (make them all ANY) so that if script is rerun with
      // different types bound to the globals it will still run
      HashMap erasedBindings = new HashMap();
      globals.keySet().forEach(k -> erasedBindings.put(k, null));
      jactlScript = Jactl.compileScript(script, erasedBindings, jactlContext);
      scriptCache.put(script, jactlScript);
    }
    this.jactlScript = jactlScript;
    return jactlScript.runSync(globals, context.getReader(), context.getWriter());
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
    if (this.globals == null || this.context == null) {
      throw new ScriptException("No ScriptContext found: JactlScriptEngine instance has not used eval() yet");
    }
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
        RuntimeState.setState(jactlContext, globals, context.getReader(), context.getWriter());
        return handle.invoke( null, "unknown", -1, args);
      }
      catch (Continuation c) {
        // Method called an async function so now we need to wait for it to finish
        CompletableFuture<Object> future = new CompletableFuture<>();
        jactlContext.asyncWork(future::complete, c, null);
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
    if (this.globals == null || this.context == null || this.jactlScript == null) {
      throw new ScriptException("No ScriptContext found: JactlScriptEngine instance has not used eval() yet");
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
      RuntimeState.setState(jactlContext, globals, context.getReader(), context.getWriter());
      return handle.invoke(null, "unknown", 0, args == null ? new Object[0] : args);
    }
    catch (Continuation c) {
      // Method called an async function so now we need to wait for it to finish
      CompletableFuture<Object> future = new CompletableFuture<>();
      jactlContext.asyncWork(future::complete, c, null);
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

  private class JactlCompiledScript extends CompiledScript {
    JactlScript jactlScript;
    public JactlCompiledScript(JactlScript jactlScript) { this.jactlScript = jactlScript; }
    @Override public Object eval(ScriptContext context) throws ScriptException {
      JactlScriptEngine.this.context = context;
      JactlScriptEngine.this.globals = extractGlobals(context);
      return jactlScript.runSync(globals, context.getReader(), context.getWriter());
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
    // Use a globals map where all lookups return null so that any
    // reference to a global is of type ANY. This means that any
    // spelling mistakes in variable names will be treated as a
    // reference to a global.
    Map<String,Object> globalsMap = new AbstractMap<String, Object>() {
      @Override public Set<Entry<String, Object>> entrySet() {
        return Collections.emptySet();
      }
      @Override public Object get(Object key) {
        return null;
      }
      @Override public boolean containsKey(Object key) {
        return true;
      }
    };
    jactlScript = Jactl.compileScript(script, globalsMap, jactlContext);
    return new JactlCompiledScript(jactlScript);
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
}