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

package io.jactl.benchmarks;

import io.jactl.*;
import io.jactl.runtime.BuiltinFunctions;
import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 3, time = 2)
@Fork(value = 1, jvmArgs={"-Xms1g", "-ea"})
//@Fork(value = 0)
public class SuspendResumeBenchmark {
  
  JactlContext jactlContext;
  JactlScript  script;
  JactlScript  sleepScript;
  
  private static String code =
    "var totals    = [:]\n" +
    "var itemCount = 0\n" +
    "var grandTotal = 0.0\n" +
    "var topCategory = ''\n" +
    "var topAmount = -1.0\n" +
    "var slept = 0\n" +
    "\n" +
    "def checkInventory(widget, count) {\n" +
    "  sleep(10, true) and slept++ if slept < sleepCount\n" +
    "  return true\n" +
    "}\n" +
    "\n" +
    "def processOrder(order) {" +
    "    var price    = order.price\n" +
    "    var qty      = order.quantity\n" +
    "    var category = order.category\n" +
    "\n" +
    "    return unless checkInventory(order.category, order.quantity)\n" +
    "\n" +
    "    var discount = 0.0\n" +
    "    if      (qty >= 100) { discount = 0.20 }\n" +
    "    else if (qty >=  50) { discount = 0.10 }\n" +
    "    else if (qty >=  20) { discount = 0.05 }\n" +
    "\n" +
    "    var lineTotal = price * qty * (1.0 - discount)\n" +
    "\n" +
    "    if (totals[category] == null) {\n" +
    "      totals[category] = 0.0\n" +
    "    }\n" +
    "    totals[category] = totals[category] + lineTotal\n" +
    "    grandTotal       = grandTotal + lineTotal\n" +
    "    itemCount        = itemCount + 1\n" +
    "\n" +
    "    if (totals[category] > topAmount) {\n" +
    "        topAmount   = totals[category]\n" +
    "        topCategory = category\n" +
    "    }\n" +
    "}\n" +
    "\n" +
    "for (order in orders) {\n" +
    "  processOrder(order)\n" +
    "}\n" +
    "\n" +
    "'Processed ' + itemCount + ' orders. Grand total: ' + grandTotal + '. Top category: ' + topCategory";

  private List<Map<String, Object>> ordersList;
  private Map<String, Object>       globals           = new HashMap<>();
  private JactlEnv jactlEnv;
  private String expected = "Processed 200 orders. Grand total: 927137.20. Top category: Books";
  
  @Setup(Level.Iteration)
  public void setup() {
    String[] categories = {"Electronics", "Clothing", "Food", "Books", "Sports"};
    final int ORDER_COUNT = 200;
    ordersList = new ArrayList<>(ORDER_COUNT);
    Random rnd = new Random(0);
    for (int i = 0; i < ORDER_COUNT; i++) {
      Map<String, Object> order = new HashMap<>();
      order.put("category", categories[i % categories.length]);
      order.put("price",    rnd.nextInt(200));
      order.put("quantity", rnd.nextInt(100));
      ordersList.add(order);
    }
    
    globals.put("orders", ordersList);
    globals.put("sleepCount", 0);

    jactlEnv = new DefaultEnv();
    jactlContext = JactlContext.create().environment(jactlEnv).build();
    script = Jactl.compileScript(code, globals, jactlContext);
    
    //jactlContext.debugLevel = 1;
    sleepScript = Jactl.compileScript("sleep(0, 3)", new HashMap<>(), jactlContext);
  }
  
  @TearDown(Level.Trial)
  public void tearDown() {
    if (jactlContext.isAsync) {
      DefaultEnv.shutdown();
    }
  }
  
  @Benchmark
  public Object suspend0() throws ExecutionException, InterruptedException {
    globals.put("sleepCount", 1);
    final int COUNT = 100;
    AtomicInteger counter = new AtomicInteger(COUNT);
    CompletableFuture<Object> future  = new CompletableFuture<>();
    for (int i = 0; i < COUNT; i++) {
      jactlEnv.scheduleEvent(null, () -> script.run(globals, result -> {
        if (counter.decrementAndGet() == 0) {
          future.complete(result);
        }
      }));
    }
    Object result = future.get();
    assert result.equals(expected);
    return result;
  }

  @Benchmark
  public Object suspend_0() throws ExecutionException, InterruptedException {
    globals.put("sleepCount", 0);
    Object result = script.eval(globals, jactlContext);
    assert result.equals(expected);
    return result;
  }

  @Benchmark
  public Object suspend1() throws ExecutionException, InterruptedException {
    globals.put("sleepCount", 1);
    CompletableFuture<Object> future = new CompletableFuture<>();
    script.run(globals, result -> future.complete(result));
    Object result = future.get();
    assert result.equals(expected);
    return result;
  }

  @Benchmark
  public Object suspend2() {
    globals.put("sleepCount", 2);
    String result = (String) script.eval(globals);
    assert result.equals("Processed 20 orders. Grand total: 22870.00. Top category: Electronics") : "Result error: " + result;
    return result;
  }

  @Benchmark
  public Object suspend5() {
    globals.put("sleepCount", 5);
    String result = (String) script.eval(globals);
    assert result.equals("Processed 20 orders. Grand total: 22870.00. Top category: Electronics") : "Result error: " + result;
    return result;
  }

  @Benchmark
  public Object suspend10() {
    globals.put("sleepCount", 10);
    String result = (String) script.eval(globals);
    assert result.equals("Processed 20 orders. Grand total: 22870.00. Top category: Electronics") : "Result error: " + result;
    return result;
  }
  
  @Benchmark
  public Object sleepBenchmark() throws ExecutionException, InterruptedException {
    CompletableFuture<Object> future = new CompletableFuture<>();
    sleepScript.run(globals, future::complete);
    Object result = future.get();
    assert (int)result == 3;
    return result;
  } 
}
