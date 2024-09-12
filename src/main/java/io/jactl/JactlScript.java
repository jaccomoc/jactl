/*
 * Copyright © 2022,2023 James Crawford
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

import io.jactl.runtime.Continuation;
import io.jactl.runtime.JactlScriptObject;
import io.jactl.runtime.RuntimeError;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A compiled JactlScript.
 */
public class JactlScript {

  private BiConsumer<Map<String,Object>,Consumer<Object>> script;
  private JactlContext jactlContext;

  public JactlScript(JactlContext jactlContext, BiConsumer<Map<String, Object>, Consumer<Object>> script) {
    this.jactlContext = jactlContext;
    this.script        = script;
  }

  public static JactlScript createScript(Function<Map<String, Object>, Object> invoker, JactlContext context) {
    return new JactlScript(context, (map,completion) -> {
      try {
        Object result = invoker.apply(map);
        completion.accept(result);
      }
      catch (Continuation c) {
        context.asyncWork(completion, c, c.scriptInstance);
      }
      catch (JactlError | IllegalStateException | IllegalArgumentException e) {
        completion.accept(e);
      }
      catch (Throwable t) {
        completion.accept(new IllegalStateException("Invocation error: " + t, t));
      }
    });
  }

  public static Function<Map<String,Object>,Object> createInvoker(Class clazz, JactlContext context) {
    try {
      Method       method     = Utils.findMethod(clazz, Utils.JACTL_SCRIPT_MAIN, false);
      MethodType   methodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
      MethodHandle mh         = MethodHandles.publicLookup().findVirtual(clazz, Utils.JACTL_SCRIPT_MAIN, methodType);
      boolean      isAsync    = method.getParameterTypes().length != 1;
      return map -> {
        JactlScriptObject instance = null;
        try {
          instance        = (JactlScriptObject)clazz.getDeclaredConstructor().newInstance();
          Object result   = isAsync ? mh.invoke(instance, (Continuation) null, map)
                                    : mh.invoke(instance, map);
          cleanUp(instance, context);
          return result;
        }
        catch (Continuation c) {
          throw c;
        }
        catch (RuntimeError e) {
          cleanUp(instance, context);
          throw e;
        }
        catch (Throwable e) {
          cleanUp(instance, context);
          throw new RuntimeException(e);
        }
      };
    }
    catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException("Internal error: " + e, e);
    }
  }

  private static void cleanUp(JactlScriptObject instance, JactlContext context) {
    if (instance.isCheckpointed()) {
      context.deleteCheckpoint(instance.getInstanceId(), instance.checkpointId());
    }
  }

  /**
   * <p>Run the script with the given global variables. When finished it will invoke the
   * completion with the result. The completion may or may not be invoked on the same
   * thread depending on whether the script has any asynchronous/blocking function calls.
   * The globals should be a Map of global variable names whose values are simple Java
   * objects.</p>
   * <p>Supported types for the values in the Map are:</p>
   * <ul>
   *   <li>Boolean</li>
   *   <li>Integer</li>
   *   <li>Long</li>
   *   <li>Double</li>
   *   <li>BigDecimal</li>
   *   <li>String</li>
   *   <li>List</li>
   *   <li>Map (where keys are Strings)</li>
   *   <li>arrays and multidimensional arrays of boolean, int, long, double, BigDecimal, String, List, Map</li>
   *   <li>null - Object with value null</li>
   * </ul>
   * <p>Also supported are object instances of Jactl classes that have been returned
   * from a previous script invocation.</p>
   * @param globals     a Map of global variables and their values
   * @param completion  code to be run once script finishes
   */
  public void run(Map<String,Object> globals, Consumer<Object> completion) {
    script.accept(globals, completion);
  }

  /**
   * <p>Run the script with the given global variables and wait for the result.</p>
   * <p>This will schedule the script invocation on an event-loop (non-blocking)
   * thread and wait for the script to complete before returning.</p>
   * <p>This should not be invoked if caller is already on an event-loop thread as
   * it blocks the current thread until the script completes.</p>
   * <p>Supported types for the values in the Map are:</p>
   * <ul>
   *   <li>Boolean</li>
   *   <li>Integer</li>
   *   <li>Long</li>
   *   <li>Double</li>
   *   <li>BigDecimal</li>
   *   <li>String</li>
   *   <li>List</li>
   *   <li>Map (where keys are Strings)</li>
   *   <li>arrays and multidimensional arrays of boolean, int, long, double, BigDecimal, String, List, Map</li>
   *   <li>null - Object with value null</li>
   * </ul>
   * <p>Also supported are object instances of Jactl classes that have been returned
   * from a previous script invocation.</p>
   * @param globals     a Map of global variables and their values
   * @return the result returned from the script
   */
  public Object runSync(Map<String,Object> globals) {
    CompletableFuture<Object> future = new CompletableFuture<Object>();
    jactlContext.executionEnv.scheduleEvent(null, () -> run(globals, future::complete));
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
}
