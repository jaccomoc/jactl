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

import io.jactl.runtime.*;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A compiled JactlScript.
 */
public class JactlScript {

  private BiConsumer<Map<String, Object>, Consumer<Object>> asyncInvoker;
  private Function<Map<String, Object>, Object>             invoker;
  private JactlContext                                      jactlContext;
  private Class<?>                                          compiledClass;
  private boolean                                           isAsync;
  private volatile Constructor<?>                           scriptConstructor;
  private volatile MethodHandle                             scriptMainMethodHandle;

  private JactlScript(Class<?> compiledClass, JactlContext jactlContext, boolean isAsync) {
    this.compiledClass = compiledClass;
    this.jactlContext = jactlContext;
    this.isAsync = isAsync;
    init();

    // For async invocation where we call back on completion once finished
    this.asyncInvoker = (map, completion) -> {
      try {
        Object result = invoker.apply(map);
        completion.accept(result);
      }
      catch (Continuation c) {
        jactlContext.asyncWork(completion, c, c.scriptInstance);
      }
      catch (JactlError | IllegalStateException | IllegalArgumentException e) {
        completion.accept(e);
      }
      catch (Throwable t) {
        completion.accept(new IllegalStateException("Invocation error: " + t, t));
      }
    };
  }

  public static JactlScript createScript(Class<?> compiledClass, JactlContext context) {
    boolean async;
    try {
      compiledClass.getDeclaredMethod(Utils.JACTL_SCRIPT_MAIN, Continuation.class, Map.class);
      async = true;
    }
    catch (NoSuchMethodException e) {
      async = false;
    }
    return createScript(compiledClass, context, async);
  }

  public static JactlScript createScript(Class<?> compiledClass, JactlContext context, boolean isAsync) {
    return new JactlScript(compiledClass, context, isAsync);
  }

  /**
   * Used by eval()
   * @return the invoker
   */
  public Function<Map<String, Object>, Object> getInvoker() {
    init();
    return invoker;
  }
  
  private void init() {
    if (isAsync) {
      invoker = map -> {
        JactlScriptObject instance = null;
        try {
          instance = (JactlScriptObject) getScriptConstructor().newInstance();
          Object result = getScriptMainMethodHandle().invoke(instance, (Continuation) null, map);
          cleanUp(instance, jactlContext);
          return result;
        }
        catch (Continuation c) {
          throw c;
        }
        catch (RuntimeError e) {
          cleanUp(instance, jactlContext);
          throw e;
        }
        catch (Throwable e) {
          cleanUp(instance, jactlContext);
          throw new RuntimeException(e);
        }
      };
    }
    else {
      invoker = map -> {
        try {
          return getScriptMainMethodHandle().invoke(getScriptConstructor().newInstance(), map);
        }
        catch (RuntimeError e) {
          throw e;
        }
        catch (Throwable e) {
          throw new RuntimeException(e);
        }
      };
    }
  }

