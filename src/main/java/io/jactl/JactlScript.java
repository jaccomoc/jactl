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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A compiled JactlScript.
 */
public class JactlScript {

  private BiConsumer<Map<String,Object>,Consumer<Object>> script;
  private JactlContext jactlContext;

  JactlScript(JactlContext jactlContext, BiConsumer<Map<String,Object>,Consumer<Object>> script) {
    this.jactlContext = jactlContext;
    this.script        = script;
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
    var future = new CompletableFuture<Object>();
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