  public Constructor<?> getScriptConstructor() {
    Constructor<?> result = scriptConstructor;
    if (result == null) { 
      synchronized(this) {
        result = scriptConstructor;
        if (result == null) {
          try {
            scriptConstructor = result = compiledClass.getDeclaredConstructor();
          }
          catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
    return result;
  }

  public MethodHandle getScriptMainMethodHandle() {
    MethodHandle result = scriptMainMethodHandle;
    if (result == null) { 
      synchronized(this) {
        result = scriptMainMethodHandle;
        if (result == null) {
          try {
            Method method = isAsync ? compiledClass.getDeclaredMethod(Utils.JACTL_SCRIPT_MAIN, Continuation.class, Map.class)
                                    : compiledClass.getDeclaredMethod(Utils.JACTL_SCRIPT_MAIN, Map.class);
            MethodType methodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
            scriptMainMethodHandle = result = MethodHandles.publicLookup().findVirtual(compiledClass, Utils.JACTL_SCRIPT_MAIN, methodType);
          }
          catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Internal error: " + e, e);
          }
        }
      }
    }
    return result;
  }

  private static void cleanUp(JactlScriptObject instance, JactlContext context) {
    if (instance != null && instance._$j$isCheckpointed()) {
      context.deleteCheckpoint(instance._$j$getInstanceId(), instance._$j$checkpointId());
    }
  }
  
  public Class<?> getCompiledClass() {
    return compiledClass;
  }

  /**
   * <p>Run the script with the given global variables. When finished it will invoke the
   * completion with the result. The completion may or may not be invoked on the same
   * thread depending on whether the script has any asynchronous/blocking function calls.
   * </p>
   * @param globals     a Map of global variables and their values
   * @param input       Reader with input for the script (if it uses nextLine()) (can be null)
   * @param output      PrintStream where print/println output will go (can be null)
   * @param completion  code to be run once script finishes
   */
  public void run(Map<String,Object> globals, Reader input, PrintStream output, Consumer<Object> completion) {
    run(globals, input, output == null ? null : new PrintWriter(output), completion);
  }
  
  public void run(Map<String,Object> globals, Reader input, Writer output, Consumer<Object> completion) {
    RuntimeState.setState(jactlContext, globals, input, output);
    asyncInvoker.accept(globals, completion);
  }

  /**
   * <p>
   * Runs the script with the provided global variables, input, and output, returning
   * a future that completes with the script's result when it finishes executing.
   * </p>
   * @param globals     a Map of global variables and their values
   * @param input       Reader with input for the script (if it uses nextLine()) (can be null)
   * @param output      PrintStream where print/println output will go (can be null)
   * @return a {@link Future} that will be completed with the script's result
   */
  public Future<Object> run(Map<String,Object> globals, Reader input, PrintStream output) {
    CompletableFuture<Object> future = new CompletableFuture<>();
    jactlContext.executionEnv.scheduleEvent(null, () -> run(globals, input, output, future::complete));
    return future;
  }

  /**
   * <p>
   * Runs the script with the provided global variables, input, and output, returning
   * a future that completes with the script's result when it finishes executing.
   * </p>
   * @param globals     a Map of global variables and their values
   * @param input       Reader with input for the script (if it uses nextLine()) (can be null)
   * @param output      Writer where print/println output will go (can be null)
   *  @return a {@link Future} that will be completed with the script's result
   */
  public Future<Object> run(Map<String,Object> globals, Reader input, Writer output) {
    CompletableFuture<Object> future = new CompletableFuture<>();
    jactlContext.executionEnv.scheduleEvent(null, () -> run(globals, input, output, future::complete));
    return future;
  }

  /**
   * <p>Run the script with the given global variables. When finished it will invoke the
   * completion with the result. The completion may or may not be invoked on the same
   * thread depending on whether the script has any asynchronous/blocking function calls.
   * The globals should be a Map of global variable names whose values are simple Java
   * objects.</p>
   * @param globals     a Map of global variables and their values
   * @param completion  code to be run once script finishes
   */
  public void run(Map<String,Object> globals, Consumer<Object> completion) {
    run(globals, null, (Writer)null, completion);
  }

  /**
   * <p>
   * Runs the script with the provided global variables and return a future
   * that will return the script's result when it finishes executing.
   * </p>
   * @param globals     a Map of global variables and their values
   *  @return a {@link Future} that will be completed with the script's result
   */
  public Future<Object> run(Map<String,Object> globals) {
    CompletableFuture<Object> future = new CompletableFuture<>();
    jactlContext.executionEnv.scheduleEvent(null, () -> run(globals, future::complete));
    return future;
  }

  /**
   * <p>Run the script with the given global variables and wait for the result.</p>
   * <p>This will schedule the script invocation on an event-loop (non-blocking)
   * thread and wait for the script to complete before returning.</p>
   * <p>This should not be invoked if caller is already on an event-loop thread as
   * it blocks the current thread until the script completes.</p>
   * @param globals     a Map of global variables and their values
   * @return the result returned from the script
   * @deprecated Use {@link #eval(Map)} instead
   */
  public Object runSync(Map<String,Object> globals) {
    return eval(globals, null, (Writer)null);
  }

  /**
   * <p>Run the script with the given global variables and wait for the result.</p>
   * <p>This will schedule the script invocation on an event-loop (non-blocking)
   * thread and wait for the script to complete before returning.</p>
   * <p>This should not be invoked if caller is already on an event-loop thread as
   * it blocks the current thread until the script completes.</p>
   * @param globals     a Map of global variables and their values
   * @return the result returned from the script
   */
  public Object eval(Map<String,Object> globals) {
    return eval(globals, null, (Writer)null);
  }

  /**
   * <p>Run the script with the given global variables and wait for the result.</p>
   * <p>This will schedule the script invocation on an event-loop (non-blocking)
   * thread and wait for the script to complete before returning.</p>
   * <p>This should not be invoked if caller is already on an event-loop thread as
   * it blocks the current thread until the script completes.</p>
   * @param globals     a Map of global variables and their values
   * @param input       Reader with input for the script (if it uses nextLine()) (can be null)
   * @param output      PrintStream where print/println output will go (can be null)
   * @return the result returned from the script
   * @deprecated Use {@link #eval(Map, Reader, PrintStream)} instead
   */
  public Object runSync(Map<String,Object> globals, Reader input, PrintStream output) {
    return eval(globals, input, output == null ? null : new PrintWriter(output));
  }
  
  /**
   * <p>Run the script with the given global variables and wait for the result.</p>
   * <p>This will schedule the script invocation on an event-loop (non-blocking)
   * thread and wait for the script to complete before returning.</p>
   * <p>This should not be invoked if caller is already on an event-loop thread as
   * it blocks the current thread until the script completes.</p>
   * @param globals     a Map of global variables and their values
   * @param input       Reader with input for the script (if it uses nextLine()) (can be null)
   * @param output      PrintStream where print/println output will go (can be null)
   * @return the result returned from the script
   */
  public Object eval(Map<String,Object> globals, Reader input, PrintStream output) {
    return eval(globals, input, output == null ? null : new PrintWriter(output));
  }
  
  /**
   * <p>Run script with given global variables and wait for result.</p>
   * @param globals     a Map of global variables and their values
   * @param input       Reader with input for the script (if it uses nextLine()) (can be null)
   * @param output      PrintWriter where print/println output will go (can be null)
   * @return the result of the script
   */
  public Object eval(Map<String,Object> globals, Reader input, Writer output) {
    if (!isAsync) {
      // We can run directly on this thread and return the result since we know script won't
      // throw a Continuation for async functions
      RuntimeState.setState(jactlContext, globals, input, output);
      return invoker.apply(globals);
    }

    // Potentially async code so run on a separate thread and wait for result
    CompletableFuture<Object> future = new CompletableFuture<Object>();
    jactlContext.executionEnv.scheduleEvent(null, () -> run(globals, input, output, future::complete));
    try {
      Object result = future.get();
      if (result instanceof RuntimeException) {
        throw (RuntimeException)result;
      }
      return result;
    }
    catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Internal error: " + e.getMessage(), e);
    }
  }

  /**
   * <p>Run script with given global variables and wait for result.</p>
   * @param globals     a Map of global variables and their values
   * @param input       Reader with input for the script (if it uses nextLine()) (can be null)
   * @param output      PrintWriter where print/println output will go (can be null)
   * @return the result of the script
   * @deprecated Use {@link #eval(Map, Reader, Writer)} instead
   */
  public Object runSync(Map<String,Object> globals, Reader input, Writer output) {
    return eval(globals, input, output);
  }
}
